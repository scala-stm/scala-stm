/* scala-stm - (c) 2009-2011, Stanford University, PPL */

package scala.concurrent.stm

import org.scalatest.funsuite.AnyFunSuite

/** Tests of the relaxed validation methods `getWith` and `relaxedGet` in
 *  multi-threaded contexts.  Single-threaded tests are found in
 *  `IsolatedRefSuite` and more multi-threaded tests are embedded in
 *  `FlipperSuite`.
 */
class RelaxedValidationSuite extends AnyFunSuite {

  test("self-write vs getWith") {
    val x = Ref(0)
    atomic { implicit txn =>
      assert(x.getWith { _ & 1 } === 0)
      x() = 1
    }
    assert(x.single() === 1)
  }

  test("self-write vs failing transformIfDefined") {
    val x = Ref(0)
    atomic { implicit txn =>
      assert(!x.transformIfDefined {
        case 1 => 2
      })
      x() = 1
    }
    assert(x.single() === 1)
  }

  test("self-write vs relaxedGet") {
    val x = Ref(0)
    atomic { implicit txn =>
      assert(x.relaxedGet( _ == _ ) === 0)
      x() = 1
    }
    assert(x.single() === 1)
  }
}