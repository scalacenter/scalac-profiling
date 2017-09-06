/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala

import ch.epfl.scala.profilers.ProfilingImpl
import ch.epfl.scala.profilers.tools.Logger

import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}

class ProfilingPlugin(val global: Global) extends Plugin {
  val name = "scalac-profiling"
  val description = "Adds instrumentation to keep an eye on Scalac performance."
  val components = List[PluginComponent](NewTypeComponent)

  private final val LogCallSite = "log-macro-call-site"
  case class PluginConfig(logCallSite: Boolean)
  private final val config = PluginConfig(super.options.contains(LogCallSite))
  private final val logger = new Logger(global)

  private def pad20(option: String): String = option + (" " * (20 - option.length))
  override def init(ops: List[String], e: (String) => Unit): Boolean = true
  override val optionsHelp: Option[String] = Some(s"""
       |-P:$name:${pad20(LogCallSite)} Logs macro information for every call-site.
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

    import global.statistics.{implicitSearchesByType, implicitSearchesByPos}
    def reportStatistics(): Unit = {
      val macroProfiler = implementation.getMacroProfiler
      if (config.logCallSite)
        logger.info("Macro data per call-site", macroProfiler.perCallSite)
      logger.info("Macro data per file", macroProfiler.perFile)
      logger.info("Macro data in total", macroProfiler.inTotal)
      val expansions = macroProfiler.repeatedExpansions.map(showExpansion)
      logger.info("Macro repeated expansions", expansions)
      logger.info("Implicit searches by position", implicitSearchesByPos)

      // Make sure we get type information after typer to avoid crashing the compiler
      val stringifiedSearchCounter =
        global.exitingTyper(implicitSearchesByType.map(kv => kv._1.toString -> kv._2))
      logger.info("Implicit searches by type", stringifiedSearchCounter)
    }

    override def newPhase(prev: Phase): Phase = {
      new StdPhase(prev) {
        override def apply(unit: global.CompilationUnit): Unit = ()
        override def run(): Unit = {
          super.run()
          reportStatistics()
        }
      }
    }
  }
}
