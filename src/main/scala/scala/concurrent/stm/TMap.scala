/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm

import scala.concurrent.stm.compat._

import scala.collection.{immutable, mutable, generic}
import scala.language.implicitConversions

object TMap {

  object View extends TMapViewCompanion {

    implicit def canBuildFrom[A, B]: CompatBuildFrom[TMap.View[_, _], (A, B), TMap.View[A, B]] = canBuildFromImpl[A, B]

    def empty[A, B]: View[A, B] = TMap.empty[A, B].single

    override def newBuilder[A, B]: mutable.Builder[(A, B), TMap.View[A, B]] = new TMapViewBuilder[A, B] {
      private val underlying = TMap.newBuilder[A, B]

      def clear(): Unit = underlying.clear()
      def addOne(elem: (A, B)): this.type = { underlying += elem ; this }
      def result(): View[A, B] = underlying.result().single
    }
  }

  /** A `Map` that provides atomic execution of all of its methods. */
  trait View[A, B] extends mutable.Map[A, B] with TMapViewTemplate[A, B] with TxnDebuggable {

    /** Returns the `TMap` perspective on this transactional map, which
     *  provides map functionality only inside atomic blocks.
     */
    def tmap: TMap[A, B]

    def clone: TMap.View[A, B]

    /** Takes an atomic snapshot of this transactional map. */
    def snapshot: immutable.Map[A, B]

    override def empty: View[A, B] = TMap.empty[A, B].single

    override protected[this] def newBuilder: mutable.Builder[(A, B), View[A, B]] = View.newBuilder[A, B]

    override def className: String = "TMap"
  }

  /** Constructs and returns a new `TMap` that will contain the key/value pairs
   *  from `kvs`.
   */
  def apply[A, B](kvs: (A, B)*): TMap[A, B] = from(kvs, kvs.size)

  private[stm] def from[A, B](it: IterableOnce[(A, B)], sizeHint: Int): TMap[A, B] = {
    val b = TMap.newBuilder[A, B]
    if (sizeHint >= 0)
      b.sizeHint(sizeHint)
    b ++= it
    b.result()
  }

  /** Constructs and returns a new empty `TMap`. */
  def empty[A, B]: TMap[A, B] = impl.STMImpl.instance.newTMap[A, B]

  /** Returns a builder of `TMap`. */
  def newBuilder[A, B]: mutable.Builder[(A, B), TMap[A, B]] =
    impl.STMImpl.instance.newTMapBuilder[A, B]

  /** Allows a `TMap` in a transactional context to be used as a `Map`. */
  implicit def asMap[A, B](m: TMap[A, B])(implicit txn: InTxn): View[A, B] = m.single
}


/** A transactional map implementation that requires that all of its map-like
 *  operations be called from inside an atomic block.  Rather than extending
 *  `Map`, an implicit conversion is provided from `TMap` to `Map` if the
 *  current scope is part of an atomic block (see `TMap.asMap`).
 *
 *  The keys (with type `A`) must be immutable, or at least not modified while
 *  they are in the map.  The `TMap` implementation assumes that it can safely
 *  perform key equality and hash checks outside a transaction without
 *  affecting atomicity.
 *
 *  @author Nathan Bronson
 */
trait TMap[A, B] extends TxnDebuggable {

  /** Returns an instance that provides transactional map functionality without
   *  requiring that operations be performed inside the static scope of an
   *  atomic block.
   */
  def single: TMap.View[A, B]

  def clone(implicit txn: InTxn): TMap[A, B] = single.clone.tmap

  // The following method work fine via the asMap mechanism, but is important
  // enough that we don't want the implicit conversion to make it invisible to
  // ScalaDoc or IDE auto-completion

  def snapshot: immutable.Map[A, B] = single.snapshot

  // The following methods work fine via the asMap mechanism, but are heavily
  // used.  We add transactional versions of them to allow overrides to get
  // access to the InTxn instance without a ThreadLocal lookup.

  def isEmpty(implicit txn: InTxn): Boolean
  def size(implicit txn: InTxn): Int
  def foreach[U](f: ((A, B)) => U)(implicit txn: InTxn): Unit
  def contains(key: A)(implicit txn: InTxn): Boolean
  def apply(key: A)(implicit txn: InTxn): B
  def get(key: A)(implicit txn: InTxn): Option[B]
  def update(key: A, value: B)(implicit txn: InTxn): Unit = put(key, value)
  def put(key: A, value: B)(implicit txn: InTxn): Option[B]
  def remove(key: A)(implicit txn: InTxn): Option[B]

  // The following methods return the wrong receiver when invoked via the asMap
  // conversion.  They are exactly the methods of mutable.Map whose return type
  // is this.type.  Note that there are other methods of mutable.Map that we
  // allow to use the implicit mechanism, such as getOrElseUpdate(k).

  def += (kv: (A, B))(implicit txn: InTxn): this.type = { put(kv._1, kv._2) ; this }
  def += (kv1: (A, B), kv2: (A, B), kvs: (A, B)*)(implicit txn: InTxn): this.type = { this += kv1 += kv2 ++= kvs }
  def ++= (kvs: IterableOnce[(A, B)])(implicit txn: InTxn): this.type = { for (kv <- kvs.iterator) this += kv ; this }
  def -= (k: A)(implicit txn: InTxn): this.type = { remove(k) ; this }
  def -= (k1: A, k2: A, ks: A*)(implicit txn: InTxn): this.type = { this -= k1 -= k2 --= ks }
  def --= (ks: IterableOnce[A])(implicit txn: InTxn): this.type = { for (k <- ks.iterator) this -= k ; this }

  def transform(f: (A, B) => B)(implicit txn: InTxn): this.type

  @deprecated("Use .filterInPlace instead of .retain", "0.8")
  @`inline` final def retain(p: (A, B) => Boolean)(implicit txn: InTxn): this.type = filterInPlace(p.tupled)

  def filterInPlace(p: ((A, B)) => Boolean)(implicit txn: InTxn): this.type
}
