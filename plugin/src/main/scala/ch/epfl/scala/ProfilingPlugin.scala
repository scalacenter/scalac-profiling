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
        override def apply(unit: global.CompilationUnit): Unit = {
          val macroProfiler = implementation.getMacroProfiler
          info(s"Expanded macros: ${macroProfiler.expandedMacros}")
          info(s"Expanded node trees: ${macroProfiler.expandedNodes}")
          val traverser = new implementation.ProfilingTraverser
          traverser.traverse(unit.body)
        }
      }
    }
  }
}
