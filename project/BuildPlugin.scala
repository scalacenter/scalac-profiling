/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala.profiling.build

import sbt.{AutoPlugin, Def, Keys, PluginTrigger, Plugins}

object BuildPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = ch.epfl.scala.sbt.release.ReleaseEarlyPlugin
  val autoImport = BuildKeys

  override def globalSettings: Seq[Def.Setting[_]] =
    BuildImplementation.globalSettings
  override def buildSettings: Seq[Def.Setting[_]] =
    BuildImplementation.buildSettings
  override def projectSettings: Seq[Def.Setting[_]] =
    BuildImplementation.projectSettings
}

object BuildKeys {
  import sbt.{settingKey, taskKey, richFile, file, uri, toGroupID}
  import sbt.{RootProject, ProjectRef, Setting, Compile, BuildRef, Reference}
  final val enableStatistics =
    settingKey[Boolean]("Enable performance debugging if true.")
  final val optionsForSourceCompilerPlugin =
    taskKey[Seq[String]]("Generate scalac options for source compiler plugin")
  final val allDepsForCompilerPlugin =
    taskKey[Def.Classpath]("Return all dependencies for the source compiler plugin.")
  final val showScalaInstances = taskKey[Unit]("Show versions of all integration tests")
  final val useScalacFork =
    settingKey[Boolean]("Make every module use the Scala fork instead latest Scala 2.12.x.")

  // Refer to setting via reference because there is no dependency to the scalac build here.
  final val scalacVersionSuffix = sbt.SettingKey[String]("baseVersionSuffix")

  // Use absolute paths so that references work even though the `ThisBuild` changes
  final val AbsolutePath = file(".").getCanonicalFile.getAbsolutePath
  final val HomeBuild = BuildRef(RootProject(file(AbsolutePath)).build)

  // Source dependency is a submodule that we modify
  final val Scalac = RootProject(file(s"$AbsolutePath/scalac"))
  final val ScalacBuild = BuildRef(Scalac.build)
  final val ScalacCompiler = ProjectRef(Scalac.build, "compiler")
  final val ScalacLibrary = ProjectRef(Scalac.build, "library")
  final val ScalacReflect = ProjectRef(Scalac.build, "reflect")
  final val ScalacDist = ProjectRef(Scalac.build, "dist")
  final val AllScalacProjects = List(ScalacCompiler, ScalacLibrary, ScalacReflect)

  final val VscodeScala = RootProject(file(s"$AbsolutePath/vscode-scala"))
  final val VscodeImplementation = ProjectRef(VscodeScala.build, "ensime-lsp")

  // Source dependencies from git are cached by sbt
  val Circe = RootProject(
    uri("git://github.com/jvican/circe.git#74daecae981ff5d7521824fea5304f9cb52dbac9")
  )
  val Monocle = RootProject(
    uri("git://github.com/jvican/Monocle.git#5da7c1ac8ffd3942a843dca9cd1fbb281ff08412")
  )
  val Scalatest = RootProject(
    uri("git://github.com/jvican/scalatest.git#2bc97995612c467e4248a33b2ad0025c003a0fcb")
  )
  val BetterFiles = RootProject(
    uri("git://github.com/jvican/better-files.git#29270d200bdc5715be0fb6875b00718de2996641")
  )
  val Shapeless = RootProject(
    uri("git://github.com/jvican/shapeless.git#a42cd4c1c99e4a7be36e0239d3ee944a6355e321")
  )
  val Magnolia = RootProject(
    uri("git://github.com/jvican/magnolia.git#249eb311a78b2967dcdf388576bd5eaa7c55c8fa")
  )

  val CirceTests = ProjectRef(Circe.build, "tests")
  val MonocleExample = ProjectRef(Monocle.build, "example")
  val MonocleTests = ProjectRef(Monocle.build, "testJVM")
  val ScalatestCore = ProjectRef(Scalatest.build, "scalatest")
  val ScalatestTests = ProjectRef(Scalatest.build, "scalatest-test")
  val BetterFilesCore = ProjectRef(BetterFiles.build, "core")
  val ShapelessCore = ProjectRef(Shapeless.build, "coreJVM")
  val ShapelessExamples = ProjectRef(Shapeless.build, "examplesJVM")
  val MagnoliaTests = ProjectRef(Magnolia.build, "tests")

