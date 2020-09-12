//import scala.reflect.ClassTag
//
//object ResolutionTest {
//  def main(args: Array[String]): Unit = {
//    run[String]()
//  }
//
//  def run[A](): Unit = {
//    val testInt : OptManifest[Int]  = implicitly[OptManifest[Int]]
//    val testA   : OptManifest[A]    = implicitly[OptManifest[A]]
//
//    println(s"Int is ${testInt.getClass}")  // ManifestFactory$IntManifest
//    println(s"None is ${testA.getClass}")   // NoManifest
//  }
//}
