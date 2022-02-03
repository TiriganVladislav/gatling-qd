package plugin.action

import com.devexperts.qd.QDContract
import com.devexperts.qd.ng.{RecordBuffer, RecordProvider}
import com.devexperts.qd.qtp.{MessageConnector, MessageConnectorState, QDEndpoint}
import com.devexperts.rmi.impl.RMIEndpointImpl
import io.gatling.commons.stats.OK
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.action.{Action, RequestAction}
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.protocol.ProtocolComponentsRegistry
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import plugin.protocol.{QDClientComponents, QDClientProtocol}
import plugin.utils.Utils


case class QDStreamBuilder(streamName: Expression[String]) {
  def subscribe(subscription: Expression[RecordBuffer]): QDSubscribeBuilder =
    QDSubscribeBuilder(streamName, subscription, QDContract.STREAM)

  def close(): QDUnsubscribeBuilder = QDUnsubscribeBuilder(streamName)
}

case class QDTickerBuilder(tickerName: Expression[String]) {
  def subscribe(subscription: Expression[RecordBuffer]): QDSubscribeBuilder =
    QDSubscribeBuilder(tickerName, subscription, QDContract.TICKER)

  def close(): QDUnsubscribeBuilder = QDUnsubscribeBuilder(tickerName)
}

case class QDHistoryBuilder(historyName: Expression[String]) {
  def subscribe(subscription: Expression[RecordBuffer]): QDSubscribeBuilder =
    QDSubscribeBuilder(historyName, subscription, QDContract.HISTORY)

  def close(): QDUnsubscribeBuilder = QDUnsubscribeBuilder(historyName)
}

case class QDSubscribeBuilder(name: Expression[String],
                              subscription: Expression[RecordBuffer],
                              contract: QDContract)
  extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action = QDSubscribeAction(this, ctx, next)
}

case class QDSubscribeAction(builder: QDSubscribeBuilder, ctx: ScenarioContext, next: Action)
  extends RequestAction with NameGen {
  private[this] val qdClientComponents = components(ctx.protocolComponentsRegistry)
  private val utils: Utils = Utils(statsEngine, clock, next)
  private val buffer = new RecordBuffer

  override def requestName: Expression[String] = builder.name

  override def sendRequest(name: String, session: Session): Validation[Unit] = {
    logger.debug("Executing action")
    val contract = builder.contract
    val isContractAllowed = qdClientComponents.qdProtocol.contracts.contains(contract)
    val connection: Option[(QDEndpoint, RMIEndpointImpl)] = qdClientComponents
      .qdConnectionPool
      .getEndpoints(session)
    if (isContractAllowed) {
      connection match {
        case Some((qdEndpoint, rmiEndpoint)) =>
          val l = qdEndpoint.getConnectors()
          if (l.size() > 0) {
            val mc: MessageConnector = l.get(0)
            if (mc.getState == MessageConnectorState.CONNECTED) {
              val agent = qdClientComponents
                .qdConnectionPool
                .getOrCreateAgent(session, name, contract)
              val sub = builder.subscription(session)
              sub match {
                case Success(subscription) => agent.setRecordListener(
                  (provider: RecordProvider) => {

                    statsEngine.logResponse(
                      session.scenario,
                      session.groups,
                      name + ".update",
                      startTimestamp = clock.nowMillis,
                      endTimestamp = clock.nowMillis,
                      status = OK,
                      responseCode = Some("OK"),
                      message = null
                    )
                    buffer.clear()
                    provider.retrieve(buffer)
                    logger.whenDebugEnabled {
                      logger.debug(s"Update received on ${builder.contract} with name=${name}. Details: ${utils.getStatsString(buffer)}")
                    }
                    logger.whenTraceEnabled {
                      logger.trace(utils.getContentString(buffer))
                    }
                  })

                  val startTimestamp = clock.nowMillis
                  agent.setSubscription(subscription)
                  statsEngine.logResponse(
                    session.scenario,
                    session.groups,
                    name + ".subscribe",
                    startTimestamp = startTimestamp,
                    endTimestamp = clock.nowMillis,
                    status = OK,
                    responseCode = Some("OK"),
                    message = null
                  )
                  subscription.release()
                  next ! session
                case Failure(message) =>
                  utils.logFailAndMoveOn(name, session, clock.nowMillis, message)
              }
            }
            else utils.logFailAndMoveOn(name + ".subscribe", session, clock.nowMillis,
              s"Endpoint with id=${session.userId} is not connected")
          }
          else utils.logFailAndMoveOn(name + ".subscribe", session, clock.nowMillis,
            s"Endpoint with id=${session.userId} doesn't have a connector")
        case _ =>
          utils.logFailAndMoveOn(name + ".subscribe", session, clock.nowMillis,
            s"Connection with id=${session.userId} doesn't exist")
      }
    } else utils.logFailAndMoveOn(name + ".subscribe", session, clock.nowMillis,
      s"Not allowed to create $contract subscriptions. Enable this feature in protocol settings.")
    Validation.unit
  }

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine

  override def clock: Clock = ctx.coreComponents.clock

  override def name: String = genName("Subscribe")

  private def components(registry: ProtocolComponentsRegistry): QDClientComponents = {
    registry.components(QDClientProtocol.qdClientProtocolKey)
  }
}

case class QDUnsubscribeBuilder(name: Expression[String])
  extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action = QDUnsubscribeAction(this, ctx, next)
}

case class QDUnsubscribeAction(builder: QDUnsubscribeBuilder, ctx: ScenarioContext, next: Action)
  extends RequestAction with NameGen {
  private[this] val qdClientComponents = components(ctx.protocolComponentsRegistry)
  private val utils: Utils = Utils(statsEngine, clock, next)

  override def requestName: Expression[String] = builder.name

  override def sendRequest(name: String, session: Session): Validation[Unit] = {
    logger.debug("Executing action")
    val agent = qdClientComponents.qdConnectionPool.getAgent(session, name)

    agent match {
      case Some(a) =>
        val startTimestamp = clock.nowMillis
        a.close()
        qdClientComponents.qdConnectionPool.removeAgent(session, name)
        statsEngine.logResponse(
          session.scenario,
          session.groups,
          name + ".close",
          startTimestamp = startTimestamp,
          endTimestamp = clock.nowMillis,
          status = OK,
          responseCode = Some("OK"),
          message = null
        )
        next ! session
      case None =>
        utils.logFailAndMoveOn(name + ".close", session, clock.nowMillis, "Agent is unavailable")
    }
    Validation.unit
  }

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine

  override def clock: Clock = ctx.coreComponents.clock

  override def name: String = genName("Unsubscribe")

  private def components(registry: ProtocolComponentsRegistry): QDClientComponents = {
    registry.components(QDClientProtocol.qdClientProtocolKey)
  }
}


