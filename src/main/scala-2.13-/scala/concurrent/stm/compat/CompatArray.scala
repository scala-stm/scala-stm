package scala.concurrent.stm
package compat

import scala.concurrent.stm.skel.{AtomicArray, AtomicArrayBuilder}

import scala.reflect.ClassTag
import scala.collection.mutable
import scala.collection.generic.CanBuildFrom

private[stm] trait AtomicArrayTemplate[T]
  extends mutable.ArrayLike[T, AtomicArray[T]]
  with ClassNameProxy { array: AtomicArray[T] =>

  override protected[this] def thisCollection: AtomicArray[T] = array
  override protected[this] def toCollection(repr: AtomicArray[T]): AtomicArray[T] = repr
}

private[stm] trait AtomicArrayCompanion {
  private[stm] def canBuildFromImpl[T](implicit m: ClassTag[T]): CanBuildFrom[AtomicArray[_], T, AtomicArray[T]] = {
    new CanBuildFrom[AtomicArray[_], T, AtomicArray[T]] {
      def apply(from: AtomicArray[_]): mutable.Builder[T, AtomicArray[T]] = {
        val b = AtomicArrayBuilder of m
        b.sizeHint(from.length)
        b
      }
      def apply(): mutable.Builder[T, AtomicArray[T]] = {
        AtomicArrayBuilder of m
      }
    }
  }
}

private[stm] trait AtomicArrayBuilderTemplate[A]
  extends mutable.Builder[A, skel.AtomicArray[A]]
  with GrowableProxy[A]
