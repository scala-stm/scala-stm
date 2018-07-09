package scala.concurrent.stm
package compat

import scala.concurrent.stm.skel.{AtomicArray, AtomicArrayBuilder}

import scala.reflect.ClassTag
import scala.collection.{mutable, ClassTagSeqFactory, StrictOptimizedClassTagSeqFactory, SeqFactory, IterableOnce}

private[stm] trait AtomicArrayTemplate[T] extends mutable.IndexedSeqOps[T, AtomicArray, AtomicArray[T]] {
  protected def newBuilder: skel.AtomicArrayBuilder[T]
  override val iterableFactory: SeqFactory[AtomicArray] = AtomicArray.untagged

  // this should not be abstract: https://github.com/scala/bug/issues/11006
  def mapInPlace(f: T => T): this.type = {
    var i = 0
    while (i < length) {
      update(i, f(apply(i)))
      i = i + 1
    }
    this
  }
}

private[stm] trait AtomicArrayCompanion extends StrictOptimizedClassTagSeqFactory[AtomicArray] {

  val untagged: SeqFactory[AtomicArray] = new ClassTagSeqFactory.AnySeqDelegate(this)

  def empty[T](implicit m: ClassTag[T]): AtomicArray[T] =
    AtomicArray[T](0)

  def from[T](it: IterableOnce[T])(implicit m: ClassTag[T]): AtomicArray[T] =
    AtomicArray[T](it)

  def newBuilder[T](implicit m: ClassTag[T]): mutable.Builder[T, AtomicArray[T]] =
    AtomicArrayBuilder of m

  private[stm] def canBuildFromImpl[T](implicit m: ClassTag[T]): CompatBuildFrom[AtomicArray[_], T, AtomicArray[T]] = ()
}

private[stm] trait AtomicArrayBuilderTemplate[A] extends mutable.Builder[A, AtomicArray[A]]
