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
  case class MacroInfo(expandedMacros: Int, expandedNodes: Int, expansionMillis: Long) {
    def +(other: MacroInfo): MacroInfo = {
      val totalExpanded = expandedMacros + other.expandedMacros
      val totalNodes = expandedNodes + other.expandedNodes
      val totalTime = expansionMillis + other.expansionMillis
      MacroInfo(totalExpanded, totalNodes, totalTime)
    }
  }

  object MacroInfo {
    private[ProfilingImpl] final val Empty = MacroInfo(0, 0, 0L)
    def aggregate(infos: Iterator[MacroInfo]): MacroInfo = {
      infos.foldLeft(MacroInfo.Empty)(_ + _)
    }
  }

  import scala.reflect.internal.util.SourceFile
  case class MacroProfiler(
      perCallSite: Map[Position, MacroInfo],
      perFile: Map[SourceFile, MacroInfo],
      inTotal: MacroInfo,
      repeatedExpansions: Map[Tree, Int]
  )

  def getMacroProfiler: MacroProfiler = {
    import ProfilingMacroPlugin.{macroInfos, repeatedTrees}
    val perCallSite = macroInfos.toMap
    val perFile = perCallSite.groupBy(_._1.source).map {
      case (file, posInfos) =>
        val onlyInfos = posInfos.iterator.map(_._2)
        file -> MacroInfo.aggregate(onlyInfos)
    }
    val inTotal = MacroInfo.aggregate(perFile.iterator.map(_._2))
    val repeated = repeatedTrees.valuesIterator
      .filter(_.count > 1)
      .map(v => v.original -> v.count)
      .toMap
    MacroProfiler(perCallSite, perFile, inTotal, repeated)
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

    type RepeatedKey = (String, String)
    case class RepeatedValue(original: Tree, result: Tree, count: Int)
    private[ProfilingImpl] val repeatedTrees = perRunCaches.newMap[RepeatedKey, RepeatedValue]
    private[ProfilingImpl] val macroInfos = perRunCaches.newMap[Position, MacroInfo]
    private final val EmptyRepeatedValue = RepeatedValue(EmptyTree, EmptyTree, 0)

    import scala.tools.nsc.Mode
    import scala.tools.nsc.typechecker.MacrosStats
    import scala.reflect.internal.util.Statistics
    import ProfilingStatistics.preciseMacroTimer

    override def pluginsMacroExpand(t: Typer, expandee: Tree, mode: Mode, pt: Type): Option[Tree] = {
      object expander extends analyzer.DefMacroExpander(t, expandee, mode, pt) {

        /**
          * Overrides the default method that expands all macros.
          *
          * We perform this because we need our own timer and access to the first timer snapshot
          * in order to obtain the expansion time for every expanded tree.
          */
        override def apply(desugared: Tree): Tree = {
          val start = Statistics.startTimer(preciseMacroTimer)
          try super.apply(desugared)
          finally updateExpansionTime(desugared, start)
        }

        def updateExpansionTime(desugared: Tree, start: Statistics.TimerSnapshot): Unit = {
          Statistics.stopTimer(preciseMacroTimer, start)
          val timeMillis = preciseMacroTimer.nanos / 1000000
          val callSitePos = desugared.pos
          // Those that are not present failed to expand
          macroInfos.get(callSitePos).foreach { found =>
            val updatedInfo = found.copy(expansionMillis = timeMillis)
            macroInfos(callSitePos) = updatedInfo
          }
        }

        override def onSuccess(expanded: Tree) = {
          val callSitePos = expandee.pos
          val printedExpandee = showRaw(expandee)
          val printedExpanded = showRaw(expanded)
          val key = (printedExpandee, printedExpanded)
          val currentValue = repeatedTrees.getOrElse(key, EmptyRepeatedValue)
          val newValue = RepeatedValue(expandee, expanded, currentValue.count + 1)
          repeatedTrees.put(key, newValue)
          val macroInfo = macroInfos.getOrElse(callSitePos, MacroInfo.Empty)
          val expandedMacros = macroInfo.expandedMacros + 1
          val treeSize = macroInfo.expandedNodes + guessTreeSize(expanded)
          // Use 0L for the timer because it will be filled in by the caller `apply`
          macroInfos.put(callSitePos, MacroInfo(expandedMacros, treeSize, 0L))
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

object ProfilingStatistics {
  import scala.reflect.internal.util.Statistics
  import scala.reflect.internal.TypesStats.typerNanos
  // Define here so that it can be accessed from the outside if necessary.
  final val preciseMacroTimer = Statistics.newTimer("precise time in macroExpand")
}
