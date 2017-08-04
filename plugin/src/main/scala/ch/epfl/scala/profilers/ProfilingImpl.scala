package ch.epfl.scala.profilers

import ch.epfl.scala.profilers.tools.Debugger

final class ProfilingImpl[G <: scala.tools.nsc.Global](val global: G) {
  import global._
  val debugger = new Debugger(global)

  def registerProfilers(): Unit = {
    // Register our profiling macro plugin
    analyzer.addMacroPlugin(ProfilingMacroPlugin)
  }

  /**
    * Represents the profiling information about expanded macros.
    *
    * Note that we could derive the value of expanded macros from the
    * number of instances of [[MacroInfo]] if it were not by the fact
    * that a macro can expand in the same position more than once. We
    * want to be able to report/analyse such cases on their own, so
    * we keep it as a paramater of this entity.
    */
  case class MacroInfo(expandedMacros: Int, expandedNodes: Int) {
    def +(other: MacroInfo): MacroInfo = {
      val totalExpanded = expandedMacros + other.expandedMacros
      val totalNodes = expandedNodes + other.expandedNodes
      MacroInfo(totalExpanded, totalNodes)
    }
  }

  object MacroInfo {
    private[ProfilingImpl] final val Empty = MacroInfo(0, 0)
    def aggregate(infos: Iterator[MacroInfo]): MacroInfo = {
      infos.foldLeft(MacroInfo.Empty)(_ + _)
    }
  }

  import scala.reflect.internal.util.SourceFile
  case class MacroProfiler(
      perCallSite: Map[Position, MacroInfo],
      perFile: Map[SourceFile, MacroInfo],
      inTotal: MacroInfo
  )

  def getMacroProfiler: MacroProfiler = {
    val perCallSite = ProfilingMacroPlugin.macroInfos.toMap
    val perFile = perCallSite.groupBy(_._1.source).map {
      case (file, posInfos) =>
        val onlyInfos = posInfos.iterator.map(_._2)
        file -> MacroInfo.aggregate(onlyInfos)
    }
    val inTotal = MacroInfo.aggregate(perFile.iterator.map(_._2))
    MacroProfiler(perCallSite, perFile, inTotal)
  }

  import global.analyzer.MacroPlugin
  /**
    * The profiling macro plugin instruments the macro interface to check
    * certain behaviours. For now, the profiler takes care of:
    *
    * - Reporting the size of expanded trees.
    *
    * It would be useful in the future to report on the amount of expanded
    * trees that are and are not discarded.
    */
  object ProfilingMacroPlugin extends MacroPlugin {
    type Typer = analyzer.Typer
    private def guessTreeSize(tree: Tree): Int =
      1 + tree.children.map(guessTreeSize).sum

    private[ProfilingImpl] val macroInfos = perRunCaches.newMap[Position, MacroInfo]

    import scala.tools.nsc.Mode
    override def pluginsMacroExpand(t: Typer, expandee: Tree, mode: Mode, pt: Type): Option[Tree] = {
      object expander extends analyzer.DefMacroExpander(t, expandee, mode, pt) {
        override def onSuccess(expanded: Tree) = {
          val callSitePos = expandee.pos
          val macroInfo = macroInfos.get(callSitePos).getOrElse(MacroInfo.Empty)
          val expandedMacros = macroInfo.expandedMacros + 1
          val treeSize = macroInfo.expandedNodes + guessTreeSize(expanded)
          macroInfos += callSitePos -> MacroInfo(expandedMacros, treeSize)
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
