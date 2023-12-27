package ch.epfl.scala.profiling

import ch.epfl.scala.profiling.docs.Sonatype
import mdoc.MainSettings

import scala.meta.io.AbsolutePath

object Docs {
  def main(args: Array[String]): Unit = {
    val cwd0 = AbsolutePath.workingDirectory
    // Depending on who runs it (sbt vs bloop), the current working directory is different
    val cwd = if (!cwd0.resolve("docs").isDirectory) cwd0.toNIO.getParent else cwd0.toNIO

    val settings = MainSettings()
      .withSiteVariables(
        Map(
          "VERSION" -> Sonatype.releaseScalacProfiling.version,
          "LATEST_VERSION" -> scalac.profiling.internal.build.BuildInfo.version,
          "SBT_PLUGIN_VERSION" -> Sonatype.releaseSbtPlugin.version
        )
      )
      .withArgs(args.toList)
      // it should work with mdoc when run inside bloop but it doesn't, let's wait until it's fixed
      .withIn(cwd.resolve("docs"))
      .withOut(cwd.resolve("out"))

    val exitCode = _root_.mdoc.Main.process(settings)
    if (exitCode != 0) sys.exit(exitCode)
  }
}
