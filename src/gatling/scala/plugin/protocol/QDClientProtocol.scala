package plugin.protocol

import io.gatling.core.CoreComponents
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolKey}
import io.gatling.core.session.{Session, SessionPrivateAttributes}
import plugin.components.QDConnectionPool

object QDClientProtocol {
  private val DefaultConnectionAttributeName: String = SessionPrivateAttributes.PrivateAttributePrefix + "qd.connection"


  val qdClientProtocolKey: ProtocolKey[QDClientProtocol, QDClientComponents] = new ProtocolKey[QDClientProtocol, QDClientComponents] {

    def protocolClass: Class[io.gatling.core.protocol.Protocol] = classOf[QDClientProtocol].asInstanceOf[Class[io.gatling.core.protocol.Protocol]]

    def defaultProtocolValue(configuration: GatlingConfiguration): QDClientProtocol =
      throw new IllegalStateException("Can't provide a default value for JmsProtocol")

    def newComponents(coreComponents: CoreComponents): QDClientProtocol => QDClientComponents = {
      qdClientProtocol =>{
        val qdConnectionPool: QDConnectionPool = new QDConnectionPool(
          coreComponents.actorSystem,
          coreComponents.statsEngine,
          coreComponents.clock,
          coreComponents.configuration,
          qdClientProtocol.oneConnection,
          DefaultConnectionAttributeName
        )
        coreComponents.actorSystem.registerOnTermination {
          qdConnectionPool.close()
        }
        new QDClientComponents(qdClientProtocol, qdConnectionPool)}
    }
  }
}

case class QDClientProtocol(address: String, oneConnection: Boolean = false) extends Protocol {
  type Components = QDClientComponents

  def useOneConnection:QDClientProtocol = copy(oneConnection = true)
}

case class QDClientProtocolBuilder(address: String) {
  def build: QDClientProtocol = QDClientProtocol(address)
}

final case class QDClientComponents(qdProtocol: QDClientProtocol, qdConnectionPool: QDConnectionPool) extends ProtocolComponents {
  override def onStart: Session => Session = Session.Identity

  override def onExit: Session => Unit = ProtocolComponents.NoopOnExit
}


