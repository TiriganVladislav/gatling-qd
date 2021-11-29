package plugin.components

import akka.actor.ActorSystem
import com.devexperts.rmi.RMIEndpoint
import io.gatling.commons.util.Clock
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.CollectionHasAsScala

class QDConnectionPool(system: ActorSystem, statsEngine: StatsEngine, clock: Clock,
                       configuration: GatlingConfiguration, oneConnection: Boolean, connectionAttributeName: String) {
  private val connections = new ConcurrentHashMap[Long, QDConnection]

  def newConnection(address: String, session: Session): QDConnection = {

    val key:Long = if(oneConnection)  -1 else session.userId


    connections.computeIfAbsent(key, (_: Long) => {
      val qdEndpoint = createEndpoint()
      qdEndpoint.endpoint.connect(address)
      qdEndpoint
    })
  }

  private def createEndpoint():QDConnection = {
    val builder = RMIEndpoint.newBuilder
    builder.withName(connectionAttributeName)
    builder.withSide(RMIEndpoint.Side.CLIENT)
    val endpoint: RMIEndpoint = builder.build
    val qdConn = QDConnection(endpoint)
    qdConn
  }

  def close(): Unit = connections.values().asScala.foreach(_.endpoint.close())

}

case class QDConnection(endpoint: RMIEndpoint)