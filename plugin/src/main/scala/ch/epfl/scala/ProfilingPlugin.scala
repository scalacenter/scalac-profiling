/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala

import java.nio.file.Files
import ch.epfl.scala.profiledb.{ProfileDb, ProfileDbPath}
import ch.epfl.scala.profiledb.utils.{AbsolutePath, RelativePath}
import ch.epfl.scala.profilers.ProfilingImpl
import ch.epfl.scala.profilers.tools.{Logger, ScalaSettingsOps, SettingsOps}

import scala.reflect.internal.util.{NoPosition, SourceFile, Statistics}
import scala.reflect.io.Path
import scala.tools.nsc.Reporting.WarningCategory
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.util.Try
import scala.util.matching.Regex

class ProfilingPlugin(val global: Global) extends Plugin { self =>
  // Every definition used at init needs to be lazy otherwise it slays the compiler
  val name = "scalac-profiling"
  val description = "Adds instrumentation to keep an eye on Scalac performance."
  val components = List[PluginComponent](ProfilingComponent)

  private final lazy val ShowProfiles = "show-profiles"
  private final lazy val SourceRoot = "sourceroot"
  private final lazy val PrintSearchResult = "print-search-result"
  private final lazy val GenerateMacroFlamegraph = "generate-macro-flamegraph"
  private final lazy val GenerateGlobalFlamegraph = "generate-global-flamegraph"
  private final lazy val PrintFailedMacroImplicits = "print-failed-implicit-macro-candidates"
  private final lazy val GenerateProfileDb = "generate-profiledb"
  private final lazy val ShowConcreteImplicitTparams = "show-concrete-implicit-tparams"
  private final lazy val PrintSearchRegex = s"$PrintSearchResult:(.*)".r
  private final lazy val SourceRootRegex = s"$SourceRoot:(.*)".r

  def findOption(name: String, pattern: Regex): Option[String] = {
    super.options.find(_.startsWith(name)).flatMap {
      case pattern(matched) => Some(matched)
      case _ => None
    }
  }

  def findSearchIds(userOption: Option[String]): Set[Int] = {
    userOption match {
      case Some(value) => value.split(",", Int.MaxValue).map(_.toInt).toSet
      case None => Set.empty
    }
  }

  private final lazy val config = PluginConfig(
    showProfiles = super.options.contains(ShowProfiles),
    generateDb = super.options.contains(GenerateProfileDb),
    sourceRoot = findOption(SourceRoot, SourceRootRegex)
      .map(AbsolutePath.apply)
      .getOrElse(AbsolutePath.workingDirectory),
    printSearchIds = findSearchIds(findOption(PrintSearchResult, PrintSearchRegex)),
    generateMacroFlamegraph = super.options.contains(GenerateMacroFlamegraph),
    generateGlobalFlamegraph = super.options.contains(GenerateGlobalFlamegraph),
    printFailedMacroImplicits = super.options.contains(PrintFailedMacroImplicits),
    concreteTypeParamsInImplicits = super.options.contains(ShowConcreteImplicitTparams)
  )

  private lazy val logger = new Logger(global)

  private def pad20(option: String): String = option + (" " * (20 - option.length))

  override def init(ops: List[String], e: (String) => Unit): Boolean = true

  // format: off
  override val optionsHelp: Option[String] = Some(
    s"""
       |-P:$name:${pad20(GenerateGlobalFlamegraph)} Creates a global flamegraph of implicit searches for all compilation units. Use the `-P:$name:$SourceRoot` option to manage the root directory, otherwise, a working directory (defined by the `user.dir` property) will be picked.
       |-P:$name:${pad20(GenerateMacroFlamegraph)} Generates a flamegraph for macro expansions. The flamegraph for implicit searches is enabled by default.
       |-P:$name:${pad20(GenerateProfileDb)} Generates profiledb (will be removed later).
       |-P:$name:${pad20(PrintFailedMacroImplicits)} Prints trees of all failed implicit searches that triggered a macro expansion.
       |-P:$name:${pad20(PrintSearchResult)}:_ Print implicit search result trees for a list of search ids separated by a comma.
       |-P:$name:${pad20(ShowConcreteImplicitTparams)} Shows types in flamegraphs of implicits with concrete type params.
       |-P:$name:${pad20(ShowProfiles)} Logs profile information for every call-site.
       |-P:$name:${pad20(SourceRoot)}:_ Sets the source root for this project.
    """.stripMargin
  ) // format: on

