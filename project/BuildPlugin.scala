/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala.profiling.build

import sbt._

object BuildPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = plugins.JvmPlugin
  val autoImport = BuildKeys

  override def globalSettings: Seq[Def.Setting[_]] =
    BuildImplementation.globalSettings
  override def buildSettings: Seq[Def.Setting[_]] =
    BuildImplementation.buildSettings
  override def projectSettings: Seq[Def.Setting[_]] =
    BuildImplementation.projectSettings
}

object BuildKeys {
  final val enableStatistics =
    settingKey[Boolean]("Enable performance debugging if true.")
  final val optionsForSourceCompilerPlugin =
    taskKey[Seq[String]]("Generate scalac options for source compiler plugin")
  final val allDepsForCompilerPlugin =
    taskKey[Def.Classpath]("Return all dependencies for the source compiler plugin.")
  final val showScalaInstances = taskKey[Unit]("Show versions of all integration tests")

  // Refer to setting via reference because there is no dependency to the scalac build here.
  final val scalacVersionSuffix = sbt.SettingKey[String]("baseVersionSuffix")

  // Use absolute paths so that references work even though the `ThisBuild` changes
  final val AbsolutePath = file(".").getCanonicalFile.getAbsolutePath
  final val HomeBuild = BuildRef(RootProject(file(AbsolutePath)).build)

  // Source dependencies from git are cached by sbt
  val BetterFiles = RootProject(
    uri(
      "https://git@github.com/pathikrit/better-files.git#6f2e3f1328b1b18eddce973510db71bc6c14fadb"
    ) // v3.9.2
  )
  val Wartremover = RootProject(
    uri(
      "https://git@github.com/wartremover/wartremover.git#29bb7b69ad49eb87c19d9ba865298071c2795bb7"
    ) // v3.1.4
  )

  val BetterFilesCore = ProjectRef(BetterFiles.build, "core")
  val WartremoverCore = ProjectRef(Wartremover.build, "core")

  val IntegrationProjectsAndReferences = List[(ProjectRef, String)](
    BetterFilesCore -> "BetterFilesCore",
    WartremoverCore -> "WartremoverCore"
  )

  val AllIntegrationProjects = IntegrationProjectsAndReferences.map(_._1)

  // Assumes that the previous scala version is the last bincompat version
  // final val ScalacVersion = Keys.version in BuildKeys.ScalacCompiler
  // final val ScalacScalaVersion = Keys.scalaVersion in BuildKeys.ScalacCompiler

  /**
   * Write all the compile-time dependencies of the compiler plugin to a file,
   * in order to read it from the created Toolbox to run the neg tests.
   */
  lazy val generateToolboxClasspath = Def.task {
    val scalaBinVersion = (Compile / Keys.scalaBinaryVersion).value
    val targetDir = (Compile / Keys.target).value
    val compiledClassesDir = targetDir / s"scala-$scalaBinVersion/classes"
    val testClassesDir = targetDir / s"scala-$scalaBinVersion/test-classes"
    val libraryJar = Keys.scalaInstance.value.libraryJars.head.getAbsolutePath
    val deps = (Compile / Keys.libraryDependencies).value.mkString(":")
    val classpath = s"$compiledClassesDir:$testClassesDir:$libraryJar:$deps"
    val resourceDir = (Compile / Keys.resourceManaged).value
    val toolboxTestClasspath = resourceDir / "toolbox.classpath"
    sbt.IO.write(toolboxTestClasspath, classpath)
    List(toolboxTestClasspath.getAbsoluteFile)
  }

  /**
   * Sbt does not like overrides of setting values that happen in ThisBuild,
   * nor in other project settings like integrations'. No. Sbt is exigent and
   * always asks you to give your best.
   *
   * Why so much code for such a simple idea? Well, `Project.extract` does force
   * the execution and initialization of settings, so as `onLoad` is a setting
   * it causes a recursive call to itself, yay!
   *
   * So, in short, solution: use an attribute in the state to short-circuit the
   * recursive invocation.
   *
   * Notes to the future reader: the bug that prompted this solution is weird
   * I can indeed override lots of settings via project refs, but when it comes
   * to overriding a setting **in a project** (that has been generated via
   * sbt-cross-project), it does not work. On top of this, this wouldn't happen
   * if monocle defined the scala versions at the build level (it instead does it
   * at the project level, which is bad practice). So, finding a repro for this
   * is going to be fun.
   */
  final val hijacked = sbt.AttributeKey[Boolean]("the hijacked sexy option.")

  ////////////////////////////////////////////////////////////////////////////////

