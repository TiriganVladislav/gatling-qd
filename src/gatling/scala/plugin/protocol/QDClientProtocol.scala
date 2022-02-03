package plugin.protocol

import com.devexperts.qd.{DataScheme, QDContract, QDFactory}
import io.gatling.core.CoreComponents
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolKey}
import io.gatling.core.session.{Session, SessionPrivateAttributes}
import plugin.components.QDConnectionPool
import java.util

object QDClientProtocol {
  private val DefaultConnectionAttributeName: String = SessionPrivateAttributes.PrivateAttributePrefix + "qd.connection"

  val qdClientProtocolKey: ProtocolKey[QDClientProtocol, QDClientComponents] =
    new ProtocolKey[QDClientProtocol, QDClientComponents] {

      def protocolClass: Class[io.gatling.core.protocol.Protocol] =
        classOf[QDClientProtocol].asInstanceOf[Class[io.gatling.core.protocol.Protocol]]

      def defaultProtocolValue(configuration: GatlingConfiguration): QDClientProtocol =
        throw new IllegalStateException("Can't provide a default value for QDProtocol")

      def newComponents(coreComponents: CoreComponents): QDClientProtocol => QDClientComponents = {
        qdClientProtocol => {
          val qdConnectionPool: QDConnectionPool = new QDConnectionPool(
            coreComponents.actorSystem,
            coreComponents.statsEngine,
            coreComponents.clock,
            coreComponents.configuration,
            qdClientProtocol,
            DefaultConnectionAttributeName
          )
          coreComponents.actorSystem.registerOnTermination {
            qdConnectionPool.closeAll()
          }
          QDClientComponents(qdClientProtocol, qdConnectionPool)
        }
      }
    }
}

case class QDClientProtocol(private[plugin] val address: String,
                            private[plugin] val oneConnection: Boolean = false,
                            private[plugin] val rmi: Boolean = false,
                            private[plugin] val contracts: util.EnumSet[QDContract] =
                            util.EnumSet.noneOf(classOf[QDContract]),
                            private[plugin] val stream: Boolean = false,
                            private[plugin] val ticker: Boolean = false,
                            private[plugin] val history: Boolean = false,
                            private[plugin] val scheme: DataScheme = QDFactory.getDefaultScheme) extends Protocol {
  type Components = QDClientComponents

  def useOneConnection: QDClientProtocol = copy(oneConnection = true)

  def withRMI: QDClientProtocol = copy(rmi = true)

  def withStream: QDClientProtocol = {
    val newSet = contracts.clone()
    newSet.add(QDContract.STREAM)
    copy(contracts = newSet, stream = true)
  }

  def withTicker: QDClientProtocol = {
    val newSet = contracts.clone()
    newSet.add(QDContract.TICKER)
    copy(contracts = newSet, ticker = true)
  }

  def withHistory: QDClientProtocol = {
    val newSet = contracts.clone()
    newSet.add(QDContract.HISTORY)
    copy(contracts = newSet, history = true)
  }

  def withScheme(scheme: DataScheme): QDClientProtocol = copy(scheme = scheme)
}

case class QDClientProtocolBuilder(address: String) {
  def build: QDClientProtocol = QDClientProtocol(address)
}

final case class QDClientComponents(qdProtocol: QDClientProtocol, qdConnectionPool: QDConnectionPool) extends ProtocolComponents {
  override def onStart: Session => Session = Session.Identity

  override def onExit: Session => Unit = session => {
    qdConnectionPool.close(session)
  }
}


