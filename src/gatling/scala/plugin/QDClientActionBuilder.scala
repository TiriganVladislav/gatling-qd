package plugin

import com.devexperts.rmi.{RMIClient, RMIRequest}
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.protocol.ProtocolComponentsRegistry
import io.gatling.core.session.{Expression}
import io.gatling.core.structure.ScenarioContext
import plugin.check.{CallDefinition, QDClientCheck}

case class QDClientActionBuilder[Res](requestName: Expression[String], f: RMIClient => RMIRequest[Res],
                                      private[plugin] override val checks: List[QDClientCheck[Res]] = Nil)
  extends ActionBuilder with CallDefinition[QDClientActionBuilder[Res], QDClientCheck, Res] {

  override def build(ctx: ScenarioContext, next: Action): Action =
    new QDClientAction(this, ctx, next)

  override def check(checks: QDClientCheck[Res]*):QDClientActionBuilder[Res] = {
    copy(checks = this.checks ::: checks.toList)
  }

  private def components(protocolComponentRegistry: ProtocolComponentsRegistry): QDClientComponents = {
    protocolComponentRegistry.components(QDClientProtocol.qdClientProtocolKey)
  }
}



