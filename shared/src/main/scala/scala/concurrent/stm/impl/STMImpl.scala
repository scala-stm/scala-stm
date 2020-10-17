/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm
package impl

import java.util.concurrent.TimeUnit

private[impl] object STMImplHolder {
  var instance: STMImpl = STMImpl.createInstance()
}

/** `STMImpl` gathers all of the functionality required to plug an STM
 *  implementation into `scala.concurrent.stm`.  Only one implementation can
 *  be selected, because `Ref`s and atomic blocks from different STM
 *  implementations are not compatible.  `STMImpl.instance` returns the
 *  `STMImpl` instance that has been selected for this program execution.
 *
 *  There are two ways to explicitly select the `STMImpl` instance:
 *
 *  1. set the JVM system property "scala.stm.impl" to the name of a class
 *     that implements `STMImpl`; or
 *
 *  2. arrange for `STMImpl.select` or `STMImpl.trySelect` to be called
 *     before any `Ref`s are constructed and before any atomic blocks are
 *     executed.
 *
 *  Setting the JVM system property "scala.stm.impl" is equivalent to making a
 *  call to `STMImpl.select(System.getProperty("scala.stm.impl"))` before any
 *  other `STMImpl` selections.
 *
 *  If there is no explicitly selected `STMImpl` instance and the classpath
 *  contains a class `scala.concurrent.stm.impl.DefaultFactory` that extends
 *  `scala.concurrent.stm.impl.STMImpl.Factory`, then an instance of that
 *  class will be instantiated and used to generate the `STMImpl` instance.
 *  ScalaSTM implementations are encouraged to implement `DefaultFactory` so
 *  that if a user includes the implementation's JAR file, it will be
 *  automatically selected.
 *
 *  If no explicit selection has been made and there is no definition of
 *  `scala.concurrent.stm.impl.DefaultFactory` present in the classpath, then
 *  ScalaSTM will fall back to the reference implementation
 *  "scala.concurrent.stm.ccstm.CCSTM".
 *
 *  @author Nathan Bronson
 */
object STMImpl extends STMSelector {
  trait Factory {
    def createInstance(): STMImpl
  }

  /** Returns the instance of `STMImpl` that should be used to implement all
   *  ScalaSTM functionality.  Calling this method forces the implementation
   *  to be chosen if it has not already been selected.
   */
  def instance: STMImpl = STMImplHolder.instance
}

/** `STMImpl` gathers all of the functionality required to plug an STM
 *  implementation into `scala.concurrent.stm`.  See the `STMImpl` companion
 *  object for information on controlling which `STMImpl` is selected at run
 *  time.
 *
 *  @author Nathan Bronson
 */
trait STMImpl extends RefFactory with TxnContext with TxnExecutor {

  /** Returns a new commit barrier suitable for coordinating commits by this
   *  STM implementation.
   */
  def newCommitBarrier(timeout: Long, unit: TimeUnit): CommitBarrier
}
