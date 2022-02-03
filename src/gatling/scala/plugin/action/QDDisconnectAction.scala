package plugin.action

import com.devexperts.qd.qtp.{MessageConnector, MessageConnectorListener, MessageConnectorState, QDEndpoint}
import com.devexperts.rmi.impl.RMIEndpointImpl
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.Validation
import io.gatling.core.action.{Action, RequestAction}
import io.gatling.core.protocol.ProtocolComponentsRegistry
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import plugin.protocol.{QDClientComponents, QDClientProtocol}


case class QDDisconnectAction(builder: QDDisconnectBuilder, ctx: ScenarioContext, next: Action) extends RequestAction
  with NameGen {
  private[this] val qdClientComponents = components(ctx.protocolComponentsRegistry)

  override def requestName: Expression[String] = builder.requestName

  override def sendRequest(requestName: String, session: Session): Validation[Unit] = {
    logger.debug("sendRequest called")
    val connection: Option[(QDEndpoint, RMIEndpointImpl)] = qdClientComponents
      .qdConnectionPool
      .getEndpoints(session)
    val startTimestamp = clock.nowMillis
    connection match {
      case Some((qdEndpoint, rmiEndpoint)) =>
        val l = qdEndpoint.getConnectors
        logger.debug(s"Connector count = ${l.size()}")
        l.forEach(c => logger.debug(s"Connector name = ${c.getName}"))
        if (l.size() > 0) {
          val mc: MessageConnector = l.get(0)
          val mcListener = new MessageConnectorListener {
            override def stateChanged(connector: MessageConnector): Unit = {
              logger.debug(s"Listener called. State - ${connector.getState}")
              if (connector.getState == MessageConnectorState.DISCONNECTED) {
                mc.removeMessageConnectorListener(this)

                val endTimestamp = clock.nowMillis
                statsEngine.logResponse(
                  session.scenario,
                  session.groups,
                  requestName,
                  startTimestamp = startTimestamp,
                  endTimestamp = endTimestamp,
                  status = OK,
                  responseCode = Some("OK"),
                  message = null
                )
                next ! session
              }
            }
          }
          if (mc.getState == MessageConnectorState.CONNECTED) {
            mc.addMessageConnectorListener(mcListener)
            mc.stop()
          }
          else {
            logFailedRequest(requestName, session, startTimestamp, "Already disconnected")
          }
        }
        else {
          logFailedRequest(requestName, session, startTimestamp, "No connectors available")
        }
      case _ =>
        logFailedRequest(requestName, session, startTimestamp,
          s"Connection with id=${session.userId} doesn't exist")
    }
    Validation.unit
  }

  private def logFailedRequest(requestName: String, session: Session, startTimestamp: Long, message: String): Unit = {
    statsEngine.logResponse(
      session.scenario,
      session.groups,
      requestName,
      startTimestamp = startTimestamp,
      endTimestamp = clock.nowMillis,
      status = KO,
      responseCode = Some("KO"),
      message = Some(message)
    )
    val newSession = session.markAsFailed
    next ! newSession
  }

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine

  override def clock: Clock = ctx.coreComponents.clock

  override def name: String = genName("Disconnect")

  private def components(registry: ProtocolComponentsRegistry): QDClientComponents = {
    registry.components(QDClientProtocol.qdClientProtocolKey)
  }
}
