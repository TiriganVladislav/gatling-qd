package plugin.check

import io.gatling.commons.validation.Validation
import io.gatling.core.check.{FindCheckBuilder, MultipleFindCheckBuilder}
import plugin.Predef

trait CallDefinition[Self, Check[_], Res <: Any] {
  def check(checks: Check[Res]*): Self

  private[plugin] def checks: List[Check[Res]]

  def extract[X](f: Res => Validation[Option[X]])(ts: (FindCheckBuilder[ResponseExtract, Res, X] =>
    Check[Res])*): Self = {
    val e = Predef.extract(f)
    check(mapToList(ts)(_.apply(e)): _*)
  }

  def extractMultiple[X](f: Res => Validation[Option[Seq[X]]])(ts: (MultipleFindCheckBuilder[ResponseExtract, Res, X] =>
    Check[Res])*): Self = {
    val e = Predef.extractMultiple[Res, X](f)
    check(mapToList(ts)(_.apply(e)): _*)
  }

  private def mapToList[T, U](s: Seq[T])(f: T => U): List[U] = s.map(f).toList
}
