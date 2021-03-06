package plugin.action

import com.devexperts.rmi.RMIOperation
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.protocol.ProtocolComponentsRegistry
import io.gatling.core.session.Expression
import io.gatling.core.structure.ScenarioContext
import plugin.check.{CallDefinition, QDClientCheck}
import plugin.protocol.{QDClientComponents, QDClientProtocol}

case class QRMIActionBuilder[Res](requestName: Expression[String],
                                  operation: RMIOperation[Res],
                                  subject: Expression[Any],
                                  parameters: Seq[Expression[Any]],
                                  private[plugin] override val checks: List[QDClientCheck[Res]] = Nil)
  extends ActionBuilder with CallDefinition[QRMIActionBuilder[Res], QDClientCheck, Res] {

  override def build(ctx: ScenarioContext, next: Action): Action =
    new QDRMIAction(this, ctx, next)

  override def check(checks: QDClientCheck[Res]*): QRMIActionBuilder[Res] = {
    copy(checks = this.checks ::: checks.toList)
  }

  private def components(protocolComponentRegistry: ProtocolComponentsRegistry): QDClientComponents = {
    protocolComponentRegistry.components(QDClientProtocol.qdClientProtocolKey)
  }
}