  def inProject(ref: Reference)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
    sbt.inScope(sbt.ThisScope.copy(project = Select(ref)))(ss)

  def inProjectRefs(refs: Seq[Reference])(ss: Setting[_]*): Seq[Setting[_]] =
    refs.flatMap(inProject(_)(ss))

  def inCompileAndTest(ss: Setting[_]*): Seq[Setting[_]] =
    Seq(sbt.Compile, sbt.Test).flatMap(sbt.inConfig(_)(ss))

  object Keywords {
    val Integration = " integration"
    val BetterFiles = " better-files"
    val Wartremover = " wartremover"
  }

  private val AllKeywords = List(
    Keywords.Integration,
    Keywords.BetterFiles,
    Keywords.Wartremover
  )

  import sbt.complete.Parser
  import sbt.complete.DefaultParsers._
  private val AllParsers =
    AllKeywords.tail.foldLeft(AllKeywords.head: Parser[String]) { case (p, s) => p.|(s) }
  private val keywordsParser = AllParsers.+.examples(AllKeywords: _*)
  val keywordsSetting: Def.Initialize[sbt.State => Parser[Seq[String]]] =
    Def.setting((state: sbt.State) => keywordsParser)
}

object BuildImplementation {

  // This should be added to upstream sbt.
  def GitHub(org: String, project: String): java.net.URL =
    url(s"https://github.com/$org/$project")
  def GitHubDev(handle: String, fullName: String, email: String) =
    Developer(handle, fullName, email, url(s"https://github.com/$handle"))

  // import ch.epfl.scala.sbt.release.ReleaseEarlyPlugin.{autoImport => ReleaseEarlyKeys}

  final val PluginProject = sbt.LocalProject("plugin")
  private final val ThisRepo = GitHub("scalacenter", "scalac-profiling")
  final val publishSettings: Seq[Def.Setting[_]] = Seq(
    Keys.startYear := Some(2017),
    Keys.autoAPIMappings := true,
    Keys.homepage := Some(ThisRepo),
    Test / Keys.publishArtifact := false,
    Keys.licenses := Seq("Apache-2.0" -> url("https://opensource.org/licenses/Apache-2.0")),
    Keys.developers := List(GitHubDev("jvican", "Jorge Vicente Cantero", "jorge@vican.me")),
    // ReleaseEarlyKeys.releaseEarlyWith := ReleaseEarlyKeys.SonatypePublisher,
    Keys.pomExtra := scala.xml.NodeSeq.Empty
  )

  object BuildDefaults {
    final val showScalaInstances: Def.Initialize[sbt.Task[Unit]] = Def.task {
      val logger = Keys.streams.value.log
      logger.info((BuildKeys.BetterFilesCore / Test / Keys.name).value)
      logger.info((BuildKeys.BetterFilesCore / Test / Keys.scalaInstance).value.toString)
      logger.info((BuildKeys.WartremoverCore / Compile / Keys.name).value)
      logger.info((BuildKeys.WartremoverCore / Compile / Keys.scalaInstance).value.toString)
      ()
    }

    import sbt.Command
    def fixPluginCross(commands: Seq[Command]): Seq[Command] = {
      val pruned = commands.filterNot(p => p == sbt.WorkingPluginCross.oldPluginSwitch)
      sbt.WorkingPluginCross.pluginSwitch +: pruned
    }

    type Hook = Def.Initialize[State => State]

    def scalacProfilingScalacOptions(ref: ProjectRef): Def.Initialize[sbt.Task[Seq[String]]] = {
      Def.task {
        val projectBuild = ref.build
        val workingDir = Keys.buildStructure.value.units(projectBuild).localBase.getAbsolutePath
        val sourceRoot = s"-P:scalac-profiling:sourceroot:$workingDir"
        val pluginOpts = (PluginProject / BuildKeys.optionsForSourceCompilerPlugin).value
        sourceRoot +: pluginOpts
      }
    }

    def setUpUnmanagedJars: Def.Initialize[sbt.Task[Def.Classpath]] = Def.task {
      val previousJars = (Compile / Keys.unmanagedJars).value
      val allPluginDeps = (PluginProject / BuildKeys.allDepsForCompilerPlugin).value
      previousJars ++ allPluginDeps
    }

    object MethodRefs {
      private final val build = "_root_.ch.epfl.scala.profiling.build"
      def scalacProfilingScalacOptionsRef(ref: String): String =
        s"${build}.BuildImplementation.BuildDefaults.scalacProfilingScalacOptions($ref)"
      final val setUpUnmanagedJarsRef: String =
        s"${build}.BuildImplementation.BuildDefaults.setUpUnmanagedJars"
    }

