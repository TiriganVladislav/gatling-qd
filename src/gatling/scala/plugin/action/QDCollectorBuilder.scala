package plugin.action

import com.devexperts.qd.QDContract
import com.devexperts.qd.ng.{RecordBuffer, RecordProvider}
import com.devexperts.qd.qtp.{MessageConnector, MessageConnectorState, QDEndpoint}
import com.devexperts.rmi.impl.RMIEndpointImpl
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.commons.util.StringHelper.Eol
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.action.{Action, RequestAction}
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.protocol.ProtocolComponentsRegistry
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import io.gatling.netty.util.StringBuilderPool
import plugin.protocol.{QDClientComponents, QDClientProtocol}

import scala.collection.mutable

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
  private val buffer = new RecordBuffer

  override def requestName: Expression[String] = builder.name

  def logQDDataRecords(buffer: RecordBuffer): Unit = {
    buffer.rewind()
    val sb = StringBuilderPool.DEFAULT.get().append(Eol)
    var cur = buffer.next()
    while (cur != null) {
      val record = cur.getRecord
      sb.append("**************************************")
        .append(Eol)
        .append("Decoded symbol: ")
        .append(cur.getDecodedSymbol)
        .append(Eol)
        .append("Record name: " + record.getName)
        .append(Eol)
        .append("Record IntFieldCount: " + record.getIntFieldCount)
        .append(Eol)
        .append("Record ObjFieldCount: " + record.getObjFieldCount)
        .append(Eol)
        .append("Record hasTime: " + record.hasTime)
        .append(Eol)
        .append("Ints section start")
        .append(Eol)

      val ints: Array[Int] = new Array[Int](record.getIntFieldCount)
      cur.getIntsTo(0, ints, 0, record.getIntFieldCount)
      for (i: Int <- 0 until record.getIntFieldCount) {
        val f = record.getIntField(i)
        sb.append(s"ints[${i}]: {Name=${f.getName}, Value=${ints(i)}}")
          .append(Eol)
      }
      sb.append("Ints section end")
        .append(Eol)
      cur = buffer.next()
    }
    sb.append(Eol).append("**************************************")
    logger.trace(sb.toString())
  }

  def logQDRecordDetails = {
    val m: mutable.Map[String, Int] = mutable.Map[String, Int]()
    buffer.rewind()
    var cur = buffer.next()
    while (cur != null) {
      val record = cur.getRecord
      val name = record.getName
      if (m.contains(name)) m(name) = m(name) + 1
      else m.addOne(name, 1)
      cur = buffer.next()
    }
    val sb = StringBuilderPool.DEFAULT.get().append(Eol)
    for (key <- m.keys) {
      sb.append(s"${m(key)} of ${key} records").append(Eol)
    }
    logger.debug(s"Update received on ${builder.contract} with name=${name}. Details: " + sb.toString())
  }

  override def sendRequest(name: String, session: Session): Validation[Unit] = {
    logger.debug("Executing action")
    val contract = builder.contract
    val connection: Option[(QDEndpoint, RMIEndpointImpl)] = qdClientComponents
      .qdConnectionPool
      .getEndpoints(session)
    connection match {
      case Some((qdEndpoint, rmiEndpoint)) =>
        val l = qdEndpoint.getConnectors()
        if (l.size() > 0) {
          val mc: MessageConnector = l.get(0)
          if (mc.getState == MessageConnectorState.CONNECTED) {
            val agent = qdClientComponents
              .qdConnectionPool
              .getOrCreateAgent(session, name, builder.contract)
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
                    logQDRecordDetails
                  }
                  logger.whenTraceEnabled {
                    logQDDataRecords(buffer)
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
                logFailAndMoveOn(name, session, message)
            }
          }
          else logFailAndMoveOn(name, session, s"Endpoint with id=${session.userId} is not connected")
        }
        else logFailAndMoveOn(name, session, s"Endpoint with id=${session.userId} doesn't have a connector")
      case _ =>
        logFailAndMoveOn(name, session, s"Connection with id=${session.userId} doesn't exist")
    }
    Validation.unit
  }

  private def logFailAndMoveOn(name: String, session: Session, message: String) = {
    statsEngine.logResponse(
      session.scenario,
      session.groups,
      name + ".subscribe",
      startTimestamp = clock.nowMillis,
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
        statsEngine.logResponse(
          session.scenario,
          session.groups,
          name + ".close",
          startTimestamp = clock.nowMillis,
          endTimestamp = clock.nowMillis,
          status = KO,
          responseCode = Some("KO"),
          message = Some("Agent is unavailable")
        )
        val newSession = session.markAsFailed
        next ! newSession
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


