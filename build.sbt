/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

lazy val root = project
  .in(file("."))
  .settings(Seq(
    name := "profiling-root",
    publish := {},
    publishLocal := {},
    watchSources ++=
      (watchSources in plugin).value ++
      (watchSources in profiledb).value ++
      (watchSources in integrations).value
  ))

import com.trueaccord.scalapb.compiler.Version.scalapbVersion
lazy val profiledb = project
  .in(file("profiledb"))
  .settings(
    // Specify scala version to allow third-party software to use this module
    scalaVersion := "2.12.4",
    crossScalaVersions := List(scalaVersion.value, "2.11.11"),
    libraryDependencies +=
      "com.trueaccord.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf",
    PB.targets in Compile := Seq(scalapb.gen() -> (sourceManaged in Compile).value)
  )

// Do not change the lhs id of this plugin, `BuildPlugin` relies on it
lazy val plugin = project
  .dependsOn(profiledb)
  .settings(
    name := "scalac-profiling",
    libraryDependencies ++= List(
      "com.lihaoyi" %% "pprint" % "0.5.3",
      scalaOrganization.value % "scala-compiler" % scalaVersion.value
    ),
    libraryDependencies ++= testDependencies,
    testOptions in Test ++= List(Tests.Argument("-v"), Tests.Argument("-s")),
    // Make the tests to compile with the plugin
    optionsForSourceCompilerPlugin := {
      val jar = (Keys.`package` in Compile).value
      val profileDbJar = (Keys.`package` in Compile in profiledb).value
      val absoluteJars = List(jar, profileDbJar).map(_.getAbsolutePath)
      // Should we filter out all the scala artifacts?
      val pluginDeps = (managedClasspath in Compile).value.files.toList
      val pluginAndDeps = (absoluteJars ++ pluginDeps).mkString(":")
      val addPlugin = "-Xplugin:" + pluginAndDeps
      val dummy = "-Jdummy=" + jar.lastModified
      // Enable debugging information when necessary
      val debuggingPluginOptions =
        if (!enableStatistics.value) Nil
        else List("-Ystatistics")//, "-P:scalac-profiling:show-profiles")
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
    }),
    packageBin in Compile := (assembly in Compile).value,
    test in assembly := {},
    assemblyOption in assembly :=
      (assemblyOption in assembly).value
        .copy(includeScala = false, includeDependency = true)
  )


// Trick to copy profiledb with Scala 2.11.11 so that vscode can depend on it
lazy val profiledb211 = profiledb
  .copy(id = "profiledb-211")
  .settings(
    scalaVersion := (scalaVersion in VscodeImplementation).value,
    // Redefining target so that sbt doesn't clash at runtime
    // This makes sense, but we should get a more sensible error message.
    target := (baseDirectory in profiledb).value./("target_211"),
    publishArtifact in (Compile, packageDoc) := false
  )

// This is the task to publish the vscode integration
val publishExtension = taskKey[Unit]("The task to publish the vscode extension.")

// Has to be in independent project because uses different Scala version
lazy val vscodeIntegration = project
  .in(file(".hidden"))
  .dependsOn(VscodeImplementation, profiledb211)
  .settings(
    scalaVersion := (scalaVersion in VscodeImplementation).value,
    libraryDependencies in VscodeImplementation += (projectID in profiledb211).value,
    // Sbt bug: doing this for VscodeImplementation just doesn't work.
    update := update.dependsOn(publishLocal in profiledb211).value,
    publish := (publish in VscodeImplementation).dependsOn(publish in profiledb211).value,
    publishLocal :=
      (publishLocal in VscodeImplementation).dependsOn(publishLocal in profiledb211).value,
    publishExtension := (Def.task {
      val scalaExtensionDir = (baseDirectory in VscodeScala).value./("scala")
      sys.process.Process(Seq("vsce", "package"), scalaExtensionDir).!!
    }).dependsOn(publishLocal).value
  )

lazy val profilingSbtPlugin = project
  .in(file("sbt-plugin"))
  .settings(
    name := "sbt-profiling",
    sbtPlugin := true,
    sbtVersion := "0.13.16",
    scalaVersion := "2.10.6",
    crossSbtVersions := List("0.13.16", "1.0.0"),
    ScriptedPlugin.scriptedSettings,
    scriptedLaunchOpts ++= Seq("-Xmx2048M", "-Xms1024M", s"-Dplugin.version=${version.value}"),
    scriptedBufferLog := false
  )

// Source dependencies are specified in `project/BuildPlugin.scala`
lazy val integrations = project
  .in(file("integrations"))
  .dependsOn(Circe)
  .settings(
    parallelExecution in Test := false,
    scalacOptions in Compile ++=
      (optionsForSourceCompilerPlugin in plugin).value,
    clean := Def.sequential(
      clean,
      (clean in Test in CirceTests),
      (clean in Test in MonocleTests),
      (clean in Test in MonocleExample),
      (clean in Compile in ScalatestCore),
      (clean in ScalacCompiler)
    ).value,
    test := Def.sequential(
        (showScalaInstances in ThisBuild),
        (profilingWarmupCompiler in Compile), // Warmup example, classloader is the same for all
        (compile in Compile),
        (compile in Test in CirceTests),
        (compile in Test in MonocleTests),
        (compile in Test in MonocleExample),
        (compile in Compile in ScalatestCore),
        (compile in ScalacCompiler)
    ).value,
    testOnly := Def.inputTaskDyn {
      val keywords = keywordsSetting.parsed
      val emptyAnalysis = Def.task(sbt.inc.Analysis.Empty)
      val CirceTask = Def.taskDyn {
        if (keywords.contains(Keywords.Circe)) Def.sequential(
          (compile in Test in CirceTests)
        ) else emptyAnalysis
      }
      val IntegrationTask = Def.taskDyn {
        if (keywords.contains(Keywords.Integration)) Def.sequential(
          (compile in Compile)
        ) else emptyAnalysis
      }
      val MonocleTask = Def.taskDyn {
        if (keywords.contains(Keywords.Monocle)) Def.sequential(
          (compile in Test in MonocleTests),
          (compile in Test in MonocleExample)
        ) else emptyAnalysis
      }
      val ScalatestTask = Def.taskDyn {
        if (keywords.contains(Keywords.Scalatest)) Def.sequential(
          (compile in Compile in ScalatestCore)
        ) else emptyAnalysis
      }
      val ScalacTask = Def.taskDyn {
         if (keywords.contains(Keywords.Scalac)) Def.sequential(
          (compile in Compile in ScalacCompiler),
          (compile in Compile in ScalatestTests)
        ) else emptyAnalysis
      }
      val BetterFilesTask = Def.taskDyn {
        if (keywords.contains(Keywords.BetterFiles)) Def.sequential(
          (compile in Compile in BetterFilesCore)
        ) else emptyAnalysis
      }
      val ShapelessTask = Def.taskDyn {
        if (keywords.contains(Keywords.Shapeless)) Def.sequential(
          (compile in Compile in ShapelessCore),
          (compile in Test in ShapelessCore)
        ) else emptyAnalysis
      }
      Def.sequential(CirceTask, MonocleTask, IntegrationTask, ScalatestTask, ScalacTask, BetterFilesTask, ShapelessTask)
    }.evaluated
  )

val proxy = project
  .in(file(".proxy"))
  .dependsOn(Circe, Monocle, Scalatest, Scalac, BetterFiles, Shapeless)
  .settings(overridingProjectSettings)
