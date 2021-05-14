/* scala-stm - (c) 2009-2016, Stanford University, PPL */

package scala.concurrent.stm

import org.scalatest.Tag
import org.scalatest.funsuite.AnyFunSuite

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.stm.skel.SimpleRandom

/** Verifies that blocking STM operations can be interrupted.
 * Note: cannot run on Dotty because ScalaTest API bifurcates here
 */
class InterruptSuite extends AnyFunSuite {


  test("txn retry arriving interrupt") {
    delayedInterrupt(100)
    val x = Ref(0)
    intercept[InterruptedException] {
      atomic { implicit txn =>
        if (x() == 0) retry
      }
    }
  }

  test("txn retry pending interrupt") {
    Thread.currentThread.interrupt()
    val x = Ref(0)
    intercept[InterruptedException] {
      atomic { implicit txn =>
        if (x() == 0) retry
      }
    }
  }

  test("single await arriving interrupt") {
    delayedInterrupt(100)
    val x = Ref(0)
    intercept[InterruptedException] {
      x.single.await( _ != 0 )
    }
  }

  test("single await pending interrupt") {
    Thread.currentThread.interrupt()
    val x = Ref(0)
    intercept[InterruptedException] {
      x.single.await( _ != 0 )
    }
  }

  test("random interrupts during contention") {
    val refs = Array.tabulate(100)( _ => Ref(0) )
    val txnInterrupts = new AtomicInteger
    val nonTxnInterrupts = new AtomicInteger
    var failure = null : Throwable
    lazy val threads: Array[Thread] = Array.tabulate[Thread](10)( _ => new Thread {
      override def run(): Unit = {
        try {
          for (_ <- 0 until 10000) {
            try {
              atomic { implicit txn =>
                for (r <- refs) r() = r() + 1
              }
            } catch {
              case _: InterruptedException => txnInterrupts.incrementAndGet
            }
            for (r <- refs) {
              try {
                r.single += 1
              } catch {
                case _: InterruptedException => nonTxnInterrupts.incrementAndGet
              }
            }
            threads(SimpleRandom.nextInt(threads.length)).interrupt()
          }
        } catch {
          case x: Throwable => failure = x
        }
      }
    })
    for (t <- threads) t.start()
    for (t <- threads) t.join()
    if (failure != null)
      throw failure
    println(s"${txnInterrupts.get} txn rollbacks, ${nonTxnInterrupts.get} non-txn interrupts")
  }

  //////// machinery for InterruptSuite

  private val pendingInterrupts = new ThreadLocal[List[Thread]] {
    override def initialValue: List[Thread] = Nil
  }

  override protected def test(testName: String, testTags: Tag*)(f: => Any)(implicit pos: org.scalactic.source.Position): Unit = {
    super.test(testName, testTags: _*) {
      // we have to use another thread, because sbt overrides .interrupt() on
      // its worker threads
      var failure = null : Throwable
      val t = new Thread {
        override def run(): Unit = {
          try {
            f
          } catch {
            case x: Throwable => failure = x
          } finally {
            while (pendingInterrupts.get.nonEmpty) {
              try {
                pendingInterrupts.get.head.join()
                pendingInterrupts.set(pendingInterrupts.get.tail)
              } catch {
                case _: Throwable =>
              }
            }
            Thread.interrupted
          }
        }
      }
      t.start()
      t.join()
      if (failure != null)
        throw failure
    }
  }

  private def delayedInterrupt(delay: Long): Unit = { delayedInterrupt(Thread.currentThread, delay) }

  private def delayedInterrupt(target: Thread, delay: Long): Unit = {
    val t = new Thread {
      override def run(): Unit = {
        Thread.sleep(delay)
        target.interrupt()
      }
    }
    pendingInterrupts.set(t :: pendingInterrupts.get)
    t.start()
  }
}
