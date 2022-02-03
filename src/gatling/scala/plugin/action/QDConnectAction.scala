package plugin.action

import com.devexperts.qd.qtp.{MessageConnector, MessageConnectorListener, MessageConnectorState}
import io.gatling.commons.stats.OK
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.Validation
import io.gatling.core.action.{Action, RequestAction}
import io.gatling.core.protocol.ProtocolComponentsRegistry
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import plugin.protocol.{QDClientComponents, QDClientProtocol}
import plugin.utils.Utils
import java.util


case class QDConnectAction(builder: QDConnectBuilder, ctx: ScenarioContext, next: Action) extends RequestAction
  with NameGen {
  private[this] val qdClientComponents = components(ctx.protocolComponentsRegistry)
  private val utils: Utils = Utils(statsEngine, clock, next)

  override def requestName: Expression[String] = builder.requestName

  override def sendRequest(requestName: String, session: Session): Validation[Unit] = {
    logger.debug("sendRequest called")
    val (qdEndpoint, rmiEndpoint) = qdClientComponents.qdConnectionPool.getOrCreateEndpoints(session)
    val startTimestamp = clock.nowMillis

    val listener = new MessageConnectorListener {
      override def stateChanged(connector: MessageConnector): Unit = {
        logger.debug(s"Listener called. State - ${connector.getState}")
        if (connector.getState == MessageConnectorState.CONNECTED) {
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
          connector.removeMessageConnectorListener(this)
          next ! session
        }
      }
    }

    val l: util.List[MessageConnector] = qdEndpoint.getConnectors
    if (l.size() > 0) {
      val mc: MessageConnector = l.get(0)
      mc.getState match {
        case MessageConnectorState.DISCONNECTED =>
          mc.addMessageConnectorListener(listener)
          mc.start()
        case _ =>
          utils.logFailAndMoveOn(requestName, session, startTimestamp,
            s"[${mc.getName}] Illegal state - ${mc.getState}")
      }
    }
    else {
      utils.logFailAndMoveOn(requestName, session, startTimestamp,
        s"[${session.userId}] No available connectors")
    }
    Validation.unit
  }

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine

  override def clock: Clock = ctx.coreComponents.clock

  override def name: String = genName("Connect")

  private def components(registry: ProtocolComponentsRegistry): QDClientComponents = {
    registry.components(QDClientProtocol.qdClientProtocolKey)
  }
}
