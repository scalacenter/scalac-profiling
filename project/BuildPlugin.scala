package build

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
  final val enablePerformanceDebugging =
    settingKey[Boolean]("Enable performance debugging if true.")
  final val optionsForSourceCompilerPlugin =
    taskKey[Seq[String]]("Generate scalac options for source compiler plugin")
  final val showScalaInstances = taskKey[Unit]("Show versions of all integration tests")

  // Refer to setting via reference because there is no dependency to the scalac build here.
  final val scalacVersionSuffix = sbt.SettingKey[String]("baseVersionSuffix")

  // Use absolute paths so that references work even though the `ThisBuild` changes
  final val AbsolutePath = file(".").getAbsolutePath
  final val HomeBuild = BuildRef(RootProject(file(AbsolutePath)).build)

  // Source dependency is a submodule that we modify
  final val Scalac = RootProject(file(s"$AbsolutePath/scalac"))
  final val ScalacBuild = BuildRef(Scalac.build)
  final val ScalacCompiler = ProjectRef(Scalac.build, "compiler")
  final val ScalacLibrary = ProjectRef(Scalac.build, "library")
  final val ScalacReflect = ProjectRef(Scalac.build, "reflect")
  final val AllScalacProjects = List(ScalacCompiler, ScalacLibrary, ScalacReflect)

  // Source dependencies from git are cached by sbt
  val Circe = RootProject(
    uri("git://github.com/circe/circe.git#96d419611c045e638ccf0b646e693d377ef95630"))
  val CirceTests = ProjectRef(Circe.build, "tests")
  val Monocle = RootProject(
    uri("git://github.com/jvican/Monocle.git#713054c46728c1fe912d2a7bae0ec19470ecaab9"))
  val MonocleExample = ProjectRef(Monocle.build, "example")
  val MonocleTests = ProjectRef(Monocle.build, "testJVM")
  val AllIntegrationProjects = List(CirceTests, MonocleExample, MonocleTests)

  // Assumes that the previous scala version is the last bincompat version
  final val ScalacVersion = Keys.version in BuildKeys.ScalacCompiler
  final val PreviousScalaVersion = Keys.scalaVersion in BuildKeys.ScalacCompiler

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
    val classpath = s"$compiledClassesDir:$testClassesDir:$libraryJar"
    val resourceDir = (Keys.resourceManaged in Compile).value
    val toolboxTestClasspath = resourceDir / "toolbox.classpath"
    sbt.IO.write(toolboxTestClasspath, classpath)
    List(toolboxTestClasspath.getAbsoluteFile)
  }

  /**
    * So you may want to ask, why is the code below this comment required?
    *
    * HA! Good question. Breathe and take your time.
    *
    * Sbt does not like overrides of setting values that happen in ThisBuild,
    * nor in other project settings like integrations'. No. Sbt is exigent and
    * always asks you to give your best.
    *
    * So, as I'm a busy developer that does not have the time to debug, find a
    * reproduction to this insidious bug and report it upstream, I force the
    * settings overrides via this cute hook in `onLoad`.
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
    * is going to be fun. Escape while you can.
    */
  final val hijacked = sbt.AttributeKey[Boolean]("The hijacked sexy option.")

  ////////////////////////////////////////////////////////////////////////////////

  def inProject(ref: Reference)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
    sbt.inScope(sbt.ThisScope.in(project = ref))(ss)

  def inProjectRefs(refs: Seq[Reference])(ss: Setting[_]*): Seq[Setting[_]] =
    refs.flatMap(inProject(_)(ss))

  def inScalacProjects(ss: Setting[_]*): Seq[Setting[_]] =
    inProjectRefs(AllScalacProjects)(ss: _*)

  def inCompileAndTest(ss: Setting[_]*): Seq[Setting[_]] =
    Seq(sbt.Compile, sbt.Test).flatMap(sbt.inConfig(_)(ss))
}

object BuildImplementation {
  import sbt.{url, file, richFile, State, Logger}
  import sbt.{ScmInfo, Developer, Resolver, ThisBuild, Watched, Compile, Test}

  // This should be added to upstream sbt.
  def GitHub(org: String, project: String): java.net.URL =
    url(s"https://github.com/$org/$project")
  def GitHubDev(handle: String, fullName: String, email: String) =
    Developer(handle, fullName, email, url(s"https://github.com/$handle"))

