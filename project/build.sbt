lazy val root = project
  .in(file("."))
  .settings(
    // Coursier bug: coursier fails to resolve source dependencies because
    // it looks for the jar of the project name even if it is just a source dep
    // addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC8"),
    addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3"),
    addSbtPlugin("ch.epfl.scala" % "sbt-release-early" % "1.2.0"),
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.11"),
    addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5"),
    // Let's add our sbt plugin to the sbt too ;)
    unmanagedSourceDirectories in Compile ++= {
      val pluginMainDir = baseDirectory.value.getParentFile / "sbt-plugin" / "src" / "main"
      List(pluginMainDir / "scala", pluginMainDir / s"scala-sbt-${Keys.sbtBinaryVersion.value}")
    },
    libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.2",
    libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
  )
