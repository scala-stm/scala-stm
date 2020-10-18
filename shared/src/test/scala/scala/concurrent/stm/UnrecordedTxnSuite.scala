/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm

import org.scalatest.funsuite.AnyFunSuite

class UnrecordedTxnSuite extends AnyFunSuite {

  test("fixed unrecorded txn") {
    val z = atomic.unrecorded { implicit txn => "foo" }
    assert(z === "foo")
  }

  test("nested fixed unrecorded txn") {
    val x = Ref(0)
    val z = atomic { implicit txn =>
      x() = 1
      atomic.unrecorded { implicit txn => "foo" }
    }
    assert(z === "foo")
  }

  test("writing unrecorded txn") {
    val x = Ref(0)
    val z = atomic.unrecorded { implicit txn =>
      x() = 1
      "foo"
    }
    assert(z === "foo")
    assert(x.single() === 0)
  }

  test("nested unrecorded txn") {
    val x = Ref(0)
    val z = atomic.unrecorded { implicit txn =>
      x += 1
      atomic.unrecorded { implicit txn =>
        x += 1
        atomic.unrecorded { implicit txn =>
          x += 1
          atomic.unrecorded { implicit txn =>
            x += 1
            atomic.unrecorded { implicit txn =>
              x += 1
              atomic.unrecorded { implicit txn =>
                x += 1
                atomic.unrecorded { implicit txn =>
                  x()
                }
              }
            }
          }
        }
      }
    }
    assert(z === 6)
    assert(x.single() === 0)
  }

  test("nested new write unrecorded txn") {
    val x = Ref(0)
    val z = atomic { implicit txn =>
      atomic.unrecorded { implicit txn =>
        x() = 1
        "foo"
      }
    }
    assert(x.single() === 0)
    assert(z === "foo")
  }

  test("nested update unrecorded txn") {
    val x = Ref(0)
    val z = atomic { implicit txn =>
      x() = 1
      atomic.unrecorded { implicit txn =>
        x() = 2
        "foo"
      }
    }
    assert(x.single() === 1)
    assert(z === "foo")
  }

  test("nested preceding unrecorded txn") {
    val x = Ref(0)
    val z = atomic { implicit txn =>
      val z = atomic.unrecorded { implicit txn =>
        x() = 2
        "foo"
      }
      x() = 1
      z
    }
    assert(x.single() === 1)
    assert(z === "foo")
  }

  class TestException extends Exception
}