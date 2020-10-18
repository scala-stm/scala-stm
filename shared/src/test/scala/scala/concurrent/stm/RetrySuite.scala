/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm

import java.util.concurrent.TimeUnit

import org.scalatest.funsuite.AnyFunSuite

/** Contains extended tests of `retry`, `retryFor` and `tryAwait`.  Some basic
 *  tests are included in `TxnSuite`.
 */
class RetrySuite extends AnyFunSuite {

  def timingAssert(ok: Boolean): Unit =
    if (!ok) {
      val x = new Exception("timing-sensitive check failed, continuing")
      x.printStackTrace()
    }

  test("tryAwait is conservative") {
    val x = Ref(10)
    val t0 = System.currentTimeMillis
    assert(!x.single.tryAwait(100)( _ == 0 ))
    val elapsed = System.currentTimeMillis - t0
    assert(elapsed >= 100)
    println("tryAwait(.., 100) took " + elapsed + " millis")
  }

  test("tryAwait in atomic is conservative") {
    val x = Ref(10)
    val t0 = System.currentTimeMillis
    val f = atomic { implicit txn => x.single.tryAwait(100)( _ == 0 ) }
    assert(!f)
    val elapsed = System.currentTimeMillis - t0
    assert(elapsed >= 100)
    println("tryAwait(.., 100) inside atomic took " + elapsed + " millis")
  }

  test("retryFor is conservative") {
    val x = Ref(false)
    val t0 = System.currentTimeMillis
    val s = atomic { implicit txn =>
      if (!x()) retryFor(100)
      "timeout"
    }
    assert(s === "timeout")
    val elapsed = System.currentTimeMillis - t0
    assert(elapsed >= 100)
    println("retryFor(100) took " + elapsed + " millis")
  }

  test("retryFor earliest is first") {
    val x = Ref(false)
    val s = atomic { implicit txn =>
      if (!x()) retryFor(100)
      "first"
    } orAtomic { implicit txn =>
      if (!x()) retryFor(200)
      "second"
    }
    assert(s === "first")
  }

  test("retryFor earliest is second") {
    val x = Ref(false)
    val s = atomic { implicit txn =>
      if (!x()) retryFor(300)
      "first"
    } orAtomic { implicit txn =>
      if (!x()) retryFor(100)
      "second"
    }
    assert(s === "second")
  }

  test("retryFor earliest is first nested") {
    val x = Ref(false)
    val s = atomic { implicit txn =>
      atomic { implicit txn =>
        if (!x()) retryFor(100)
        "first"
      } orAtomic { implicit txn =>
        if (!x()) retryFor(200)
        "second"
      }
    }
    assert(s === "first")
  }

  test("retryFor earliest is second nested") {
    val x = Ref(false)
    val s = atomic { implicit txn =>
      atomic { implicit txn =>
        if (!x()) retryFor(300)
        "first"
      } orAtomic { implicit txn =>
        if (!x()) retryFor(100)
        "second"
      }
    }
    assert(s === "second")
  }

  test("retryFor only is first") {
    val x = Ref(false)
    val s = atomic { implicit txn =>
      if (!x()) retryFor(100)
      "first"
    } orAtomic { implicit txn =>
      if (!x()) retry
      "second"
    }
    assert(s === "first")
  }

  test("retryFor only is second") {
    val x = Ref(false)
    val s = atomic { implicit txn =>
      if (!x()) retry
      "first"
    } orAtomic { implicit txn =>
      if (!x()) retryFor(100)
      "second"
    }
    assert(s === "second")
  }

  test("retryFor ladder") {
    val buf = new StringBuilder
    val x = Ref(0)
    atomic { implicit txn =>
      buf += 'a'
      retryFor(1)
      buf += 'b'
      retryFor(1)
      buf += 'c'
      retryFor(0)
      buf += 'd'
      retryFor(1)
      buf += 'e'
      retryFor(1)
      buf += 'f'
      ()
    } orAtomic { implicit txn =>
      if (x() == 0) retry
    }
    assert(buf.toString === "aababcdabcdeabcdef")
  }

