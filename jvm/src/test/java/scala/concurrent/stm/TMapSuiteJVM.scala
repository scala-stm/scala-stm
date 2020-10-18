/* scala-stm - (c) 2009-2012, Stanford University, PPL */

package scala.concurrent.stm

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.stm.compat._  // used
import scala.concurrent.stm.skel.SimpleRandom

class TMapSuiteJVM extends AnyFunSuite {

  private def value(k: Int) = "x" + k
  private def kvRange(b: Int, e: Int) = (b until e) map { i => i -> value(i) }

  test("contention") {
    val values = (0 until 37) map { i => "foo" + i }
    for (_ <- 0 until 2) {
      val numThreads = 8
      val m = TMap.empty[Int, String]
      val threads = for (t <- 0 until numThreads) yield new Thread {
        override def run(): Unit = {
          var rand = new SimpleRandom(t)
          var i = 0
          while (i < 1000000) {
            if (rand.nextInt(2) == 0) {
              var j = 0
              while (j < 64) {
                val key = rand.nextInt(1 << 11)
                val pct = rand.nextInt(100)
                if (pct < 33)
                  m.single.contains(key)
                else if (pct < 33)
                  m.single.put(key, values(rand.nextInt(values.length)))
                else
                  m.single.remove(key)
                j += 1
              }
            } else {
              rand = atomic { implicit txn =>
                val r = rand.clone
                var j = 0
                while (j < 64) {
                  val key = r.nextInt(1 << 11)
                  val pct = r.nextInt(100)
                  if (pct < 33)
                    m.contains(key)
                  else if (pct < 33)
                    m.put(key, values(r.nextInt(values.length)))
                  else
                    m.remove(key)
                  j += 1
                }
                r
              }
            }
            i += 64
          }
        }
      }

      val begin = System.currentTimeMillis
      for (t <- threads) t.start()
      for (t <- threads) t.join()
      val elapsed = System.currentTimeMillis - begin

      println("TMap: contended: " + numThreads + " threads, total throughput was " + (elapsed / numThreads) + " nanos/op")
    }
  }

  test("atomicity violation") {
    // This test makes sure that the copy-on-write snapshot mechanism can't
    // expose the intermediate state of a txn to a non-txn get.
    val m = TMap(kvRange(0, 1000): _*).single
    m(0) = "okay"
    val failed = Ref(-1).single
    val threads = Array.tabulate(2) { _ =>
      new Thread {
        override def run(): Unit = {
          val r = new SimpleRandom
          for (i <- 0 until 100000) {
            if (r.nextInt(2) == 0) {
              if (m(0) != "okay") {
                failed() = i
                return
              }
            } else {
              atomic { implicit txn =>
                m(0) = "should be isolated"
                m.snapshot
                m(0) = "okay"
              }
            }
          }
        }
      }
    }
    for (t <- threads) t.start()
    for (t <- threads) t.join()
    assert(failed() === -1)
  }

}
