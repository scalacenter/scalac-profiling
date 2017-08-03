package ch.epfl.scala.profilers

import ch.epfl.scala.profilers.tools.Debugger

final class ProfilingImpl[G <: scala.tools.nsc.Global](val global: G) {
  import global._
  val debugger = new Debugger(global)

  def registerProfilers(): Unit = {
    // Register our profiling macro plugin
    analyzer.addMacroPlugin(ProfilingMacroPlugin)
  }

  import scala.tools.nsc.Mode
  object ProfilingMacroPlugin extends global.analyzer.MacroPlugin {
    def guessTreeSize(tree: Tree): Int = tree.children.size
    override def pluginsMacroExpand(typer: analyzer.Typer,
                                    expandee: Tree,
                                    mode: Mode,
                                    pt: Type): Option[Tree] = {
      object expander extends analyzer.DefMacroExpander(typer, expandee, mode, pt) {
        override def onSuccess(expanded: Tree) = {
          val callSitePos = expandee.pos
          val treeSize = guessTreeSize(expanded)
          val msg = s"Expanded into a tree of size $treeSize: ${expanded}"
          reporter.info(callSitePos, msg, true)
          super.onSuccess(expanded)
        }
      }
      Some(expander(expandee))
    }
  }

  final class ProfilingTraverser extends Traverser {
    override def traverse(tree: Tree): Unit = {
      super.traverse(tree)
    }
  }
}
