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
      Seq(addPlugin, dummy)
    },
    scalacOptions in Test ++= optionsForSourceCompilerPlugin.value,
    // Log implicits to identify which info we get currently
    scalacOptions in Test ++= {
      if (!enablePerformanceDebugging.value) Nil
      else List("-Xlog-implicits")
    },
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

lazy val integrations = project
  .in(file("integrations"))
  .dependsOn(Circe, Monocle)
  .settings(
    inCompileAndTest(
      scalacOptions in Compile ++=
        (optionsForSourceCompilerPlugin in plugin).value,
      scalacOptions in MonocleExample ++=
        (optionsForSourceCompilerPlugin in plugin).value,
      scalacOptions in MonocleTests ++=
        (optionsForSourceCompilerPlugin in plugin).value,
      scalacOptions in CirceTests ++=
        (optionsForSourceCompilerPlugin in plugin).value
    ),
    test := {
      Def.sequential(
        (compile in Compile),
        (compile in Test in CirceTests),
        (compile in Test in MonocleTests),
        (compile in Test in MonocleExample)
      ).value
    }
  )