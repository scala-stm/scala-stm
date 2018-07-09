package scala.concurrent.stm

import scala.collection.mutable

package object compat {
  implicit class MutableMapExtensionMethods[A, B](val map: mutable.Map[A, B]) extends AnyVal {
    def mapValuesInPlace(f: (A, B) => B): map.type =
      map.mapInPlace{ case (a, b) => (a, f(a, b)) }
  }
  // for < 2.12, the canBuildFrom instance needs to be directly in the companion object (for exmaple TMap.scala),
  // because the implicit priority needs to be the highest
  // for 2.13, we don't want to provide a CanBuildFrom instance since it's already available via
  // the BuildFrom companion
  private[stm] type CompatBuildFrom[-From, -A, +C] = Unit
}
