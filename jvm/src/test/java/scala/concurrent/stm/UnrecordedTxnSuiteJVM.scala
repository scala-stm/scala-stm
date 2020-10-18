/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm

import java.util.concurrent.CountDownLatch

import org.scalatest.funsuite.AnyFunSuite

class UnrecordedTxnSuiteJVM extends AnyFunSuite {
  test("read set emptied") {
    val b = new CountDownLatch(1)
    val e = new CountDownLatch(1)

    val x = Ref(0)

    new Thread {
      override def run(): Unit = {
        b.await()
        x.single() = 1
        e.countDown()
      }
    }.start()

    var tries = 0
    val (z1, z2) = atomic { implicit txn =>
      tries += 1
      val z1 = atomic.unrecorded { implicit txn => x() }
      b.countDown()
      e.await()
      (z1, x())
    }

    assert(z1 === 0)
    assert(z2 === 1)
    assert(tries === 1)
  }

  test("outerFailure handler") {

    val x = Ref(0)

    var z: Any = null
    intercept[TestException] {
      atomic { implicit txn =>
        val level = NestingLevel.root
        val done = new CountDownLatch(1)
        new Thread {
          override def run(): Unit = {
            level.requestRollback(Txn.UncaughtExceptionCause(new TestException))
            done.countDown()
          }
        }.start()
        done.await()

        z = atomic.unrecorded({ implicit txn => x() }, { cause => cause })
      }
    }

    assert(z.isInstanceOf[Txn.UncaughtExceptionCause])
  }
}