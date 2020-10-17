package scala.concurrent.stm

import scala.collection.mutable

package object compat {
  private[stm] type IterableOnce[+X] = scala.collection.TraversableOnce[X]
  private[stm] val  IterableOnce     = scala.collection.TraversableOnce

  private[stm] implicit class MutableMapExtensionMethods[A, B](val map: mutable.Map[A, B]) extends AnyVal {
    def filterInPlace(f: ((A, B)) => Boolean) = map.retain((a, b) => f((a, b)))
    def mapValuesInPlace(f: (A, B) => B): map.type = map.transform(f)
  }

  private[stm] implicit class MutableSetExtensionMethods[A](val set: mutable.Set[A]) extends AnyVal {
    def filterInPlace(f: A => Boolean) = set.retain(f)
  }

  private[stm] implicit class TMapExtensionMethods[A, B](val tmap: TMap[A, B]) extends AnyVal {
    def filterInPlace(f: ((A, B)) => Boolean)(implicit txn: InTxn): tmap.type = tmap.retain((a, b) => f((a, b)))
    def mapValuesInPlace(f: (A, B) => B)(implicit txn: InTxn): tmap.type = tmap.transform(f)
  }

  private[stm] implicit class TSetExtensionMethods[A](val tset: TSet[A]) extends AnyVal {
    def filterInPlace(f: A => Boolean)(implicit txn: InTxn): tset.type = tset.retain(f)
  }
}
