/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm

import org.scalatest.funsuite.AnyFunSuite

import java.util.concurrent.CountDownLatch

class CallbackSuiteJVM extends AnyFunSuite {

  test("retry in beforeCommit") {
    val n = 50
    val x = Ref(0)
    val b = Array.tabulate(n) { _ => new CountDownLatch(1) }
    val t = new Thread("trigger") {
      override def run(): Unit = {
        for (i <- 0 until n) {
          b(i).await()
          Thread.sleep(5)
          x.single() += 1
        }
      }
    }
    var tries = 0
    t.start()
    val y = Ref(0)
    atomic { implicit t =>
      tries += 1
      y() = 1
      Txn.beforeCommit { implicit t =>
        if (x() < n) {
          for (i <- 0 until math.min(n, tries))
            b(i).countDown()
          retry
        }
      }
    }
    assert(tries >= n)
  }

  test("afterRollback on rollback") {
    val x = Ref(10)
    var ran = false
    atomic { implicit t =>
      Txn.afterRollback { _ =>
        assert(!ran)
        ran = true
      }
      if (x() == 10) {
        val adversary = new Thread {
          override def run(): Unit = {
            x.single.transform(_ + 1)
          }
        }
        adversary.start()
        adversary.join()
        x()
        assert(false)
      }
    }
    assert(ran)
  }

  test("whileCommitting ordering", Slow) {
    val numThreads = 10
    val numPutsPerThread = 100000
    val startingGate = new java.util.concurrent.CountDownLatch(1)
    val active = Ref(numThreads)
    val failure = Ref(null : Throwable)

    val x = Ref(0)
    val notifier = new java.util.concurrent.LinkedTransferQueue[Int]()
    val EOF = -1

    for (_ <- 1 to numThreads) {
      new Thread {
        override def run(): Unit = {
          try {
            startingGate.await()
            for (i <- 1 to numPutsPerThread) {
              atomic { implicit txn =>
                x() = x() + 1
                val y = x()
                Txn.whileCommitting { _ =>
                  if ((i & 127) == 0) // try to perturb the timing
                    Thread.`yield`()
                  notifier.put(y)
                }
              }
            }
          } catch {
            case xx: Throwable => failure.single() = xx
          }
          if (active.single.transformAndGet(_ - 1) == 0)
            notifier.put(EOF)
        }
      }.start()
    }

    startingGate.countDown()
    for (expected <- 1 to numThreads * numPutsPerThread)
      assert(expected === notifier.take())
    assert(EOF === notifier.take())

    if (failure.single() != null)
      throw failure.single()
  }
}
