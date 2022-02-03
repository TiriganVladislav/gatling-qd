package plugin.components

import akka.actor.ActorSystem
import com.devexperts.qd.qtp.socket.SocketMessageAdapterFactory
import com.devexperts.qd.{DataScheme, QDAgent, QDCollector, QDContract, QDFactory, QDHistory, QDStream, QDTicker}
import com.devexperts.qd.qtp.{AgentAdapter, DistributorAdapter, MessageConnectorState, MessageConnectors, QDEndpoint}
import com.devexperts.rmi.impl.{RMIClientImpl, RMIEndpointImpl}
import com.devexperts.rmi.{RMIEndpoint, RMIEndpointListener}
import com.dxfeed.api.DXEndpoint
import com.typesafe.scalalogging.StrictLogging
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.{Failure, Validation}
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import plugin.protocol.QDClientProtocol

import java.util
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import scala.Predef.->
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.CollectionHasAsScala

class QDConnectionPool(system: ActorSystem,
                       statsEngine: StatsEngine,
                       clock: Clock,
                       configuration: GatlingConfiguration,
                       qdProtocol: QDClientProtocol,
                       connectionAttributeName: String)
  extends StrictLogging {
  private val endpoints = new ConcurrentHashMap[Long, (QDEndpoint, RMIEndpointImpl)]
  private val agents = new ConcurrentHashMap[(Long, String), QDAgent]

  def getOrCreateAgent(session: Session, name: String, contract: QDContract) = {
    val key: Long = if (qdProtocol.oneConnection) -1 else session.userId
    agents.computeIfAbsent((key, name), (_: (Long, String)) => {
      val e = endpoints.get(key)
      if (e != null) {
        contract match {
          case QDContract.STREAM =>
            e._1.getStream.agentBuilder().build()
          case QDContract.TICKER =>
            e._1.getTicker.agentBuilder().build()
          case QDContract.HISTORY =>
            e._1.getHistory.agentBuilder().build()
          case _ => null
        }
      } else null
    })
  }

  def getAgent(session: Session, name: String): Option[QDAgent] = {
    val key: Long = if (qdProtocol.oneConnection) -1 else session.userId
    agents.get((key, name)) match {
      case a: QDAgent => Some(a)
      case _ => None
    }
  }

  def removeAgent(session: Session, name: String) = {
    val key: Long = if (qdProtocol.oneConnection) -1 else session.userId
    agents.remove((key, name))
  }

  def getOrCreateEndpoints(session: Session): (QDEndpoint, RMIEndpointImpl) = {
    val key: Long = if (qdProtocol.oneConnection) -1 else session.userId

    endpoints.computeIfAbsent(key, (_: Long) => {
      createEndpoints(key)
    })
  }

  def removeEndpoints(session: Session): Unit = {
    val key: Long = if (qdProtocol.oneConnection) -1 else session.userId
    val endpoint = endpoints.remove(key)
    if (endpoint != null)
      logger.debug(s"Connection with id=${key} removed")
    else logger.warn(s"Connection with id=${key} is not present")
  }

  def getEndpoints(session: Session): Option[(QDEndpoint, RMIEndpointImpl)] = {
    val key: Long = if (qdProtocol.oneConnection) -1 else session.userId
    endpoints.get(key) match {
      case e: (QDEndpoint, RMIEndpointImpl) => Some(e)
      case _ => None
    }
  }

  private def createEndpoints(id: Long): (QDEndpoint, RMIEndpointImpl) = {
    logger.debug(s"[id=${id}] Creating endpoints")

    def createQDEndpoint: QDEndpoint = if (qdProtocol.contracts == null) {
      logger.debug(s"[id=${id}] Creating qd endpoint with no contracts")
      val qdEndpoint = QDEndpoint.newBuilder
        .withName(s"${connectionAttributeName}-${id}")
        .withScheme(qdProtocol.scheme)
        .build
      qdEndpoint
    }
    else {
      logger.debug(s"[id=${id}] Creating qd endpoint with contracts")
      val qdEndpoint = QDEndpoint.newBuilder
        .withName(s"${connectionAttributeName}-${id}")
        .withScheme(qdProtocol.scheme)
        .withContracts(util.EnumSet.copyOf(qdProtocol.contracts))
        .build
      if (qdProtocol.stream) qdEndpoint.getStream.setEnableWildcards(true)
      qdEndpoint
    }

    val qdEndpoint: QDEndpoint = createQDEndpoint
    val factory = new DistributorAdapter.Factory(qdEndpoint, null)
    val rmiEndpoint: RMIEndpointImpl = if (qdProtocol.rmi == true) {
      logger.debug(s"[id=${id}] Adding rmi endpoint to qd endpoint")
      val rmiEndpoint = new RMIEndpointImpl(RMIEndpoint.Side.CLIENT, qdEndpoint, factory, null)
      qdEndpoint.initializeConnectorsForAddress(qdProtocol.address)
      rmiEndpoint
    }
    else {
      logger.debug(s"[id=${id}] Adding connector to qd endpoint")
      qdEndpoint.addConnectors(MessageConnectors.createMessageConnectors(
        factory, qdProtocol.address, qdEndpoint.getRootStats()))
      null
    }
    (qdEndpoint, rmiEndpoint)
  }

  def closeAll(): Unit = {
    endpoints.values().asScala.foreach(_._1.close())
    endpoints.clear()
  }

  def close(session: Session): (QDEndpoint, RMIEndpointImpl) = {
    val key: Long = if (qdProtocol.oneConnection) -1 else session.userId
    endpoints.computeIfPresent(key, (id, endpoints) => {
      endpoints._1.close()
      endpoints
    })
    endpoints.remove(key)
  }
}