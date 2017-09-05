lazy val root = project
  .in(file("."))
  .aggregate(plugin)
  .settings(Seq(
    name := "profiling-root",
    publish := {},
    publishLocal := {}
  ))

// Do not change the lhs id of this plugin, `BuildPlugin` relies on it
lazy val plugin = project
  .dependsOn(Scalac)
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
        else List("-Ystatistics", "-P:scalac-profiling:log-macro-call-site")
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
      val scalaPartialVersion = CrossVersion partialVersion scalaVersion.value
      scalaPartialVersion.collect {
        case (2, y) if y == 11 => new File(scalaSource.value.getPath + "-2.11")
        case (2, y) if y >= 12 => new File(scalaSource.value.getPath + "-2.12")
      }.toList
    })
  )

// Source dependencies are specified in `project/BuildPlugin.scala`
lazy val integrations = project
  .in(file("integrations"))
  .dependsOn(Circe, Monocle)
  .settings(
    scalacOptions in Compile ++=
      (optionsForSourceCompilerPlugin in plugin).value,
    test := {
      Def.sequential(
        (showScalaInstances in ThisBuild),
        (compile in Compile),
        (compile in Test in CirceTests),
        (compile in Test in MonocleTests),
        (compile in Test in MonocleExample)
      ).value
    },
    testOnly := Def.inputTaskDyn {
      val keywords = keywordsSetting.parsed
      val emptyAnalysis = Def.task(sbt.inc.Analysis.Empty)
      val CirceTask = Def.taskDyn {
        if (keywords.contains(CirceKeyword)) (compile in Test in CirceTests)
        else emptyAnalysis
      }
      val IntegrationTask = Def.taskDyn {
        if (keywords.contains(IntegrationKeyword)) (compile in Compile)
        else emptyAnalysis
      }
      val MonocleTask = Def.taskDyn {
        if (keywords.contains(MonocleKeyword)) Def.sequential(
          (compile in Test in MonocleTests),
          (compile in Test in MonocleExample)
        ) else emptyAnalysis
      }
      Def.sequential(CirceTask, MonocleTask, IntegrationTask)
    }.evaluated
  )