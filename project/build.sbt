lazy val root = project
  .in(file("."))
  .settings(
    // Coursier bug: coursier fails to resolve source dependencies because
    // it looks for the jar of the project name even if it is just a source dep
    // addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC8"),
    addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3"),
    addSbtPlugin("ch.epfl.scala" % "sbt-release-early" % "1.1.0"),
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.11"),
    libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.2"
  )
