package plugin.action

import com.devexperts.rmi.impl.RMIEndpointImpl
import com.devexperts.rmi.{RMIRequest, RMIRequestListener}
import com.typesafe.scalalogging.StrictLogging
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.action.{Action, RequestAction}
import io.gatling.core.check.Check
import io.gatling.core.protocol.ProtocolComponentsRegistry
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import plugin.check.ResponseTypeExtract
import plugin.protocol.{QDClientComponents, QDClientProtocol}

class QDClientAction[Res](builder: QDClientActionBuilder[Res],
                          ctx: ScenarioContext, override val next: Action) extends
  RequestAction with StrictLogging with NameGen {

  private[this] val throttler = ctx.coreComponents.throttler
  private[this] val qdClientComponents = components(ctx.protocolComponentsRegistry)

  private[this] val resolvedChecks = if (builder.checks.exists(_.checkStatus)) builder.checks
  else ResponseTypeExtract.DefaultCheck :: builder.checks

  override def requestName: Expression[String] = builder.requestName

  override def name: String = genName("RMIRequest")

  override def sendRequest(requestName: String, session: Session): Validation[Unit] = {
    throttler.fold(
      sendAndLog(requestName, session))(_.throttle(session.scenario,
      () => sendAndLog(requestName, session)))
    Success(true)
  }

  def sendAndLog(requestName: String, session: Session): Unit = {
    val endpoint: RMIEndpointImpl = qdClientComponents
          .qdConnectionPool
          .newEndpoint(session)
    val startTimestamp = clock.nowMillis
    if (endpoint.isConnected) {
      val reqOperation: Validation[RMIRequest[Res]] = builder.f(endpoint.getClient, session)

      reqOperation match {
        case Success(request) =>
          request.setListener(new RMIRequestListener() {
            override def requestCompleted(request: RMIRequest[_]): Unit = {
              val endTimestamp = clock.nowMillis
              val res = request.getResponseMessage
              val ex = request.getException()
              val (checkSaveUpdated, checkError) = Check.check(QDClientResponse(res, ex), session,
                resolvedChecks, preparedCache = null)
              val status = if (checkError.isEmpty) OK else KO
              val errorMessage = checkError.map(_.message)

              val newSession = {
                val withStatus = if (status == KO) checkSaveUpdated.markAsFailed else checkSaveUpdated
                statsEngine.logResponse(
                  withStatus.scenario,
                  withStatus.groups,
                  requestName,
                  startTimestamp = startTimestamp,
                  endTimestamp = endTimestamp,
                  status = status,
                  responseCode = Some(res.getType.toString),
                  message = errorMessage
                )
                withStatus.logGroupRequestTimings(startTimestamp = startTimestamp, endTimestamp = endTimestamp)
              }
              next ! newSession
            }
          })
          request.send()
        case Failure(message) =>
          statsEngine.logResponse(session.scenario,
            session.groups,
            requestName,
            startTimestamp = startTimestamp,
            endTimestamp = clock.nowMillis,
            status = KO,
            responseCode = Some("KO"),
            message = Some(message))
          val newSession = session.markAsFailed
          next ! newSession
      }
    }
    else {
      statsEngine.logResponse(session.scenario,
        session.groups,
        requestName,
        startTimestamp = startTimestamp,
        endTimestamp = clock.nowMillis,
        status = KO,
        responseCode = Some("KO"),
        message = Some("Invalid request"))
      val newSession = session.markAsFailed
      next ! newSession
    }
  }

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine

  override def clock: Clock = ctx.coreComponents.clock

  private def components(registry: ProtocolComponentsRegistry): QDClientComponents = {
    registry.components(QDClientProtocol.qdClientProtocolKey)
  }
}