  lazy val implementation = new ProfilingImpl(ProfilingPlugin.this.global, config, logger)
  implementation.registerProfilers()

  private object ProfilingComponent extends PluginComponent {
    override val global: implementation.global.type = implementation.global
    override val phaseName: String = "scalacenter-profiling"
    override val runsAfter: List[String] = List("jvm")
    override val runsBefore: List[String] = List("terminal")

    private def showExpansion(expansion: (global.Tree, Int)): (String, Int) =
      global.showCode(expansion._1) -> expansion._2

    // This is just for displaying purposes
    import scala.collection.mutable.LinkedHashMap
    private def toLinkedHashMap[K, V](xs: Seq[(K, V)]): LinkedHashMap[K, V] = {
      val builder = LinkedHashMap.newBuilder[K, V]
      builder.++=(xs)
      builder.result()
    }

    private def reportStatistics(graphsPath: AbsolutePath): Unit = {
      val globalDir =
        if (config.generateGlobalFlamegraph) {
          val scalaDir =
            if (ScalaSettingsOps.isScala212)
              "scala-2.12"
            else if (ScalaSettingsOps.isScala213)
              "scala-2.13"
            else
              sys.error(
                s"Currently, only Scala 2.12 and 2.13 are supported, " +
                  s"but [${global.settings.source.value}] has been spotted"
              )

          val globalDir =
            ProfileDbPath.toGraphsProfilePath(
              config.sourceRoot.resolve(RelativePath(s"target/$scalaDir/classes"))
            )

          Some(globalDir)
        } else None

      val persistedGraphData = implementation.generateGraphData(graphsPath, globalDir)
      persistedGraphData.foreach(p => logger.info(s"Writing graph to ${p.underlying}"))

      if (config.showProfiles) {
        val macroProfiler = implementation.macroProfiler

        logger.info("Macro data per call-site", macroProfiler.perCallSite)
        logger.info("Macro data per file", macroProfiler.perFile)
        logger.info("Macro data in total", macroProfiler.inTotal)
        val expansions = macroProfiler.repeatedExpansions.map(showExpansion)
        logger.info("Macro repeated expansions", expansions)

        val macrosType = implementation.macrosByType.toList.sortBy(_._2)
        val macrosTypeLines = global.exitingTyper(macrosType.map(kv => kv._1.toString -> kv._2))
        logger.info("Macro expansions by type", toLinkedHashMap(macrosTypeLines))

        val implicitSearchesPosition = toLinkedHashMap(
          implementation.implicitSearchesByPos.toList.sortBy(_._2)
        )
        logger.info("Implicit searches by position", implicitSearchesPosition)

        val sortedImplicitSearches =
          implementation.implicitSearchesSourceFilesByType.toVector
            .flatMap {
              case (tpe, sourceFiles) =>
                val firings = implementation.implicitSearchesByType.getOrElse(tpe, 0)
                val files = sourceFiles.toList.flatMap {
                  case f if f.length > 0 =>
                    List(f.path)
                  case _ =>
                    List.empty
                }

                ImplicitSearchDebugInfo(firings, files).map(tpe -> _)
            }
            .sortBy(_._2.firings)
        // Make sure to stringify types right after typer to avoid compiler crashes
        val stringifiedSortedImplicitSearches =
          global.exitingTyper(
            sortedImplicitSearches
              .map(kv => kv._1.toString() -> kv._2)
          )
        logger.info("Implicit searches by type", toLinkedHashMap(stringifiedSortedImplicitSearches))
      }
    }

    import com.google.protobuf.duration.Duration
    import com.google.protobuf.timestamp.Timestamp
    import ch.epfl.scala.profiledb.{profiledb => schema}
    private final val nanoScale: Int = 1000000000

