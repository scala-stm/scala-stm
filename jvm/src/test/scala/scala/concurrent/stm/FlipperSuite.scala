/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm

import org.scalatest.funsuite.AnyFunSuite

import java.util.concurrent.CyclicBarrier
import scala.collection.immutable


/** Flipper is derived from a test originally used for the ATLAS HTM prototype.
 *
 *  @author Nathan Bronson
 */
class FlipperSuite extends AnyFunSuite {
  val DEFAULT_SYNC_COUNT = 3
  val DEFAULT_TRANS_COUNT = 100
  val DEFAULT_INSTR_COUNT = 100
  val DEFAULT_THREAD_COUNT = 4
  val DEFAULT_WORD_COUNT = 4096
  val DEFAULT_FLIP_PROB = 0.5f
  val DEFAULT_REF_ARRAY_FACTORY: Int => collection.IndexedSeq[Ref[Int]] = { n =>
    Array.tabulate[Ref[Int]](n) { _ => Ref(0) }
  }

  val DEFAULT_MASKED_READER: (Ref[Int], Int) => Int = { (r, m) => r.single() & m }

  val TARRAY_REF_ARRAY_FACTORY: Int => immutable.IndexedSeq[Ref[Int]] = { n => TArray.ofDim[Int](n).refs }

  val GET_WITH_MASKED_READER: (Ref[Int], Int) => Int = { (r, m) => r.single.getWith( _ & m ) }
  val RELAXED_GET_MASKED_READER: (Ref[Int], Int) => Int = { (r, m) =>
    r.single.relaxedGet({ (seen, correct) => (seen & m) == (correct & m) }) & m
  }
  val TRANSFORM_IF_DEFINED_MASKED_READER: (Ref[Int], Int) => Int = { (r, m) =>
    val f = r.single.transformIfDefined { case v if (v & m) != 0 => v }
    if (f) m else 0
  }

  test("small flipper test") {
    Config(
      DEFAULT_SYNC_COUNT,
      DEFAULT_TRANS_COUNT / 2,
      DEFAULT_INSTR_COUNT / 2,
      DEFAULT_THREAD_COUNT,
      DEFAULT_WORD_COUNT / 2,
      DEFAULT_FLIP_PROB,
      0,
      DEFAULT_REF_ARRAY_FACTORY,
      DEFAULT_MASKED_READER).runTest()
  }

  test("small flipper test using getWith") {
    Config(
      DEFAULT_SYNC_COUNT,
      DEFAULT_TRANS_COUNT / 2,
      DEFAULT_INSTR_COUNT / 2,
      DEFAULT_THREAD_COUNT,
      DEFAULT_WORD_COUNT / 2,
      DEFAULT_FLIP_PROB,
      0,
      DEFAULT_REF_ARRAY_FACTORY,
      GET_WITH_MASKED_READER).runTest()
  }

  test("small flipper test using relaxedGet") {
    Config(
      DEFAULT_SYNC_COUNT,
      DEFAULT_TRANS_COUNT / 2,
      DEFAULT_INSTR_COUNT / 2,
      DEFAULT_THREAD_COUNT,
      DEFAULT_WORD_COUNT / 2,
      DEFAULT_FLIP_PROB,
      0,
      DEFAULT_REF_ARRAY_FACTORY,
      RELAXED_GET_MASKED_READER).runTest()
  }

  test("small flipper test reading using transformIfDefined") {
    Config(
      DEFAULT_SYNC_COUNT,
      DEFAULT_TRANS_COUNT / 2,
      DEFAULT_INSTR_COUNT / 2,
      DEFAULT_THREAD_COUNT,
      DEFAULT_WORD_COUNT / 2,
      DEFAULT_FLIP_PROB,
      0,
      DEFAULT_REF_ARRAY_FACTORY,
      TRANSFORM_IF_DEFINED_MASKED_READER).runTest()
  }

  test("default flipper test", Slow) {
    Config(
      DEFAULT_SYNC_COUNT,
      DEFAULT_TRANS_COUNT,
      DEFAULT_INSTR_COUNT,
      DEFAULT_THREAD_COUNT,
      DEFAULT_WORD_COUNT,
      DEFAULT_FLIP_PROB,
      0,
      DEFAULT_REF_ARRAY_FACTORY,
      DEFAULT_MASKED_READER).runTest()
  }

  test("small flipper test w/TArray") {
    Config(
      DEFAULT_SYNC_COUNT,
      DEFAULT_TRANS_COUNT / 2,
      DEFAULT_INSTR_COUNT / 2,
      DEFAULT_THREAD_COUNT,
      DEFAULT_WORD_COUNT / 2,
      DEFAULT_FLIP_PROB,
      0,
      TARRAY_REF_ARRAY_FACTORY,
      DEFAULT_MASKED_READER).runTest()
  }

  test("default flipper test w/TArray", Slow) {
    Config(
      DEFAULT_SYNC_COUNT,
      DEFAULT_TRANS_COUNT,
      DEFAULT_INSTR_COUNT,
      DEFAULT_THREAD_COUNT,
      DEFAULT_WORD_COUNT,
      DEFAULT_FLIP_PROB,
      0,
      TARRAY_REF_ARRAY_FACTORY,
      DEFAULT_MASKED_READER).runTest()
  }

