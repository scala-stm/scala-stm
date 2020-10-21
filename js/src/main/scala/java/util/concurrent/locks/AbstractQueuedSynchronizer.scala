// https://github.com/apache/harmony/blob/trunk/classlib/modules/concurrent/src/main/java/java/util/concurrent/locks/AbstractQueuedSynchronizer.java

/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.locks

/**
 * @since 1.5
 * @author Doug Lea
 */
object AbstractQueuedSynchronizer {
  private object Node {
    val SHARED = new Node

    // val EXCLUSIVE : Node = null

    final val CANCELLED = 1
    final val SIGNAL    = -1
    // final val CONDITION = -2
    final val PROPAGATE = -3
  }

  private final class Node {
    var waitStatus = 0

    var prev: Node = null
    var next: Node = null

    var thread    : Thread  = null
    private var nextWaiter: Node    = null

    /*
     * Returns true if node is waiting in shared mode.
     */
    def isShared: Boolean = nextWaiter eq Node.SHARED

    /*
     * Returns previous node, or throws NullPointerException if null.
     * Use when predecessor cannot be null.  The null check could
     * be elided, but is present to help the VM.
     *
     * @return the predecessor of this node
     */
    @throws[NullPointerException]
    def predecessor: Node = {
      val p = prev
      if (p == null) throw new NullPointerException
      else p
    }

    def this(thread: Thread, mode: Node) = {
      this()
      // Used by addWaiter
      this.nextWaiter = mode
      this.thread     = thread
    }

    def this(thread: Thread, waitStatus: Int) = {
      this()
      // Used by Condition
      this.waitStatus = waitStatus
      this.thread     = thread
    }
  }
}
abstract class AbstractQueuedSynchronizer {
  import AbstractQueuedSynchronizer.Node

  private var head: Node = null
  private var tail: Node = null

  protected def tryAcquireShared(arg: Int): Int     // = throw new UnsupportedOperationException
  protected def tryReleaseShared(arg: Int): Boolean // = throw new UnsupportedOperationException

  @throws[InterruptedException]
  def tryAcquireSharedNanos(arg: Int, nanosTimeout: Long): Boolean = {
    if (Thread.interrupted) throw new InterruptedException
    tryAcquireShared(arg) >= 0 || doAcquireSharedNanos(arg, nanosTimeout)
  }

  /*
   * Sets head of queue to be node, thus dequeuing. Called only by
   * acquire methods.  Also nulls out unused fields for sake of GC
   * and to suppress unnecessary signals and traversals.
   *
   * @param node the node
   */
  private def setHead(node: Node): Unit = {
    head = node
    node.thread = null
    node.prev   = null
  }

  /*
   * Sets head of queue, and checks if successor may be waiting
   * in shared mode, if so propagating if either propagate > 0 or
   * PROPAGATE status was set.
   *
   * @param node      the node
   * @param propagate the return value from a tryAcquireShared
   */
  private def setHeadAndPropagate(node: Node, propagate: Int): Unit = {
    val h = head // Record old head for check below
    setHead(node)
    /*
     * Try to signal next queued node if:
     *   Propagation was indicated by caller,
     *     or was recorded (as h.waitStatus either before
     *     or after setHead) by a previous operation
     *     (note: this uses sign-check of waitStatus because
     *      PROPAGATE status may transition to SIGNAL.)
     * and
     *   The next node is waiting in shared mode,
     *     or we don't know, because it appears null
     *
     * The conservatism in both of these checks may cause
     * unnecessary wake-ups, but only when there are multiple
     * racing acquires/releases, so most need signals now or soon
     * anyway.
     */
    if (propagate > 0 || h == null || h.waitStatus < 0) {
      val s = node.next
      if (s == null || s.isShared) doReleaseShared()
    }
  }

  /*
   * CAS waitStatus field of a node.
   */
  private def compareAndSetWaitStatus(node: Node, expect: Int, update: Int): Boolean =
    (node.waitStatus == expect) && {
      node.waitStatus = update
      true
    }

  /*
   * CAS next field of a node.
   */
  private def compareAndSetNext(node: Node, expect: Node, update: Node): Boolean =
    (node.next eq expect) && {
      node.next = update
      true
    }

  /*
   * Wakes up node's successor, if one exists.
   *
   * @param node the node
   */
  private def unparkSuccessor(node: Node): Unit = {
    /*
     * If status is negative (i.e., possibly needing signal) try
     * to clear in anticipation of signalling.  It is OK if this
     * fails or if status is changed by waiting thread.
     */
    val ws = node.waitStatus
    if (ws < 0) compareAndSetWaitStatus(node, ws, 0)
    /*
     * Thread to unpark is held in successor, which is normally
     * just the next node.  But if cancelled or apparently null,
     * traverse backwards from tail to find the actual
     * non-cancelled successor.
     */
    var s = node.next
    if (s == null || s.waitStatus > 0) {
      s = null
      var t = tail
      while ( {
        t != null && (t ne node)
      }) {
        if (t.waitStatus <= 0) s = t
        t = t.prev
      }
    }
    if (s != null) {
      () // SJSXXX LockSupport.unpark(s.thread)
    }
  }