    private def toDuration(nanos: Long): Duration = {
      val seconds: Long = nanos / nanoScale
      val remainder: Int = (nanos % nanoScale).toInt
      Duration(seconds = seconds, nanos = remainder)
    }

    private lazy val getCurrentTimestamp: Timestamp = {
      val duration = toDuration(System.nanoTime())
      Timestamp(seconds = duration.seconds, nanos = duration.nanos)
    }

    private def toGlobalDatabase(statistics: Statistics): schema.Database = {
      import statistics.{Timer, Counter}
      def toSchemaTimer(scalacTimer: Timer): schema.Timer = {
        val id = scalacTimer.prefix
        val duration = toDuration(scalacTimer.nanos)
        schema.Timer(id = id, duration = Some(duration))
      }

      def toSchemaCounter(scalacCounter: Counter): schema.Counter = {
        val id = scalacCounter.prefix
        val ticks = scalacCounter.value.toLong
        schema.Counter(id = id, ticks = ticks)
      }

      val allScalacPhases = global.phaseDescriptors.map(_.phaseName)
      val scalacQuantities = statistics.allQuantities.toList
      val quantitiesPerPhase =
        allScalacPhases.map(phase => phase -> scalacQuantities.filter(_.showAt(phase)))
      val phaseProfiles = quantitiesPerPhase.map {
        case (phaseName, phaseQuantities) =>
          val timers = phaseQuantities.collect { case t: Timer => t }.map(toSchemaTimer)
          val counters = phaseQuantities.collect { case c: Counter => c }.map(toSchemaCounter)
          schema.PhaseProfile(name = phaseName, timers = timers, counters = counters)
      }

      val timestamp = Some(getCurrentTimestamp)
      val runProfile = Some(schema.RunProfile(phaseProfiles = phaseProfiles))
      val entry = schema.DatabaseEntry(
        timestamp = timestamp,
        runProfile = runProfile,
        compilationUnitProfile = None
      )
      schema.Database(
        `type` = schema.ContentType.GLOBAL,
        entries = List(entry)
      )
    }

    private def getOutputDirFor(absFile: AbstractFile): Path = Path {
      val outputPath = global.settings.outputDirs.outputDirFor(absFile).path
      if (outputPath.isEmpty) "." else outputPath
    }

    private def dbPathFor(sourceFile: SourceFile): Option[ProfileDbPath] = {
      val absoluteSourceFile = AbsolutePath(sourceFile.file.path)
      val targetPath = absoluteSourceFile.toRelative(config.sourceRoot)
      if (targetPath.syntax.endsWith(".scala")) {
        val outputDir = getOutputDirFor(sourceFile.file)
        val absoluteOutput = AbsolutePath(outputDir.jfile)
        val dbTargetPath = ProfileDbPath.toProfileDbPath(targetPath)
        Some(ProfileDbPath(absoluteOutput, dbTargetPath))
      } else None
    }

    private final val EmptyDuration = Duration.defaultInstance
    private def profileDbEntryFor(sourceFile: SourceFile): schema.DatabaseEntry = {
      import scala.reflect.internal.util.Position
      import implementation.{MacroInfo, ImplicitInfo}

      def perFile[V](ps: Map[Position, V]): Map[Position, V] =
        ps.collect { case t @ (pos, _) if pos.source == sourceFile => t }

      def toPos(pos: Position): schema.Position = {
        val point = pos.point
        val line = pos.line
        val column = pos.column
        schema.Position(point = point, line = line, column = column)
      }

      def toMacroProfile(pos: Position, info: MacroInfo): schema.MacroProfile = {
        val currentPos = Some(toPos(pos))
        val expandedMacros = info.expandedMacros.toLong
        val approximateSize = info.expandedNodes.toLong
        val duration = Some(toDuration(info.expansionTime.toNanos))
        schema.MacroProfile(
          position = currentPos,
          expandedMacros = expandedMacros,
          approximateSize = approximateSize,
          duration = duration
        )
      }

      def toImplicitProfile(pos: Position, info: ImplicitInfo): schema.ImplicitSearchProfile = {
        val currentPos = Some(toPos(pos))
        val searches = info.count.toLong
        val duration = Some(EmptyDuration)
        schema.ImplicitSearchProfile(
          position = currentPos,
          searches = searches,
          duration = duration
        )
      }

      val macroProfiles = perFile(implementation.macroProfiler.perCallSite)
        .map { case (pos: Position, info: MacroInfo) => toMacroProfile(pos, info) }
      val implicitSearchProfiles = perFile(implementation.implicitProfiler.perCallSite)
        .map { case (pos: Position, info: ImplicitInfo) => toImplicitProfile(pos, info) }

      val timestamp = Some(getCurrentTimestamp)
      val compilationUnitProfile = Some(
        schema.CompilationUnitProfile(
          macroProfiles = macroProfiles.toList,
          implicitSearchProfiles = implicitSearchProfiles.toList
        )
      )
      schema.DatabaseEntry(timestamp = timestamp, compilationUnitProfile = compilationUnitProfile)
    }

