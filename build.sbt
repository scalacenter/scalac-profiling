val ScalaVersions = Seq("2.11.11", "2.12.3")
inThisBuild(
  Seq(
    resolvers += Resolver.sonatypeRepo("staging"),
    scalaVersion in ThisBuild := ScalaVersions.last,
    crossScalaVersions in ThisBuild := ScalaVersions,
    organization in ThisBuild := "me.vican.jorge"
  ))

lazy val testDependencies = Seq(
  "junit" % "junit" % "4.12" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test"
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  licenses := Seq(
    // Scala Center license... BSD 3-clause
    "BSD" -> url("http://opensource.org/licenses/BSD-3-Clause")
  ),
  homepage := Some(url("https://github.com/scalacenter/scalac-profiling")),
  autoAPIMappings := true,
  startYear := Some(2017),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/scalacenter/scalac-profiling"),
      "scm:git:git@github.com:scalacenter/scalac-profiling.git"
    )
  ),
  developers := List(
    Developer("jvican",
              "Jorge Vicente Cantero",
              "jorge.vicentecantero@epfl.ch",
              url("http://github.com/jvican"))
  )
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {}
)

lazy val root = project
  .in(file("."))
  .aggregate(plugin)

def inCompileAndTest(ss: Setting[_]*): Seq[Setting[_]] =
  Seq(Compile, Test).flatMap(inConfig(_)(ss))

val scalaPartialVersion =
  Def.setting(CrossVersion partialVersion scalaVersion.value)

lazy val optionsForSourceCompilerPlugin =
  taskKey[Seq[String]]("Generate scalac options for source compiler plugin")

lazy val plugin = project
  .settings(
    name := "scalac-profiling",
    scalaVersion := ScalaVersions.last,
    crossScalaVersions := ScalaVersions,
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    libraryDependencies ++= testDependencies,
    testOptions in Test ++= List(Tests.Argument("-v"), Tests.Argument("-s")),
    publishSettings,
    // Make the tests to compile with the plugin
    optionsForSourceCompilerPlugin := {
      val jar = (Keys.`package` in Compile).value
      val addPlugin = "-Xplugin:" + jar.getAbsolutePath
      val dummy = "-Jdummy=" + jar.lastModified
      Seq(addPlugin, dummy)
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

/* Write all the compile-time dependencies of the spores macro to a file,
 * in order to read it from the created Toolbox to run the neg tests. */
lazy val generateToolboxClasspath = Def.task {
  val scalaBinVersion = (scalaBinaryVersion in Compile).value
  val targetDir = (target in Compile).value
  val compiledClassesDir = targetDir / s"scala-$scalaBinVersion/classes"
  val testClassesDir = targetDir / s"scala-$scalaBinVersion/test-classes"
  val libraryJar = scalaInstance.value.libraryJar.getAbsolutePath
  val classpath = s"$compiledClassesDir:$testClassesDir:$libraryJar"
  val resourceDir = (resourceManaged in Compile).value
  val toolboxTestClasspath = resourceDir / "toolbox.classpath"
  IO.write(toolboxTestClasspath, classpath)
  List(toolboxTestClasspath.getAbsoluteFile)
}

val Circe = RootProject(uri("git://github.com/circe/circe.git#96d419611c045e638ccf0b646e693d377ef95630"))
val CirceTests = ProjectRef(uri("git://github.com/circe/circe.git#96d419611c045e638ccf0b646e693d377ef95630"), "tests")

lazy val integrations = project
  .in(file("integrations"))
  .dependsOn(Circe)
  .settings(
    inCompileAndTest(
      scalacOptions in CirceTests ++=
        (optionsForSourceCompilerPlugin in plugin).value
    )
  )
