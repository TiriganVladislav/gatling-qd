package plugin.action

import com.devexperts.rmi.impl.RMIEndpointImpl
import com.devexperts.rmi.{RMIEndpoint, RMIEndpointListener}
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

case class QDConnectAction(builder: QDConnectBuilder, ctx: ScenarioContext, next: Action) extends RequestAction
  with NameGen {
  private[this] val qdClientComponents = components(ctx.protocolComponentsRegistry)

  override def requestName: Expression[String] = builder.requestName

  override def sendRequest(requestName: String, session: Session): Validation[Unit] = {
    val address = qdClientComponents.qdProtocol.address
    val endpoint: RMIEndpointImpl = qdClientComponents.qdConnectionPool
          .newEndpoint(session)
    val startTimestamp = clock.nowMillis
    val listener: RMIEndpointListener = new RMIEndpointListener {
      override def stateChanged(rmiEndpoint: RMIEndpoint): Unit = {
        if (endpoint.isConnected) {
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
          endpoint.removeEndpointListener(this)
          next ! session
        }
      }
    }

    if (endpoint.isConnected) {
      logger.error(s"[${endpoint.getName}] Already connected")
      statsEngine.logResponse(
        session.scenario,
        session.groups,
        requestName,
        startTimestamp = startTimestamp,
        endTimestamp = clock.nowMillis,
        status = KO,
        responseCode = Some("KO"),
        message = Some("Already connected")
      )
      val newSession = session.markAsFailed
      next ! newSession
    }
    else {
      endpoint.addEndpointListener(listener)
      endpoint.connect(address)
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
