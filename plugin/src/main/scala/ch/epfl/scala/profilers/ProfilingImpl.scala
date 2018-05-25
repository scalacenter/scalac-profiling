/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala.profilers

import java.nio.file.{Files, Path, StandardOpenOption}

import ch.epfl.scala.PluginConfig
import ch.epfl.scala.profiledb.utils.AbsolutePath

import scala.tools.nsc.Global
import ch.epfl.scala.profilers.tools.{Logger, QuantitiesHijacker}

import scala.reflect.internal.util.StatisticsStatics

final class ProfilingImpl[G <: Global](
    override val global: G,
    config: PluginConfig,
    logger: Logger[G]
) extends ProfilingStats {
  import global._

  def registerProfilers(): Unit = {
    // Register our profiling macro plugin
    analyzer.addMacroPlugin(ProfilingMacroPlugin)
    analyzer.addAnalyzerPlugin(ProfilingAnalyzerPlugin)
  }

  case class MacroExpansion(tree: Tree, tpe: Type)

  /**
    * Represents the profiling information about expanded macros.
    *
    * Note that we could derive the value of expanded macros from the
    * number of instances of [[MacroInfo]] if it were not by the fact
    * that a macro can expand in the same position more than once. We
    * want to be able to report/analyse such cases on their own, so
    * we keep it as a paramater of this entity.
    */
  case class MacroInfo(expandedMacros: Int, expandedNodes: Int, expansionNanos: Long) {
    def +(other: MacroInfo): MacroInfo = {
      val totalExpanded = expandedMacros + other.expandedMacros
      val totalNodes = expandedNodes + other.expandedNodes
      val totalTime = expansionNanos + other.expansionNanos
      MacroInfo(totalExpanded, totalNodes, totalTime)
    }
  }

  object MacroInfo {
    final val Empty = MacroInfo(0, 0, 0L)
    implicit val macroInfoOrdering: Ordering[MacroInfo] = Ordering.by(_.expansionNanos)
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

  def toMillis(nanos: Long): Long =
    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(nanos)

  def groupPerFile[V](
      kvs: Map[Position, V]
  )(empty: V, aggregate: (V, V) => V): Map[SourceFile, V] = {
    kvs.groupBy(_._1.source).mapValues {
      case posInfos: Map[Position, V] => posInfos.valuesIterator.fold(empty)(aggregate)
    }
  }

  lazy val macroProfiler: MacroProfiler = {
    import ProfilingMacroPlugin.macroInfos //, repeatedTrees}
    val perCallSite = macroInfos.toMap
    val perFile = groupPerFile(perCallSite)(MacroInfo.Empty, _ + _)
      .mapValues(i => i.copy(expansionNanos = toMillis(i.expansionNanos)))
    val inTotal = MacroInfo.aggregate(perFile.valuesIterator)

    /*    val repeated = repeatedTrees.toMap.valuesIterator
      .filter(_.count > 1)
      .map(v => v.original -> v.count)
      .toMap*/

    // perFile and inTotal are already converted to millis
    val callSiteNanos = perCallSite
      .mapValues(i => i.copy(expansionNanos = toMillis(i.expansionNanos)))
    MacroProfiler(callSiteNanos, perFile, inTotal, Map.empty) //repeated)
  }

  case class ImplicitInfo(count: Int) {
    def +(other: ImplicitInfo): ImplicitInfo = ImplicitInfo(count + other.count)
  }

  object ImplicitInfo {
    final val Empty = ImplicitInfo(0)
    def aggregate(infos: Iterator[ImplicitInfo]): ImplicitInfo = infos.fold(Empty)(_ + _)
    implicit val infoOrdering: Ordering[ImplicitInfo] = Ordering.by(_.count)
  }

  case class ImplicitProfiler(
      perCallSite: Map[Position, ImplicitInfo],
      perFile: Map[SourceFile, ImplicitInfo],
      perType: Map[Type, ImplicitInfo],
      inTotal: ImplicitInfo
  )

  lazy val implicitProfiler: ImplicitProfiler = {
    val perCallSite = implicitSearchesByPos.toMap.mapValues(ImplicitInfo.apply)
    val perFile = groupPerFile[ImplicitInfo](perCallSite)(ImplicitInfo.Empty, _ + _)
    val perType = implicitSearchesByType.toMap.mapValues(ImplicitInfo.apply)
    val inTotal = ImplicitInfo.aggregate(perFile.valuesIterator)
    ImplicitProfiler(perCallSite, perFile, perType, inTotal)
  }

  // Copied from `TypeDiagnostics` to have expanded types in implicit search
  private object DealiasedType extends TypeMap {
    def apply(tp: Type): Type = tp match {
      case TypeRef(pre, sym, _) if sym.isAliasType && !sym.isInDefaultNamespace =>
        mapOver(tp.dealias)
      case _ => mapOver(tp)
    }
  }

  def concreteTypeFromSearch(tree: Tree, default: Type): Type = {
    tree match {
      case EmptyTree => default
      case Block(_, expr) => expr.tpe
      case Try(block, _, _) =>
        block match {
          case Block(_, expr) => expr.tpe
          case t => t.tpe
        }
      case t =>
        val treeType = t.tpe
        if (treeType == null || treeType == NoType) default else treeType
    }
  }

  def generateGraphData(outputDir: AbsolutePath): List[AbsolutePath] = {
    Files.createDirectories(outputDir.underlying)
    val graphName = s"implicit-searches-${java.lang.Long.toString(System.currentTimeMillis())}"
    /*    val dotFile = outputDir.resolve(s"$graphName.dot")
    ProfilingAnalyzerPlugin.dottify(graphName, dotFile.underlying)*/
    val flamegraphFile = outputDir.resolve(s"$graphName.flamegraph")
    ProfilingAnalyzerPlugin.foldImplicitStacks(flamegraphFile.underlying)
    List(flamegraphFile)
  }

  // Moving this here so that it's accessible to the macro plugin
  private type Entry =
    (global.analyzer.ImplicitSearch, statistics.TimerSnapshot, statistics.TimerSnapshot)
  private var implicitsStack: List[Entry] = Nil

  private object ProfilingAnalyzerPlugin extends global.analyzer.AnalyzerPlugin {
    import scala.collection.mutable
    private val implicitsTimers = perRunCaches.newAnyRefMap[Type, statistics.Timer]()
    private val searchIdsToTargetTypes = perRunCaches.newMap[Int, Type]()
    private val stackedNanos = perRunCaches.newMap[Int, (Long, Type)]()
    private val stackedNames = perRunCaches.newMap[Int, String]()
    private val registeredQuantities = QuantitiesHijacker.getRegisteredQuantities(global)
    private val searchIdsToTimers = perRunCaches.newMap[Int, statistics.Timer]()
    private val implicitsDependants = new mutable.AnyRefMap[Type, mutable.HashSet[Type]]()
    private val searchIdChildren = perRunCaches.newMap[Int, List[analyzer.ImplicitSearch]]()

    private def typeToString(`type`: Type): String =
      global.exitingTyper(`type`.toLongString).trim
    def logType(t: Type): Unit =
      logger.info(t.map(t => t.underlying.typeOfThis).toLongString)

    def foldImplicitStacks(outputPath: Path): Unit = {
      // This part is memory intensive and hence the use of java collections
      val stacksJavaList = new java.util.ArrayList[String]()
      stackedNanos.foreach {
        case (id, (nanos, tpe)) =>
          val stackName =
            stackedNames.getOrElse(id, sys.error(s"Stack name for search id ${id} doesn't exist!"))
          //val count = implicitSearchesByType.getOrElse(tpe, sys.error(s"No counter for ${tpe}"))
          stacksJavaList.add(s"$stackName ${nanos / 1000}")
      }
      java.util.Collections.sort(stacksJavaList)
      Files.write(outputPath, stacksJavaList, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    }

    def dottify(graphName: String, outputPath: Path): Unit = {
      def clean(`type`: Type) = typeToString(`type`).replace("\"", "\'")
      def qualify(node: String, timing: Long, counter: Int): String = {
        val nodeName = node.stripPrefix("\"").stripSuffix("\"")
        val style = if (timing >= 500) "style=filled, fillcolor=\"#ea9d8f\"," else ""
        s"""$node [${style}label="${nodeName}\\l${counter} times = ${timing}ms"];"""
      }

      val nodes = implicitSearchesByType.keys
      val nodesIds = nodes.map(`type` => `type` -> s""""${clean(`type`)}"""").toMap
      def getNodeId(`type`: Type): String = {
        nodesIds.getOrElse(
          `type`,
          sys.error {
            s"""Id for ${`type`} doesn't exist.
              |
              |  Information about the type:
              |   - `structure` -> ${global.showRaw(`type`)}
              |   - `safeToString` -> ${`type`.safeToString}
              |   - `toLongString` after typer -> ${typeToString(`type`)}
              |   - `typeSymbol` -> ${`type`.typeSymbol}
            """.stripMargin
          }
        )
      }

      val connections = for {
        (dependee, dependants) <- implicitsDependants.toSet
        dependant <- dependants
        dependantId = getNodeId(dependant)
        dependeeId = getNodeId(dependee)
        if dependeeId != dependantId && !dependantId.isEmpty && !dependeeId.isEmpty
      } yield s"$dependantId -> $dependeeId;"

      val nodeInfos = nodes.map { `type` =>
        val id = getNodeId(`type`)
        val timer = getImplicitTimerFor(`type`).nanos / 1000000
        val count = implicitSearchesByType.getOrElse(`type`, sys.error(s"No counter for ${`type`}"))
        qualify(id, timer, count)
      }

      val graph = s"""digraph "$graphName" {
        | graph [ranksep=0, rankdir=LR];
        |${nodeInfos.mkString("  ", "\n  ", "\n  ")}
        |${connections.mkString("  ", "\n  ", "\n  ")}
        |}""".stripMargin.getBytes
      Files.write(outputPath, graph, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    }

    private def getImplicitTimerFor(candidate: Type): statistics.Timer =
      implicitsTimers.getOrElse(candidate, sys.error(s"Timer for ${candidate} doesn't exist"))

    private def getSearchTimerFor(searchId: Int): statistics.Timer = {
      searchIdsToTimers
        .getOrElse(searchId, sys.error(s"Missing non-cumulative timer for $searchId"))
    }

    override def pluginsNotifyImplicitSearch(search: global.analyzer.ImplicitSearch): Unit = {
      if (StatisticsStatics.areSomeColdStatsEnabled() && statistics.areStatisticsLocallyEnabled) {
        val targetType = search.pt
        val targetPos = search.pos

        // Stop counter of dependant implicit search
        implicitsStack.headOption.foreach {
          case (search, _, searchStart) =>
            val searchTimer = getSearchTimerFor(search.searchId)
            statistics.stopTimer(searchTimer, searchStart)
        }

        // We add ourselves to the child list of our parent implicit search
        implicitsStack.headOption match {
          case Some((prevSearch, _, _)) =>
            val prevId = prevSearch.searchId
            val prevChilds = searchIdChildren.getOrElse(prevId, Nil)
            searchIdChildren.update(prevId, search :: prevChilds)
          case None => ()
        }

        // Create timer and unregister it so that it is invisible in console output
        val prefix = s"  $targetType"
        val perTypeTimer = implicitsTimers
          .getOrElseUpdate(targetType, statistics.newTimer(prefix, "typer"))
        registeredQuantities.remove(s"/$prefix")

        // Create non-cumulative timer for the search and unregister it too
        val searchId = search.searchId
        val searchPrefix = s"  implicit search ${searchId}"
        val searchTimer = statistics.newTimer(searchPrefix, "typer")
        registeredQuantities.remove(s"/$searchPrefix")
        searchIdsToTimers.+=(searchId -> searchTimer)

        // Start the timer as soon as possible
        val implicitTypeStart = statistics.startTimer(perTypeTimer)
        val searchStart = statistics.startTimer(searchTimer)

        // Update all timers and counters
        val typeCounter = implicitSearchesByType.getOrElse(targetType, 0)
        implicitSearchesByType.update(targetType, typeCounter + 1)
        val posCounter = implicitSearchesByPos.getOrElse(targetPos, 0)
        implicitSearchesByPos.update(targetPos, posCounter + 1)
        if (global.analyzer.openMacros.nonEmpty)
          statistics.incCounter(implicitSearchesByMacrosCount)

        searchIdsToTargetTypes.+=((search.searchId, targetType))

/*        // Add dependants once we hit a concrete node
        search.context.openImplicits.headOption.foreach { dependant =>
          implicitsDependants
            .getOrElseUpdate(targetType, new mutable.HashSet())
            .+=(dependant.pt)
        }*/

        implicitsStack = (search, implicitTypeStart, searchStart) :: implicitsStack
      }
    }

    override def pluginsNotifyImplicitSearchResult(result: global.analyzer.SearchResult): Unit = {
      super.pluginsNotifyImplicitSearchResult(result)
      if (StatisticsStatics.areSomeColdStatsEnabled() && statistics.areStatisticsLocallyEnabled) {
        // 1. Get timer of the running search
        val (search, implicitTypeStart, searchStart) = implicitsStack.head
        val targetType = search.pt
        val timer = getImplicitTimerFor(targetType)

        // 2. Register the timing diff for every stacked name.
        def stopTimerFlamegraph(prev: Option[analyzer.ImplicitSearch]): Unit = {
          val searchId = search.searchId
          def missing(name: String): Nothing =
            sys.error(s"Missing $name for $searchId ($targetType).")

          // Detect macro name if the type we get comes from a macro to add it to the stack
          val macroName = {
            val errorTag = if (result.isFailure) " _[j]" else ""
            result.tree.attachments.get[analyzer.MacroExpansionAttachment] match {
              case Some(analyzer.MacroExpansionAttachment(expandee: Tree, _)) =>
                val expandeeSymbol = treeInfo.dissectApplied(expandee).core.symbol
                analyzer.loadMacroImplBinding(expandeeSymbol) match {
                  case Some(a) =>
                    s"(id ${searchId}) from `${a.className}.${a.methName}` _[i]"
                  case None => errorTag
                }
              case None => errorTag
            }
          }

          // Complete stack names of triggered implicit searches
          val children = searchIdChildren.getOrElse(searchId, Nil)
          prev.foreach { p =>
            val current = searchIdChildren.getOrElse(p.searchId, Nil)
            searchIdChildren.update(p.searchId, children ::: current)
          }

          val typeForStack = DealiasedType {
            if (!config.concreteTypeParamsInImplicits) targetType
            else concreteTypeFromSearch(result.subst(result.tree), targetType)
          }

          if (config.printSearchIds.contains(searchId)) {
            logger.info(
              s"""Showing tree of implicit search ${searchId} of type `${typeForStack}`:
                 |${showCode(result.tree)}
                 |""".stripMargin)
          }

          val cause = {
            if (result.isAmbiguousFailure) "ambiguous"
            else if (result.isDivergent) "divergent"
            else if (result.isFailure) "failure"
            else ""
          }

          if (!cause.isEmpty) {
            val forcedExpansions = searchesToMacros.getOrElse(searchId, Nil)
            logger.info(s"$cause search forced ${forcedExpansions.mkString(", ")}")
          }

          val thisStackName = s"${typeToString(typeForStack)}$macroName"
          stackedNames.update(searchId, thisStackName)
          children.foreach { childSearch =>
            val id = childSearch.searchId
            val childrenStackName = stackedNames.getOrElse(id, missing("stack name"))
            stackedNames.update(id, s"$thisStackName;$childrenStackName")
          }

          // Save the nanos for this implicit search
          val searchTimer = getSearchTimerFor(searchId)
          val stackedType = searchIdsToTargetTypes.getOrElse(searchId, missing("stack type"))
          statistics.stopTimer(searchTimer, searchStart)
          val (previousNanos, _) = stackedNanos.getOrElse(searchId, (0L, stackedType))
          stackedNanos.+=((searchId, ((searchTimer.nanos + previousNanos), stackedType)))
        }

        // 3. Reset the stack and stop timer if there is a dependant search
        val previousImplicits = implicitsStack.tail
        implicitsStack = previousImplicits.headOption match {
          case Some((prevSearch, prevImplicitTypeStart, _)) =>
            stopTimerFlamegraph(Some(prevSearch))
            statistics.stopTimer(timer, implicitTypeStart)
            val newPrevStart = statistics.startTimer(getSearchTimerFor(prevSearch.searchId))
            (prevSearch, prevImplicitTypeStart, newPrevStart) :: previousImplicits.tail
          case None =>
            stopTimerFlamegraph(None)
            statistics.stopTimer(timer, implicitTypeStart)
            previousImplicits
        }

      }
    }
  }

  private[ProfilingImpl] val searchesToMacros = perRunCaches.newMap[Int, List[MacroExpansion]]
  object ProfilingMacroPlugin extends global.analyzer.MacroPlugin {
    type Typer = analyzer.Typer
    private def guessTreeSize(tree: Tree): Int =
      1 + tree.children.map(guessTreeSize).sum

    type RepeatedKey = (String, String)
    //case class RepeatedValue(original: Tree, result: Tree, count: Int)
    //private final val EmptyRepeatedValue = RepeatedValue(EmptyTree, EmptyTree, 0)
    //private[ProfilingImpl] val repeatedTrees = perRunCaches.newMap[RepeatedKey, RepeatedValue]

    private[ProfilingImpl] val macroInfos = perRunCaches.newAnyRefMap[Position, MacroInfo]

    import scala.tools.nsc.Mode
    override def pluginsMacroExpand(t: Typer, expandee: Tree, md: Mode, pt: Type): Option[Tree] = {
      object expander extends analyzer.DefMacroExpander(t, expandee, md, pt) {
        private var alreadyTracking: Boolean = false

        /**
          * Overrides the default method that expands all macros.
          *
          * We perform this because we need our own timer and access to the first timer snapshot
          * in order to obtain the expansion time for every expanded tree.
          */
        override def apply(desugared: Tree): Tree = {
          val shouldTrack = statistics.enabled && !alreadyTracking
          val start = if (shouldTrack) {
            alreadyTracking = true
            statistics.startTimer(preciseMacroTimer)
          } else null
          try super.apply(desugared)
          finally if (shouldTrack) {
            alreadyTracking = false
            updateExpansionTime(desugared, start)
          } else ()
        }

        def updateExpansionTime(desugared: Tree, start: statistics.TimerSnapshot): Unit = {
          statistics.stopTimer(preciseMacroTimer, start)
          val (nanos0, _) = start
          val timeNanos = (preciseMacroTimer.nanos - nanos0)
          val callSitePos = desugared.pos
          // Those that are not present failed to expand
          macroInfos.get(callSitePos).foreach { found =>
            val updatedInfo = found.copy(expansionNanos = timeNanos)
            macroInfos(callSitePos) = updatedInfo
          }
        }

        def mapToSearch(exp: MacroExpansion): Unit = {
          implicitsStack.headOption match {
            case Some(i) =>
              val id = i._1.searchId
              val currentMacros = searchesToMacros.getOrElse(id, Nil)
              searchesToMacros.update(id, exp :: currentMacros)
            case None => ()
          }
        }

        override def onFailure(expanded: Tree) = {
          mapToSearch(MacroExpansion(expanded, NoType))
          statistics.incCounter(failedMacros)
          super.onFailure(expanded)
        }

        override def onDelayed(expanded: Tree) = {
          mapToSearch(MacroExpansion(expanded, NoType))
          statistics.incCounter(delayedMacros)
          super.onDelayed(expanded)
        }

        override def onSuccess(expanded0: Tree) = {
          val expanded = super.onSuccess(expanded0)
          val expandedType = concreteTypeFromSearch(expanded, pt)
          mapToSearch(MacroExpansion(expanded, expandedType))

          // Update macro counter per type returned
          val macroTypeCounter = macrosByType.getOrElse(expandedType, 0)
          macrosByType.update(expandedType, macroTypeCounter + 1)

          val callSitePos = expandee.pos
          /*          val printedExpandee = showRaw(expandee)
          val printedExpanded = showRaw(expanded)
          val key = (printedExpandee, printedExpanded)
          val currentValue = repeatedTrees.getOrElse(key, EmptyRepeatedValue)
          val newValue = RepeatedValue(expandee, expanded, currentValue.count + 1)
          repeatedTrees.put(key, newValue)*/
          val macroInfo = macroInfos.getOrElse(callSitePos, MacroInfo.Empty)
          val expandedMacros = macroInfo.expandedMacros + 1
          val treeSize = 0 //macroInfo.expandedNodes + guessTreeSize(expanded)

          // Use 0L for the timer because it will be filled in by the caller `apply`
          macroInfos.put(callSitePos, MacroInfo(expandedMacros, treeSize, 0L))
          expanded
        }
      }
      Some(expander(expandee))
    }
  }
}

trait ProfilingStats {
  val global: Global
  import global.statistics.{newTimer, newSubCounter, macroExpandCount, implicitSearchCount}
  macroExpandCount.children.clear()

  final val preciseMacroTimer = newTimer("precise time in macroExpand")
  final val failedMacros = newSubCounter("  of which failed macros", macroExpandCount)
  final val delayedMacros = newSubCounter("  of which delayed macros", macroExpandCount)

  final val implicitSearchesByMacrosCount = newSubCounter("  from macros", implicitSearchCount)

  import scala.reflect.internal.util.Position
  final val macrosByType = new scala.collection.mutable.HashMap[global.Type, Int]()
  final val implicitSearchesByType = global.perRunCaches.newMap[global.Type, Int]()
  final val implicitSearchesByPos = global.perRunCaches.newMap[Position, Int]()
}
