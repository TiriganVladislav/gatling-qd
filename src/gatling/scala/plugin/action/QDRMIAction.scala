package plugin.action

import com.devexperts.rmi.{RMIClient, RMIRequest, RMIRequestListener}
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
import plugin.check.{QDClientResponse, ResponseTypeExtract}
import plugin.protocol.{QDClientComponents, QDClientProtocol}
import plugin.utils.Utils

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.break

class QDRMIAction[Res](builder: QRMIActionBuilder[Res],
                       ctx: ScenarioContext, override val next: Action) extends
  RequestAction with StrictLogging with NameGen {

  private[this] val throttler = ctx.coreComponents.throttler
  private[this] val qdClientComponents = components(ctx.protocolComponentsRegistry)
  private[this] val isRMIAllowed = qdClientComponents.qdProtocol.rmi
  private val utils: Utils = Utils(statsEngine, clock, next)
  private[this] val resolvedChecks = if (builder.checks.exists(_.checkStatus)) builder.checks
  else ResponseTypeExtract.DefaultCheck :: builder.checks

  override def requestName: Expression[String] = builder.requestName

  override def name: String = genName("RMIRequest")

  override def sendRequest(requestName: String, session: Session): Validation[Unit] = {
    throttler.fold(
      sendAndLog(requestName, session))(_.throttle(session.scenario,
      () => sendAndLog(requestName, session)))
    Validation.unit
  }

  def createRequest(client: RMIClient, session: Session): Validation[RMIRequest[Res]] = {
    if (builder.subject != null) {
      for {
        subject <- builder.subject(session)
        resolvedParams <- resolveParameters(session)
      }
      yield client.createRequest(subject, builder.operation, resolvedParams: _*)
    }
    else for {
      resolvedParams <- resolveParameters(session)
    }
    yield client.createRequest(null, builder.operation, resolvedParams: _*)
  }

  def createNewListener(requestName: String, startTimestamp: Long, session: Session): RMIRequestListener = {
    new RMIRequestListener() {
      override def requestCompleted(request: RMIRequest[_]): Unit = {
        val endTimestamp = clock.nowMillis
        val res = request.getResponseMessage
        val ex = request.getException
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
    }
  }

  def sendAndLog(requestName: String, session: Session): Unit = {
    if (!isRMIAllowed) {
      utils.logFailAndMoveOn(requestName, session, clock.nowMillis,
        "Not allowed to send RMI requests. Enable this feature in protocol settings.")
      return
    }
    val (_, rmiEndpoint) = qdClientComponents.qdConnectionPool.getOrCreateEndpoints(session)
    if (rmiEndpoint == null) {
      utils.logFailAndMoveOn(requestName, session, clock.nowMillis, "RMI endpoint is null")
      return
    }
    if (!rmiEndpoint.isConnected) {
      utils.logFailAndMoveOn(requestName, session, clock.nowMillis, "Not connected")
      return
    }

    val request: Validation[RMIRequest[Res]] = createRequest(rmiEndpoint.getClient, session)
    request match {
      case Success(request) =>
        val startTimestamp = clock.nowMillis
        val listener = createNewListener(requestName, startTimestamp, session)
        request.setListener(listener)
        request.send()
      case Failure(message) => utils.logFailAndMoveOn(requestName, session, clock.nowMillis, message)
    }
  }

  private def resolveParameters(session: Session): Validation[Seq[Any]] = {
    var failureMessage: Validation[Any] = Validation.success
    val params = ArrayBuffer.empty[Any]
    for (param <- builder.parameters) {
      val resolvedParam: Validation[Any] = param(session)
      resolvedParam match {
        case Success(value) => params += value
        case Failure(message) =>
          failureMessage = Failure(message)
          break
      }
    }
    failureMessage match {
      case Success(_) => Success(params.toSeq)
      case Failure(message) => Failure(message)
    }
  }

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine

  override def clock: Clock = ctx.coreComponents.clock

  private def components(registry: ProtocolComponentsRegistry): QDClientComponents = {
    registry.components(QDClientProtocol.qdClientProtocolKey)
  }
}
