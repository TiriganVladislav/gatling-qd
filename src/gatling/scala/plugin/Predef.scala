package plugin

import io.gatling.core.session.Expression
import plugin.action._
import plugin.check.QDClientCheckSupport
import plugin.protocol.{QDClientProtocol, QDClientProtocolBuilder}

object Predef extends QDClientCheckSupport {
  def qd(address: String): QDClientProtocol = QDClientProtocolBuilder(address)

  def connect(requestName: Expression[String]): QDConnectBuilder = QDConnectBuilder(requestName)

  def disconnect(requestName: Expression[String]): QDDisconnectBuilder = QDDisconnectBuilder(requestName)

  def rmirequest(requestName: Expression[String]): QDClientRequestBuilderBase =
    QDClientRequestBuilderBase(requestName)

  def stream(streamName: Expression[String]): QDStreamBuilder = QDStreamBuilder(streamName)

  def ticker(tickerName: Expression[String]): QDTickerBuilder = QDTickerBuilder(tickerName)

  def history(historyName: Expression[String]): QDHistoryBuilder = QDHistoryBuilder(historyName)

  implicit def qdClientProtocolBuilder2qdClientProtocol(builder: QDClientProtocolBuilder): QDClientProtocol =
    builder.build
}