  test("random flipper test", Slow) {
    for (_ <- 0 until 1) {
      Config(
        DEFAULT_SYNC_COUNT,
        DEFAULT_TRANS_COUNT,
        DEFAULT_INSTR_COUNT,
        DEFAULT_THREAD_COUNT,
        DEFAULT_WORD_COUNT,
        DEFAULT_FLIP_PROB,
        System.currentTimeMillis + System.nanoTime,
        DEFAULT_REF_ARRAY_FACTORY,
        DEFAULT_MASKED_READER).runTest()
    }
  }

  case class Config(syncCount: Int,
                    transCount: Int,
                    instrCount: Int,
                    threadCount: Int,
                    wordCount: Int,
                    flipProb: Float,
                    randSeed: Long,
                    refArrayFactory: Int => collection.IndexedSeq[Ref[Int]],
                    maskedReader: (Ref[Int], Int) => Int) {

    private val len   = syncCount*transCount*instrCount*threadCount
    private val rand  = new java.util.Random(randSeed)

    val R: Array[Int]     = Array.tabulate(len)({ _ => rand.nextInt(wordCount) })
    val F: Array[Boolean] = Array.tabulate(len)({ _ => rand.nextDouble() < flipProb })

    def index(id: Int, sync: Int, trans: Int, instr: Int): Int = {
      ((id*syncCount+sync)*transCount+trans)*instrCount+instr
    }

    def runTest(): Unit = {
      println(this)

      print("computing sequentially...")
      Console.flush()

      val P = Array.tabulate[Ref[Boolean]](len)({ _ => Ref(false) })
      val expected = computeSequential(this, P)

      print("\ncomputing in parallel with transactions...")
      Console.flush()

      val actual = computeParallelTxn(this, P)

      println()
      for (i <- expected.indices) {
        assert(expected(i).single.get === actual(i).single.get)
      }
    }
  }

  abstract class FlipperTask(val config: Config,
                             val A: collection.IndexedSeq[Ref[Int]],
                             val P: Array[Ref[Boolean]],
                             val computeP: Boolean,
                             val id: Int,
                             val sync: Int) extends (() => Unit) {
    def doWork(task: => Unit): Unit

    def maskedRead(ref: Ref[Int], mask: Int): Int = config.maskedReader(ref, mask)
    def read[T](ref: Ref[T]): T
    def write[T](ref: Ref[T], v: T): Unit

    def apply(): Unit = {
      val mask = 1 << id
      for (trans <- 0 until config.transCount) {
        doWork {
          for (instr <- 0 until config.instrCount) {
            val i = config.index(id, sync, trans, instr)
            val target = config.R(i)
            val p = maskedRead(A(target), mask) != 0
            if (computeP) {
              write(P(i), p)
            }
            else {
              assert(read(P(i)) === p)
            }
            if (config.F(i)) {
              // do some work before storing to A, to increase probability of a conflict
              var h = i
              var j = 0
              while (j < 10000) {
                h |= 1+((h >>> 1)^(h*13))
                j += 1
              }
              if (h == i) println("?")
              write(A(target), read(A(target)) ^ mask)
            }
          }
        }
        //println("thread " + id + " transaction " + trans + " completed (" + computeP + ")")
      }
    }
  }

  def computeSequential(config: Config, P: Array[Ref[Boolean]]): Array[Ref[Int]] = {
    val A = Array.tabulate[Ref[Int]](config.wordCount) { _ => Ref(0) }
    for (sync <- 0 until config.syncCount) {
      for (thread <- 0 until config.threadCount) {
        (new FlipperTask(config, A, P, true, thread, sync) {
          def read[T](ref: Ref[T]): T = ref.single()
          def write[T](ref: Ref[T], v: T): Unit = { ref.single() = v }
          def doWork(task: => Unit): Unit = task
        })()
      }
    }
    A
  }

  def computeParallelTxn(config: Config, P: Array[Ref[Boolean]]): collection.IndexedSeq[Ref[Int]] = {
    val A = config.refArrayFactory(config.wordCount)
    for (sync <- 0 until config.syncCount) {
      val tasks = for (thread <- 0 until config.threadCount) yield {
        new FlipperTask(config, A, P, false, thread, sync) {
          implicit var txn: InTxn = null

          def read[T](ref: Ref[T]): T = ref()

          def write[T](ref: Ref[T], v: T): Unit =
            ref() = v

          def doWork(task: => Unit): Unit = {
            atomic { t =>
              txn = t
              task
            }
            txn = null
          }
        }
      }
      parallelRun(tasks)
    }
    A
  }

  private def parallelRun(tasks: Iterable[() => Unit]): Unit = {
    val barrier = new CyclicBarrier(tasks.size)
    var failure: Throwable = null
    val threads = for (task <- tasks.toList) yield new Thread {
      override def run(): Unit = {
        barrier.await
        try {
          task()
        } catch {
          case x: Throwable =>
            x.printStackTrace()
            failure = x
        }
      }
    }
    for (t <- threads) t.start()
    for (t <- threads) t.join()
    if (null != failure)
      throw failure
  }
}
