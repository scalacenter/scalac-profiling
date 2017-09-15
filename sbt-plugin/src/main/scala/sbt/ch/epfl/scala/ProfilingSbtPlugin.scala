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

import sbt.{AutoPlugin, Def, Keys, PluginTrigger, TaskKey}

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
  val profilingWarmupCompilerProxy = taskKey[Unit]("Proxy")
}

object ProfilingPluginImplementation {
  import java.lang.{Long => BoxedLong}
  import sbt.{Compile, Test, ConsoleLogger, ThisBuild, Project, Compiler, Task, ScopedKey}
  private val logger = ConsoleLogger.apply()
  private val timingsForCompilers = new ConcurrentHashMap[Compiler.Compilers, BoxedLong]()
  private val timingsForKeys = new ConcurrentHashMap[ScopedKey[_], BoxedLong]()

  def inCompileAndTest(ss: Def.Setting[_]*): Seq[Def.Setting[_]] =
    Seq(Compile, Test).flatMap(sbt.inConfig(_)(ss))

  def timeCompileExecution(
      compileKey: ScopedKey[_],
      currentCompiler: Compiler.Compilers
  ): Def.Initialize[Task[Unit]] = Def.task {
    val logger = Keys.streams.value.log
    timingsForKeys.get(compileKey) match {
      case executionTime: java.lang.Long => timingsForCompilers.put(currentCompiler, executionTime)
      case null => logger.error(s"No timer found for task ${compileKey.scopedKey}")
    }
  }

  // Again, required to sidestep buggy dynamic sbt analysis
  private[this] val StableDef = Def

  val globalSettings: Seq[Def.Setting[_]] = List(
    Keys.executeProgress := (
        _ => new Keys.TaskProgress(new SbtTaskTimer(timingsForKeys, Keys.sLog.value))
    )
  )

  def getWarmupTime(currentCompiler: Compiler.Compilers): Long = {
    val time = timingsForCompilers.get(currentCompiler)
    TimeUnit.MILLISECONDS.toSeconds(if (time == null) 0 else time.toLong)
  }

  val buildSettings: Seq[Def.Setting[_]] = Nil
  val projectSettings: Seq[Def.Setting[_]] = List(
    BuildKeys.profilingWarmupCompiler in Compile := Def.taskDyn {
      val logger = Keys.streams.value.log
      val currentCompiler = Keys.compilers.value
      val warmupDuration = BuildKeys.profilingWarmupDuration.value.toLong
      val currentExecutionTime = getWarmupTime(currentCompiler)

      if (currentExecutionTime == 0)
        logger.info(s"Warming up compiler ($currentExecutionTime out of $warmupDuration)...")

      val scopedKey: sbt.ScopedKey[_] = Keys.resolvedScoped.value
      val compileKey = Keys.compile in scopedKey.scope
      val cleanKey = Keys.clean in scopedKey.scope
      val timeExecution = timeCompileExecution(compileKey, currentCompiler)
      def recurse = Def.taskDyn {
        val t = getWarmupTime(currentCompiler)
        loop(t)
      }

      def loop(currentDuration: Long): Def.Initialize[Task[Unit]] = {
        if (currentDuration < warmupDuration) Def.task {
          if (currentDuration != 0)
            logger.info(s"Warming up compiler ($currentDuration out of $warmupDuration)...")
          StableDef.sequential(compileKey, timeExecution, cleanKey, recurse).value
        } else StableDef.task(logger.success(s"The compiler is warmed up (${warmupDuration}s)."))
      }

      loop(getWarmupTime(currentCompiler))
    }.value,
    BuildKeys.profilingWarmupDuration := BuildDefaults.profilingWarmupDuration.value
  )

  object BuildDefaults {
    val profilingWarmupDuration: Def.Initialize[Int] = Def.setting(60)
  }
}
