package plugin

import com.devexperts.rmi.message.{RMIResponseMessage, RMIResponseType}
import io.gatling.commons.validation.SuccessWrapper
import io.gatling.core.Predef.value2Expression
import io.gatling.core.check._
import plugin.check.QDClientCheck

object ResponseTypeExtract {
  val ResponseType: FindCheckBuilder[ResponseTypeExtract, RMIResponseMessage, RMIResponseType] =
    new DefaultFindCheckBuilder(extractor = new FindExtractor[RMIResponseMessage, RMIResponseType](
      name = "rmiResponseType",
      extractor = responseMessage => Option(responseMessage.getType).success
    ),
      displayActualValue = true)

  object Materializer extends CheckMaterializer[ResponseTypeExtract, QDClientCheck[Any],
    QDClientResponse[Any], RMIResponseMessage](specializer = check.QDClientCheck(_, QDClientCheck.Status)) {
    override protected def preparer: Preparer[QDClientResponse[Any], RMIResponseMessage] =
      _.response.success
  }

  private val isSuccess = ResponseType.find.is(RMIResponseType.SUCCESS)
  val DefaultCheck: QDClientCheck[Any] = isSuccess.build(Materializer)
}


trait ResponseTypeExtract