    def writeDatabase(db: schema.Database, path: ProfileDbPath): Try[schema.Database] = {
      if (Files.exists(path.target.underlying)) {
        ProfileDb.read(path).flatMap { oldDb =>
          val oldDbType = oldDb.`type`
          val newDbType = db.`type`
          if (
            oldDbType.isGlobal && newDbType.isGlobal ||
            (oldDbType.isPerCompilationUnit && newDbType.isPerCompilationUnit)
          ) {
            val updatedDb = oldDb.addAllEntries(db.entries)
            ProfileDb.write(updatedDb, path)
          } else Try(sys.error(s"Db type mismatch: $newDbType != $oldDbType"))
        }
      } else ProfileDb.write(db, path)
    }

    lazy val globalOutputDir = AbsolutePath(
      new java.io.File(
        global.settings.outputDirs.getSingleOutput
          .map(_.file.getAbsolutePath)
          .getOrElse(global.settings.outdir.value)
      )
    )

    private final val PerCompilationUnit = schema.ContentType.PER_COMPILATION_UNIT
    override def newPhase(prev: Phase): Phase = {
      new StdPhase(prev) {
        override def apply(unit: global.CompilationUnit): Unit = {
          if (
            SettingsOps.areStatisticsEnabled(global) &&
            config.generateDb
          ) {
            val currentSourceFile = unit.source
            val compilationUnitEntry = profileDbEntryFor(currentSourceFile)
            dbPathFor(currentSourceFile) match {
              case Some(profileDbPath) =>
                val canonicalTarget = profileDbPath.target.underlying.normalize()
                logger.debug(s"Creating profiledb for ${canonicalTarget}")
                val freshDatabase =
                  schema.Database(`type` = PerCompilationUnit, entries = List(compilationUnitEntry))
                writeDatabase(freshDatabase, profileDbPath).failed
                  .foreach(t => global.globalError(s"I/O profiledb error: ${t.getMessage}"))
              case None => global.globalError(s"Could not write profiledb for $currentSourceFile.")
            }
          }
        }

        override def run(): Unit = {
          super.run()

          if (!SettingsOps.areStatisticsEnabled(global)) {
            val flagName = global.settings.Ystatistics.name
            global.runReporting.warning(
              NoPosition,
              s"`${self.name}` compiler plugin requires the option `$flagName` to be enabled",
              WarningCategory.OtherDebug,
              ""
            )
          }

          val graphsRelativePath = ProfileDbPath.GraphsProfileDbRelativePath
          val graphsDir = globalOutputDir.resolve(graphsRelativePath)
          reportStatistics(graphsDir)

          if (config.generateDb) {
            val globalDatabase = toGlobalDatabase(global.statistics)
            val globalRelativePath = ProfileDbPath.GlobalProfileDbRelativePath
            val globalProfileDbPath = ProfileDbPath(globalOutputDir, globalRelativePath)
            val pathToWrite = globalProfileDbPath.target.underlying.toAbsolutePath
            logger.info(s"Creating global statistics information at $pathToWrite.")
            writeDatabase(globalDatabase, globalProfileDbPath)
          }
        }
      }
    }
  }
}
