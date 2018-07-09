package scala.concurrent.stm
package compat

import scala.collection._
import scala.collection.{mutable, immutable}
import scala.language.higherKinds

private[stm] trait TSetViewCompanion extends IterableFactory[TSet.View] {
  def from[A](it: IterableOnce[A]): TSet.View[A] =
    (TSet.newBuilder[A] ++= it).result().single

  def canBuildFromImpl[A]: CompatBuildFrom[TSet.View[_], A, TSet.View[A]] = ()
}

private[stm] trait TSetViewTemplate[A] extends mutable.SetOps[A, TSet.View, TSet.View[A]] {
  protected def newBuilder: mutable.Builder[A, TSet.View[A]]
  override def empty: TSet.View[A] = TSet.View.empty[A]
  override def iterableFactory: IterableFactory[TSet.View] = TSet.View
}

private[stm] trait TSetViewBuilder[A] extends mutable.Builder[A, TSet.View[A]]

private[stm] trait HashTrieTSetBuilder[A] extends mutable.Builder[A, TSet[A]]

private[stm] trait HashTrieTSetTemplate[A] extends mutable.Shrinkable[A]

private[stm] trait FrozenMutableSetTemplate[A] extends immutable.Set[A]

private[stm] trait TSetViaCloneTemplate[A] {
  def result(): TSet.View[A]
}
