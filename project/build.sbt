lazy val root = project
  .in(file("."))
  .settings(
    addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3"),
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.23"),
    addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5"),

    // addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.0.0-M10"),
    // // Let's add our sbt plugin to the sbt too ;)
    // unmanagedSourceDirectories in Compile ++= {
    //   val pluginMainDir = baseDirectory.value.getParentFile / "sbt-plugin" / "src" / "main"
    //   List(pluginMainDir / "scala", pluginMainDir / s"scala-sbt-${Keys.sbtBinaryVersion.value}")
    // },
    libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.9.7",
  )