    def setUpSourceDependenciesCmd(refs: List[String]): Def.Initialize[String] = {
      Def.setting {
        val scalaV = Keys.scalaVersion.value
        def setScalaVersion(ref: String) =
          s"""$ref / ${Keys.scalaVersion.key.label} := "$scalaV""""
        def setScalacOptions(ref: String) =
          s"""$ref / ${Keys.scalacOptions.key.label} := ${MethodRefs
              .scalacProfilingScalacOptionsRef(ref)}.value""".stripMargin
        def setUnmanagedJars(ref: String, config: String) =
          s"""$ref / $config / ${Keys.unmanagedJars.key.label} := ${MethodRefs.setUpUnmanagedJarsRef}.value"""
        val msg = "The build integrations are set up."
        val setLoadMessage = s"""sbt.Global / ${Keys.onLoadMessage.key.label} := "$msg""""
        val allSettingsRedefinitions = refs.flatMap { ref =>
          val setsUnmanagedJars =
            List(setUnmanagedJars(ref, "Compile"), setUnmanagedJars(ref, "Test"))
          List(setScalaVersion(ref), setScalacOptions(ref)) ++ setsUnmanagedJars
        } ++ List(setLoadMessage)

        s"set List(${allSettingsRedefinitions.mkString(",")})"
      }
    }

    final val hijackScalaVersions: Hook = Def.settingDyn {
      val cmd = setUpSourceDependenciesCmd(BuildKeys.IntegrationProjectsAndReferences.map(_._2))
      Def.setting { (state: State) =>
        if (state.get(BuildKeys.hijacked).getOrElse(false)) state.remove(BuildKeys.hijacked)
        else cmd.value :: state.put(BuildKeys.hijacked, true)
      }
    }

    final val customOnLoad: Hook = Def.settingDyn {
      Def.setting(hijackScalaVersions.value)
    }
  }

  final val globalSettings: Seq[Def.Setting[_]] = Seq(
    Test / Keys.testOptions += sbt.Tests.Argument("-oD"),
    // BuildKeys.useScalacFork := false,
    Keys.commands ~= BuildDefaults.fixPluginCross _,
    Keys.onLoadMessage := Header.intro,
    Keys.onLoad := (Keys.onLoad in sbt.Global).value andThen (BuildDefaults.customOnLoad.value)
  )

  final val commandAliases: Seq[Def.Setting[sbt.State => sbt.State]] = {
    // val scalacRef = sbt.Reference.display(BuildKeys.ScalacBuild)
    // val scalac = sbt.addCommandAlias("scalac", s"project ${scalacRef}")
    val homeRef = sbt.Reference.display(BuildKeys.HomeBuild)
    val home = sbt.addCommandAlias("home", s"project ${homeRef}")
    home
  }

  final val buildSettings: Seq[Def.Setting[_]] = Seq(
    Keys.organization := "ch.epfl.scala",
    Keys.resolvers += Resolver.jcenterRepo,
    Keys.updateOptions := Keys.updateOptions.value.withCachedResolution(true),
    Keys.scalaVersion := "2.12.19",
    sbt.nio.Keys.watchTriggeredMessage := Watch.clearScreenOnTrigger,
    BuildKeys.enableStatistics := true,
    BuildKeys.showScalaInstances := BuildDefaults.showScalaInstances.value
  ) ++ publishSettings ++ commandAliases

  final val projectSettings: Seq[Def.Setting[_]] = Seq(
    Compile / Keys.scalacOptions := {
      val base = (
        "-deprecation" :: "-encoding" :: "UTF-8" :: "-feature" :: "-language:existentials" ::
          "-language:higherKinds" :: "-language:implicitConversions" :: "-unchecked" ::
          "-Ywarn-numeric-widen" :: "-Xlint" :: Nil
      )

      if (Keys.scalaVersion.value.startsWith("2.13")) base else base :+ "-Xfuture"
    }
    // Necessary because the scalac version has to be always SNAPSHOT to avoid caching issues
    // Scope here is wrong -- we put it here temporarily until this is fixed upstream
    // ReleaseEarlyKeys.releaseEarlyBypassSnapshotCheck := true
  )
}

object Header {
  val intro: String =
    """      _____            __         ______           __
      |     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____
      |     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/
      |    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /
      |   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/
      |
      |   ***********************************************************
      |   ***       Welcome to the build of scalac-profiling      ***
      |   *** An effort funded by the Scala Center Advisory Board ***
      |   ***********************************************************
    """.stripMargin
}
