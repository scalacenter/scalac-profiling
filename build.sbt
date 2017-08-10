lazy val root = project
  .in(file("."))
  .aggregate(plugin)
  .settings(Seq(
    publish := {},
    publishLocal := {}
  ))

lazy val plugin = project
  .settings(
    name := "scalac-profiling",
    libraryDependencies ++= List(
      "com.lihaoyi" %% "pprint" % "0.5.0",
      scalaOrganization.value % "scala-compiler" % scalaVersion.value
    ),
    libraryDependencies ++= testDependencies,
    testOptions in Test ++= List(Tests.Argument("-v"), Tests.Argument("-s")),
    // Make the tests to compile with the plugin
    optionsForSourceCompilerPlugin := {
      val jar = (Keys.`package` in Compile).value
      // Should we filter out all the scala artifacts?
      val pluginDeps = (managedClasspath in Compile).value.files.toList
      val pluginAndDeps = (jar.getAbsolutePath :: pluginDeps).mkString(":")
      val addPlugin = "-Xplugin:" + pluginAndDeps
      val dummy = "-Jdummy=" + jar.lastModified
      // Enable debugging information when necessary
      val debuggingPluginOptions =
        if (!enablePerformanceDebugging.value) Nil
        else List("-Ystatistics:typer")
        //else List("-Xlog-implicits", "-Ystatistics:typer")
      Seq(addPlugin, dummy) ++ debuggingPluginOptions
    },
    scalacOptions in Test ++= optionsForSourceCompilerPlugin.value,
    // Generate toolbox classpath while compiling for both configurations
    resourceGenerators in Compile += generateToolboxClasspath.taskValue,
    resourceGenerators in Test += Def.task {
      val options = scalacOptions.value
      val stringOptions = options.filterNot(_ == "-Ydebug").mkString(" ")
      val pluginOptionsFile = resourceManaged.value / "toolbox.plugin"
      IO.write(pluginOptionsFile, stringOptions)
      List(pluginOptionsFile.getAbsoluteFile)
    }.taskValue,
    inCompileAndTest(unmanagedSourceDirectories ++= {
      scalaPartialVersion.value.collect {
        case (2, y) if y == 11 => new File(scalaSource.value.getPath + "-2.11")
        case (2, y) if y >= 12 => new File(scalaSource.value.getPath + "-2.12")
      }.toList
    })
  )

lazy val scalac = project
  .in(file("compiler"))
  .dependsOn(Scalac)

// Source dependencies from git are cached by sbt
val Circe = RootProject(uri("git://github.com/circe/circe.git#96d419611c045e638ccf0b646e693d377ef95630"))
val CirceTests = ProjectRef(Circe.build, "tests")
val Monocle = RootProject(uri("git://github.com/jvican/Monocle.git#713054c46728c1fe912d2a7bae0ec19470ecaab9"))
val MonocleExample = ProjectRef(Monocle.build, "example")
val MonocleTests = ProjectRef(Monocle.build, "testJVM")
val AllIntegrationProjects = List(CirceTests, MonocleExample, MonocleTests)
val showScalaInstances = taskKey[Unit]("Show versions of all integration tests")

lazy val integrations = project
  .in(file("integrations"))
  .dependsOn(Circe, Monocle)
  .settings(
    inProjectRefs(AllIntegrationProjects)(
      // Set both -- scalaInstance is not reloaded when scalaVersion changes
      scalaVersion := (scalaVersion in Test in plugin).value,
      scalaInstance := (scalaInstance in Test in plugin).value,
      scalacOptions ++= (optionsForSourceCompilerPlugin in plugin).value
    ),
    scalacOptions in Compile ++=
      (optionsForSourceCompilerPlugin in plugin).value,
    showScalaInstances := {
      val logger = streams.value.log
      logger.info((name in Compile).value)
      logger.info((scalaInstance in Compile).value.toString)
      logger.info((name in Test in CirceTests).value)
      logger.info((scalaInstance in Test in CirceTests).value.toString)
      logger.info((name in Test in MonocleTests).value)
      logger.info((scalaInstance in Test in MonocleTests).value.toString)
      logger.info((name in Test in MonocleExample).value)
      logger.info((scalaInstance in Test in MonocleExample).value.toString)
      ()
    },
    test := {
      Def.sequential(
        showScalaInstances,
        (compile in Compile),
        (compile in Test in CirceTests),
        (compile in Test in MonocleTests),
        (compile in Test in MonocleExample)
      ).value
    }
  )