  test("retryFor as sleep") {
    val begin = System.currentTimeMillis
    atomic { implicit txn => retryFor(100) }
    val elapsed = System.currentTimeMillis - begin
    println("retryFor(100) as sleep took " + elapsed + " millis")
    assert(elapsed >= 100)
  }

  ///////////// CURSOR

  test("skipped retryFor deadline is retained") {
    val begin = System.currentTimeMillis
    atomic { implicit txn =>
      val f = atomic { implicit txn =>
        retryFor(50)
        false
      } orAtomic { implicit txn =>
        true
      }
      if (f)
        retryFor(1000)
    }
    val elapsed = System.currentTimeMillis - begin
    assert(elapsed < 500)
  }

  test("concatenated failing tryAwait") {
    val begin = System.currentTimeMillis
    val x = Ref(0)
    atomic { implicit txn =>
      x.single.tryAwait(50)( _ != 0 )
      x.single.tryAwait(50)( _ != 0 )
      x.single.tryAwait(50)( _ != 0 )
    }
    val elapsed = System.currentTimeMillis - begin
    assert(elapsed > 150)
    timingAssert(elapsed < 200)
  }

  test("futile retry should fail") {
    val x = true
    intercept[IllegalStateException] {
      atomic { implicit txn =>
        if (x)
          retry
      }
    }
  }

  test("withRetryTimeout") {
    val x = Ref(0)
    val t0 = System.currentTimeMillis
    intercept[InterruptedException] {
      atomic.withRetryTimeout(100000, TimeUnit.MICROSECONDS) { implicit txn =>
        if (x() == 0)
          retry
      }
    }
    val elapsed = System.currentTimeMillis - t0
    assert(elapsed >= 100)
    timingAssert(elapsed < 150)
  }

  test("retryFor wins over withRetryTimeout") {
    val x = Ref(0)
    val t0 = System.currentTimeMillis
    val f = atomic.withRetryTimeout(100) { implicit txn =>
      if (x() == 0) {
        retryFor(100)
        true
      } else
        false
    }
    assert(f)
    val elapsed = System.currentTimeMillis - t0
    assert(elapsed >= 100)
    timingAssert(elapsed < 150)
  }

  test("withRetryTimeout applies to retryFor") {
    val x = Ref(0)
    val t0 = System.currentTimeMillis
    intercept[InterruptedException] {
      atomic.withRetryTimeout(100) { implicit txn =>
        if (x() == 0)
          retryFor(101)
        assert(false)
      }
    }
    val elapsed = System.currentTimeMillis - t0
    assert(elapsed >= 100)
    timingAssert(elapsed < 150)
  }

  test("nested global withRetryTimeout") {
    val orig = TxnExecutor.defaultAtomic
    try {
      TxnExecutor.transformDefault( _.withRetryTimeout(100) )
      val x = Ref(0)
      val t0 = System.currentTimeMillis
      intercept[InterruptedException] {
        atomic { implicit txn =>
          atomic { implicit txn =>
            atomic { implicit txn =>
              if (x() == 0)
                retry
              assert(false)
            }
          }
        }
      }
      val elapsed = System.currentTimeMillis - t0
      println(elapsed)
      assert(elapsed >= 100)
      timingAssert(elapsed < 150)
    } finally {
      TxnExecutor.transformDefault( _ => orig )
    }
  }

  test("tighter timeout wins") {
    val t0 = System.currentTimeMillis
    intercept[InterruptedException] {
      atomic.withRetryTimeout(100) { implicit txn =>
        atomic.withRetryTimeout(1000) { implicit txn =>
          retry
        }
      }
    }
    val elapsed = System.currentTimeMillis - t0
    assert(elapsed >= 100)
    timingAssert(elapsed < 150)
  }
}
