/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

import xsbti.compile.CompileAnalysis

lazy val root = project
  .in(file("."))
  .aggregate(profiledb, plugin, profilingSbtPlugin)
  .settings(
    Seq(
      name := "profiling-root",
      crossScalaVersions := bin212 ++ bin213,
      publish := {},
      publishLocal := {},
      // crossSbtVersions := List("0.13.17", "1.1.1"),
      watchSources ++=
        (plugin / watchSources).value ++
          (profiledb / watchSources).value ++
          (integrations / watchSources).value
    )
  )

val bin212 = Seq("2.12.15", "2.12.14", "2.12.13", "2.12.12", "2.12.11")
val bin213 = Seq("2.13.8", "2.13.7", "2.13.6", "2.13.5")

// Copied from
// https://github.com/scalameta/scalameta/blob/370e304b0d10db1dd65fc79a5abc1f39004aeffd/build.sbt#L724-L737
lazy val fullCrossVersionSettings = Seq(
  crossVersion := CrossVersion.full,
  crossScalaVersions := bin212 ++ bin213,
  Compile / unmanagedSourceDirectories += {
    // NOTE: sbt 0.13.8 provides cross-version support for Scala sources
    // (http://www.scala-sbt.org/0.13/docs/sbt-0.13-Tech-Previews.html#Cross-version+support+for+Scala+sources).
    // Unfortunately, it only includes directories like "scala_2.11" or "scala_2.12",
    // not "scala_2.11.8" or "scala_2.12.1" that we need.
    // That's why we have to work around here.
    val base = (Compile/ sourceDirectory).value
    val versionDir = scalaVersion.value.replaceAll("-.*", "")
    base / ("scala-" + versionDir)
  }
)

import _root_.ch.epfl.scala.profiling.build.BuildImplementation.BuildDefaults
import scalapb.compiler.Version.scalapbVersion
lazy val profiledb = project
  .in(file("profiledb"))
  //.settings(metalsSettings)
  .settings(
    // Specify scala version to allow third-party software to use this module
    crossScalaVersions := bin212 ++ bin213,
    // scalaVersion := "2.12.12",
    libraryDependencies +=
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf",
    Compile / PB.targets := Seq(scalapb.gen() -> (Compile / sourceManaged).value)
  )

// Do not change the lhs id of this plugin, `BuildPlugin` relies on it
lazy val plugin = project
  .dependsOn(profiledb)
  //.settings(metalsSettings)
  .settings(
    fullCrossVersionSettings,
    name := "scalac-profiling",
    libraryDependencies ++= List(
      "com.lihaoyi" %% "pprint" % "0.5.7",
      scalaOrganization.value % "scala-compiler" % scalaVersion.value
    ),
    libraryDependencies ++= List(
      "junit" % "junit" % "4.12" % "test",
      "com.novocode" % "junit-interface" % "0.11" % "test"
    ),
    Test / testOptions ++= List(Tests.Argument("-v"), Tests.Argument("-s")),
    allDepsForCompilerPlugin := {
      val jar = (Compile / Keys.packageBin).value
      val profileDbJar = (profiledb / Compile / Keys.`package`).value
      val absoluteJars = List(jar, profileDbJar).classpath
      val pluginDeps = (Compile / managedClasspath).value
      (absoluteJars ++ pluginDeps)
    },
    // Make the tests to compile with the plugin
    optionsForSourceCompilerPlugin := {
      val jar = (Compile / Keys.packageBin).value
      val pluginAndDeps = allDepsForCompilerPlugin.value.map(_.data.getAbsolutePath()).mkString(":")
      val addPlugin = "-Xplugin:" + pluginAndDeps
      val dummy = "-Jdummy=" + jar.lastModified
      // Enable debugging information when necessary
      val debuggingPluginOptions =
        if (!enableStatistics.value) Nil
        else List("-Ystatistics") //, "-P:scalac-profiling:show-profiles")
      //else List("-Xlog-implicits", "-Ystatistics:typer")
      Seq(addPlugin, dummy) ++ debuggingPluginOptions
    },
    Test / scalacOptions ++= optionsForSourceCompilerPlugin.value,
    // Generate toolbox classpath while compiling for both configurations
    Compile / resourceGenerators += generateToolboxClasspath.taskValue,
    Test / resourceGenerators += Def.task {
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
        case (2, y) if y == 12 => new File(scalaSource.value.getPath + "-2.12")
        case (2, y) if y >= 13 => new File(scalaSource.value.getPath + "-2.13")
      }.toList
    }),
    Compile / Keys.packageBin := (Compile / assembly).value,
    assembly / test := {},
    assembly / assemblyOption :=
      (assembly / assemblyOption).value
        .copy(includeScala = false, includeDependency = true)
  )

// Trick to copy profiledb with Scala 2.11.11 so that vscode can depend on it
// lazy val profiledb211 = profiledb
//   .copy(id = "profiledb-211")
//   .settings(
//     moduleName := "profiledb",
//     scalaVersion := (scalaVersion in VscodeImplementation).value,
//     // Redefining target so that sbt doesn't clash at runtime
//     // This makes sense, but we should get a more sensible error message.
//     target := (baseDirectory in profiledb).value./("target_211")
//   )

// This is the task to publish the vscode integration
// val publishExtension = taskKey[Unit]("The task to publish the vscode extension.")

