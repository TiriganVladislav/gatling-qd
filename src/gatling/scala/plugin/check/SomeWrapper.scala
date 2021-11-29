package plugin.check

case class SomeWrapper[T](private val value: T) extends AnyVal {
  def some: Some[T] = Some(value)
}
