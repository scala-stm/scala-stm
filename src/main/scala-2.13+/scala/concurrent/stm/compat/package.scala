package scala.concurrent.stm

import scala.collection.mutable

package object compat {
  // This is temporary and should be available in 2.13.0-M5
  implicit class MutableMapExtensionMethods[A, B](val map: mutable.Map[A, B]) extends AnyVal {
    def mapValuesInPlace(f: (A, B) => B): map.type =
      map.mapInPlace{ case (a, b) => (a, f(a, b)) }
  }
}