  val IntegrationProjectsAndReferences = List(
    CirceTests -> "CirceTests",
    MonocleExample -> "MonocleExample",
    MonocleTests -> "MonocleTests",
    ScalatestCore -> "ScalatestCore",
    ScalatestTests -> "ScalatestTests",
    BetterFilesCore -> "BetterFilesCore",
    ShapelessCore -> "ShapelessCore",
    ShapelessExamples -> "ShapelessExamples",
    MagnoliaTests -> "MagnoliaTests"
    // Enable the scalac compiler when it's not used as a fork
    // ScalacCompiler,
  )

  val AllIntegrationProjects = IntegrationProjectsAndReferences.map(_._1)

  // Assumes that the previous scala version is the last bincompat version
  final val ScalacVersion = Keys.version in BuildKeys.ScalacCompiler
  final val ScalacScalaVersion = Keys.scalaVersion in BuildKeys.ScalacCompiler

  final val testDependencies = List(
    "junit" % "junit" % "4.12" % "test",
    "com.novocode" % "junit-interface" % "0.11" % "test"
  )

  /** Write all the compile-time dependencies of the compiler plugin to a file,
    * in order to read it from the created Toolbox to run the neg tests. */
  lazy val generateToolboxClasspath = Def.task {
    val scalaBinVersion = (Keys.scalaBinaryVersion in Compile).value
    val targetDir = (Keys.target in Compile).value
    val compiledClassesDir = targetDir / s"scala-$scalaBinVersion/classes"
    val testClassesDir = targetDir / s"scala-$scalaBinVersion/test-classes"
    val libraryJar = Keys.scalaInstance.value.libraryJar.getAbsolutePath
    val deps = (Keys.libraryDependencies in Compile).value.mkString(":")
    val classpath = s"$compiledClassesDir:$testClassesDir:$libraryJar:$deps"
    val resourceDir = (Keys.resourceManaged in Compile).value
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
  final val hijacked = sbt.AttributeKey[Boolean]("The hijacked sexy option.")

  ////////////////////////////////////////////////////////////////////////////////

  def inProject(ref: Reference)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
    sbt.inScope(sbt.ThisScope.in(project = ref))(ss)

  def inProjectRefs(refs: Seq[Reference])(ss: Setting[_]*): Seq[Setting[_]] =
    refs.flatMap(inProject(_)(ss))

  private[build] def inScalacProjects(ss: Setting[_]*): Seq[Setting[_]] =
    inProjectRefs(AllScalacProjects)(ss: _*)

  def inCompileAndTest(ss: Setting[_]*): Seq[Setting[_]] =
    Seq(sbt.Compile, sbt.Test).flatMap(sbt.inConfig(_)(ss))

  object Keywords {
    val Circe = " circe"
    val Monocle = " monocle"
    val Integration = " integration"
    val Scalatest = " scalatest"
    val Scalac = " scalac"
    val BetterFiles = " better-files"
    val Shapeless = " shapeless"
    val Magnolia = " magnolia"
  }

  // Circe has to be always at the beginning
  private val AllKeywords = List(
    Keywords.Circe,
    Keywords.Monocle,
    Keywords.Integration,
    Keywords.Scalatest,
    Keywords.Scalac,
    Keywords.BetterFiles,
    Keywords.Shapeless,
    Keywords.Magnolia
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
  import sbt.{url, file, richFile, State, Logger}
  import sbt.{Developer, Resolver, ThisBuild, Watched, Compile, Test}

  // This should be added to upstream sbt.
  def GitHub(org: String, project: String): java.net.URL =
    url(s"https://github.com/$org/$project")
  def GitHubDev(handle: String, fullName: String, email: String) =
    Developer(handle, fullName, email, url(s"https://github.com/$handle"))

  import com.typesafe.sbt.SbtPgp.autoImport.PgpKeys
  import ch.epfl.scala.sbt.release.ReleaseEarlyPlugin.{autoImport => ReleaseEarlyKeys}

  final val PluginProject = sbt.LocalProject("plugin")
  private final val ThisRepo = GitHub("scalacenter", "scalac-profiling")
  final val publishSettings: Seq[Def.Setting[_]] = Seq(
    Keys.startYear := Some(2017),
    Keys.autoAPIMappings := true,
    Keys.publishMavenStyle := true,
    Keys.homepage := Some(ThisRepo),
    Keys.publishArtifact in Test := false,
    Keys.licenses := Seq("Apache-2.0" -> url("https://opensource.org/licenses/Apache-2.0")),
    Keys.developers := List(GitHubDev("jvican", "Jorge Vicente Cantero", "jorge@vican.me")),
    PgpKeys.pgpPublicRing := {
      if (sys.env.get("CI").isDefined) file("/drone/.gnupg/pubring.asc")
      else PgpKeys.pgpPublicRing.value
    },
    PgpKeys.pgpSecretRing := {
      if (sys.env.get("CI").isDefined) file("/drone/.gnupg/secring.asc")
      else PgpKeys.pgpSecretRing.value
    },
    ReleaseEarlyKeys.releaseEarlyWith := ReleaseEarlyKeys.SonatypePublisher
  )

  // Paranoid level: removes doc generation by all means
  final val scalacSettings: Seq[Def.Setting[_]] = BuildKeys.inScalacProjects(
    Keys.aggregate in Keys.doc := false,
    Keys.sources in Compile in Keys.doc := Seq.empty,
    Keys.scalacOptions in Compile in Keys.doc := Seq.empty,
    Keys.publishArtifact in Compile in Keys.packageDoc := false,
    // Use snapshot only for local development plz.
    // If placed in global settings, it's not applied. Sbt bug? Ordinary order init in sourcedeps bug.
    BuildKeys.scalacVersionSuffix in BuildKeys.Scalac := BuildDefaults.scalacVersionSuffix.value
  )

  object BuildDefaults {
    final val scalacVersionSuffix = Def.setting {
      val previousSuffix = (BuildKeys.scalacVersionSuffix in BuildKeys.Scalac).value
      if (!previousSuffix.contains("stats")) s"stats-${previousSuffix}" else previousSuffix
    }
    final val showScalaInstances: Def.Initialize[sbt.Task[Unit]] = Def.task {
      val logger = Keys.streams.value.log
      logger.info((Keys.name in Test in BuildKeys.CirceTests).value)
      logger.info((Keys.scalaInstance in Test in BuildKeys.CirceTests).value.toString)
      logger.info((Keys.name in Test in BuildKeys.MonocleTests).value)
      logger.info((Keys.scalaInstance in Test in BuildKeys.MonocleTests).value.toString)
      logger.info((Keys.name in Test in BuildKeys.MonocleExample).value)
      logger.info((Keys.scalaInstance in Test in BuildKeys.MonocleExample).value.toString)
      logger.info((Keys.name in Test in BuildKeys.ScalatestCore).value)
      logger.info((Keys.scalaInstance in Test in BuildKeys.ScalatestCore).value.toString)
      logger.info((Keys.name in BuildKeys.ScalacCompiler).value)
      logger.info((Keys.scalaInstance in BuildKeys.ScalacCompiler).value.toString)
      logger.info((Keys.name in Test in BuildKeys.BetterFilesCore).value)
      logger.info((Keys.scalaInstance in Test in BuildKeys.BetterFilesCore).value.toString)
      ()
    }

    import sbt.Command
    def fixPluginCross(commands: Seq[Command]): Seq[Command] = {
      val pruned = commands.filterNot(p => p == sbt.WorkingPluginCross.oldPluginSwitch)
      sbt.WorkingPluginCross.pluginSwitch +: pruned
    }

    val fixScalaVersionForSbtPlugin: Def.Initialize[String] = Def.setting {
      val orig = Keys.scalaVersion.value
      val is013 = (Keys.sbtVersion in Keys.pluginCrossBuild).value.startsWith("0.13")
      if (is013) "2.10.6" else orig
    }

    type Hook = Def.Initialize[State => State]

    private final val UnknownHash = "UNKNOWN"
    final val publishForkScalac: Hook = Def.setting { (state: State) =>
      import sbt.IO
      import com.typesafe.sbt.git.JGit
      // Only publish scalac if file doesn't exist
      val baseDirectory = (Keys.baseDirectory in ThisBuild).value
      val scalacHashFile = baseDirectory./(".scalac-hash")
      val repository = JGit(baseDirectory./("scalac"))
      // If sha cannot be fetched, always force publishing of fork.
      val currentHash = repository.headCommitSha.getOrElse(UnknownHash)
      if (!repository.hasUncommittedChanges &&
        scalacHashFile.exists() &&
        currentHash == IO.read(scalacHashFile)) {
        state
      } else {
        val logger = Keys.sLog.value
        val extracted = sbt.Project.extract(state)
        val buildData = extracted.structure.data
        val maybeVersion = BuildKeys.ScalacVersion.get(buildData).get
        val newState = publishCustomScalaFork(state, maybeVersion, logger)
        if (currentHash != UnknownHash)
          IO.write(scalacHashFile, currentHash)
        newState
      }
    }

    private[build] val MinimumScalaVersion = "2.12.4"
    def pickScalaVersion: Def.Initialize[String] = Def.settingDyn {
      if (!BuildKeys.useScalacFork.value) Def.setting(MinimumScalaVersion)
      // 2.12.3 has no statistics, so if scalaHome isn't used it will fail to compile
      else Def.setting("2.12.3")
    }

    def scalacProfilingScalacOptions: Def.Initialize[sbt.Task[Seq[String]]] = Def.task {
      val projectBuild = Keys.thisProjectRef.value.build
      val workingDir = Keys.buildStructure.value.units(projectBuild).localBase.getAbsolutePath
      val sourceRoot = s"-P:scalac-profiling:sourceroot:$workingDir"
      val pluginOpts = (BuildKeys.optionsForSourceCompilerPlugin in PluginProject).value
      sourceRoot +: pluginOpts
    }

    def setUpScalaHome: Def.Initialize[Option[sbt.File]] = Def.setting {
      val pathToHome = new java.io.File(s"${BuildKeys.Scalac.build.toURL().getFile()}build/pack")
      if (!pathToHome.exists()) {
        Keys.sLog.value.warn(s"Scala home $pathToHome didn't exist yet.")
      }
      Some(pathToHome)
    }

    def setUpUnmanagedJars: Def.Initialize[sbt.Task[Def.Classpath]] = Def.task {
      val previousJars = Keys.unmanagedJars.in(Compile).value
      val allPluginDeps = BuildKeys.allDepsForCompilerPlugin.in(PluginProject).value
      previousJars ++ allPluginDeps
    }

    object MethodRefs {
      private final val build = "_root_.ch.epfl.scala.profiling.build"
      final val scalacProfilingScalacOptionsRef: String =
        s"${build}.BuildImplementation.BuildDefaults.scalacProfilingScalacOptions"
      final val setUpScalaHomeRef: String =
        s"${build}.BuildImplementation.BuildDefaults.setUpScalaHome"
      final val setUpUnmanagedJarsRef: String =
        s"${build}.BuildImplementation.BuildDefaults.setUpUnmanagedJars"
    }

    def setUpSourceDependenciesCmd(refs: List[String]): Def.Initialize[String] = {
      Def.setting {
        val scalaVersion = BuildDefaults.pickScalaVersion.value
        def setScalaVersion(ref: String) =
          s"""${Keys.scalaVersion.key.label} in $ref := "$scalaVersion""""
        def setScalacOptions(ref: String) =
          s"""${Keys.scalacOptions.key.label} in $ref := ${MethodRefs.scalacProfilingScalacOptionsRef}.value"""
        def setScalaHome(ref: String) =
          s"""${Keys.scalaHome.key.label} in $ref := ${MethodRefs.setUpScalaHomeRef}.value"""
        def setUnmanagedJars(ref: String, config: String) =
          s"""${Keys.unmanagedJars.key.label} in $config in $ref := ${MethodRefs.setUpUnmanagedJarsRef}.value"""
        val msg = s"The build integrations are using a local Scalac home."
        val setLoadMessage = s"""${Keys.onLoadMessage.key.label} in sbt.Global := "$msg""""
        val allSettingsRedefinitions = refs.flatMap { ref =>
          val setsUnmanagedJars =
            List(setUnmanagedJars(ref, "Compile"), setUnmanagedJars(ref, "Test"))
          List(setScalaVersion(ref), setScalacOptions(ref), setScalaHome(ref)) ++ setsUnmanagedJars
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
      if (!BuildKeys.useScalacFork.value) Def.setting(hijackScalaVersions.value)
      else Def.setting(publishForkScalac.value andThen hijackScalaVersions.value)
    }
  }

  final val globalSettings: Seq[Def.Setting[_]] = Seq(
    Keys.testOptions in Test += sbt.Tests.Argument("-oD"),
    BuildKeys.useScalacFork := true,
    Keys.commands ~= BuildDefaults.fixPluginCross _,
    Keys.onLoadMessage := Header.intro,
    Keys.onLoad := (Keys.onLoad in sbt.Global).value andThen (BuildDefaults.customOnLoad.value)
  )

  final val commandAliases: Seq[Def.Setting[sbt.State => sbt.State]] = {
    val scalacRef = sbt.Reference.display(BuildKeys.ScalacBuild)
    val scalac = sbt.addCommandAlias("scalac", s"project ${scalacRef}")
    val homeRef = sbt.Reference.display(BuildKeys.HomeBuild)
    val home = sbt.addCommandAlias("home", s"project ${homeRef}")
    scalac ++ home
  }

  final val buildSettings: Seq[Def.Setting[_]] = Seq(
    Keys.organization := "ch.epfl.scala",
    Keys.resolvers += Resolver.jcenterRepo,
    Keys.updateOptions := Keys.updateOptions.value.withCachedResolution(true),
    Keys.scalaVersion := BuildKeys.ScalacScalaVersion.value,
    Keys.triggeredMessage := Watched.clearWhenTriggered,
    BuildKeys.enableStatistics := true,
    BuildKeys.showScalaInstances := BuildDefaults.showScalaInstances.value,
    Keys.publishArtifact in Compile in Keys.packageDoc := false
  ) ++ publishSettings ++ commandAliases

  final val projectSettings: Seq[Def.Setting[_]] = Seq(
    Keys.scalacOptions in Compile := reasonableCompileOptions,
    // Necessary because the scalac version has to be always SNAPSHOT to avoid caching issues
    // Scope here is wrong -- we put it here temporarily until this is fixed upstream
    ReleaseEarlyKeys.releaseEarlyBypassSnapshotCheck := true
  ) ++ scalacSettings

  final val reasonableCompileOptions = (
    "-deprecation" :: "-encoding" :: "UTF-8" :: "-feature" :: "-language:existentials" ::
      "-language:higherKinds" :: "-language:implicitConversions" :: "-unchecked" ::
      "-Yno-adapted-args" :: "-Ywarn-numeric-widen" :: "-Xfuture" :: "-Xlint" :: Nil
  )

  private final val mkPack = sbt.taskKey[java.io.File]("mkPack")
  // This is only used when we use the fork instead of upstream. As of 2.12.4, we use upstream.
  private def publishCustomScalaFork(state0: State, version: String, logger: Logger): State = {
    import sbt.{Project, Value, Inc, Incomplete}
    logger.warn(s"Publishing Scala version $version from the fork...")
    // Bah, reuse the same state for everything.
    Project.runTask(mkPack in BuildKeys.ScalacDist, state0) match {
      case None => sys.error(s"Key for publishing is not defined?")
      case Some((newState, Value(v))) => newState
      case Some((newState, Inc(inc))) =>
        sys.error(s"Got error when publishing the Scala fork: $inc")
    }
  }
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
