package plugin

import io.gatling.core.session.Expression
import plugin.check.QDClientCheckSupport

object Predef extends QDClientCheckSupport {
  def qd(address: String): QDClientProtocol = QDClientProtocolBuilder(address)

  //def qdRequest(requestName: Expression[String]) = QDClientActionBuilderBase(requestName)
  def rmirequest(requestName: Expression[String]) = QDClientRequestBuilderBase(requestName)

  implicit def qdClientProtocolBuilder2qdClientProtocol(builder: QDClientProtocolBuilder):QDClientProtocol =
    builder.build
}
