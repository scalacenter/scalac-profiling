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
import ch.epfl.scala.profilers.tools.Logger

import scala.reflect.internal.util.Statistics
import scala.reflect.io.Path
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.util.SourceFile
import scala.util.Try
import scala.util.matching.Regex

class ProfilingPlugin(val global: Global) extends Plugin {
  val name = "scalac-profiling"
  val description = "Adds instrumentation to keep an eye on Scalac performance."
  val components = List[PluginComponent](NewTypeComponent)

  private final val ShowProfiles = "show-profiles"
  private final val SourceRoot = "sourceroot"
  private final val SourceRootRegex = s"sourceroot:(.*)".r
  case class PluginConfig(showProfiles: Boolean, sourceRoot: Option[AbsolutePath])

  def findOption(name: String, pattern: Regex): Option[String] = {
    super.options.find(_.startsWith(name)).flatMap {
      case pattern(matched) => Some(matched)
      case _ => None
    }
  }

  private final val config = PluginConfig(
    super.options.contains(ShowProfiles),
    findOption(SourceRoot, SourceRootRegex).map(AbsolutePath.apply)
  )

  private final val logger = new Logger(global)

  private def pad20(option: String): String = option + (" " * (20 - option.length))
  override def init(ops: List[String], e: (String) => Unit): Boolean = true
  override val optionsHelp: Option[String] = Some(s"""
       |-P:$name:${pad20(SourceRoot)} Sets the source root for this project.
       |-P:$name:${pad20(ShowProfiles)} Logs profile information for every call-site.
    """.stripMargin)

  // Make it not `lazy` and it will slay the compiler :)
  lazy val implementation = new ProfilingImpl(ProfilingPlugin.this.global, logger)
  implementation.registerProfilers()

  private object NewTypeComponent extends PluginComponent {
    override val global: implementation.global.type = implementation.global
    override val phaseName: String = "compile-newtype"
    override val runsAfter: List[String] = List("jvm")
    override val runsBefore: List[String] = List("terminal")

    private def showExpansion(expansion: (global.Tree, Int)): (String, Int) =
      global.showCode(expansion._1) -> expansion._2

    private def reportStatistics(): Unit = if (config.showProfiles) {
      val macroProfiler = implementation.getMacroProfiler
      logger.info("Macro data per call-site", macroProfiler.perCallSite)
      logger.info("Macro data per file", macroProfiler.perFile)
      logger.info("Macro data in total", macroProfiler.inTotal)
      val expansions = macroProfiler.repeatedExpansions.map(showExpansion)
      logger.info("Macro repeated expansions", expansions)

      import implementation.{implicitSearchesByPos, implicitSearchesByType}
      logger.info("Implicit searches by position", implicitSearchesByPos)
      // Make sure we get type information after typer to avoid crashing the compiler
      val stringifiedSearchCounter =
        global.exitingTyper(implicitSearchesByType.map(kv => kv._1.toString -> kv._2))
      logger.info("Implicit searches by type", stringifiedSearchCounter)
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
      import statistics.{Timer, Counter, Quantity}
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

      val allScalacPhases = global.phaseNames
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

    private final def sourceRoot = config.sourceRoot.getOrElse(AbsolutePath.workingDirectory)
    private def dbPathFor(sourceFile: SourceFile): Option[ProfileDbPath] = {
      val absoluteSourceFile = AbsolutePath(sourceFile.file.path)
      val targetPath = absoluteSourceFile.toRelative(sourceRoot)
      if (targetPath.syntax.endsWith(".scala")) {
        val outputDir = getOutputDirFor(sourceFile.file)
        val absoluteOutput = AbsolutePath(getOutputDirFor(sourceFile.file).jfile)
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
        val approximateSize = info.expandedNodes
        val duration = Some(toDuration(info.expansionNanos))
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

      val macroProfiles = perFile(implementation.getMacroProfiler.perCallSite)
        .map { case (pos, info) => toMacroProfile(pos, info) }
      val implicitSearchProfiles = perFile(implementation.getImplicitProfiler.perCallSite)
        .map { case (pos, info) => toImplicitProfile(pos, info) }

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
          if (oldDbType.isGlobal && newDbType.isGlobal ||
            (oldDbType.isPerCompilationUnit && newDbType.isPerCompilationUnit)) {
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
          .getOrElse(global.settings.d.value)
      )
    )

    private final val PerCompilationUnit = schema.ContentType.PER_COMPILATION_UNIT
    override def newPhase(prev: Phase): Phase = {
      new StdPhase(prev) {
        override def apply(unit: global.CompilationUnit): Unit = {
          val currentSourceFile = unit.source
          val compilationUnitEntry = profileDbEntryFor(currentSourceFile)
          dbPathFor(currentSourceFile) match {
            case Some(profileDbPath) =>
              logger.info(s"Creating compilation unit for ${profileDbPath.target}")
              val freshDatabase =
                schema.Database(`type` = PerCompilationUnit, entries = List(compilationUnitEntry))
              writeDatabase(freshDatabase, profileDbPath).failed
                .foreach(t => global.globalError(s"I/O profiledb error: ${t.getMessage}"))
            case None => global.globalError(s"Could not write profiledb for $currentSourceFile.")
          }
        }

        override def run(): Unit = {
          super.run()
          reportStatistics()
          val globalDatabase = toGlobalDatabase(global.statistics)
          val globalRelativePath = ProfileDbPath.GlobalProfileDbRelativePath
          val globalProfileDbPath = ProfileDbPath(globalOutputDir, globalRelativePath)
          writeDatabase(globalDatabase, globalProfileDbPath)
        }
      }
    }
  }
}
