package plugin.check

import io.gatling.core.check.{CheckMaterializer, Preparer}
import plugin.{QDClientResponse, check}

private[plugin] object ResponseMaterializers {
  def materializer[Res]: CheckMaterializer[ResponseExtract, QDClientCheck[Res], QDClientResponse[Res], Res] =
    new CheckMaterializer[ResponseExtract, QDClientCheck[Res], QDClientResponse[Res], Res](specializer =
      check.QDClientCheck(_, QDClientCheck.Value)) {
      override protected def preparer: Preparer[QDClientResponse[Res], Res] = _.validation
    }
}
