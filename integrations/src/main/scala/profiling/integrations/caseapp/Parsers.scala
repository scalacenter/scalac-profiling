package profiling.integrations

import java.io.{InputStream, PrintStream}
import java.nio.file.{Path, Paths}

import CommonOptions.PrettyProperties
import caseapp.{CaseApp, CommandParser}
import caseapp.util.{Implicit, AnnotationList}
import caseapp.core.{ArgParser, DefaultBaseCommand, HListParser, Parser, Default}
import shapeless.{Annotations, Coproduct, LabelledGeneric, Strict, Generic, HList}
import caseapp.{Name, ValueDescription, HelpMessage, Hidden, Recurse}

import scala.util.Try

trait CachedImplicits {
    implicit val inputStreamRead: ArgParser[InputStream] =
    ArgParser.instance[InputStream]("stdin")(_ => Right(System.in))
  implicit val printStreamRead: ArgParser[PrintStream] =
    ArgParser.instance[PrintStream]("stdout")(_ => Right(System.out))

  implicit val pathParser: ArgParser[Path] = ArgParser.instance("A filepath parser") {
    case supposedPath: String =>
      val toPath = Try(Paths.get(supposedPath)).toEither
      toPath.left.map(t => s"The provided path ${supposedPath} is not valid: '${t.getMessage()}'.")
  }

  implicit val propertiesParser: ArgParser[PrettyProperties] = {
    ArgParser.instance("A properties parser") {
      case whatever => Left("You cannot pass in properties through the command line.")
    }
  }

  import shapeless.{HNil, CNil, :+:, ::}
  implicit val implicitHNil: Implicit[HNil] = Implicit.hnil
  implicit val implicitNone: Implicit[None.type] = Implicit.instance(None)
  implicit val implicitNoneCnil: Implicit[None.type :+: CNil] =
    Implicit.instance(Coproduct(None))

  implicit val implicitOptionDefaultString: Implicit[Option[Default[String]]] =
    Implicit.instance(Some(caseapp.core.Defaults.string))

  implicit val implicitOptionDefaultInt: Implicit[Option[Default[Int]]] =
    Implicit.instance(Some(caseapp.core.Defaults.int))

  implicit val implicitOptionDefaultBoolean: Implicit[Option[Default[Boolean]]] =
    Implicit.instance(Some(caseapp.core.Defaults.boolean))

  implicit val implicitDefaultBoolean: Implicit[Default[Boolean]] =
    Implicit.instance(caseapp.core.Defaults.boolean)

  implicit val implicitOptionDefaultOptionPath: Implicit[Option[Default[Option[Path]]]] =
    Implicit.instance(None)

  implicit val implicitOptionDefaultPrintStream: Implicit[Option[Default[PrintStream]]] =
    Implicit.instance(Some(Default.instance[PrintStream](System.out)))

  implicit val implicitOptionDefaultInputStream: Implicit[Option[Default[InputStream]]] =
    Implicit.instance(Some(Default.instance[InputStream](System.in)))
}

object Parsers extends CachedImplicits {

  import shapeless.{the, HNil, ::}

  implicit val labelledGenericCommonOptions: LabelledGeneric.Aux[CommonOptions, _] = LabelledGeneric.materializeProduct
  implicit val commonOptionsParser: Parser.Aux[CommonOptions, _] = Parser.generic
  implicit val labelledGenericCliOptions: LabelledGeneric.Aux[CliOptions, _] = LabelledGeneric.materializeProduct
  implicit val cliOptionsParser: Parser.Aux[CliOptions, _] = Parser.generic

  implicit val strictAutocompleteParser: Parser.Aux[Commands.Autocomplete, _] = Parser.generic
  implicit val strictAboutParser: Parser.Aux[Commands.About, _] = Parser.generic
  implicit val strictBspParser: Parser.Aux[Commands.Bsp, _] = Parser.generic
  implicit val strictCleanParser: Parser.Aux[Commands.Clean, _] = Parser.generic
  implicit val strictCompileParser: Parser.Aux[Commands.Compile, _] = Parser.generic
  implicit val strictConfigureParser: Parser.Aux[Commands.Configure, _] = Parser.generic
  implicit val strictConsoleParser: Parser.Aux[Commands.Console, _] = Parser.generic
  implicit val strictHelpParser: Parser.Aux[Commands.Help, _] = Parser.generic
  implicit val strictProjectsParser: Parser.Aux[Commands.Projects, _] = Parser.generic
  implicit val strictRunParser: Parser.Aux[Commands.Run, _] = Parser.generic
  implicit val strictTestParser: Parser.Aux[Commands.Test, _] = Parser.generic

  val BaseMessages: caseapp.core.Messages[DefaultBaseCommand] =
    caseapp.core.Messages[DefaultBaseCommand]
  val CommandsMessages: caseapp.core.CommandsMessages[Commands.RawCommand] =
    implicitly[caseapp.core.CommandsMessages[Commands.RawCommand]]
  val CommandsParser: CommandParser[Commands.RawCommand] =
    implicitly[caseapp.core.CommandParser[Commands.RawCommand]]
}

object Main extends App {
  import Parsers._
/*  assert(CommandsParser != null)
  assert(CommandsMessages != null)*/
  println("Hello World!")
}
