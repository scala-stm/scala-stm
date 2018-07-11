package scala.concurrent.stm

import scala.collection.mutable

import scala.collection.generic._
import scala.collection.GenTraversable
import scala.language.higherKinds

package object compat {
  private[stm] type IterableOnce[+X] = scala.collection.TraversableOnce[X]
  private[stm] val  IterableOnce     = scala.collection.TraversableOnce

  private[stm] implicit class TraversableOnceExtensionMethods[A](private val self: TraversableOnce[A]) extends AnyVal {
    def iterator: Iterator[A] = self.toIterator
  }

  private[stm] implicit class MutableMapExtensionMethods[A, B](val map: mutable.Map[A, B]) extends AnyVal {
    def mapValuesInPlace(f: (A, B) => B): map.type = map.transform(f)
    def filterInPlace(f: ((A, B)) => Boolean): map.type = map.retain((a, b) => f((a, b)))
  }

  private[stm] implicit class MutableSetExtensionMethods[A](val set: mutable.Set[A]) extends AnyVal {
    def filterInPlace(f: A => Boolean): set.type = {set.retain(f); set}
  }

  private[stm] implicit class IterableFactoryExtensionMethods[CC[X] <: GenTraversable[X]](private val fact: GenericCompanion[CC]) extends AnyVal {
    def from[A](source: TraversableOnce[A]): CC[A] = fact.apply(source.toSeq: _*)
  }

  private[stm] type CompatBuildFrom[-From, -A, +C] = CanBuildFrom[From, A, C]
}
