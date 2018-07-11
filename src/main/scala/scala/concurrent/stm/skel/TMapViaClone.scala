/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm
package skel

import scala.concurrent.stm.compat._

import scala.collection.{immutable, mutable}

private[stm] object TMapViaClone {
  class FrozenMutableMap[A, B](self: mutable.Map[A, B]) extends FrozenMutableMapTemplate[A, B] {
    override def isEmpty: Boolean = self.isEmpty
    override def size: Int = self.size
    def get(key: A): Option[B] = self.get(key)
    def iterator: Iterator[(A, B)] = self.iterator
    override def foreach[U](f: ((A, B)) => U): Unit = { self foreach f }
    override def updated[B1 >: B](key: A, value: B1): immutable.Map[A, B1] =
        new FrozenMutableMap(self.clone().asInstanceOf[mutable.Map[A, B1]] += ((key, value)))
    def remove(key: A): immutable.Map[A, B] = new FrozenMutableMap(self.clone() -= key)
  }
}

/** Provides an implementation for the bulk of the functionality of `TMap` and
 *  `TMap.View` by making extensive use of `clone()`.  Assumes that the
 *  underlying implementation of `clone()` is O(1).
 *
 *  @author Nathan Bronson
 */
private[stm] trait TMapViaClone[A, B] extends TMap.View[A, B] with TMap[A, B] with TMapViaCloneTemplate[A, B] {

  import TMapViaClone._

  // Implementations may be able to do better.
  override def snapshot: immutable.Map[A, B] = new FrozenMutableMap(clone())

  def tmap: TMap[A, B] = this
  def single: TMap.View[A, B] = this

  /** Something like `"TMap[size=1]((1 -> 10))"`, stopping after 1K chars */
  def dbgStr: String = atomic.unrecorded({ _ =>
    mkStringPrefix("TMap", single.view.map { kv => kv._1 + " -> " + kv._2 })
  }, { _.toString })

  /** Returns an array of key/value pairs, since that is likely to be the
   *  easiest to examine in a debugger.  Also, this avoids problems with
   *  relying on copy-on-write after discarding `Ref` writes.
   */
  def dbgValue: Any = atomic.unrecorded({ _ => toArray }, { x => x })

  //////////// builder functionality

  override def result(): TMap.View[A, B] = this

  //////////// construction of new TMaps

  // A cheap clone means that mutable.MapLike's implementations of +, ++,
  // -, and -- are all pretty reasonable.

  override def clone: TMap.View[A, B]

  //////////// atomic compound ops

  override def getOrElseUpdate(key: A, op: => B): B = {
    single.get(key) getOrElse {
      atomic { implicit txn =>
        tmap.get(key) getOrElse { val v = op ; tmap.put(key, v) ; v }
      }
    }
  }

  // wait for 2.13.0-M5, https://github.com/scala/bug/issues/10996
  // override
  // override def transform(f: (A, B) => B): this.type = {
  //   atomic { implicit txn =>
  //     for (kv <- tmap)
  //       tmap.update(kv._1, f(kv._1, kv._2))
  //   }
  //   this
  // }

  override def filterInPlace(p: ((A, B)) => Boolean): this.type = {
    atomic { implicit txn =>
      for (kv <- tmap)
        if (!p(kv._1, kv._2))
          tmap -= kv._1
    }
    this
  }
}
