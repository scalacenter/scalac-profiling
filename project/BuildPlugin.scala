package build

import sbt.{AutoPlugin, Def, Keys, PluginTrigger, Plugins}

object BuildPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = ch.epfl.scala.sbt.release.ReleaseEarlyPlugin
  val autoImport = BuildKeys

  override def globalSettings: Seq[Def.Setting[_]] =
    BuildDefaults.globalSettings
  override def buildSettings: Seq[Def.Setting[_]] =
    BuildDefaults.buildSettings
  override def projectSettings: Seq[Def.Setting[_]] =
    BuildDefaults.projectSettings
}

object BuildKeys {
  import sbt.{settingKey, RootProject, ProjectRef, file}
  val enablePerformanceDebugging =
    settingKey[Boolean]("Enable performance debugging if true.")

  // Source dependency is a submodule that we modify
  val Scalac = RootProject(file("./scalac"))
  val ScalacCompiler = ProjectRef(Scalac.build, "compiler")
  val ScalacBuild = ProjectRef(Scalac.build, "dist")
}

object BuildDefaults {
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

  final val globalSettings: Seq[Def.Setting[_]] = Seq(
    Keys.triggeredMessage in ThisBuild := Watched.clearWhenTriggered,
    Keys.testOptions in Test += sbt.Tests.Argument("-oD"),
    Keys.onLoad := { (state: State) =>
      val logger = Keys.sLog.value
      val extracted = sbt.Project.extract(state)
      val buildData = extracted.structure.data
      val maybeVersion = ScalacVersion.get(buildData)
      maybeVersion match {
        case Some(version) =>
          (Keys.scalaVersion in ThisBuild).get(buildData) match {
            case Some(scalaVersion) if scalaVersion == version =>
              publishCustomScalaFork(state, version, logger)
            case _ => state // Do nothing
          }
        case None => state // Do nothing
      }
    }
  )

  private final val ScalacVersion = Keys.version in BuildKeys.ScalacCompiler
  private final val ScalaVersions = Seq("2.11.11", "2.12.3")
  final val buildSettings: Seq[Def.Setting[_]] = Seq(
    Keys.organization := "ch.epfl.scala",
    Keys.resolvers += Resolver.jcenterRepo,
    Keys.updateOptions := Keys.updateOptions.value.withCachedResolution(true),
    Keys.resolvers += Resolver.sonatypeRepo("staging"),
    Keys.scalaVersion := ScalacVersion.value,
    Keys.crossScalaVersions := ScalaVersions ++ List(ScalacVersion.value),
    BuildKeys.enablePerformanceDebugging in ThisBuild := false
  ) ++ publishSettings

  final val projectSettings: Seq[Def.Setting[_]] = Seq(
    Keys.scalacOptions in Compile := reasonableCompileOptions
  )

  final val reasonableCompileOptions = (
    "-deprecation" :: "-encoding" :: "UTF-8" :: "-feature" :: "-language:existentials" ::
      "-language:higherKinds" :: "-language:implicitConversions" :: "-unchecked" ::
      "-Yno-adapted-args" :: "-Ywarn-numeric-widen" :: "-Xfuture" :: "-Xlint" :: Nil
  )

  private final val scalacPublishKey = Keys.publishLocal in BuildKeys.ScalacCompiler
  def publishCustomScalaFork(state0: State, version: String, logger: Logger): State = {
    import sbt.{Project, Value, Inc, Incomplete}
    logger.warn(s"Publishing Scala version $version from the fork...")
    Project.runTask(scalacPublishKey, state0) match {
      case Some((newState, Value(v))) => newState
      case None                       => sys.error(s"Key `${scalacPublishKey.key.label}` is not defined?")
      case Some((newState, Inc(inc))) =>
        val previousError = Incomplete.show(inc.tpe)
        sys.error(s"Got error when running ${scalacPublishKey.key.label}: $previousError")
    }
  }
}
