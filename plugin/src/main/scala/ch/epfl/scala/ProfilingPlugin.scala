package ch.epfl.scala

import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}

class ProfilingPlugin(val global: Global) extends Plugin {
  val name = "newtype"
  val description = "Adds a newtype compile-time check a la Haskell."
  val components = List[PluginComponent](NewTypeComponent)

  lazy val implementation = new ProfilingImpl(ProfilingPlugin.this.global)

  private object NewTypeComponent extends PluginComponent {
    override val global: implementation.global.type = implementation.global
    override val phaseName: String = "compile-newtype"
    override val runsAfter: List[String] = List("typer")
    override val runsBefore: List[String] = List("patmat")

    override def newPhase(prev: Phase): Phase = {
      new StdPhase(prev) {
        override def apply(unit: global.CompilationUnit): Unit = {
          val traverser = new implementation.ProfilingTraverser
          traverser.traverse(unit.body)
        }
      }
    }
  }
}