  import com.typesafe.sbt.SbtPgp.{autoImport => PgpKeys}
  import ch.epfl.scala.sbt.release.ReleaseEarlyPlugin.{autoImport => ReleaseEarlyKeys}

  private final val ThisRepo = GitHub("scalacenter", "scalac-profiling")
  final val publishSettings: Seq[Def.Setting[_]] = Seq(
    // Global settings to set up repository-related settings
    Keys.licenses := Seq("BSD" -> url("http://opensource.org/licenses/BSD-3-Clause")),
    Keys.publishArtifact in Test := false,
    Keys.homepage := Some(ThisRepo),
    Keys.autoAPIMappings := true,
    Keys.publishMavenStyle := true,
    Keys.startYear := Some(2017),
    Keys.scmInfo := Some(
      ScmInfo(ThisRepo, "scm:git:git@github.com:scalacenter/sbt-release-early.git")),
    Keys.developers := List(GitHubDev("jvican", "Jorge Vicente Cantero", "jorge@vican.me")),
    // Necessary to publish for our Drone CI -- specific to this repo setup.
    PgpKeys.pgpPublicRing := file("/drone/.gnupg/pubring.asc"),
    PgpKeys.pgpSecretRing := file("/drone/.gnupg/secring.asc"),
    ReleaseEarlyKeys.releaseEarlyWith := ReleaseEarlyKeys.SonatypePublisher
  )

  // Paranoid level: removes doc generation by all means
  final val scalacSettings: Seq[Def.Setting[_]] = BuildKeys.inScalacProjects(
    Keys.aggregate in Keys.doc := false,
    Keys.sources in Compile in Keys.doc := Seq.empty,
    Keys.scalacOptions in Compile in Keys.doc := Seq.empty,
    Keys.publishArtifact in Compile in Keys.packageDoc := false,
    // Use snapshot only for local development plz.
    // If placed in global settings, it's not applied. Sbt bug?
    BuildKeys.scalacVersionSuffix in BuildKeys.Scalac := "bin-stats-SNAPSHOT"
  )

  object BuildDefaults {
    final val showScalaInstances: Def.Initialize[sbt.Task[Unit]] = Def.task {
      val logger = Keys.streams.value.log
      logger.info((Keys.name in Test in BuildKeys.CirceTests).value)
      logger.info((Keys.scalaInstance in Test in BuildKeys.CirceTests).value.toString)
      logger.info((Keys.name in Test in BuildKeys.MonocleTests).value)
      logger.info((Keys.scalaInstance in Test in BuildKeys.MonocleTests).value.toString)
      logger.info((Keys.name in Test in BuildKeys.MonocleExample).value)
      logger.info((Keys.scalaInstance in Test in BuildKeys.MonocleExample).value.toString)
      ()
    }

    type Hook = Def.Initialize[State => State]

    private final val UnknownHash = "UNKNOWN"
    final val publishForkScalac: Hook = Def.setting { (state: State) =>
      import sbt.IO
      import com.typesafe.sbt.git.JGit
      // Only publish scalac if file doesn't exist
      val baseDirectory = (Keys.baseDirectory in sbt.ThisBuild).value
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
        val maybeVersion = BuildKeys.ScalacVersion.get(buildData)
        maybeVersion match {
          case Some(version) =>
            (Keys.scalaVersion in ThisBuild).get(buildData) match {
              case Some(scalaVersion) if scalaVersion == version =>
                val newState = publishCustomScalaFork(state, version, logger)
                if (currentHash != UnknownHash)
                  IO.write(scalacHashFile, currentHash)
                newState
              case _ => state // Do nothing
            }
          case None => state // Do nothing
        }
      }
    }

    import sbt.ModuleID

    /**
      * Removes scala version from those modules that use full cross version and injects
      * the manual Scala version in the library dependency name assuming that the library
      * will not use any binary incompatible change in the compiler sources (or assuming
      * that there is none, which is even better!).
      */
    def trickLibraryDependency(dependency: ModuleID, validVersion: String): ModuleID = {
      dependency.crossVersion match {
        case fullVersion: sbt.CrossVersion.Full =>
          val manualNameWithScala = s"${dependency.name}_$validVersion"
          dependency.copy(name = manualNameWithScala).copy(crossVersion = sbt.CrossVersion.Disabled)
        case _ => dependency
      }
    }

