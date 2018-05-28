/*import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

sealed trait Foo
case class Bar(xs: List[String]) extends Foo
case class Qux(i: Int, d: Option[Double]) extends Foo

object WebsiteExample extends App {
  val foo: Foo = Qux(13, Some(14.0))
/*  implicit val ef: Encoder[Foo] = implicitly[Encoder[Foo]]*/
  foo.asJson.noSpaces
  println(decode[Foo](foo.asJson.spaces4))
/*
  import io.circe.generic.semiauto._
  implicit val fooDecoder: Decoder[Foo] = deriveDecoder[Foo]
  implicit val fooEncoder: Encoder[Foo] = deriveEncoder[Foo]*/
}*/
