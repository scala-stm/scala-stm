package scala.concurrent.stm
package compat

import scala.collection.generic
import scala.collection.{mutable, immutable}
import scala.language.higherKinds

private[stm] trait TSetViewCompanion extends generic.MutableSetFactory[TSet.View] {
  def canBuildFromImpl[A]: CompatBuildFrom[TSet.View[_], A, TSet.View[A]] =
    setCanBuildFrom[A]
}

private[stm] trait TSetViewTemplate[A]
  extends mutable.Set[A]
  with mutable.SetLike[A, TSet.View[A]]
  with SetOps[A]
  with ClassNameProxy {

  override def empty: TSet.View[A] = TSet.empty[A].single
  override def companion: generic.GenericCompanion[TSet.View] = TSet.View
}

private[stm] trait TSetViewBuilder[A]
  extends mutable.Builder[A, TSet.View[A]]
  with GrowableProxy[A]

private[stm] trait HashTrieTSetBuilder[A]
  extends mutable.Builder[A, TSet[A]]
  with generic.Growable[A]
  with GrowableProxy[A]

private[stm] trait HashTrieTSetTemplate[A]
  extends generic.Shrinkable[A]
  with generic.Growable[A]
  with ShrinkableProxy[A]
  with GrowableProxy[A]

private[stm] trait FrozenMutableSetTemplate[A]
  extends immutable.Set[A]
  with ImmutableSetProxy[A]

private[stm] trait TSetViaCloneTemplate[A] { _ : skel.TSetViaClone[A] =>
  override protected[this] def newBuilder: TSet.View[A] = empty
}
