package plugin.check

import com.devexperts.rmi.message.{RMIResponseMessage, RMIResponseType}
import io.gatling.commons.validation.Validation
import io.gatling.core.check.{CheckBuilder, CheckMaterializer, DefaultMultipleFindCheckBuilder, FindCheckBuilder, ValidatorCheckBuilder}
import plugin.{QDClientResponse, ResponseTypeExtract}

trait QDClientCheckSupport {

  def extract[T, X](f: T => Validation[Option[X]]): FindCheckBuilder[ResponseExtract, T, X] = ResponseExtract
    .extract(f, "response")

  def extractMultiple[T, X](f: T => Validation[Option[Seq[X]]]):
  DefaultMultipleFindCheckBuilder[ResponseExtract, T, X] =
    ResponseExtract.extractMultiple(f, "response")

  implicit def resMat[Res]:
  CheckMaterializer[ResponseExtract, QDClientCheck[Res], QDClientResponse[Res], Res] =
    ResponseMaterializers.materializer[Res]

  implicit val statusMat: CheckMaterializer[ResponseTypeExtract, QDClientCheck[Any],
    QDClientResponse[Any], RMIResponseMessage] = ResponseTypeExtract.Materializer

  implicit def checkBuilder2QDClientCheck[A, P, X, ResOrAny, Res](checkBuilder: CheckBuilder[A, P, X])(
    implicit materializer: CheckMaterializer[A, QDClientCheck[ResOrAny], QDClientResponse[ResOrAny], P],
    contravarianceHelper: QDClientCheck[ResOrAny] => QDClientCheck[Res]
  ): QDClientCheck[Res] = contravarianceHelper(checkBuilder.build(materializer))

  implicit def validatorCheckBuilder2GrpcCheck[A, P, X, ResOrAny, Res](vCheckBuilder: ValidatorCheckBuilder[A, P, X])(
    implicit materializer: CheckMaterializer[A, QDClientCheck[ResOrAny], QDClientResponse[ResOrAny], P],
    contravarianceHelper: QDClientCheck[ResOrAny] => QDClientCheck[Res]
  ): QDClientCheck[Res] =
    vCheckBuilder.exists

  implicit def findCheckBuilder2QDClientCheck[A, P, X, ResOrAny, Res](findCheckBuilder: FindCheckBuilder[A, P, X])(
    implicit materializer: CheckMaterializer[A, QDClientCheck[ResOrAny], QDClientResponse[ResOrAny], P],
    contravarianceHelper: QDClientCheck[ResOrAny] => QDClientCheck[Res]
  ): QDClientCheck[Res] =
    findCheckBuilder.find.exists

  implicit def someWrapper[T](value: T): SomeWrapper[T] = new SomeWrapper(value)
}
