//import java.util.concurrent.atomic.AtomicLongFieldUpdater
//
//import scala.concurrent.stm.{InTxn, TMap}
//
//trait TMapCompiles {
//  private type Properties = TMap[AnyRef, Map[String, Any]]
//
//  private[this] val properties: Properties = TMap.empty
//
//  def initGraph()(implicit tx: InTxn): Unit = {
//    properties.clear()
////    g.controls.foreach { conf =>
////      properties.put(conf.control.token, conf.properties)
////    }
//  }
//
//  def getProperty[A](token: AnyRef, key: String)(implicit tx: InTxn): Option[A] = {
//    val m0: Map[String, Any] = properties.get(token).orNull
//    if (m0 == null) None else {
//      m0.get(key).asInstanceOf[Option[A]]
//    }
//  }
//
//  {
//    val u = AtomicLongFieldUpdater.newUpdater(classOf[TMapCompiles], "foo")
//  }
//}
