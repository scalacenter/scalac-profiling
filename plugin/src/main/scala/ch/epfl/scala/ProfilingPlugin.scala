package ch.epfl.scala

import ch.epfl.scala.profilers.ProfilingImpl

import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}

class ProfilingPlugin(val global: Global) extends Plugin {
  val name = "scalac-profiling"
  val description = "Adds instrumentation to keep an eye on Scalac performance."
  val components = List[PluginComponent](NewTypeComponent)

  private final val LogCallSite = "log-macro-call-site"
  case class PluginConfig(logCallSite: Boolean)
  private final val config = PluginConfig(super.options.contains(LogCallSite))

  private def pad20(option: String): String = option + (" " * (20 - option.length))
  override def init(ops: List[String], e: (String) => Unit): Boolean = true
  override val optionsHelp: Option[String] = Some(s"""
       |-P:$name:${pad20(LogCallSite)} Logs macro information for every call-site.
    """.stripMargin)

  // Make it not `lazy` and it will slay the compiler :)
  lazy val implementation = new ProfilingImpl(ProfilingPlugin.this.global)
  implementation.registerProfilers()

  private object NewTypeComponent extends PluginComponent {
    override val global: implementation.global.type = implementation.global
    override val phaseName: String = "compile-newtype"
    override val runsAfter: List[String] = List("jvm")
    override val runsBefore: List[String] = List("terminal")

    import scala.reflect.internal.util.NoPosition
    override def newPhase(prev: Phase): Phase = {
      new StdPhase(prev) {
        private def info(msg: String): Unit =
          global.reporter.info(NoPosition, msg, true)
        private def info[T: pprint.TPrint](header: String, value: T): Unit = {
          val tokens = pprint.tokenize(value).mkString
          info(s"$header:\n$tokens")
        }

        override def run(): Unit = {
          // Run first the phase across all compilation units
          super.run()

          val macroProfiler = implementation.getMacroProfiler
          if (config.logCallSite)
            info("Macro data per call-site", macroProfiler.perCallSite)
          info("Macro data per file", macroProfiler.perFile)
          info("Macro data in total", macroProfiler.inTotal)
          val expansions =
            macroProfiler.repeatedExpansions.map(kv => global.showCode(kv._1) -> kv._2)
          info("Macro repeated expansions", expansions)
        }

        override def apply(unit: global.CompilationUnit): Unit = {
          val traverser = new implementation.ProfilingTraverser
          traverser.traverse(unit.body)
        }
      }
    }
  }
}
