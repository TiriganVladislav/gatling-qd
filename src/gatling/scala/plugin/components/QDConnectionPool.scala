package plugin.components

import akka.actor.ActorSystem
import com.devexperts.qd.qtp.socket.SocketMessageAdapterFactory
import com.devexperts.qd.{DataScheme, QDContract, QDFactory}
import com.devexperts.qd.qtp.{AgentAdapter, MessageConnectorState, MessageConnectors, QDEndpoint}
import com.devexperts.rmi.impl.{RMIClientImpl, RMIEndpointImpl}
import com.devexperts.rmi.{RMIEndpoint, RMIEndpointListener}
import com.dxfeed.api.DXEndpoint
import com.typesafe.scalalogging.StrictLogging
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.{Failure, Validation}
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine

import java.util
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import scala.Predef.->
import scala.jdk.CollectionConverters.CollectionHasAsScala

class QDConnectionPool(system: ActorSystem, statsEngine: StatsEngine, clock: Clock,
                       configuration: GatlingConfiguration, oneConnection: Boolean, connectionAttributeName: String)
  extends StrictLogging {
  private val endpoints = new ConcurrentHashMap[Long, RMIEndpointImpl]

  def newEndpoint(session: Session): RMIEndpointImpl = {
    val key: Long = if (oneConnection) -1 else session.userId

    endpoints.computeIfAbsent(key, (_: Long) => {
      val qdEndpoint = createEndpoint(key)
      qdEndpoint
    })
  }

  def remove(id: Long): Unit = {
    val endpoint = endpoints.remove(id)
    if (endpoint != null)
      logger.debug(s"Connection with id=${id} removed")
    else logger.warn(s"Connection with id=${id} is not present")
  }

  def get(id: Long): Option[RMIEndpointImpl] = endpoints.get(id) match {
    case e: RMIEndpointImpl => Some(e)
    case _ => None
  }

  private def createEndpoint(id: Long): RMIEndpointImpl = {
    val builder: RMIEndpoint.Builder = RMIEndpoint.newBuilder
    builder.withName(s"gatling-qd-${id}")
    builder.withSide(RMIEndpoint.Side.CLIENT)
    val endpoint = builder.build.asInstanceOf[RMIEndpointImpl]
    endpoint
  }

  def disconnect(): Unit = endpoints.values().asScala.filter(_.isConnected).foreach(_.disconnect())

  def disconnect(id: Long): RMIEndpointImpl = endpoints.computeIfPresent(id, (id, endpoint) => {
    if (endpoint.isConnected) endpoint.disconnect()
    endpoint
  })
}