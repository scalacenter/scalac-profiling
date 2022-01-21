package profiling.integrations

import java.io.{InputStream, PrintStream}
import java.nio.file.{Path, Paths}

import CommonOptions.PrettyProperties
import caseapp.{CaseApp, CommandParser}
import caseapp.core.default.Default
import caseapp.core.Error.Other

import caseapp.core.argparser.{ArgParser, SimpleArgParser}
import caseapp.core.parser.Parser
import caseapp.core.default.Default
import shapeless.{Annotations, Coproduct, LabelledGeneric, Strict, Generic, HList}
import caseapp.{Name, ValueDescription, HelpMessage, Hidden, Recurse}

import scala.util.Try

trait CachedImplicits {
  implicit val inputStreamRead: ArgParser[InputStream] =
    SimpleArgParser.from[InputStream]("stdin")(_ => Right(System.in))
  implicit val printStreamRead: ArgParser[PrintStream] =
    SimpleArgParser.from[PrintStream]("stdout")(_ => Right(System.out))

  implicit val pathParser: ArgParser[Path] = SimpleArgParser.from("A filepath parser") {
    case supposedPath: String =>
      val toPath = Try(Paths.get(supposedPath)).toEither
      toPath.left.map(t => Other(s"The provided path ${supposedPath} is not valid: '${t.getMessage()}'."))
  }

  implicit val propertiesParser: ArgParser[PrettyProperties] = {
    SimpleArgParser.from("A properties parser") {
      case whatever => Left(Other("You cannot pass in properties through the command line."))
    }
  }

  import shapeless.{HNil, CNil, :+:, ::}
  implicit val implicitHNil: HNil = HList.apply()

  implicit val implicitOptionDefaultString: Option[Default[String]] =
    Some(Default(""))

  implicit val implicitOptionDefaultInt: Option[Default[Int]] =
    Some(Default(0))

  implicit val implicitOptionDefaultBoolean: Option[Default[Boolean]] =
    Some(Default(true))

  implicit val implicitDefaultBoolean: Default[Boolean] =
    Default(true)

  implicit val implicitOptionDefaultOptionPath: Option[Default[Option[Path]]] =
    Some(Default(None))

  implicit val implicitOptionDefaultPrintStream: Option[Default[PrintStream]] =
    Some(Default[PrintStream](System.out))

  implicit val implicitOptionDefaultInputStream: Option[Default[InputStream]] =
    Some(Default[InputStream](System.in))
}

object Parsers extends CachedImplicits {

  implicit val labelledGenericCommonOptions: LabelledGeneric.Aux[CommonOptions, _] = LabelledGeneric.materializeProduct
  implicit val commonOptionsParser: Parser.Aux[CommonOptions, _] = Parser.derive
  implicit val labelledGenericCliOptions: LabelledGeneric.Aux[CliOptions, _] = LabelledGeneric.materializeProduct
  implicit val cliOptionsParser: Parser.Aux[CliOptions, _] = Parser.derive

  implicit val strictAutocompleteParser: Parser.Aux[Commands.Autocomplete, _] = Parser.derive
  implicit val strictAboutParser: Parser.Aux[Commands.About, _] = Parser.derive
  implicit val strictBspParser: Parser.Aux[Commands.Bsp, _] = Parser.derive
  implicit val strictCleanParser: Parser.Aux[Commands.Clean, _] = Parser.derive
  implicit val strictCompileParser: Parser.Aux[Commands.Compile, _] = Parser.derive
  implicit val strictConfigureParser: Parser.Aux[Commands.Configure, _] = Parser.derive
  implicit val strictConsoleParser: Parser.Aux[Commands.Console, _] = Parser.derive
  implicit val strictHelpParser: Parser.Aux[Commands.Help, _] = Parser.derive
  implicit val strictProjectsParser: Parser.Aux[Commands.Projects, _] = Parser.derive
  implicit val strictRunParser: Parser.Aux[Commands.Run, _] = Parser.derive
  implicit val strictTestParser: Parser.Aux[Commands.Test, _] = Parser.derive

}

object Main extends App {
  println("Hello World!")
}
