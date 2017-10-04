/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package sbt.ch.epfl.scala

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import sbt.{AutoPlugin, Def, Keys, PluginTrigger}

object ProfilingSbtPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  val autoImport = BuildKeys

  override def globalSettings: Seq[Def.Setting[_]] =
    ProfilingPluginImplementation.globalSettings
  override def buildSettings: Seq[Def.Setting[_]] =
    ProfilingPluginImplementation.buildSettings
  override def projectSettings: Seq[Def.Setting[_]] =
    ProfilingPluginImplementation.projectSettings
}

object BuildKeys {
  import sbt.{settingKey, taskKey}
  val profilingWarmupDuration = settingKey[Int]("The duration of the compiler warmup in seconds.")
  val profilingWarmupCompiler = taskKey[Unit]("Warms up the compiler for a given period of time.")
}

object ProfilingPluginImplementation {
  import java.lang.{Long => BoxedLong}
  import sbt.{Compile, ConsoleLogger, Project, Compiler, Task, ScopedKey, Tags}
  import sbt.compiler.AnalyzingCompiler

  private val logger = ConsoleLogger.apply()
  private val timingsForCompilers = new ConcurrentHashMap[AnalyzingCompiler, BoxedLong]()
  private val timingsForKeys = new ConcurrentHashMap[ScopedKey[_], BoxedLong]()
  private val WarmupTag = Tags.Tag("Warmup")

  val globalSettings: Seq[Def.Setting[_]] = List(
    Keys.commands += BuildDefaults.profilingWarmupCommand,
    BuildKeys.profilingWarmupDuration := BuildDefaults.profilingWarmupDuration.value,
    Keys.concurrentRestrictions += Tags.limit(WarmupTag, 1),
    Keys.executeProgress :=
      (_ => new Keys.TaskProgress(new SbtTaskTimer(timingsForKeys, Keys.sLog.value)))
  )

  val buildSettings: Seq[Def.Setting[_]] = Nil
  val projectSettings: Seq[Def.Setting[_]] = List(
    BuildKeys.profilingWarmupCompiler :=
      BuildDefaults.profilingWarmupCompiler.tag(WarmupTag).value
  )

  object BuildDefaults {
    import sbt.{Command, State}
    import sbt.complete.Parser

    private val GlobalStringRepr = "◊global"
    private val ThisStringRepr = "◊this"
    val profilingWarmupCompiler: Def.Initialize[Task[Unit]] = Def.task {
      // Meh, we don't care about the resulting state, we'll throw it away.
      def runCommandAndRemaining(command: String): State => State = { st: State =>
        @annotation.tailrec
        def runCommand(command: String, state: State): State = {
          val nextState = Parser.parse(command, state.combinedParser) match {
            case Right(cmd) => cmd()
            case Left(msg) => sys.error(s"Invalid programmatic input:\n$msg")
          }
          nextState.remainingCommands.toList match {
            case Nil => nextState
            case head :: tail => runCommand(head, nextState.copy(remainingCommands = tail))
          }
        }
        runCommand(command, st.copy(remainingCommands = Nil))
          .copy(remainingCommands = st.remainingCommands)
      }

      // The fact that we cannot call `name` on a recently created Command is pretty ugly.
      val commandName = profilingWarmupCommand.asInstanceOf[sbt.SimpleCommand].name
      runCommandAndRemaining(commandName)(Keys.state.value)
      ()
    }

    val profilingWarmupDuration: Def.Initialize[Int] = Def.setting(60)

    private def getWarmupTime(currentCompiler: AnalyzingCompiler): Long = {
      val time = timingsForCompilers.get(currentCompiler)
      TimeUnit.MILLISECONDS.toSeconds(if (time == null) 0 else time.toLong)
    }

    import sbt.{Scope, IO, Path}

    /**
      * This command defines the warming up behaviour.
      *
      * After incessant attempts to get it working within tasks by only limiting ourselves
      * to the task API, this task has proven itself impossible because sbt does not allow
      * recursiveness at the task level. Any tried workaround (using task proxies et al) has
      * miserably failed.
      *
      * As a result, we have no other choice than delegating to the Command API and using
      * the state directly, implementing a traditional while loop that takes care of warming
      * the compiler up.
      *
      * This command is private and SHOULD NOT be invoked directly. Use `profilingWarmupCompiler`.
      */
    val profilingWarmupCommand: Command = Command.command("warmupCompileFor") { (st0: State) =>
      val logger = st0.log
      val extracted = Project.extract(st0)
      val (st1, compilers) = extracted.runTask(Keys.compilers in extracted.currentRef, st0)
      val compilerInstance = compilers.scalac

      val warmupDurationMs = extracted.get(BuildKeys.profilingWarmupDuration) * 1000
      var currentDurationMs = getWarmupTime(compilerInstance)

      var lastState = st1
      while (currentDurationMs < warmupDurationMs) {
        logger.warn(s"Warming up compiler ($currentDurationMs out of $warmupDurationMs)ms...")
        // Compile and clean class files so that incremental compilation doesn't kick in.
        val compileScope = Scope.GlobalScope.in(Compile).in(extracted.currentRef)
        val compileKeyRef = Keys.compile.in(compileScope)
        val compileTaskKey = extracted.get(compileKeyRef).info.get(Def.taskDefinitionKey).get
        val (afterCompile, _) = extracted.runTask(compileKeyRef, lastState)
        val classDirectory = extracted.get(Keys.classDirectory.in(compileScope))
        IO.delete(Path.allSubpaths(classDirectory).toIterator.map(_._1).toIterable)
        lastState = afterCompile

        // I know... but sbt needs it. Otherwise SOE.
        System.gc()

        // Let's update the timing for the compile task with the knowledge of the task timer!
        currentDurationMs = timingsForKeys.get(compileTaskKey.scopedKey) match {
          case executionTime: java.lang.Long =>
            logger.debug(s"Registering warmup compile time for ${compileTaskKey.scopedKey}")
            timingsForCompilers.put(compilerInstance, executionTime)
            executionTime
          case null => sys.error("Abort: compile key was not measured. Report this error.")
        }
      }

      logger.success(s"The compiler has been warmed up for ${warmupDurationMs}ms.")
      lastState
    }
  }
}
