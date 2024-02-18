/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala.profilers

import ch.epfl.scala.PluginConfig
import ch.epfl.scala.profiledb.utils.AbsolutePath
import ch.epfl.scala.profilers.tools.{Logger, QuantitiesHijacker, SettingsOps}

import scala.concurrent.duration._
import scala.tools.nsc.Global
import scala.reflect.internal.util.SourceFile

import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.concurrent.TimeUnit

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

  /**
   * Represents the profiling information about expanded macros.
   *
   * Note that we could derive the value of expanded macros from the
   * number of instances of [[MacroInfo]] if it were not by the fact
   * that a macro can expand in the same position more than once. We
   * want to be able to report/analyse such cases on their own, so
   * we keep it as a paramater of this entity.
   */
  case class MacroInfo(expandedMacros: Int, expandedNodes: Int, expansionTime: FiniteDuration) {
    def +(other: MacroInfo): MacroInfo = {
      val totalExpanded = expandedMacros + other.expandedMacros
      val totalNodes = expandedNodes + other.expandedNodes
      val totalTime = expansionTime + other.expansionTime
      MacroInfo(totalExpanded, totalNodes, totalTime)
    }
  }

  object MacroInfo {
    final val Empty = MacroInfo(0, 0, 0.millis)
    implicit val macroInfoOrdering: Ordering[MacroInfo] = Ordering.by(_.expansionTime)
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

  def groupPerFile[V](
      kvs: Map[Position, V]
  )(empty: V, aggregate: (V, V) => V): Map[SourceFile, V] = {
    kvs.groupBy(_._1.source).map {
      case (sf, posInfos: Map[Position, V]) => sf -> posInfos.valuesIterator.fold(empty)(aggregate)
    }
  }

  lazy val macroProfiler: MacroProfiler = {
    import ProfilingMacroPlugin.macroInfos // , repeatedTrees}
    val perCallSite = macroInfos.toMap
    val perFile = groupPerFile(perCallSite)(MacroInfo.Empty, _ + _)
      .map {
        case (sf, mi) =>
          sf -> mi.copy(expansionTime =
            FiniteDuration(mi.expansionTime.toMillis, TimeUnit.MILLISECONDS)
          )
      }
    val inTotal = MacroInfo.aggregate(perFile.valuesIterator)

    /*    val repeated = repeatedTrees.toMap.valuesIterator
      .filter(_.count > 1)
      .map(v => v.original -> v.count)
      .toMap*/

    val callSiteMicros = perCallSite.map {
      case (k, v) =>
        k -> v.copy(expansionTime = FiniteDuration(v.expansionTime.toMicros, TimeUnit.MICROSECONDS))
    }

    MacroProfiler(callSiteMicros, perFile, inTotal, Map.empty)
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
    val perCallSite = implicitSearchesByPos.map {
      case (pos, i) => pos -> ImplicitInfo.apply(i)
    }.toMap
    val perFile = groupPerFile[ImplicitInfo](perCallSite)(ImplicitInfo.Empty, _ + _)
    val perType = implicitSearchesByType.map {
      case (pos, i) => pos -> ImplicitInfo.apply(i)
    }.toMap
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

  def generateGraphData(
      outputDir: AbsolutePath,
      globalDirMaybe: Option[AbsolutePath]
  ): List[AbsolutePath] = {
    Files.createDirectories(outputDir.underlying)

    val randomId = java.lang.Long.toString(System.currentTimeMillis())

    /*val dotFile = outputDir.resolve(s"$graphName.dot")
    ProfilingAnalyzerPlugin.dottify(graphName, dotFile.underlying)*/

    val implicitFlamegraphFiles = {
      val mkImplicitGraphName: String => String =
        postfix => s"implicit-searches-$postfix.flamegraph"
      val compileUnitFlamegraphFile = outputDir.resolve(mkImplicitGraphName(randomId))

      globalDirMaybe match {
        case Some(globalDir) =>
          Files.createDirectories(globalDir.underlying)

          val globalFile =
            globalDir
              .resolve(mkImplicitGraphName("global"))

          List(compileUnitFlamegraphFile, globalFile)

        case None =>
          List(compileUnitFlamegraphFile)
      }
    }

    val macroFlamegraphFiles =
      if (config.generateMacroFlamegraph) {
        val macroGraphName = s"macros-$randomId"
        val file = outputDir.resolve(s"$macroGraphName.flamegraph")
        List(file)
      } else Nil

    ProfilingAnalyzerPlugin.foldImplicitStacks(implicitFlamegraphFiles)
    ProfilingMacroPlugin.foldMacroStacks(macroFlamegraphFiles)

    implicitFlamegraphFiles ::: macroFlamegraphFiles
  }

  private val registeredQuantities = QuantitiesHijacker.getRegisteredQuantities(global)
  def registerTyperTimerFor(prefix: String): statistics.Timer = {
    val typerTimer = statistics.newTimer(prefix, "typer")
    registeredQuantities.remove(s"/$prefix")
    typerTimer
  }

  private def typeToString(`type`: Type): String =
    global.exitingTyper(`type`.toLongString).trim

  // Moving this here so that it's accessible to the macro plugin
  private type Entry =
    (global.analyzer.ImplicitSearch, statistics.TimerSnapshot, statistics.TimerSnapshot)
  private var implicitsStack: List[Entry] = Nil

  private object ProfilingAnalyzerPlugin extends global.analyzer.AnalyzerPlugin {
    import scala.collection.mutable
    private val implicitsTimers = perRunCaches.newAnyRefMap[Type, statistics.Timer]()
    private val searchIdsToTargetTypes = perRunCaches.newMap[Int, Type]()
    private val stackedNanos = perRunCaches.newMap[Int, (Long, Type)]()
    private val stackedNames = perRunCaches.newMap[Int, List[String]]()
    private val searchIdsToTimers = perRunCaches.newMap[Int, statistics.Timer]()
    private val implicitsDependants = new mutable.AnyRefMap[Type, mutable.HashSet[Type]]()
    private val searchIdChildren = perRunCaches.newMap[Int, List[analyzer.ImplicitSearch]]()

    def foldImplicitStacks(outputPaths: Seq[AbsolutePath]): Unit =
      if (outputPaths.nonEmpty) {
        // This part is memory intensive and hence the use of java collections
        val stacksJavaList = new java.util.ArrayList[String]()
        stackedNanos.foreach {
          case (id, (nanos, _)) =>
            val names =
              stackedNames.getOrElse(
                id,
                sys.error(s"Stack name for search id ${id} doesn't exist!")
              )
            val stackName = names.mkString(";")
            // val count = implicitSearchesByType.getOrElse(tpe, sys.error(s"No counter for ${tpe}"))
            stacksJavaList.add(s"$stackName ${nanos / 1000}")
        }
        java.util.Collections.sort(stacksJavaList)

        outputPaths.foreach(path =>
          Files.write(
            path.underlying,
            stacksJavaList,
            StandardOpenOption.APPEND,
            StandardOpenOption.CREATE
          )
        )
      } else ()

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
      if (SettingsOps.areStatisticsEnabled(global)) {
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
        val searchTimer = registerTyperTimerFor(searchPrefix)
        searchIdsToTimers.+=(searchId -> searchTimer)

        // Start the timer as soon as possible
        val implicitTypeStart = statistics.startTimer(perTypeTimer)
        val searchStart = statistics.startTimer(searchTimer)

        // Update all timers and counters
        val typeCounter = implicitSearchesByType.getOrElse(targetType, 0)
        implicitSearchesByType.update(targetType, typeCounter + 1)
        val posCounter = implicitSearchesByPos.getOrElse(targetPos, 0)
        implicitSearchesByPos.update(targetPos, posCounter + 1)

        if (config.showProfiles) {
          val sourceFiles =
            implicitSearchesSourceFilesByType.getOrElseUpdate(targetType, mutable.HashSet.empty)
          if (!sourceFiles.contains(targetPos.source)) {
            sourceFiles.add(targetPos.source)
          }
        }

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
      if (SettingsOps.areStatisticsEnabled(global)) {
        // 1. Get timer of the running search
        val (search, implicitTypeStart, searchStart) = implicitsStack.head
        val targetType = search.pt
        val timer = getImplicitTimerFor(targetType)

        // 2. Register the timing diff for every stacked name.
        def stopTimerFlamegraph(prev: Option[analyzer.ImplicitSearch]): Unit = {
          val searchId = search.searchId
          def missing(name: String): Nothing =
            sys.error(s"Missing $name for $searchId ($targetType).")

          val forcedExpansions =
            ProfilingMacroPlugin.searchIdsToMacroStates.getOrElse(searchId, Nil)
          val expandedStr = s"(expanded macros ${forcedExpansions.size})"

          // Detect macro name if the type we get comes from a macro to add it to the stack
          val suffix = {
            val errorTag = if (result.isFailure) " _[j]" else ""
            result.tree.attachments.get[analyzer.MacroExpansionAttachment] match {
              case Some(analyzer.MacroExpansionAttachment(expandee: Tree, _)) =>
                val expandeeSymbol = treeInfo.dissectApplied(expandee).core.symbol
                analyzer.loadMacroImplBinding(expandeeSymbol) match {
                  case Some(a) =>
                    val l = if (errorTag.isEmpty) " _[i]" else errorTag
                    s" (id ${searchId}) $expandedStr (tree from `${a.className}.${a.methName}`)$l"
                  case None => s" $expandedStr $errorTag"
                }
              case None => s" $expandedStr $errorTag"
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

          if (
            config.printSearchIds.contains(
              searchId
            ) || (result.isFailure && config.printFailedMacroImplicits)
          ) {
            logger.info(
              s"""implicit search ${searchId}:
                 |  -> valid ${result.isSuccess}
                 |  -> type `${typeForStack}`
                 |  -> ${search.undet_s}
                 |  -> ${search.ctx_s}
                 |  -> tree:
                 |${showCode(result.tree)}
                 |  -> forced expansions:
                 |${forcedExpansions.mkString("  ", "  \n", "\n")}
                 |""".stripMargin
            )
          }

          val thisStackName = s"${typeToString(typeForStack)}$suffix"
          stackedNames.update(searchId, List(thisStackName))
          children.foreach { childSearch =>
            val id = childSearch.searchId
            val childrenStackName = stackedNames.getOrElse(id, missing("stack name"))
            stackedNames.update(id, thisStackName :: childrenStackName)
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

  sealed trait MacroState {
    def pt: Type
    def tree: Tree
  }

  case class DelayedMacro(pt: Type, tree: Tree) extends MacroState
  case class SkippedMacro(pt: Type, tree: Tree) extends MacroState
  case class SuppressedMacro(pt: Type, tree: Tree) extends MacroState
  case class FallbackMacro(pt: Type, tree: Tree) extends MacroState
  case class FailedMacro(pt: Type, tree: Tree) extends MacroState
  case class SucceededMacro(pt: Type, tree: Tree) extends MacroState

  case class MacroEntry(
      id: Int,
      originalPt: Type,
      start: statistics.TimerSnapshot,
      state: Option[MacroState]
  )

  private var macrosStack: List[MacroEntry] = Nil
  private var macroCounter: Int = 0

  object ProfilingMacroPlugin extends global.analyzer.MacroPlugin {
    type Typer = analyzer.Typer
    private def guessTreeSize(tree: Tree): Int =
      1 + tree.children.map(guessTreeSize).sum

    type RepeatedKey = (String, String)
    // case class RepeatedValue(original: Tree, result: Tree, count: Int)
    // private final val EmptyRepeatedValue = RepeatedValue(EmptyTree, EmptyTree, 0)
    // private[ProfilingImpl] val repeatedTrees = perRunCaches.newMap[RepeatedKey, RepeatedValue]

    val macroInfos = perRunCaches.newAnyRefMap[Position, MacroInfo]()
    val searchIdsToMacroStates = perRunCaches.newMap[Int, List[MacroState]]()
    private val macroIdsToTimers = perRunCaches.newMap[Int, statistics.Timer]()
    private val macroChildren = perRunCaches.newMap[Int, List[MacroEntry]]()
    private val stackedNanos = perRunCaches.newMap[Int, Long]()
    private val stackedNames = perRunCaches.newMap[Int, List[String]]()

    def foldMacroStacks(outputPaths: Seq[AbsolutePath]): Unit =
      if (outputPaths.nonEmpty) {
        // This part is memory intensive and hence the use of java collections
        val stacksJavaList = new java.util.ArrayList[String]()
        stackedNanos.foreach {
          case (id, nanos) =>
            val names =
              stackedNames.getOrElse(id, sys.error(s"Stack name for macro id ${id} doesn't exist!"))
            val stackName = names.mkString(";")
            stacksJavaList.add(s"$stackName ${nanos / 1000}")
        }
        java.util.Collections.sort(stacksJavaList)

        outputPaths.foreach(path =>
          Files.write(
            path.underlying,
            stacksJavaList,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE
          )
        )
      } else ()

    import scala.tools.nsc.Mode
    override def pluginsMacroExpand(t: Typer, expandee: Tree, md: Mode, pt: Type): Option[Tree] = {
      val macroId = macroCounter
      macroCounter = macroCounter + 1

      object expander extends analyzer.DefMacroExpander(t, expandee, md, pt) {

        /** The default method that expands all macros. */
        override def apply(desugared: Tree): Tree = {
          val prevData = macrosStack.headOption.map { prev =>
            macroIdsToTimers.getOrElse(
              prev.id,
              sys.error(s"fatal error: missing timer for ${prev.id}")
            ) -> prev
          }

          // Let's first stop the previous timer to have consistent times for the flamegraph
          prevData.foreach {
            case (prevTimer, prev) => statistics.stopTimer(prevTimer, prev.start)
          }

          // Let's create our own timer
          val searchPrefix = s"  macro ${macroId}"
          val macroTimer = registerTyperTimerFor(searchPrefix)
          macroIdsToTimers += ((macroId, macroTimer))
          val start = statistics.startTimer(macroTimer)

          val entry = MacroEntry(macroId, pt, start, None)

          if (config.generateMacroFlamegraph) {
            // We add ourselves to the child list of our parent macro
            prevData.foreach {
              case (_, entry) =>
                val prevId = entry.id
                val prevChilds = macroChildren.getOrElse(prevId, Nil)
                macroChildren.update(prevId, entry :: prevChilds)
            }
          }

          macrosStack = entry :: macrosStack
          try super.apply(desugared)
          finally {
            val children = macroChildren.getOrElse(macroId, Nil)
            if (config.generateMacroFlamegraph) {
              // Complete stack names of triggered implicit searches
              prevData.foreach {
                case (_, p) =>
                  val prevChildren = macroChildren.getOrElse(p.id, Nil)
                  macroChildren.update(p.id, children ::: prevChildren)
              }
            }

            // We need to fetch the entry from the stack as it can be modified
            val parents = macrosStack.tail
            macrosStack.headOption match {
              case Some(head) =>
                if (config.generateMacroFlamegraph) {
                  val thisStackName = head.state match {
                    case Some(FailedMacro(pt, _)) => s"${typeToString(pt)} [failed]"
                    case Some(DelayedMacro(pt, _)) => s"${typeToString(pt)} [delayed]"
                    case Some(SucceededMacro(pt, _)) => s"${typeToString(pt)}"
                    case Some(SuppressedMacro(pt, _)) => s"${typeToString(pt)} [suppressed]"
                    case Some(SkippedMacro(pt, _)) => s"${typeToString(pt)} [skipped]"
                    case Some(FallbackMacro(pt, _)) => s"${typeToString(pt)} [fallback]"
                    case None => sys.error("Fatal error: macro has no state!")
                  }

                  stackedNames.update(macroId, thisStackName :: Nil)
                  children.foreach { childSearch =>
                    val id = childSearch.id
                    val childrenStackName = stackedNames.getOrElse(id, sys.error("no stack name"))
                    stackedNames.update(id, thisStackName :: childrenStackName)
                  }
                }

                statistics.stopTimer(macroTimer, head.start)
                val previousNanos = stackedNanos.getOrElse(macroId, 0L)
                val nanos = macroTimer.nanos + previousNanos

                stackedNanos.+=((macroId, nanos))

                val callSitePos = desugared.pos
                // Those that are not present failed to expand
                macroInfos.get(callSitePos) match {
                  case Some(found) =>
                    macroInfos.update(
                      callSitePos,
                      found.copy(expansionTime = FiniteDuration(nanos, TimeUnit.NANOSECONDS))
                    )
                  case None =>
                    macroInfos.update(
                      callSitePos,
                      MacroInfo.Empty.copy(expansionTime =
                        FiniteDuration(nanos, TimeUnit.NANOSECONDS)
                      )
                    )
                }

                prevData match {
                  case Some((prevTimer, prev)) =>
                    // Let's restart the timer of the previous macro expansion
                    val newStart = statistics.startTimer(prevTimer)
                    // prev is the head of `parents`, so let's replace it on stack with the new start
                    macrosStack = prev.copy(start = newStart) :: parents.tail
                  case None => macrosStack = parents
                }
              case None => sys.error(s"fatal error: expected macro entry for macro id $macroId")
            }
          }
        }

        def mapToCurrentImplicitSearch(exp: MacroState): Unit = {
          implicitsStack.headOption match {
            case Some(i) =>
              val id = i._1.searchId
              val currentMacros = searchIdsToMacroStates.getOrElse(id, Nil)
              searchIdsToMacroStates.update(id, exp :: currentMacros)
            case None => ()
          }
        }

        def updateStack(state: MacroState): Unit = {
          macrosStack.headOption match {
            case Some(entry) =>
              macrosStack = entry.copy(state = Some(state)) :: macrosStack.tail
            case None => sys.error("fatal error: stack cannot be empty while updating!")
          }
        }

        override def onFailure(expanded: Tree) = {
          val state = FailedMacro(pt, expanded)
          mapToCurrentImplicitSearch(state)
          statistics.incCounter(failedMacros)
          updateStack(state)
          super.onFailure(expanded)
        }

        override def onSkipped(expanded: Tree) = {
          val state = SkippedMacro(pt, expanded)
          mapToCurrentImplicitSearch(state)
          statistics.incCounter(skippedMacros)
          updateStack(state)
          super.onDelayed(expanded)
        }

        override def onFallback(expanded: Tree) = {
          val state = FallbackMacro(pt, expanded)
          mapToCurrentImplicitSearch(state)
          statistics.incCounter(fallbackMacros)
          updateStack(state)
          super.onFallback(expanded)
        }

        override def onSuppressed(expanded: Tree) = {
          val state = SuppressedMacro(pt, expanded)
          mapToCurrentImplicitSearch(state)
          statistics.incCounter(suppressedMacros)
          updateStack(state)
          super.onSuppressed(expanded)
        }

        override def onDelayed(expanded: Tree) = {
          val state = DelayedMacro(pt, expanded)
          mapToCurrentImplicitSearch(state)
          statistics.incCounter(delayedMacros)
          updateStack(state)
          super.onDelayed(expanded)
        }

        override def onSuccess(expanded0: Tree) = {
          val expanded = super.onSuccess(expanded0)
          val expandedType = concreteTypeFromSearch(expanded, pt)
          val state = SucceededMacro(expandedType, expanded)
          mapToCurrentImplicitSearch(state)
          updateStack(state)

          // Update macro counter per type returned
          val macroTypeCounter = macrosByType.getOrElse(expandedType, 0)
          macrosByType.update(expandedType, macroTypeCounter + 1)

          val callSitePos = this.expandee.pos
          /*          val printedExpandee = showRaw(expandee)
          val printedExpanded = showRaw(expanded)
          val key = (printedExpandee, printedExpanded)
          val currentValue = repeatedTrees.getOrElse(key, EmptyRepeatedValue)
          val newValue = RepeatedValue(expandee, expanded, currentValue.count + 1)
          repeatedTrees.put(key, newValue)*/
          val macroInfo = macroInfos.getOrElse(callSitePos, MacroInfo.Empty)
          val expandedMacros = macroInfo.expandedMacros + 1
          val treeSize = 0 // macroInfo.expandedNodes + guessTreeSize(expanded)

          // Use 0L for the timer because it will be filled in by the caller `apply`
          macroInfos.put(callSitePos, MacroInfo(expandedMacros, treeSize, macroInfo.expansionTime))
          expanded
        }
      }
      Some(expander(expandee))
    }
  }
}

trait ProfilingStats {
  val global: Global
  import global.statistics.{newSubCounter, macroExpandCount, implicitSearchCount}
  macroExpandCount.children.clear()

  final val failedMacros = newSubCounter("  of which failed macros", macroExpandCount)
  final val delayedMacros = newSubCounter("  of which delayed macros", macroExpandCount)
  final val suppressedMacros = newSubCounter("  of which suppressed macros", macroExpandCount)
  final val fallbackMacros = newSubCounter("  of which fallback macros", macroExpandCount)
  final val skippedMacros = newSubCounter("  of which skipped macros", macroExpandCount)
  final val implicitSearchesByMacrosCount = newSubCounter("  from macros", implicitSearchCount)

  import scala.reflect.internal.util.Position
  import scala.collection.mutable

  final val macrosByType = new mutable.HashMap[global.Type, Int]()
  final val implicitSearchesByType = global.perRunCaches.newMap[global.Type, Int]()
  final val implicitSearchesByPos = global.perRunCaches.newMap[Position, Int]()
  final val implicitSearchesSourceFilesByType =
    global.perRunCaches.newMap[global.Type, mutable.HashSet[SourceFile]]()
}
