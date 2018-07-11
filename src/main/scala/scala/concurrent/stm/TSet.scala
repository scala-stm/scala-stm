/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm

import scala.concurrent.stm.compat._

import scala.collection.{immutable, mutable, generic}
import scala.language.implicitConversions

object TSet {

  object View extends TSetViewCompanion {

    implicit def canBuildFrom[A]: CompatBuildFrom[TSet.View[_], A, TSet.View[A]] = canBuildFromImpl

    override def empty[A]: View[A] = TSet.empty[A].single

    override def newBuilder[A]: mutable.Builder[A, TSet.View[A]] = new TSetViewBuilder[A] {
      private val underlying = TSet.newBuilder[A]

      def clear(): Unit = underlying.clear()
      def addOne(elem: A): this.type = { underlying += elem ; this }
      def result(): View[A] = underlying.result().single
    }
  }

  /** A `Set` that provides atomic execution of all of its methods. */
  trait View[A] extends mutable.Set[A] with TSetViewTemplate[A] with TxnDebuggable {

    /** Returns the `TSet` perspective on this transactional set, which
     *  provides set functionality only inside atomic blocks.
     */
    def tset: TSet[A]

    def clone: TSet.View[A]

    /** Takes an atomic snapshot of this transactional set. */
    def snapshot: immutable.Set[A]

    override protected[this] def newBuilder: mutable.Builder[A, TSet.View[A]] = TSet.View.newBuilder[A]

    override def className: String = "TSet"
  }

  /** Constructs and returns a new `TSet` that will contain the elements from
   *  `xs`.
   */
  def apply[A](xs: A*): TSet[A] = from(xs, xs.size)

  private[stm] def from[A](it: IterableOnce[A], sizeHint: Int): TSet[A] = {
    val b = TSet.newBuilder[A]
    if (sizeHint >= 0)
      b.sizeHint(sizeHint)
    b ++= it
    b.result()
  }


  /** Constructs and returns a new empty `TSet`. */
  def empty[A]: TSet[A] = impl.STMImpl.instance.newTSet[A]

  /** Returns a builder of `TSet`. */
  def newBuilder[A]: mutable.Builder[A, TSet[A]] = impl.STMImpl.instance.newTSetBuilder[A]

  /** Allows a `TSet` in a transactional context to be used as a `Set`. */
  implicit def asSet[A](s: TSet[A])(implicit txn: InTxn): View[A] = s.single
}


/** A transactional set implementation that requires that all of its set-like
 *  operations be called from inside an atomic block.  Rather than extending
 *  `Set`, an implicit conversion is provided from `TSet` to `Set` if the
 *  current scope is part of an atomic block (see `TSet.asSet`).
 *
 *  The elements (with type `A`) must be immutable, or at least not modified
 *  while they are in the set.  The `TSet` implementation assumes that it can
 *  safely perform equality and hash checks outside a transaction without
 *  affecting atomicity.
 *
 *  @author Nathan Bronson
 */
trait TSet[A] extends TxnDebuggable {

  /** Returns an instance that provides transactional set functionality without
   *  requiring that operations be performed inside the static scope of an
   *  atomic block.
   */
  def single: TSet.View[A]

  def clone(implicit txn: InTxn): TSet[A] = single.clone.tset

  // Fast snapshots are one of TSet's core features, so we don't want the
  // implicit conversion to hide it from ScalaDoc and IDE completion
  def snapshot: immutable.Set[A] = single.snapshot

  // The following methods work fine via the asSet mechanism, but are heavily
  // used.  We add transactional versions of them to allow overrides.

  def isEmpty(implicit txn: InTxn): Boolean
  def size(implicit txn: InTxn): Int
  def foreach[U](f: A => U)(implicit txn: InTxn): Unit
  def contains(elem: A)(implicit txn: InTxn): Boolean
  def apply(elem: A)(implicit txn: InTxn): Boolean = contains(elem)
  def add(elem: A)(implicit txn: InTxn): Boolean
  def update(elem: A, included: Boolean)(implicit txn: InTxn): Unit = if (included) add(elem) else remove(elem)
  def remove(elem: A)(implicit txn: InTxn): Boolean

  // The following methods return the wrong receiver when invoked via the asSet
  // conversion.  They are exactly the methods of mutable.Set whose return type
  // is this.type.

  def += (x: A)(implicit txn: InTxn): this.type = { add(x) ; this }
  def += (x1: A, x2: A, xs: A*)(implicit txn: InTxn): this.type = { this += x1 += x2 ++= xs }
  def ++= (xs: IterableOnce[A])(implicit txn: InTxn): this.type = { for (x <- xs.iterator) this += x ; this }
  def -= (x: A)(implicit txn: InTxn): this.type = { remove(x) ; this }
  def -= (x1: A, x2: A, xs: A*)(implicit txn: InTxn): this.type = { this -= x1 -= x2 --= xs }
  def --= (xs: IterableOnce[A])(implicit txn: InTxn): this.type = { for (x <- xs.iterator) this -= x ; this }


  @deprecated("Use .filterInPlace instead of .retain", "0.8")
  @`inline` final def retain(p: A => Boolean)(implicit txn: InTxn): this.type = filterInPlace(p)

  def filterInPlace(p: A => Boolean)(implicit txn: InTxn): this.type
}
