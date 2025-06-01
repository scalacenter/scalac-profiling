lazy val root = project
  .in(file("."))
  .settings(
    addSbtPlugin("com.github.sbt" % "sbt-git" % "2.1.0"),
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.7"),
    addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1"),
    addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.0"),
    addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4"),
    addSbtPlugin("com.github.sbt" % "sbt-ghpages" % "0.8.0"),
    addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.7.1"),
    addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1"),
    // // Let's add our sbt plugin to the sbt too ;)
    // unmanagedSourceDirectories in Compile ++= {
    //   val pluginMainDir = baseDirectory.value.getParentFile / "sbt-plugin" / "src" / "main"
    //   List(pluginMainDir / "scala", pluginMainDir / s"scala-sbt-${Keys.sbtBinaryVersion.value}")
    // },
    libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.17"
  )
