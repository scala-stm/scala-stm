/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm

import org.scalatest.funsuite.AnyFunSuite


class TxnLocalSuiteJVM extends AnyFunSuite {
  test("isolation") {
    val barrier = new java.util.concurrent.CyclicBarrier(2)
    val tl = TxnLocal("init")
    var failure: Throwable = null
    new Thread {
      override def run(): Unit = {
        try {
          atomic { implicit txn =>
            barrier.await
            assert(tl() === "init")
            barrier.await
            tl() = "thread"
            barrier.await
            assert(tl() === "thread")
            barrier.await
          }
        } catch {
          case x: Throwable => failure = x
        }
        barrier.await
      }
    }.start()

    atomic { implicit txn =>
      barrier.await
      assert(tl() === "init")
      barrier.await
      tl() = "main"
      barrier.await
      assert(tl() === "main")
      barrier.await
    }
    barrier.await

    if (failure != null)
      throw failure
  }
}
