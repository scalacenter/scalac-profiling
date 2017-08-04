package ch.epfl.scala

import ch.epfl.scala.profilers.ProfilingImpl

import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}

class ProfilingPlugin(val global: Global) extends Plugin {
  val name = "scalac-profiling"
  val description = "Adds instrumentation to keep an eye on Scalac performance."
  val components = List[PluginComponent](NewTypeComponent)

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
          // Don't show per call-site unless verbose is enabled for now
          if (global.settings.verbose.value)
            info("Macro data per call-site", macroProfiler.perCallSite)
          info("Macro data per file", macroProfiler.perFile)
          info("Macro data in total", macroProfiler.inTotal)
        }

        override def apply(unit: global.CompilationUnit): Unit = {
          val traverser = new implementation.ProfilingTraverser
          traverser.traverse(unit.body)
        }
      }
    }
  }
}
