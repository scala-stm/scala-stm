package scala.concurrent.stm
package compat

import scala.collection.generic
import scala.collection.{mutable, immutable}
import scala.language.higherKinds

private[stm] trait TMapViewCompanion extends generic.MutableMapFactory[TMap.View] {
  override def apply[A, B](kvs: (A, B)*): TMap.View[A, B] = TMap(kvs: _*).single
  def canBuildFromImpl[A, B]: CompatBuildFrom[TMap.View[_, _], (A, B), TMap.View[A, B]] =
    new MapCanBuildFrom[A, B]
}

private[stm] trait TMapViewTemplate[A, B]
  extends mutable.MapLike[A, B, TMap.View[A, B]]
  with MapOps[A, B]
  with ClassNameProxy



private[stm] trait TMapViewBuilder[A, B]
  extends mutable.Builder[(A, B), TMap.View[A, B]]
  with GrowableProxy[(A, B)]

private[stm] trait HashTrieTMapBuilder[A, B]
  extends mutable.Builder[(A, B), TMap[A, B]]
  with GrowableProxy[(A, B)]


private[stm] trait HashTrieTMapTemplate[A, B]
  extends generic.Shrinkable[A]
  with generic.Growable[(A, B)]
  with ShrinkableProxy[A]
  with GrowableProxy[(A, B)]

private[stm] trait FrozenMutableMapTemplate[A, B]
  extends immutable.Map[A, B]
  with ImmutableMapProxy[A, B]

private[stm] trait TMapViaCloneTemplate[A, B] { _: skel.TMapViaClone[A, B] =>
  override protected[this] def newBuilder: TMap.View[A, B] = empty
}
