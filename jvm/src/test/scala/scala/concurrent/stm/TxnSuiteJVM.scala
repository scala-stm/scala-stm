/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm

import org.scalatest.funsuite.AnyFunSuite

import java.util.concurrent.CountDownLatch

class TxnSuiteJVM extends AnyFunSuite {
  test("basic retry") {
    val x = Ref(0)
    val y = Ref(false)
    val b = new CountDownLatch(1)
    new Thread {
      override def run(): Unit = {
        b.await()
        Thread.sleep(10)
        y.single() = true
        x.single() = 1
      }
    }.start()

    atomic { implicit txn =>
      b.countDown()
      if (x() == 0)
        retry
    }
    assert(y.single())
  }

  test("nested retry") {
    val x = Ref(0)
    val y = Ref(false)
    val b = new CountDownLatch(1)
    new Thread {
      override def run(): Unit = {
        b.await()
        Thread.sleep(10)
        y.single() = true
        x.single() = 1
      }
    }.start()

    atomic { implicit txn =>
      atomic { implicit txn =>
        // this will cause the nesting to materialize
        NestingLevel.current

        b.countDown()
        if (x() == 0)
          retry
      }
    }
    assert(y.single())
  }

  test("atomic.oneOf") {
    val refs = Array(Ref(false), Ref(false), Ref(false))
    for (w <- 0 until 3) {
      new Thread("wakeup") {
        override def run(): Unit = {
          Thread.sleep(200)
          refs(w).single() = true
        }
      }.start()
      oneOfExpect(refs, w, Array(0))
    }
  }

  test("nested atomic.oneOf") {
    val refs = Array(Ref(false), Ref(false), Ref(false))
    for (w <- 0 until 3) {
      new Thread("wakeup") {
        override def run(): Unit = {
          Thread.sleep(200)
          refs(w).single() = true
        }
      }.start()
      val retries = Array(0)
      atomic { implicit txn => oneOfExpect(refs, w, retries) }
    }
  }

  test("alternative atomic.oneOf") {
    val a = Ref(0)
    val refs = Array(Ref(false), Ref(false), Ref(false))
    for (w <- 0 until 3) {
      new Thread("wakeup") {
        override def run(): Unit = {
          Thread.sleep(200)
          refs(w).single() = true
        }
      }.start()
      val retries = Array(0)
      val f = atomic { implicit txn =>
        if (a() == 0)
          retry
        false
      } orAtomic { implicit txn =>
        oneOfExpect(refs, w, retries)
        true
      }
      assert(f)
    }
  }

  test("partial rollback due to invalid read") {
    val x = Ref(0)
    val y = Ref(0)

    new Thread {
      override def run(): Unit = {
        Thread.sleep(100); y.single() = 1
      }
    }.start()

    atomic { implicit t =>
      x()
      atomic { implicit t =>
        y()
        Thread.sleep(200)
        y()
      } orAtomic { implicit t =>
        throw new Error("should not be run")
      }
    } orAtomic { implicit t =>
      throw new Error("should not be run")
    }
  }

  test("partial rollback of invalid read") {
    val x = Ref(0)
    var xtries = 0
    val y = Ref(0)
    var ytries = 0

    new Thread {
      override def run(): Unit = {
        Thread.sleep(100); y.single() = 1
      }
    }.start()

    atomic { implicit txn =>
      xtries += 1
      x += 1
      atomic { implicit txn =>
        ytries += 1
        y()
        Thread.sleep(200)
        y()
      } orAtomic { implicit txn =>
        throw new Error("should not be run")
      }
    }

    // We can't assert, because different STMs might do different things.
    // For CCSTM it should be 1, 2
    println(s"xtries = $xtries, ytries = $ytries")
  }

  test("await") {
    val x = Ref(0)

    new Thread {
      override def run(): Unit = {
        Thread.sleep(50)
        x.single() = 1
        Thread.sleep(50)
        x.single() = 2
      }
    }.start()

    x.single.await( _ == 2 )
    assert(x.single() === 2)
  }

  test("remote cancel") {
    val x = Ref(0)

    val finished = atomic { implicit txn =>
      x += 1
      NestingLevel.current
    }
    assert(x.single() === 1)

    for (_ <- 0 until 100) {
      intercept[UserException] {
        atomic { implicit txn =>
          val active = NestingLevel.current
          new Thread {
            override def run(): Unit = {
              val cause = Txn.UncaughtExceptionCause(new UserException)
              assert(finished.requestRollback(cause) === Txn.Committed)
              assert(active.requestRollback(cause) == Txn.RolledBack(cause))
            }
          }.start()

          while (true)
            x() = x() + 1
        }
      }
      assert(x.single() === 1)
    }
  }

  test("remote cancel of root") {
    val x = Ref(0)

    val finished = atomic { implicit txn =>
      x += 1
      NestingLevel.current
    }
    assert(x.single() === 1)

    for (_ <- 0 until 100) {
      intercept[UserException] {
        atomic { implicit txn =>
          // this is to force true nesting for CCSTM, but the test should pass for any STM
          atomic { implicit txn => NestingLevel.current }

          val active = NestingLevel.current
          new Thread {
            override def run(): Unit = {
              Thread.`yield`()
              Thread.`yield`()
              val cause = Txn.UncaughtExceptionCause(new UserException)
              assert(finished.requestRollback(cause) === Txn.Committed)
              assert(active.requestRollback(cause) == Txn.RolledBack(cause))
            }
          }.start()

          while (true)
            atomic { implicit txn => x += 1 }
        }
      }
      assert(x.single() === 1)
    }
  }

  test("remote cancel of child") {
    val x = Ref(0)

    for (_ <- 0 until 100) {
      intercept[UserException] {
        atomic { implicit txn =>
          atomic { implicit txn =>
            val active = NestingLevel.current
            new Thread {
              override def run(): Unit = {
                Thread.`yield`()
                Thread.`yield`()
                val cause = Txn.UncaughtExceptionCause(new UserException)
                assert(active.requestRollback(cause) == Txn.RolledBack(cause))
              }
            }.start()

            while (true)
              x() = x() + 1
          }
        }
      }
      assert(x.single() === 0)
    }
  }

  test("many simultaneous Txns", Slow) {
    // CCSTM supports 2046 simultaneous transactions
    val threads = Array.tabulate(2500) { _ => new Thread {
      override def run(): Unit = { atomic { implicit txn => Thread.sleep(1000) } }
    }}
    val begin = System.currentTimeMillis
    for (t <- threads) t.start()
    for (t <- threads) t.join()
    val elapsed = System.currentTimeMillis - begin
    println(s"${threads.length} empty sleep(1000) txns took $elapsed millis")
  }

  private def oneOfExpect(refs: Array[Ref[Boolean]], which: Int, sleeps: Array[Int]): Unit = {
    val result = Ref(-1)
    atomic.oneOf(
      { (t: InTxn) => implicit val txn: InTxn = t; result() = 0 ; if (!refs(0)()) retry },
      { (t: InTxn) => implicit val txn: InTxn = t; if (refs(1)()) result() = 1 else retry },
      { (t: InTxn) => implicit val txn: InTxn = t; if (refs(2)()) result() = 2 else retry },
      { (t: InTxn) => implicit val txn: InTxn = t; sleeps(0) += 1 ; retry }
    )
    refs(which).single() = false
    assert(result.single.get === which)
    if (sleeps(0) != 0)
      assert(sleeps(0) === 1)
  }

  class UserException extends Exception
}
