package scala.concurrent.stm
package compat

import scala.collection._
import scala.collection.{mutable, immutable}
import scala.language.higherKinds

private[stm] trait TMapViewCompanion extends MapFactory[TMap.View] {
  def from[A, B](it: IterableOnce[(A, B)]): TMap.View[A, B] = {
    val b = newBuilder[A, B]
    if (it.knownSize >= 0)
      b.sizeHint(it.knownSize)

    b ++= it
    b.result()
  }
  def canBuildFromImpl[A, B]: CompatBuildFrom[TMap.View[_, _], (A, B), TMap.View[A, B]] = ()
}

private[stm] trait TMapViewTemplate[A, B] extends mutable.MapOps[A, B, TMap.View, TMap.View[A, B]] {
  protected def newBuilder: mutable.Builder[(A, B), TMap.View[A, B]]
  override def mapFactory: MapFactory[TMap.View] = TMap.View
}

private[stm] trait TMapViewBuilder[A, B] extends mutable.Builder[(A, B), TMap.View[A, B]]

private[stm] trait HashTrieTMapBuilder[A, B] extends mutable.Builder[(A, B), TMap[A, B]]

private[stm] trait HashTrieTMapTemplate[A, B] extends mutable.Shrinkable[A]

private[stm] trait FrozenMutableMapTemplate[A, B] extends immutable.Map[A, B]

private[stm] trait TMapViaCloneTemplate[A, B] {
  def result(): TMap.View[A, B]
}
