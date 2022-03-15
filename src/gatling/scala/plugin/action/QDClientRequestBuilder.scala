package plugin.action

import com.devexperts.rmi.RMIOperation
import io.gatling.core.session._
import plugin.action


case class QDClientRequestBuilderBase0(private val requestName: Expression[String]) {
  def operation[Res](op: RMIOperation[Res]): QDClientRequestBuilderBase1[Res] =
    QDClientRequestBuilderBase1(requestName, op)
}

case class QDClientRequestBuilderBase1[Res](private val requestName: Expression[String],
                                            private val operation: RMIOperation[Res]) {
  def subject(subj: Expression[Any]): QDClientRequestBuilderBase2[Res] =
    QDClientRequestBuilderBase2(requestName, operation, subj)
}

case class QDClientRequestBuilderBase2[Res](private val requestName: Expression[String],
                                            private val operation: RMIOperation[Res],
                                            private val subject: Expression[Any] = null) {
  def parameters(params: Expression[Any]*): QRMIActionBuilder[Res] =
    action.QRMIActionBuilder(requestName, operation, subject, params)
}
