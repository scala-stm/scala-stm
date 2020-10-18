/* scala-stm - (c) 2009-2016, Stanford University, PPL */

package scala.concurrent.stm

import org.scalatest.funsuite.AnyFunSuite

class CommitBarrierSuite extends AnyFunSuite {

  test("single member commit") {
    val x = Ref(0)
    val cb = CommitBarrier(Long.MaxValue)
    val m = cb.addMember()
    val z = m.atomic { implicit t =>
      x() = x() + 1
      "result"
    }
    assert(z === Right("result"))
    assert(x.single() === 1)
  }

  test("single member cancel") {
    val x = Ref(0)
    val cb = CommitBarrier(60000)
    val m = cb.addMember()
    val z = m.atomic { implicit t =>
      m.cancel(CommitBarrier.UserCancel("cancel"))
      x() = x() + 1
      "result"
    }
    assert(z === Left(CommitBarrier.UserCancel("cancel")))
    assert(x.single() === 0)

    // commit barrier can still be used
    val m2 = cb.addMember()
    val z2 = m2.atomic { implicit t =>
      x() = x() + 1
      "result2"
    }
    assert(z2 === Right("result2"))
    assert(x.single() === 1)
  }

  test("single member failure") {
    val x = Ref(0)
    val cb = CommitBarrier(60000)
    val m = cb.addMember()
    intercept[Exception] {
      m.atomic { implicit t =>
        x() = x() + 1
        throw new Exception
      }
    }
    assert(x.single() === 0)

    // commit barrier is now dead
    intercept[IllegalStateException] {
      cb.addMember()
    }
  }

  test("override executor") {
    val x = Ref(0)
    val cb = CommitBarrier(60000)
    val m = cb.addMember()
    m.executor = m.executor.withRetryTimeout(10)
    intercept[InterruptedException] {
      m.atomic { implicit txn =>
        if (x() == 0)
          retry
        x() = 10
      }
    }
    assert(x.single() === 0)

    // commit barrier is now dead
    intercept[IllegalStateException] {
      cb.addMember()
    }
  }

  test("embedded orAtomic") {
    val x = Ref(0)
    val y = Ref(0)
    val z = CommitBarrier(60000).addMember().atomic { implicit txn =>
      atomic { implicit txn =>
        y() = 1
        if (x() == 0)
          retry
        "first"
      } orAtomic { implicit txn =>
        x() = 1
        if (y() == 1)
          retry
        "second"
      }
    }
    assert(z === Right("second"))
    assert(x.single() === 1)
    assert(y.single() === 0)
  }
}
