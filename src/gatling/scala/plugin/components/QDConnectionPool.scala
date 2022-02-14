package plugin.components

import akka.actor.ActorSystem
import com.devexperts.qd.{QDAgent, QDContract}
import com.devexperts.qd.qtp.{DistributorAdapter, MessageConnectors, QDEndpoint}
import com.devexperts.rmi.impl.RMIEndpointImpl
import com.devexperts.rmi.RMIEndpoint
import com.typesafe.scalalogging.StrictLogging
import io.gatling.commons.util.Clock
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import plugin.protocol.QDClientProtocol

import java.util
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.CollectionHasAsScala

class QDConnectionPool(system: ActorSystem,
                       statsEngine: StatsEngine,
                       clock: Clock,
                       configuration: GatlingConfiguration,
                       qdProtocol: QDClientProtocol,
                       connectionAttributeName: String)
  extends StrictLogging {
  private val endpoints = new ConcurrentHashMap[Long, (QDEndpoint, RMIEndpointImpl)]
  private val agents = new ConcurrentHashMap[Long, ConcurrentHashMap[String, QDAgent]]

  def getOrCreateAgent(session: Session, name: String, contract: QDContract): Either[String, QDAgent] = {
    val key: Long = if (qdProtocol.oneConnection) -1 else session.userId
    val e = endpoints.get(key)
    if (e != null) {
      val agentBuilder = contract match {
        case QDContract.STREAM => Some(e._1.getStream.agentBuilder())
        case QDContract.TICKER => Some(e._1.getTicker.agentBuilder())
        case QDContract.HISTORY => Some(e._1.getHistory.agentBuilder())
        case _ => None
      }
      agentBuilder match {
        case Some(value) =>
          val nAgents = agents.computeIfAbsent(key, (_: Long) => {
            val agent = value.build()
            val namedAgents = new ConcurrentHashMap[String, QDAgent]()
            namedAgents.put(name, agent)
            namedAgents
          })
          val agent = nAgents.computeIfAbsent(name: String, (_: String) => {
            value.build()
          })
          Right(agent)
        case None =>
          Left("Unable to create agent builder")
      }
    } else {
      Left("Unable to create an agent. Endpoint is not valid")
    }
  }

  def getAgent(session: Session, name: String): Either[String, QDAgent] = {
    val key: Long = if (qdProtocol.oneConnection) -1 else session.userId
    agents.get(key) match {
      case namedAgents: ConcurrentHashMap[String, QDAgent] =>
        val agent = namedAgents.get(name)
        if (agent != null) Right(agent)
        else Left(s"Agent not found. Name($name) is not present")
      case _ => Left(s"Agent not found. Key($key) is not present.")
    }
  }

  def removeAgent(session: Session, name: String): Either[String, QDAgent] = {
    val key: Long = if (qdProtocol.oneConnection) -1 else session.userId
    val namedAgents = agents.get(key)
    if (namedAgents != null) {
      val agent = namedAgents.remove(name)
      if (agent != null) {
        Right(agent)
      } else Left(s"Could not remove agent. Agent with name=$name is not present")
    } else {
      Left(s"Could not remove agent. Agent with key=$key is not present")
    }
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
      logger.debug(s"Connection with id=$key removed")
    else logger.warn(s"Connection with id=$key is not present")
  }

  def getEndpoints(session: Session): Option[(QDEndpoint, RMIEndpointImpl)] = {
    val key: Long = if (qdProtocol.oneConnection) -1 else session.userId
    endpoints.get(key) match {
      case e: (QDEndpoint, RMIEndpointImpl) => Some(e)
      case _ => None
    }
  }

  private def createEndpoints(id: Long): (QDEndpoint, RMIEndpointImpl) = {
    logger.debug(s"[id=$id] Creating endpoints")

    def createQDEndpoint: QDEndpoint = if (qdProtocol.contracts == null) {
      logger.debug(s"[id=$id] Creating qd endpoint with no contracts")
      val qdEndpoint = QDEndpoint.newBuilder
        .withName(s"${connectionAttributeName}-$id")
        .withScheme(qdProtocol.scheme)
        .build
      qdEndpoint
    }
    else {
      logger.debug(s"[id=$id] Creating qd endpoint with contracts")
      val qdEndpoint = QDEndpoint.newBuilder
        .withName(s"$connectionAttributeName-$id")
        .withScheme(qdProtocol.scheme)
        .withCollectors(util.EnumSet.copyOf(qdProtocol.contracts))
        .build
      if (qdProtocol.stream) qdEndpoint.getStream.setEnableWildcards(true)
      qdEndpoint
    }

    val qdEndpoint: QDEndpoint = createQDEndpoint
    val factory = new DistributorAdapter.Factory(qdEndpoint, null)
    val rmiEndpoint: RMIEndpointImpl = if (qdProtocol.rmi) {
      logger.debug(s"[id=$id] Adding rmi endpoint to qd endpoint")
      val rmiEndpoint = new RMIEndpointImpl(RMIEndpoint.Side.CLIENT, qdEndpoint, factory, null)
      qdEndpoint.initializeConnectorsForAddress(qdProtocol.address)
      rmiEndpoint
    }
    else {
      logger.debug(s"[id=$id] Adding connector to qd endpoint")
      qdEndpoint.addConnectors(MessageConnectors.createMessageConnectors(
        factory, qdProtocol.address, qdEndpoint.getRootStats))
      null
    }
    (qdEndpoint, rmiEndpoint)
  }

  def closeAll(): Unit = {
    agents.values().asScala.foreach(_.values().asScala.foreach(_.close()))
    agents.clear()
    endpoints.values().asScala.foreach(_._1.close())
    endpoints.clear()
  }

  def close(session: Session): (QDEndpoint, RMIEndpointImpl) = {
    val key: Long = if (qdProtocol.oneConnection) -1 else session.userId
    val namedAgents = agents.get(key)
    if (namedAgents != null) {
      namedAgents.values().asScala.foreach(_.close())
      namedAgents.clear()
    }
    agents.remove(key)
    val e = endpoints.get(key)
    if (e != null) e._1.close()
    endpoints.remove(key)
  }
}