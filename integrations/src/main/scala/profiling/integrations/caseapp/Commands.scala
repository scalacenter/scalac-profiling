package profiling.integrations

import java.nio.file.Path

import caseapp.core.ArgParser
import caseapp.{ArgsName, CommandName, ExtraName, HelpMessage, Hidden, Recurse}
import caseapp.core.CommandMessages

object Commands {

  /*  sealed abstract class Mode(val name: String)

  /** The kind of items that should be returned for autocompletion */
  object Mode {
    case object Commands extends Mode("commands")
    case object Projects extends Mode("projects")
    case object ProjectBoundCommands extends Mode("project-commands")
    case object Flags extends Mode("flags")
    case object Reporters extends Mode("reporters")
    case object Protocols extends Mode("protocols")
    case object TestsFQCN extends Mode("testsfqcn")
    case object MainsFQCN extends Mode("mainsfqcn")

    implicit val completionModeRead: ArgParser[Mode] = ???
  }

  sealed abstract class BspProtocol(val name: String)

  object BspProtocol {
    case object Local extends BspProtocol("local")
    case object Tcp extends BspProtocol("tcp")

    implicit val bspProtocolRead: ArgParser[BspProtocol] = ???
  }

  sealed abstract class ReporterKind(val name: String)
  case object ScalacReporter extends ReporterKind("scalac")
  case object BloopReporter extends ReporterKind("bloop")*/

  sealed trait RawCommand {
    def cliOptions: CliOptions
  }

  sealed trait CompilingCommand extends RawCommand {
    //def project: String
    //def reporter: ReporterKind
  }

/*  sealed trait Tree[A]
  case class Leaf[A](value: A) extends Tree[A]
  case class Branch[A](
      left: Tree[A],
      right: Tree[A]
  ) extends Tree[A]*/

  case class Help(
      @Recurse cliOptions: CliOptions = CliOptions.default
  ) extends RawCommand

  case class Autocomplete(
      @Recurse cliOptions: CliOptions = CliOptions.default,
      //mode: Mode,
      //format: Format,
      /*      command: Option[String],
      project: Option[String]*/
  ) extends RawCommand

  case class About(
      @Recurse cliOptions: CliOptions = CliOptions.default
  ) extends RawCommand

  case class Projects(
      /*      @ExtraName("dot")
      @HelpMessage("Print out a dot graph you can pipe into `dot`. By default, false.")
      dotGraph: Boolean = false,*/
      @Recurse cliOptions: CliOptions = CliOptions.default
  ) extends RawCommand

  case class Configure(
      /*      @ExtraName("parallelism")
      @HelpMessage("Set the number of threads used for parallel compilation and test execution.")
      threads: Int = 4,*/
      @Recurse cliOptions: CliOptions = CliOptions.default
  ) extends RawCommand

  case class Clean(
      /*      @ExtraName("p")
      @HelpMessage("The projects to clean.")
      project: List[String] = Nil,
      @HelpMessage("Do not run clean for dependencies. By default, false.")
      isolated: Boolean = false,*/
      @Recurse cliOptions: CliOptions = CliOptions.default,
  ) extends RawCommand

  @CommandName("bsp")
  case class Bsp(
      /*/*      @ExtraName("p")
      @HelpMessage("The connection protocol for the bsp server. By default, local.")
      protocol: BspProtocol = BspProtocol.Local,*/
      @ExtraName("h")
      @HelpMessage("The server host for the bsp server (TCP only).")
      host: String = "127.0.0.1",
      @HelpMessage("The port for the bsp server (TCP only).")
      port: Int = 5101,
      @ExtraName("s")
      @HelpMessage("A path to a socket file to communicate through Unix sockets (local only).")
      socket: Option[Path] = None,
      @ExtraName("pn")
      @HelpMessage(
        "A path to a new existing socket file to communicate through Unix sockets (local only)."
      )
      pipeName: Option[String] = None,*/
      @Recurse cliOptions: CliOptions = CliOptions.default
  ) extends RawCommand

  case class Compile(
      /*      @ExtraName("p")
      @HelpMessage("The project to compile (will be inferred from remaining cli args).")
      project: String = "",
      @HelpMessage("Compile the project incrementally. By default, true.")
      incremental: Boolean = true,
/*      @HelpMessage("Pick reporter to show compilation messages. By default, bloop's used.")
      reporter: ReporterKind = BloopReporter,*/
      @ExtraName("w")
      @HelpMessage("Run the command when projects' source files change. By default, false.")
      watch: Boolean = false,*/
      @Recurse cliOptions: CliOptions = CliOptions.default,
  ) extends CompilingCommand

  case class Test(
      /*      @ExtraName("p")
      @HelpMessage("The project to test (will be inferred from remaining cli args).")
      project: String = "",
      @HelpMessage("Do not run tests for dependencies. By default, false.")
      isolated: Boolean = false,
      @ExtraName("o")
      @HelpMessage("The list of test suite filters to test for only.")
      only: List[String] = Nil,
      @HelpMessage("The arguments to pass in to the test framework.")
      args: List[String] = Nil,
/*      @HelpMessage("Pick reporter to show compilation messages. By default, bloop's used.")
      reporter: ReporterKind = BloopReporter,*/
      @ExtraName("w")
      @HelpMessage("Run the command when projects' source files change. By default, false.")
      watch: Boolean = false,*/
      @Recurse cliOptions: CliOptions = CliOptions.default
  ) extends CompilingCommand

  case class Console(
      /*      @ExtraName("p")
      @HelpMessage("The project to run the console at (will be inferred from remaining cli args).")
      project: String = "",
/*      @HelpMessage("Pick reporter to show compilation messages. By default, bloop's used.")
      reporter: ReporterKind = BloopReporter,*/
      @HelpMessage("Start up the console compiling only the target project's dependencies.")
      excludeRoot: Boolean = false,*/
      @Recurse cliOptions: CliOptions = CliOptions.default
  ) extends CompilingCommand

  case class Run(
      /*      @ExtraName("p")
      @HelpMessage("The project to run (will be inferred from remaining cli args).")
      project: String = "",
      @ExtraName("m")
      @HelpMessage("The main class to run. Leave unset to let bloop select automatically.")
      main: Option[String] = None,
/*      @HelpMessage("Pick reporter to show compilation messages. By default, bloop's used.")
      reporter: ReporterKind = BloopReporter,*/
      @HelpMessage("The arguments to pass in to the main class.")
      args: List[String] = Nil,
      @ExtraName("w")
      @HelpMessage("If set, run the command whenever projects' source files change.")
      watch: Boolean = false,*/
      @Recurse cliOptions: CliOptions = CliOptions.default
  ) extends CompilingCommand
}
