lazy val root = project
  .in(file("."))
  .settings(
    addSbtPlugin("com.github.sbt" % "sbt-git" % "2.0.1"),
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6"),
    addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5"),
    addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12"),
    addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2"),
    // // Let's add our sbt plugin to the sbt too ;)
    // unmanagedSourceDirectories in Compile ++= {
    //   val pluginMainDir = baseDirectory.value.getParentFile / "sbt-plugin" / "src" / "main"
    //   List(pluginMainDir / "scala", pluginMainDir / s"scala-sbt-${Keys.sbtBinaryVersion.value}")
    // },
    libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.14"
  )
