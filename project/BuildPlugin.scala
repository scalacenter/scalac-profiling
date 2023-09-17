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
  // override def requires: Plugins = ch.epfl.scala.sbt.release.ReleaseEarlyPlugin
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

  // final val VscodeScala = RootProject(file(s"$AbsolutePath/vscode-scala"))
  // final val VscodeImplementation = ProjectRef(VscodeScala.build, "ensime-lsp")

  // Source dependencies from git are cached by sbt
  val Circe = RootProject(
    uri("ssh://git@github.com/circe/circe.git#bbcbd53637b601953dfbdb4fd6fb55944c4e476e")
  )
  val Monocle = RootProject(
    uri("ssh://git@github.com/optics-dev/Monocle.git#8577ca6f818e7728bfd695d4739865bd73b0db0c") // 3.1.0, scala 2.13.8, kind-projector 0.13.2
  )
  val Scalatest = RootProject(
    uri("ssh://git@github.com/scalatest/scalatest.git#2840dca367cb385a1d01ccdd0821f83badb07012") // 3.2.11
  )
  val BetterFiles = RootProject(
    uri("ssh://git@github.com/pathikrit/better-files.git#81a3da05c58b9ab0cabe34235c6d7d88bcd16dca")
  )
  // val Shapeless = RootProject(
  //   uri("ssh://git@github.com/milessabin/shapeless.git#0a08460573883cef8ea2d44bc1688a09aa83d7f1") // 2.13.8
  // )

  val CirceTests = ProjectRef(Circe.build, "testsJVM")
  val MonocleExample = ProjectRef(Monocle.build, "example")
  val MonocleTests = ProjectRef(Monocle.build, "testJVM")
  val ScalatestCore = ProjectRef(Scalatest.build, "scalatest")
  val ScalatestTests = ProjectRef(Scalatest.build, "scalatest-test")
  val BetterFilesCore = ProjectRef(BetterFiles.build, "core")
  // val ShapelessCore = ProjectRef(Shapeless.build, "coreJVM")
  // val ShapelessExamples = ProjectRef(Shapeless.build, "examplesJVM")
  // val MagnoliaTests = ProjectRef(Magnolia.build, "tests")

  val IntegrationProjectsAndReferences = List[(ProjectRef, String)](
    CirceTests -> "CirceTests",
    MonocleExample -> "MonocleExample",
    MonocleTests -> "MonocleTests",
    ScalatestCore -> "ScalatestCore",
    ScalatestTests -> "ScalatestTests",
    BetterFilesCore -> "BetterFilesCore",
    // ShapelessCore -> "ShapelessCore",
    // ShapelessExamples -> "ShapelessExamples"
    // MagnoliaTests -> "MagnoliaTests"
    // Enable the scalac compiler when it's not used as a fork
    // ScalacCompiler,
  )

  val AllIntegrationProjects = IntegrationProjectsAndReferences.map(_._1)

  // Assumes that the previous scala version is the last bincompat version
  // final val ScalacVersion = Keys.version in BuildKeys.ScalacCompiler
  // final val ScalacScalaVersion = Keys.scalaVersion in BuildKeys.ScalacCompiler

  /** Write all the compile-time dependencies of the compiler plugin to a file,
    * in order to read it from the created Toolbox to run the neg tests. */
  lazy val generateToolboxClasspath = Def.task {
    val scalaBinVersion = (Compile / Keys.scalaBinaryVersion).value
    val targetDir = (Compile / Keys.target).value
    val compiledClassesDir = targetDir / s"scala-$scalaBinVersion/classes"
    val testClassesDir = targetDir / s"scala-$scalaBinVersion/test-classes"
    val libraryJar = Keys.scalaInstance.value.libraryJar.getAbsolutePath
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
    sbt.inScope(sbt.ThisScope.in(project = ref))(ss)

  def inProjectRefs(refs: Seq[Reference])(ss: Setting[_]*): Seq[Setting[_]] =
    refs.flatMap(inProject(_)(ss))

  def inCompileAndTest(ss: Setting[_]*): Seq[Setting[_]] =
    Seq(sbt.Compile, sbt.Test).flatMap(sbt.inConfig(_)(ss))

  object Keywords {
    val Circe = " circe"
    val Monocle = " monocle"
    val Integration = " integration"
    val Scalatest = " scalatest"
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
    // Keywords.Scalac,
    Keywords.BetterFiles,
    Keywords.Shapeless
    // Keywords.Magnolia
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

  // import com.typesafe.sbt.SbtPgp.autoImport.PgpKeys
  // import ch.epfl.scala.sbt.release.ReleaseEarlyPlugin.{autoImport => ReleaseEarlyKeys}

  final val PluginProject = sbt.LocalProject("plugin")
  private final val ThisRepo = GitHub("scalacenter", "scalac-profiling")
  final val publishSettings: Seq[Def.Setting[_]] = Seq(
    Keys.startYear := Some(2017),
    Keys.autoAPIMappings := true,
    Keys.publishMavenStyle := true,
    Keys.homepage := Some(ThisRepo),
    Test / Keys.publishArtifact := false,
    Keys.licenses := Seq("Apache-2.0" -> url("https://opensource.org/licenses/Apache-2.0")),
    Keys.developers := List(GitHubDev("jvican", "Jorge Vicente Cantero", "jorge@vican.me")),
    // PgpKeys.pgpPublicRing := {
    //   if (sys.env.get("CI").isDefined) file("/drone/.gnupg/pubring.asc")
    //   else PgpKeys.pgpPublicRing.value
    // },
    // PgpKeys.pgpSecretRing := {
    //   if (sys.env.get("CI").isDefined) file("/drone/.gnupg/secring.asc")
    //   else PgpKeys.pgpSecretRing.value
    // },
    // ReleaseEarlyKeys.releaseEarlyWith := ReleaseEarlyKeys.SonatypePublisher,
    Keys.pomExtra := scala.xml.NodeSeq.Empty
  )

  object BuildDefaults {
    final val showScalaInstances: Def.Initialize[sbt.Task[Unit]] = Def.task {
      val logger = Keys.streams.value.log
      logger.info((BuildKeys.CirceTests / Test / Keys.name).value)
      logger.info((BuildKeys.CirceTests / Test / Keys.scalaInstance).value.toString)
      logger.info((BuildKeys.MonocleTests / Test /  Keys.name).value)
      logger.info((BuildKeys.MonocleTests / Test / Keys.scalaInstance).value.toString)
      logger.info((BuildKeys.MonocleExample / Test / Keys.name).value)
      logger.info((BuildKeys.MonocleExample / Test / Keys.scalaInstance).value.toString)
      logger.info((BuildKeys.ScalatestCore / Test / Keys.name).value)
      logger.info((BuildKeys.ScalatestCore / Test / Keys.scalaInstance).value.toString)
      logger.info((BuildKeys.BetterFilesCore / Test / Keys.name).value)
      logger.info((BuildKeys.BetterFilesCore / Test / Keys.scalaInstance).value.toString)
      ()
    }

    import sbt.Command
    def fixPluginCross(commands: Seq[Command]): Seq[Command] = {
      val pruned = commands.filterNot(p => p == sbt.WorkingPluginCross.oldPluginSwitch)
      sbt.WorkingPluginCross.pluginSwitch +: pruned
    }

    // val fixScalaVersionForSbtPlugin: Def.Initialize[String] = Def.setting {
    //   val orig = Keys.scalaVersion.value
    //   val is013 = (Keys.sbtVersion in Keys.pluginCrossBuild).value.startsWith("0.13")
    //   if (is013) "2.10.6" else orig
    // }

    type Hook = Def.Initialize[State => State]


    // private[build] val MinimumScalaVersion = "2.12.6"
    // def pickScalaVersion: Def.Initialize[String] = Def.settingDyn {
    //   // if (!BuildKeys.useScalacFork.value) Def.setting(MinimumScalaVersion)
    //   // 2.12.3 has no statistics, so if scalaHome isn't used it will fail to compile
    //   scalaVersion.value
    // }

    def scalacProfilingScalacOptions(ref: ProjectRef): Def.Initialize[sbt.Task[Seq[String]]] = {
      Def.task {
        val projectBuild = ref.build
        val workingDir = Keys.buildStructure.value.units(projectBuild).localBase.getAbsolutePath
        val sourceRoot = s"-P:scalac-profiling:sourceroot:$workingDir"
        val noProfileDb = s"-P:scalac-profiling:no-profiledb"
        val pluginOpts = (PluginProject / BuildKeys.optionsForSourceCompilerPlugin).value
        val forMonocle = if (ref.build == BuildKeys.Monocle.build) List("-language:postfixOps", "-Ymacro-annotations") else Nil
        (noProfileDb +: sourceRoot +: pluginOpts) ++ forMonocle
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
          s"""$ref / ${Keys.scalacOptions.key.label} := ${MethodRefs.scalacProfilingScalacOptionsRef(ref)}.value""".stripMargin
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
    Keys.scalaVersion := "2.13.8",
    Keys.version := "1.0.1-SNAPSHOT",
    Keys.triggeredMessage := Watched.clearWhenTriggered,
    BuildKeys.enableStatistics := true,
    BuildKeys.showScalaInstances := BuildDefaults.showScalaInstances.value
  ) ++ publishSettings ++ commandAliases

  final val projectSettings: Seq[Def.Setting[_]] = Seq(
    Compile / Keys.scalacOptions := reasonableCompileOptions,
    // Necessary because the scalac version has to be always SNAPSHOT to avoid caching issues
    // Scope here is wrong -- we put it here temporarily until this is fixed upstream
    // ReleaseEarlyKeys.releaseEarlyBypassSnapshotCheck := true
  )

  final val reasonableCompileOptions = (
    "-deprecation" :: "-encoding" :: "UTF-8" :: "-feature" :: "-language:existentials" ::
      "-language:higherKinds" :: "-language:implicitConversions" :: "-unchecked" ::
      "-Ywarn-numeric-widen" :: "-Xfuture" :: "-Xlint" :: Nil
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
