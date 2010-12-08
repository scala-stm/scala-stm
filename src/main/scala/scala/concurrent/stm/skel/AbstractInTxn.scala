/* scala-stm - (c) 2009-2010, Stanford University, PPL */

package scala.concurrent.stm
package skel

import concurrent.stm.Txn.{RollbackCause, Status, ExternalDecider}
import collection.mutable.ArrayBuffer
import annotation.tailrec

private[stm] object AbstractInTxn {
  abstract class SuccessCallback[A, B] {
    protected def buffer(owner: A): ArrayBuffer[B => Unit]

    def add(owner: A, handler: B => Unit) { }
  }
}

private[stm] trait AbstractInTxn extends InTxn {
  import Txn._

  override def currentLevel: AbstractNestingLevel

  //////////// implementation of functionality for the InTxn implementer

  protected def requireActive() {
    currentLevel.status match {
      case Active =>
      case RolledBack(_) => throw RollbackError
      case s => throw new IllegalStateException(s.toString)
    }
  }

  protected def requireNotDecided() {
    currentLevel.status match {
      case s if !s.decided =>
      case RolledBack(_) => throw RollbackError
      case s => throw new IllegalStateException(s.toString)
    }
  }

  protected def requireNotCompleted() {
    currentLevel.status match {
      case s if !s.completed =>
      case RolledBack(_) => throw RollbackError
      case s => throw new IllegalStateException(s.toString)
    }
  }

  private var _decider: ExternalDecider = null
  protected def externalDecider = _decider

  /** Set to true if any callbacks are registered. */
  private var _callbacksPresent = false
  
  private val _beforeCommitList = new CallbackList[InTxn]
  private val _whileValidatingList = new CallbackList[NestingLevel]
  private val _whilePreparingList = new CallbackList[InTxnEnd]
  private val _whileCommittingList = new CallbackList[InTxnEnd]
  private val _afterCommitList = new CallbackList[Status]
  private val _afterRollbackList = new CallbackList[Status]

  /** Returns true if there are while-preparing handlers, while-committing
   *  handlers, or an external decider.
   */
  protected def writeResourcesPresent: Boolean = _callbacksPresent && writeResourcesPresentImpl

  private def writeResourcesPresentImpl: Boolean = {
    !_whilePreparingList.isEmpty || !_whileCommittingList.isEmpty || externalDecider != null
  }

  protected def fireBeforeCommitCallbacks() {
    if (_callbacksPresent)
      _beforeCommitList.fire(currentLevel, this)
  }

  protected def fireWhilePreparingCallbacks() {
    if (_callbacksPresent)
      _whilePreparingList.fire(currentLevel, this)
  }

  protected def checkpointCallbacks() {
    if (_callbacksPresent)
      checkpointCallbacksImpl()
  }

  private def checkpointCallbacksImpl() {
    val level = currentLevel
    level._beforeCommitSize = _beforeCommitList.size
    level._whileValidatingSize = _whileValidatingList.size
    level._whilePreparingSize = _whilePreparingList.size
    level._whileCommittingSize = _whileCommittingList.size
    level._afterCommitSize = _afterCommitList.size
    level._afterRollbackSize = _afterRollbackList.size
  }

  /** Returns the discarded `afterRollbackList` entries, or null if none */
  protected def rollbackCallbacks(): Array[Status => Unit] = {
    if (!_callbacksPresent) null else rollbackCallbacksImpl()
  }

  private def rollbackCallbacksImpl(): Array[Status => Unit] = {
    val level = currentLevel
    _beforeCommitList.size = level._beforeCommitSize
    _whileValidatingList.size = level._whileValidatingSize
    _whilePreparingList.size = level._whilePreparingSize
    _whileCommittingList.size = level._whileCommittingSize
    _afterCommitList.size = level._afterCommitSize
    _afterRollbackList.truncate(level._afterRollbackSize)
  }

  /** Returns the discarded `afterCommitList` entries, or null if none. */
  protected def resetCallbacks(): Array[Status => Unit] = {
    if (!_callbacksPresent) null else resetCallbacksImpl()
  }

  private def resetCallbacksImpl(): Array[Status => Unit] = {
    _beforeCommitList.size = 0
    _whileValidatingList.size = 0
    _whilePreparingList.size = 0
    _whileCommittingList.size = 0
    _afterRollbackList.size = 0
    _afterCommitList.truncate(0)
  }

  protected def fireWhileValidating() {
    val n = _whileValidatingList.size
    if (n > 0)
      fireWhileValidating(n - 1, currentLevel)
  }

  @tailrec private def fireWhileValidating(i: Int, level: AbstractNestingLevel) {
    if (i >= 0) {
      if (i < level._whileValidatingSize)
        fireWhileValidating(i, level.parLevel)
      else if (level.status ne Txn.Active)
        fireWhileValidating(level._whileValidatingSize - 1, level.parLevel) // skip the rest at this level
      else {
        try {
          _whileValidatingList(i)(level)
        } catch {
          case x => level.requestRollback(UncaughtExceptionCause(x))
        }
        fireWhileValidating(i - 1, level)
      }
    }
  }

  //////////// implementation of functionality for the InTxn user

  override def rootLevel: AbstractNestingLevel = currentLevel.root

  def beforeCommit(handler: InTxn => Unit) {
    requireActive()
    _callbacksPresent = true
    _beforeCommitList += handler
  }

  def whileValidating(handler: NestingLevel => Unit) {
    requireActive()
    _callbacksPresent = true
    _whileValidatingList += handler
  }

  def whilePreparing(handler: InTxnEnd => Unit) {
    requireNotDecided()
    _callbacksPresent = true
    _whilePreparingList += handler
  }

  def whileCommitting(handler: InTxnEnd => Unit) {
    requireNotCompleted()
    _callbacksPresent = true
    _whileCommittingList += handler
  }

  def afterCommit(handler: Status => Unit) {
    requireNotCompleted()
    _callbacksPresent = true
    _afterCommitList += handler
  }

  def afterRollback(handler: Status => Unit) {
    requireNotCompleted()
    _callbacksPresent = true
    _afterRollbackList += handler
  }

  def afterCompletion(handler: Status => Unit) {
    requireNotCompleted()
    _callbacksPresent = true
    _afterCommitList += handler
    _afterRollbackList += handler
  }

  def setExternalDecider(decider: ExternalDecider) {
    if (status.decided)
      throw new IllegalArgumentException("can't set ExternalDecider after decision, status = " + status)

    if (_decider != null) {
      if (_decider != decider)
        throw new IllegalArgumentException("can't set two different ExternalDecider-s in the same top-level atomic block")
    } else {
      _decider = decider
      // if this nesting level rolls back then the decider should be unregistered
      afterRollback { status =>
        assert(_decider eq decider)
        _decider = null
      }
    }
  }
}