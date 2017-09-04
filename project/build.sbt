val SbtReleaseEarly = RootProject(uri("git://github.com/scalacenter/sbt-release-early.git#e2acefca5a9449ac6d8f3e87805c327bd1e3ebd9"))
lazy val root = project
  .in(file("."))
  .dependsOn(SbtReleaseEarly)
  .settings(
    // Coursier bug: coursier fails to resolve source dependencies because
    // it looks for the jar of the project name even if it is just a source dep
    // addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC8"),
    addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")
  )
