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
    name := "scalac-profiling-root",
    crossScalaVersions := bin212 ++ bin213,
    publish := {},
    publishLocal := {},
    skip / publish := true,
    watchSources ++=
      (plugin / watchSources).value ++
        (profiledb / watchSources).value ++
        (integrations / watchSources).value
  )

val bin212 = Seq("2.12.18", "2.12.17", "2.12.16", "2.12.15", "2.12.14", "2.12.13")
val bin213 = Seq("2.13.12", "2.13.11", "2.13.10", "2.13.9", "2.13.8", "2.13.7", "2.13.6", "2.13.5")

// Copied from
// https://github.com/scalameta/scalameta/blob/370e304b0d10db1dd65fc79a5abc1f39004aeffd/build.sbt#L724-L737
lazy val fullCrossVersionSettings = Seq(
  crossVersion := CrossVersion.full,
  crossScalaVersions := bin212 ++ bin213,
  Compile / unmanagedSourceDirectories += {
    // NOTE: SBT 1.x provides cross-version support for Scala sources
    // (https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Scala-version+specific+source+directory).
    // Unfortunately, it only includes directories like "scala_2.12" or "scala_2.13",
    // not "scala_2.12.18" or "scala_2.13.12" that we need.
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
    scalaVersion := bin212.head,
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
      "com.lihaoyi" %% "pprint" % "0.8.1",
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
        case (2, y) if y == 12 => new File(scalaSource.value.getPath + "-2.12")
        case (2, y) if y >= 13 => new File(scalaSource.value.getPath + "-2.13")
      }.toList
    }),
    Compile / Keys.packageBin := (Compile / assembly).value,
    assembly / test := {}
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
    scalaVersion := bin212.head,
    scriptedLaunchOpts ++= Seq("-Xmx2048M", "-Xms1024M", "-Xss8M", s"-Dplugin.version=${version.value}"),
    scriptedBufferLog := false
  )
  .enablePlugins(SbtPlugin)

// Source dependencies are specified in `project/BuildPlugin.scala`
lazy val integrations = project
  .in(file("integrations"))
  .settings(
    skip / publish := true,
    libraryDependencies += "com.github.alexarchambault" %% "case-app" % "2.0.6",
    Test / parallelExecution := false,
    Compile / scalacOptions := (Def.taskDyn {
      val options = (Compile / scalacOptions).value
      val ref = Keys.thisProjectRef.value
      Def.task(options ++ BuildDefaults.scalacProfilingScalacOptions(ref).value)
    }).value,
    clean := Def
      .sequential(
        clean,
        (BetterFilesCore / Compile / clean),
        (WartremoverCore / Compile / clean),
      )
      .value,
    test := Def
      .sequential(
        (ThisBuild / showScalaInstances),
        (Compile / compile),
      )
      .value,
    testOnly := Def.inputTaskDyn {
      val keywords = keywordsSetting.parsed
      val emptyAnalysis = Def.task[CompileAnalysis](sbt.internal.inc.Analysis.Empty)
      val IntegrationTask = Def.taskDyn {
        if (keywords.contains(Keywords.Integration))
          Def.sequential(
            (Compile / compile)
          )
        else emptyAnalysis
      }
      val BetterFilesTask = Def.taskDyn {
        if (keywords.contains(Keywords.BetterFiles))
          Def.sequential(
            (BetterFilesCore / Compile / compile)
          )
        else emptyAnalysis
      }
      val WartremoverTask = Def.taskDyn {
        if (keywords.contains(Keywords.Wartremover))
          Def.sequential(
            (WartremoverCore / Compile / compile)
          )
        else emptyAnalysis
      }

      Def.sequential(
        IntegrationTask,
        BetterFilesTask,
        WartremoverTask
      )
    }.evaluated
  )

val proxy = project
  .in(file(".proxy"))
  .aggregate(BetterFiles, Wartremover)
  .settings(skip / publish := true)
