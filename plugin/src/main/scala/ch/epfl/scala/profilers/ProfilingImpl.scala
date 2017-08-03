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

  /**
   * The profiling macro plugin instruments the macro interface to check
   * certain behaviours. For now, the profiler takes care of:
   * 
   * - Reporting the size of expanded trees.
   * 
   * It would be useful in the future to report on the amount of expanded
   * trees that are and are not discarded.
   */
  object ProfilingMacroPlugin extends global.analyzer.MacroPlugin {
    type Typer = analyzer.Typer
    private def guessTreeSize(tree: Tree): Int =
      1 + tree.children.map(guessTreeSize).sum

    override def pluginsMacroExpand(t: Typer, expandee: Tree, mode: Mode, pt: Type): Option[Tree] = {
      object expander extends analyzer.DefMacroExpander(t, expandee, mode, pt) {
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
