package plugin

import io.gatling.core.session.Expression
import plugin.action.{QDClientRequestBuilderBase, QDConnectBuilder, QDDisconnectBuilder}
import plugin.check.QDClientCheckSupport
import plugin.protocol.{QDClientProtocol, QDClientProtocolBuilder}

object Predef extends QDClientCheckSupport {
  def qd(address: String): QDClientProtocol = QDClientProtocolBuilder(address)

  def connect(requestName: Expression[String]): QDConnectBuilder = action.QDConnectBuilder(requestName)

  def disconnect(requestName: Expression[String]): QDDisconnectBuilder = action.QDDisconnectBuilder(requestName)

  //def qdRequest(requestName: Expression[String]) = QDClientActionBuilderBase(requestName)
  def rmirequest(requestName: Expression[String]) = QDClientRequestBuilderBase(requestName)

  implicit def qdClientProtocolBuilder2qdClientProtocol(builder: QDClientProtocolBuilder): QDClientProtocol =
    builder.build
}
