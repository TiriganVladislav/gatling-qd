package plugin

import com.devexperts.rmi.{RMIException, RMIExceptionType, RMIRequest}
import com.devexperts.rmi.message.{RMIResponseMessage, RMIResponseType}
import io.gatling.commons.validation.{Failure, Success, Validation}

import scala.annotation.unchecked.uncheckedVariance

case class QDClientResponse[+T](response: RMIResponseMessage,
                                rmiException: RMIException) {
  private[this] var _validation: Validation[T@uncheckedVariance] = _

  def validation: Validation[T] = {
    if (_validation eq null) {
      _validation = if (response == null) Failure("Response is null") else if (response.getType() == RMIResponseType.SUCCESS) {
        val res = response.getMarshalledResult.getObject.asInstanceOf[T]
        Success(res)
      }
      else {
        val msg = rmiException.getMessage
        val exType = rmiException.getType
        Failure(s"Request failed. Exception type: $exType, message: $msg")
      }
    }
    _validation
  }
}