// Has to be in independent project because uses different Scala version
// lazy val vscodeIntegration = project
//   .in(file(".hidden"))
//   .dependsOn(VscodeImplementation, profiledb211)
//   .settings(
//     scalaVersion := (scalaVersion in VscodeImplementation).value,
//     libraryDependencies in VscodeImplementation += (projectID in profiledb211).value,
//     // Sbt bug: doing this for VscodeImplementation just doesn't work.
//     update := update.dependsOn(publishLocal in profiledb211).value,
//     publish := (publish in VscodeImplementation).dependsOn(publish in profiledb211).value,
//     publishLocal :=
//       (publishLocal in VscodeImplementation).dependsOn(publishLocal in profiledb211).value,
//     publishExtension := (Def
//       .task {
//         val scalaExtensionDir = (baseDirectory in VscodeScala).value./("scala")
//         sys.process.Process(Seq("vsce", "package"), scalaExtensionDir).!!
//       })
//       .dependsOn(publishLocal)
//       .value
//   )
// 
lazy val profilingSbtPlugin = project
  .in(file("sbt-plugin"))
  .settings(
    name := "sbt-scalac-profiling",
    scalaVersion := "2.12.15",
    scriptedLaunchOpts ++= Seq("-Xmx2048M", "-Xms1024M", "-Xss8M", s"-Dplugin.version=${version.value}"),
    scriptedBufferLog := false
  )
  .enablePlugins(SbtPlugin)

// Source dependencies are specified in `project/BuildPlugin.scala`
lazy val integrations = project
  .in(file("integrations"))
  // .dependsOn(Circe)
  .settings(
    libraryDependencies += "com.github.alexarchambault" %% "case-app" % "2.0.6",
    // scalaHome := BuildDefaults.setUpScalaHome.value,
    Test / parallelExecution := false,
    Compile / scalacOptions := (Def.taskDyn {
      val options = (Compile / scalacOptions).value
      val ref = Keys.thisProjectRef.value
      Def.task(options ++ BuildDefaults.scalacProfilingScalacOptions(ref).value)
    }).value,
    clean := Def
      .sequential(
        clean,
        (CirceTests/ Test / clean),
        (BetterFilesCore / Compile / clean),
        (MonocleTests / Test / clean),
        (MonocleExample / Test / clean),
        (ScalatestCore / Compile / clean),
        (ScalatestTests / Test / clean)
        //(clean in Compile in MagnoliaTests),
        // (clean in ScalacCompiler)
      )
      .value,
    test := Def
      .sequential(
        (ThisBuild / showScalaInstances),
        // (profilingWarmupCompiler in Compile), // Warmup example, classloader is the same for all
        (Compile / compile),
        (CirceTests / Test / compile),
        (MonocleTests / Test / compile),
        (MonocleExample / Test / compile),
        (ScalatestCore / Compile / compile),
        (ScalatestTests / Test / compile)
        //(compile in Compile in MagnoliaTests),
        // (compile in ScalacCompiler)
      )
      .value,
    testOnly := Def.inputTaskDyn {
      val keywords = keywordsSetting.parsed
      val emptyAnalysis = Def.task[CompileAnalysis](sbt.internal.inc.Analysis.Empty)
      val CirceTask = Def.taskDyn {
        if (keywords.contains(Keywords.Circe))
          Def.sequential(
            (CirceTests / Test / compile)
          )
        else emptyAnalysis
      }
      val IntegrationTask = Def.taskDyn {
        if (keywords.contains(Keywords.Integration))
          Def.sequential(
            (Compile / compile)
          )
        else emptyAnalysis
      }
      val MonocleTask = Def.taskDyn {
        if (keywords.contains(Keywords.Monocle))
          Def.sequential(
            (MonocleTests/ Test/ compile),
            (MonocleExample / Test / compile)
          )
        else emptyAnalysis
      }
      val ScalatestTask = Def.taskDyn {
        if (keywords.contains(Keywords.Scalatest))
          Def.sequential(
            (ScalatestCore / Compile / compile),
            (ScalatestTests / Test / compile)
          )
        else emptyAnalysis
      }
      // val ScalacTask = Def.taskDyn {
      //   if (keywords.contains(Keywords.Scalac))
      //     Def.sequential(
      //       (compile in Compile in ScalacCompiler)
      //     )
      //   else emptyAnalysis
      // }
      val BetterFilesTask = Def.taskDyn {
        if (keywords.contains(Keywords.BetterFiles))
          Def.sequential(
            (BetterFilesCore / Compile / compile)
          )
        else emptyAnalysis
      }
      // val ShapelessTask = Def.taskDyn {
      //   if (keywords.contains(Keywords.Shapeless))
      //     Def.sequential(
      //       (ShapelessCore / Compile / compile),
      //       (ShapelessCore / Test / compile)
      //     )
      //   else emptyAnalysis
      // }
      // val MagnoliaTask = Def.taskDyn {
      //   if (keywords.contains(Keywords.Magnolia))
      //     Def.sequential(
      //       (compile in Compile in MagnoliaTests)
      //     )
      //   else emptyAnalysis
      // }
      Def.sequential(
        CirceTask,
        MonocleTask,
        IntegrationTask,
        ScalatestTask,
        // ScalacTask,
        BetterFilesTask,
        // ShapelessTask//,MagnoliaTask
      )
    }.evaluated
  )

val proxy = project
  .in(file(".proxy"))
   .aggregate(Circe, BetterFiles, Scalatest, Monocle) // Shapeless, Monocle Scalac, Magnolia)