  /*
   * Release action for shared mode -- signals successor and ensures
   * propagation. (Note: For exclusive mode, release just amounts
   * to calling unparkSuccessor of head if it needs signal.)
   */
  private def doReleaseShared(): Unit = {
    /*
     * Ensure that a release propagates, even if there are other
     * in-progress acquires/releases.  This proceeds in the usual
     * way of trying to unparkSuccessor of head if it needs
     * signal. But if it does not, status is set to PROPAGATE to
     * ensure that upon release, propagation continues.
     * Additionally, we must loop in case a new node is added
     * while we are doing this. Also, unlike other uses of
     * unparkSuccessor, we need to know if CAS to reset status
     * fails, if so rechecking.
     */
    while ({
      var continue = false
      val h = head
      if (h != null && (h ne tail)) {
        val ws = h.waitStatus
        if (ws == Node.SIGNAL) {
          if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) {
            continue = true
            // loop to recheck cases
          } else {
            unparkSuccessor(h)
          }
        } else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE)) {
          continue = true
          // loop on failed CAS
        }
      }
      var break = false
      if (!continue) {
        if (h eq head) { // loop if head changed
          break = true
        }
      }

      !break
    }) ()
  }

  /*
   * Checks and updates status for a node that failed to acquire.
   * Returns true if thread should block. This is the main signal
   * control in all acquire loops.  Requires that pred == node.prev.
   *
   * @param pred node's predecessor holding status
   * @param node the node
   * @return {@code true} if thread should block
   */
  private def shouldParkAfterFailedAcquire(pred: Node, node: Node): Boolean = {
    val ws = pred.waitStatus
    if (ws == Node.SIGNAL) {
      /*
       * This node has already set status asking a release
       * to signal it, so it can safely park.
       */
      return true
    }
    if (ws > 0) {
      var predV = pred
      /*
       * Predecessor was cancelled. Skip over predecessors and
       * indicate retry.
       */
      do {
        val pp    = predV.prev
        predV     = pp
        node.prev = pp
      } while ({
        predV.waitStatus > 0
      })
      predV.next = node
    }
    else {
      /*
       * waitStatus must be 0 or PROPAGATE.  Indicate that we
       * need a signal, but don't park yet.  Caller will need to
       * retry to make sure it cannot acquire before parking.
       */
      compareAndSetWaitStatus(pred, ws, Node.SIGNAL)
    }
    false
  }

  /*
   * The number of nanoseconds for which it is faster to spin
   * rather than to use timed park. A rough estimate suffices
   * to improve responsiveness with very short timeouts.
   */
  private val spinForTimeoutThreshold = 1000L

  /*
   * Acquires in shared timed mode.
   *
   * @param arg          the acquire argument
   * @param nanosTimeout max wait time
   * @return {@code true} if acquired
   */
  @throws[InterruptedException]
  private def doAcquireSharedNanos(arg: Int, nanosTimeout: Long): Boolean = {
    if (nanosTimeout <= 0L) return false
    val deadline = System.nanoTime + nanosTimeout
    val node = addWaiter(Node.SHARED)
    var failed = true
    try {
      while ( {
        true
      }) {
        val p = node.predecessor
        if (p eq head) {
          val r = tryAcquireShared(arg)
          if (r >= 0) {
            setHeadAndPropagate(node, r)
            p.next = null // help GC

            failed = false
            return true
          }
        }
        val nanosTimeoutV = deadline - System.nanoTime
        if (nanosTimeoutV <= 0L) return false
        if (shouldParkAfterFailedAcquire(p, node) && nanosTimeoutV > spinForTimeoutThreshold) {
          () // SJSXXX LockSupport.parkNanos(this, nanosTimeoutV)
        }
        if (Thread.interrupted) throw new InterruptedException
      }

      throw new Exception("Never here")

    } finally {
      if (failed) cancelAcquire(node)
    }
  }

  /*
   * Cancels an ongoing attempt to acquire.
   *
   * @param node the node
   */
  private def cancelAcquire(node: Node): Unit = { // Ignore if node doesn't exist
    if (node == null) return
    node.thread = null
    // Skip cancelled predecessors
    var pred = node.prev
    while ( {
      pred.waitStatus > 0
    }) {
      val pp = pred.prev
      pred      = pp
      node.prev = pp
    }
    // predNext is the apparent node to unsplice. CASes below will
    // fail if not, in which case, we lost race vs another cancel
    // or signal, so no further action is necessary.
    val predNext = pred.next
    // Can use unconditional write instead of CAS here.
    // After this atomic step, other Nodes can skip past us.
    // Before, we are free of interference from other threads.
    node.waitStatus = Node.CANCELLED
    // If we are the tail, remove ourselves.
    if ((node eq tail) && compareAndSetTail(node, pred)) compareAndSetNext(pred, predNext, null)
    else { // If successor needs signal, try to set pred's next-link
      // so it will get one. Otherwise wake it up to propagate.
      var ws = 0
      if ((pred ne head) &&
        ({ ws = pred.waitStatus; ws } == Node.SIGNAL ||
          (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) && pred.thread != null) {
        val next = node.next
        if (next != null && next.waitStatus <= 0) compareAndSetNext(pred, predNext, next)
      }
      else unparkSuccessor(node)
      node.next = node // help GC

    }
  }

  /*
   * CAS tail field. Used only by enq.
   */
  private def compareAndSetTail(expect: Node, update: Node) =
    (tail eq expect) && {
      tail = update
      true
    }

  /*
   * CAS head field. Used only by enq.
   */
  private def compareAndSetHead(update: Node): Boolean =
    (head eq null) && {
      head = update
      true
    }

  /*
   * Creates and enqueues node for current thread and given mode.
   *
   * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
   * @return the new node
   */
  private def addWaiter(mode: Node): Node = {
    val node = new Node(Thread.currentThread, mode)
    // Try the fast path of enq; backup to full enq on failure
    val pred = tail
    if (pred != null) {
      node.prev = pred
      if (compareAndSetTail(pred, node)) {
        pred.next = node
        return node
      }
    }
    enq(node)
    node
  }

  /*
   * Inserts node into queue, initializing if necessary. See picture above.
   *
   * @param node the node to insert
   * @return node's predecessor
   */
  private def enq(node: Node): Node = {
    while ({
      true
    }) {
      val t = tail
      if (t == null) { // Must initialize
        if (compareAndSetHead(new Node)) tail = head
      }
      else {
        node.prev = t
        if (compareAndSetTail(t, node)) {
          t.next = node
          return t
        }
      }
    }

    throw new Exception("Never here")
  }

  /*
   * Acquires in shared interruptible mode.
   *
   * @param arg the acquire argument
   */
  @throws[InterruptedException]
  private def doAcquireSharedInterruptibly(arg: Int): Unit = {
    val node = addWaiter(Node.SHARED)
    var failed = true
    try
      while ( {
        true
      }) {
        val p = node.predecessor
        if (p eq head) {
          val r = tryAcquireShared(arg)
          if (r >= 0) {
            setHeadAndPropagate(node, r)
            p.next = null // help GC

            failed = false
            return
          }
        }
        if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt) throw new InterruptedException
      }
    finally if (failed) cancelAcquire(node)
  }

  /*
   * Convenience method to park and then check if interrupted
   *
   * @return {@code true} if interrupted
   */
  private def parkAndCheckInterrupt: Boolean = {
    // LockSupport.park(this)
    // Thread.interrupted
    false // SJSXXX
  }

  /**
   * Acquires in shared mode, aborting if interrupted.  Implemented
   * by first checking interrupt status, then invoking at least once
   * `tryAcquireShared`, returning on success.  Otherwise the
   * thread is queued, possibly repeatedly blocking and unblocking,
   * invoking `tryAcquireShared` until success or the thread
   * is interrupted.
   *
   * @param arg the acquire argument.
   *            This value is conveyed to `tryAcquireShared` but is
   *            otherwise uninterpreted and can represent anything
   *            you like.
   * @throws InterruptedException if the current thread is interrupted
   */
  @throws[InterruptedException]
  def acquireSharedInterruptibly(arg: Int): Unit = {
    if (Thread.interrupted) throw new InterruptedException
    if (tryAcquireShared(arg) < 0) doAcquireSharedInterruptibly(arg)
  }

  /**
   * Atomically sets synchronization state to the given updated
   * value if the current state value equals the expected value.
   * This operation has memory semantics of a `volatile` read
   * and write.
   *
   * @param expect the expected value
   * @param update the new value
   * @return `true` if successful. False return indicates that the actual
   *         value was not equal to the expected value.
   */
  protected def compareAndSetState(expect: Int, update: Int): Boolean = { // See below for intrinsics setup to support this
    (state == expect) && {
      state = update
      true
    }
  }

  /**
   * Releases in shared mode.  Implemented by unblocking one or more
   * threads if `tryReleaseShared` returns true.
   *
   * @param arg the release argument.  This value is conveyed to
   *            `tryReleaseShared` but is otherwise uninterpreted
   *            and can represent anything you like.
   * @return the value returned from `tryReleaseShared`
   */
  def releaseShared(arg: Int): Boolean =
    (tryReleaseShared(arg)) && {
      doReleaseShared()
      true
    }

  /*
   * The synchronization state.
   */
  @volatile private var state = 0

  /**
   * Returns the current value of synchronization state.
   * This operation has memory semantics of a `volatile` read.
   *
   * @return current state value
   */
  protected def getState: Int = state

  /**
   * Sets the value of synchronization state.
   * This operation has memory semantics of a `volatile` write.
   *
   * @param newState the new state value
   */
  protected def setState(newState: Int): Unit =
    state = newState
}