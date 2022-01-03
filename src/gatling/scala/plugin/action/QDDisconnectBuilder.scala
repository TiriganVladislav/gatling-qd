package plugin.action

import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.protocol.ProtocolComponentsRegistry
import io.gatling.core.session.Expression
import io.gatling.core.structure.ScenarioContext
import plugin.protocol.{QDClientComponents, QDClientProtocol}

case class QDDisconnectBuilder(requestName: Expression[String]) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action = QDDisconnectAction(this, ctx, next)

  private def components(protocolComponentRegistry: ProtocolComponentsRegistry): QDClientComponents = {
    protocolComponentRegistry.components(QDClientProtocol.qdClientProtocolKey)
  }
}
