package scala.concurrent.stm
package compat

import scala.collection.GenTraversableLike
import scala.collection.generic
import scala.collection.immutable


private[stm] trait GrowableProxy[-A] { _: generic.Growable[A] =>
  def addOne(elem: A): this.type
  @`inline` override final def += (elem: A): this.type = addOne(elem)
}

private[stm] trait ShrinkableProxy[-A] { _: generic.Shrinkable[A] =>
  def subtractOne(elem: A): this.type
  @`inline` override final def -= (elem: A): this.type = subtractOne(elem)
}

private[stm] trait ClassNameProxy { _: GenTraversableLike[_, _] =>
  def className: String
  override def stringPrefix: String = className
}

private[stm] trait ImmutableSetProxy[A] { _: immutable.Set[A] =>
  def excl(elem: A): immutable.Set[A]
  @`inline` final def - (elem: A): immutable.Set[A] = excl(elem)

  def incl(elem: A): immutable.Set[A]
  @`inline` def + (elem: A): immutable.Set[A] = incl(elem)

}

private[stm] trait ImmutableMapProxy[A, B] { _: immutable.Map[A, B] =>
  def remove(key: A): immutable.Map[A, B]
  @`inline` final def - (key: A): immutable.Map[A, B] = remove(key)

  def updated[B1 >: B](key: A, value: B1): immutable.Map[A, B1]
  override def + [B1 >: B](kv: (A, B1)): immutable.Map[A, B1] = updated(kv._1, kv._2)
}

private[stm] trait MapOps[A, B] {
  def filterInPlace(p: ((A, B)) => Boolean): this.type
}

private[stm] trait SetOps[A] {
  def filterInPlace(p: A ⇒ Boolean): this.type
  def addOne(elem: A): this.type
}
