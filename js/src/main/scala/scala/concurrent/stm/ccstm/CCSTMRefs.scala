/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm
package ccstm

import java.util.concurrent.atomic.AtomicLong

import scala.collection.mutable
import scala.concurrent.stm.compat._
import scala.reflect.ClassTag

private[ccstm] object CCSTMRefs {
  
  trait Factory extends impl.RefFactory {
    def newRef(v0: Boolean): Ref[Boolean] = new BooleanRef(v0)
    def newRef(v0: Byte   ): Ref[Byte   ] = new ByteRef   (v0)
    def newRef(v0: Short  ): Ref[Short  ] = new ShortRef  (v0)
    def newRef(v0: Char   ): Ref[Char   ] = new CharRef   (v0)
    def newRef(v0: Int    ): Ref[Int    ] = new IntRef    (v0)
    def newRef(v0: Float  ): Ref[Float  ] = new FloatRef  (v0)
    def newRef(v0: Long   ): Ref[Long   ] = new LongRef   (v0)
    def newRef(v0: Double ): Ref[Double ] = new DoubleRef (v0)
    def newRef(v0: Unit   ): Ref[Unit   ] = new GenericRef(v0)
    def newRef[T: ClassTag](v0: T): Ref[T]= new GenericRef(v0)

    def newTxnLocal[A](init: => A,
                       initialValue: InTxn => A,
                       beforeCommit: InTxn => Unit,
                       whilePreparing: InTxnEnd => Unit,
                       whileCommitting: InTxnEnd => Unit,
                       afterCommit: A => Unit,
                       afterRollback: Txn.Status => Unit,
                       afterCompletion: Txn.Status => Unit): TxnLocal[A] = new TxnLocalImpl(
        init, initialValue, beforeCommit, whilePreparing, whileCommitting, afterCommit, afterRollback, afterCompletion)

    def newTArray[A: ClassTag](length: Int): TArray[A] = new TArrayImpl[A](length)
    def newTArray[A: ClassTag](xs: IterableOnce[A]): TArray[A] = new TArrayImpl[A](xs)

    def newTMap[A, B]: TMap[A, B] = skel.HashTrieTMap.empty[A, B]
    def newTMapBuilder[A, B]: mutable.Builder[(A, B), TMap[A, B]] = skel.HashTrieTMap.newBuilder[A, B]

    def newTSet[A]: TSet[A] = skel.HashTrieTSet.empty[A]
    def newTSetBuilder[A]: mutable.Builder[A, TSet[A]] = skel.HashTrieTSet.newBuilder[A]
  }

  private abstract class BaseRef[A] extends Handle[A] with RefOps[A] with ViewOps[A] {
    private[this] val metaAtom = new AtomicLong(0L)

    override final def meta: Long = metaAtom.get()

    override final def meta_=(value: Long): Unit = metaAtom.set(value)

    override final def metaCAS(m0: Long, m1: Long): Boolean = metaAtom.compareAndSet(m0, m1)

    override final def handle : Handle  [A] = this
    override final def single : Ref.View[A] = this
    override final def ref    : Ref     [A] = this

    override final def base       : AnyRef  = this
    override final def metaOffset : Int     = 0
    override final def offset     : Int     = 0

    override def dbgStr   : String  = super[RefOps].dbgStr
    override def dbgValue : Any     = super[RefOps].dbgValue
  }

  private class IntRef(@volatile var data: Int) extends BaseRef[Int] {
    override def += (rhs: Int)(implicit num: Numeric[Int]): Unit = incr(rhs)
    override def -= (rhs: Int)(implicit num: Numeric[Int]): Unit = incr(-rhs)

    private def incr(delta: Int): Unit =
      if (delta != 0) {
        InTxnImpl.dynCurrentOrNull match {
          case null => NonTxn.getAndAdd(handle, delta)
          case txn => txn.getAndAdd(handle, delta)
        }
      }
  }

  private class BooleanRef    (@volatile var data: Boolean) extends BaseRef[Boolean]
  private class ByteRef       (@volatile var data: Byte   ) extends BaseRef[Byte]
  private class ShortRef      (@volatile var data: Short  ) extends BaseRef[Short]
  private class CharRef       (@volatile var data: Char   ) extends BaseRef[Char]
  private class FloatRef      (@volatile var data: Float  ) extends BaseRef[Float]
  private class LongRef       (@volatile var data: Long   ) extends BaseRef[Long]
  private class DoubleRef     (@volatile var data: Double ) extends BaseRef[Double]
  private class GenericRef[A] (@volatile var data: A      ) extends BaseRef[A]
}
