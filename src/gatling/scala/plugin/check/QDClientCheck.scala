package plugin.check

import io.gatling.commons.validation.Validation
import io.gatling.core.check.{Check, CheckResult}
import io.gatling.core.session.Session
import plugin.QDClientResponse
import plugin.check.QDClientCheck.{Scope, Status}

import java.util.{Map => JMap}
import scala.annotation.unchecked.uncheckedVariance

case class QDClientCheck[-T](wrapped: Check[QDClientResponse[T]@uncheckedVariance], scope: Scope)
  extends Check[QDClientResponse[T]@uncheckedVariance] {

  override def check(response: QDClientResponse[T],
                     session: Session,
                     cache: JMap[Any, Any]): Validation[CheckResult] =
    wrapped.check(response, session, cache)

  def checkStatus: Boolean = scope == Status
}

object QDClientCheck {
  sealed trait Scope

  case object Status extends Scope

  case object Value extends Scope
}
