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
val CirceBuild = BuildRef(Circe.build)
val CirceTests = ProjectRef(Circe.build, "tests")
val Monocle = RootProject(uri("git://github.com/jvican/Monocle.git#713054c46728c1fe912d2a7bae0ec19470ecaab9"))
val MonocleBuild = BuildRef(Monocle.build)
val MonocleExample = ProjectRef(Monocle.build, "example")
val MonocleTests = ProjectRef(Monocle.build, "testJVM")

val AllIntegrationProjects = List(CirceTests, MonocleExample, MonocleTests)


val showScalaInstances = taskKey[Unit]("Show versions of all integration tests")
showScalaInstances in ThisBuild := {
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
}

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
    }
  )

/**
 * So you may want to ask, why is the code below this comment required?
 * 
 * HA! Good question. Breathe and take your time.
 * 
 * Sbt does not like overrides of setting values that happen in ThisBuild,
 * nor in other project settings like integrations'. No. Sbt is exigent and
 * always asks you to give your best.
 * 
 * So, as I'm a busy developer that does not have the time to debug, find a
 * reproduction to this insidious bug and report it upstream, I force the
 * settings overrides via this cute hook in `onLoad`.
 * 
 * Why so much code for such a simple idea? Well, `Project.extract` does force
 * the execution and initialization of settings, so as `onLoad` is a setting
 * it causes a recursive call to itself, yay!
 * 
 * So, in short, solution: use an attribute in the state to short-circuit the
 * recursive invocation.
 * 
 * Notes to the future reader: the bug that prompted this solution is weird
 * I can indeed override lots of settings via project refs, but when it comes
 * to overriding a setting **in a project** (that has been generated via
 * sbt-cross-project), it does not work. On top of this, this wouldn't happen
 * if monocle defined the scala versions at the build level (it instead does it
 * at the project level, which is bad practice). So, finding a repro for this
 * is going to be fun. Escape while you can.
 */
val hijacked = AttributeKey[Boolean]("The hijacked sexy option.")
onLoad in Global := (onLoad in Global).value andThen { (state: State) =>
  def hijackScalaVersions(state: State): State = {
    val hijackedState = state.put(hijacked, true)
    val extracted = Project.extract(hijackedState)
    val toAppend = inProjectRefs(AllIntegrationProjects)(
      scalaVersion := (scalaVersion in Test in plugin).value,
      scalaInstance := (scalaInstance in Test in plugin).value,
      scalacOptions ++= (optionsForSourceCompilerPlugin in plugin).value,
      libraryDependencies ~= { previousDependencies =>
        // Assumes that all of these projects are on the same bincompat version (2.12.x)
        val validScalaVersion = PreviousScalaVersion.value
        previousDependencies.map { dependency =>
          dependency.crossVersion match {
            case fullVersion: sbt.CrossVersion.Full =>
              // If it's full version, let's trick the dependency into thinking
              // that we're still the previous version because they are bincompat
              // at the compiler source level (even though this may not be the case)
              val manualNameWithScala = dependency.name + s"_$validScalaVersion"
              val newDependency = dependency
                .copy(name = manualNameWithScala)
                .copy(crossVersion = sbt.CrossVersion.Disabled)
              println(s"REPLACING $dependency with $newDependency")
              newDependency
            case _ => dependency
          }
        }
      }
    )
    extracted.append(toAppend, hijackedState)
  }

  if(state.get(hijacked).getOrElse(false)) state
  else hijackScalaVersions(state)
}