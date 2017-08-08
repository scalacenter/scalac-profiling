val SbtReleaseEarly = RootProject(uri("git://github.com/scalacenter/sbt-release-early.git#e2acefca5a9449ac6d8f3e87805c327bd1e3ebd9"))
lazy val root = project
  .in(file("."))
  .dependsOn(SbtReleaseEarly)
  .settings(
    addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC8")
  )