    final val PluginProject = sbt.LocalProject("plugin")
    final val hijackScalaVersions: Hook = Def.setting { (state: State) =>
      if (state.get(BuildKeys.hijacked).getOrElse(false)) state
      else {
        val hijackedState = state.put(BuildKeys.hijacked, true)
        val extracted = sbt.Project.extract(hijackedState)
        val toAppend = BuildKeys.inProjectRefs(BuildKeys.AllIntegrationProjects)(
          Keys.scalaVersion := (Keys.scalaVersion in Test in PluginProject).value,
          Keys.scalaInstance := (Keys.scalaInstance in Test in PluginProject).value,
          Keys.scalacOptions ++= (BuildKeys.optionsForSourceCompilerPlugin in PluginProject).value,
          Keys.libraryDependencies ~= { previousDependencies =>
            // Assumes that all of these projects are on the same bincompat version (2.12.x)
            val validScalaVersion = BuildKeys.PreviousScalaVersion.value
            previousDependencies.map(dep => trickLibraryDependency(dep, validScalaVersion))
          }
        )
        extracted.append(toAppend, hijackedState)
      }
    }

    final val customOnLoad: Hook =
      Def.setting(publishForkScalac.value andThen hijackScalaVersions.value)
  }

  final val globalSettings: Seq[Def.Setting[_]] = Seq(
    Keys.testOptions in Test += sbt.Tests.Argument("-oD"),
    Keys.onLoad := (Keys.onLoad in sbt.Global).value andThen (BuildDefaults.customOnLoad.value)
  )

  final val commandAliases: Seq[Def.Setting[sbt.State => sbt.State]] = {
    val scalacRef = sbt.Reference.display(BuildKeys.ScalacBuild)
    val scalac = sbt.addCommandAlias("scalac", s"project ${scalacRef}")
    val homeRef = sbt.Reference.display(BuildKeys.HomeBuild)
    val home = sbt.addCommandAlias("home", s"project ${homeRef}")
    scalac ++ home
  }

  private final val ScalaVersions = Seq("2.11.11", "2.12.3")
  final val buildSettings: Seq[Def.Setting[_]] = Seq(
    Keys.organization := "ch.epfl.scala",
    Keys.resolvers += Resolver.jcenterRepo,
    Keys.updateOptions := Keys.updateOptions.value.withCachedResolution(true),
    Keys.scalaVersion := BuildKeys.ScalacVersion.value,
    Keys.crossScalaVersions := ScalaVersions ++ List(BuildKeys.ScalacVersion.value),
    Keys.triggeredMessage := Watched.clearWhenTriggered,
    BuildKeys.enablePerformanceDebugging := sys.env.get("CI").isDefined,
    BuildKeys.showScalaInstances := BuildDefaults.showScalaInstances.value
  ) ++ publishSettings ++ commandAliases

  final val projectSettings: Seq[Def.Setting[_]] = Seq(
    Keys.scalacOptions in Compile := reasonableCompileOptions
  ) ++ scalacSettings

  final val reasonableCompileOptions = (
    "-deprecation" :: "-encoding" :: "UTF-8" :: "-feature" :: "-language:existentials" ::
      "-language:higherKinds" :: "-language:implicitConversions" :: "-unchecked" ::
      "-Yno-adapted-args" :: "-Ywarn-numeric-widen" :: "-Xfuture" :: "-Xlint" :: Nil
  )

  private def publishCustomScalaFork(state0: State, version: String, logger: Logger): State = {
    import sbt.{Project, Value, Inc, Incomplete}
    logger.warn(s"Publishing Scala version $version from the fork...")
    // Bah, reuse the same state for everything.
    val publishing = Project
      .runTask(Keys.publishLocal in BuildKeys.ScalacLibrary, state0)
      .flatMap(_ => Project.runTask(Keys.publishLocal in BuildKeys.ScalacReflect, state0))
      .flatMap(_ => Project.runTask(Keys.publishLocal in BuildKeys.ScalacCompiler, state0))
    publishing match {
      case None                       => sys.error(s"Key for publishing is not defined?")
      case Some((newState, Value(v))) => newState
      case Some((newState, Inc(inc))) =>
        sys.error(s"Got error when publishing the Scala fork: $inc")
    }
  }
}
