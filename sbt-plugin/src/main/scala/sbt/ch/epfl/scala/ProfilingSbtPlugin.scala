/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package sbt.ch.epfl.scala

import java.util.concurrent.ConcurrentHashMap
import sbt.{AutoPlugin, Def, Keys, PluginTrigger, Select}

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
  import sbt.{settingKey, taskKey, AttributeKey, ProjectRef, ConfigKey}
  val profilingWarmupDuration = settingKey[Int]("The duration of the compiler warmup in seconds.")
  val profilingWarmupCompiler = taskKey[Unit]("Warms up the compiler for a given period of time.")
  private[sbt] val currentProject = AttributeKey[ProjectRef]("thisProjectRef")
  private[sbt] val currentConfigKey = AttributeKey[Option[ConfigKey]]("thisConfig")
}

object ProfilingPluginImplementation {
  import java.lang.{Long => BoxedLong}
  import sbt.{Compile, Test, Project, Task, ScopedKey, Tags}

  private val timingsForCompilers = new ConcurrentHashMap[ClassLoader, BoxedLong]()
  private val timingsForKeys = new ConcurrentHashMap[ScopedKey[_], BoxedLong]()
  private val WarmupTag = Tags.Tag("Warmup")

  val globalSettings: Seq[Def.Setting[_]] = List(
    Keys.commands += BuildDefaults.profilingWarmupCommand,
    BuildKeys.profilingWarmupDuration := BuildDefaults.profilingWarmupDuration.value,
    Keys.concurrentRestrictions += Tags.limit(WarmupTag, 1),
    Keys.progressReports := {
      val debug = (Keys.progressReports / Keys.logLevel).value == sbt.Level.Debug
      Seq(new Keys.TaskProgress(new SbtTaskTimer(timingsForKeys, debug)))
    },
    Keys.progressReports / Keys.logLevel := sbt.Level.Info
  )

  val buildSettings: Seq[Def.Setting[_]] = Nil
  val projectSettings: Seq[Def.Setting[_]] = List(
    Compile / BuildKeys.profilingWarmupCompiler :=
      BuildDefaults.profilingWarmupCompiler.tag(WarmupTag).value,
    Test / BuildKeys.profilingWarmupCompiler :=
      BuildDefaults.profilingWarmupCompiler.tag(WarmupTag).value
  )

  object BuildDefaults {
    import sbt.{Command, State}
    import sbt.complete.Parser

    val profilingWarmupCompiler: Def.Initialize[Task[Unit]] = Def.task {
      // Meh, we don't care about the resulting state, we'll throw it away.
      def runCommandAndRemaining(command: sbt.Exec): State => State = { st: State =>
        @annotation.tailrec
        def runCommand(command: sbt.Exec, state: State): State = {
          val nextState = Parser.parse(command.commandLine, state.combinedParser) match {
            case Right(cmd) => cmd()
            case Left(msg) => sys.error(s"Invalid programmatic input:\n$msg")
          }
          nextState.remainingCommands match {
            case Nil => nextState
            case head :: tail => runCommand(head, nextState.copy(remainingCommands = tail))
          }
        }
        runCommand(command, st.copy(remainingCommands = Nil))
          .copy(remainingCommands = st.remainingCommands)
      }

      val currentState = Keys.state.value
      val currentConfigKey = Keys.resolvedScoped.value.scope.config.toOption
      val tweakedState = currentState
        .put(BuildKeys.currentConfigKey, currentConfigKey)
        .put(BuildKeys.currentProject, Keys.thisProjectRef.value)

      // This is ugly, but the Command sbt API is constrained in this regard.
      val commandName = profilingWarmupCommand.asInstanceOf[sbt.SimpleCommand].name
      runCommandAndRemaining(sbt.Exec(commandName, None, None))(tweakedState)
      ()
    }

    val profilingWarmupDuration: Def.Initialize[Int] = Def.setting(60)

    private def getWarmupTime(compilerLoader: ClassLoader): Long = {
      val time = timingsForCompilers.get(compilerLoader)
      if (time == null) 0 else time.toLong
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
      def getStateAttribute[T](key: sbt.AttributeKey[T]): T =
        st0.get(key).getOrElse(sys.error(s"The caller did not pass the attribute ${key.label}"))

      // We do this because sbt does not correctly report `thisProjectRef` here, neither via
      // the extracted state nor the access to the build structure with `get(Keys.thisProjectRef)`.
      val currentProject = getStateAttribute(BuildKeys.currentProject)

      // We do this because `configuration` does not return the referencing configuration in scope
      // and `resolvedScoped` only reports the scope in which it was defined, not called from.
      val currentConfigKey = getStateAttribute(BuildKeys.currentConfigKey)

      val logger = st0.log
      val extracted = Project.extract(st0)
      val (st1, compilers) = extracted.runTask(extracted.currentRef / Keys.compilers, st0)
      val compilerLoader = compilers.scalac.scalaInstance.loader()

      val warmupDurationMs = extracted.get(BuildKeys.profilingWarmupDuration) * 1000
      var currentDurationMs = getWarmupTime(compilerLoader)

      val baseScope = Scope.ThisScope.copy(project = Select(currentProject))
      val scope = currentConfigKey.map(k => baseScope.copy(config = Select(k))).getOrElse(baseScope)
      val classDirectory = extracted.get(Keys.classDirectory.in(scope))
      val compileKeyRef = Keys.compile.in(scope)
      // We get the scope from `taskDefinitionKey` to be the same than the timer uses.
      val compileTaskKey = extracted.get(compileKeyRef).info.get(Def.taskDefinitionKey).get

      def deleteClassFiles(): Unit = {
        logger.info(s"Removing class files in ${classDirectory.getAbsolutePath}")
        IO.delete(Path.allSubpaths(classDirectory).toIterator.map(_._1).toIterable)
      }

      var lastState = st1
      if (currentDurationMs < warmupDurationMs)
        deleteClassFiles()

      while (currentDurationMs < warmupDurationMs) {
        logger.warn(s"Warming up compiler ($currentDurationMs out of $warmupDurationMs)ms...")
        val (afterCompile, _) = extracted.runTask(compileKeyRef, st1)
        lastState = afterCompile

        // Let's update the timing for the compile task with the knowledge of the task timer!
        val key = compileTaskKey.scopedKey
        currentDurationMs = timingsForKeys.get(key) match {
          case executionTime: java.lang.Long =>
            logger.debug(s"Registering $executionTime compile time for $key")
            timingsForCompilers.put(compilerLoader, executionTime)
            executionTime.toLong
          case null => sys.error("Abort: compile key was not measured. Report this error.")
        }

        // Clean class files so that incremental compilation doesn't kick in and then compile.
        deleteClassFiles()
      }

      logger.success(s"The compiler has been warmed up for ${warmupDurationMs}ms.")
      lastState
    }
  }
}
