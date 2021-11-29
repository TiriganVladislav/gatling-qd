package plugin.check

import io.gatling.commons.validation.{Validation, safely}
import io.gatling.core.check.{DefaultFindCheckBuilder, DefaultMultipleFindCheckBuilder, Extractor, FindCheckBuilder}
import io.gatling.core.session.{Expression, ExpressionSuccessWrapper}

//taken from gatling-grpc plugin
private[plugin] object ResponseExtract {
  abstract class ResponseExtractor[T, X](override val name: String) extends Extractor[T, X] {
    def extract(prepared: T): Validation[Option[X]]

    override final def apply(prepared: T): Validation[Option[X]] = safely()(extract(prepared))
  }

  class SingleExtractor[T, X](f: T => Validation[Option[X]], name: String) extends ResponseExtractor[T, X](name) {
    override def extract(prepared: T): Validation[Option[X]] = f(prepared)

    override def arity: String = "find"
  }

  def extract[T, X](f: T => Validation[Option[X]], name: String = "response"):
  FindCheckBuilder[ResponseExtract, T, X] = new DefaultFindCheckBuilder(displayActualValue = true,
    extractor = new SingleExtractor[T, X](f, name).expressionSuccess)

  def extractMultiple[T, X](f: T => Validation[Option[Seq[X]]], name: String = "response"):
  DefaultMultipleFindCheckBuilder[ResponseExtract, T, X] =
    new DefaultMultipleFindCheckBuilder[ResponseExtract, T, X](displayActualValue = true) {
      override protected def findExtractor(occurrence: Int): Expression[ResponseExtractor[T, X]] = new
          ResponseExtractor[T, X](name) {
        override def extract(prepared: T): Validation[Option[X]] = f(prepared)
          .map(_.flatMap(s => if (s.isDefinedAt(occurrence)) Some(s(occurrence)) else None))

        override val arity: String = if (occurrence == 0) "find" else s"find($occurrence)"
      }.expressionSuccess

      override protected def findAllExtractor: Expression[ResponseExtractor[T, Seq[X]]] = new
          ResponseExtractor[T, Seq[X]](name) {
        override def extract(prepared: T): Validation[Option[Seq[X]]] = f(prepared)

        override def arity: String = "findAll"
      }.expressionSuccess

      override protected def countExtractor: Expression[ResponseExtractor[T, Int]] = new ResponseExtractor[T, Int](name) {
        override def extract(prepared: T): Validation[Option[Int]] = f(prepared).map(_.map(_.size))

        override def arity: String = "count"
      }.expressionSuccess
    }
}


trait ResponseExtract