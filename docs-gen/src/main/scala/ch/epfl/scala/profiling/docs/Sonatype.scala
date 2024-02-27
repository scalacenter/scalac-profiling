package ch.epfl.scala.profiling.docs

import java.text.SimpleDateFormat
import java.util.Date
import org.jsoup.Jsoup

import scala.util.control.NonFatal
import coursierapi.MavenRepository

import scala.jdk.CollectionConverters._

final case class Release(version: String, lastModified: Date)

object Sonatype {
  lazy val releaseScalacProfiling = fetchLatest("scalac-profiling_2.12.19")
  lazy val releaseSbtPlugin = fetchLatest("sbt-scalac-profiling_2.12_1.0")

  /** Returns the latest published snapshot release, or the current release if. */
  private def fetchLatest(artifact: String): Release = {
    val artifacts = List(
      DependencyResolution.Artifact("ch.epfl.scala", artifact, "latest.release")
    )
    val resolvedJars = DependencyResolution.resolve(
      artifacts,
      additionalRepos =
        List(MavenRepository.of(s"https://oss.sonatype.org/content/repositories/staging"))
    )

    val latestStableVersion = resolvedJars.find(_.syntax.contains(artifact)) match {
      case None => sys.error(s"Missing jar for resolved artifact '$artifact'")
      case Some(jar) =>
        val firstTry =
          jar.underlying
            .getFileName()
            .toString
            .stripSuffix(".jar")
            .stripPrefix(artifact + "-")

        if (!firstTry.endsWith("_2.12.18") && !firstTry.endsWith("_2.12_1.0"))
          firstTry
        else jar.getParent.getParent.underlying.getFileName.toString
    }

    val doc = Jsoup
      .connect(
        s"https://oss.sonatype.org/content/repositories/releases/ch/epfl/scala/$artifact/"
      )
      .get

    val dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm")
    val releases = doc
      .select("pre")
      .asScala
      .flatMap { versionRow =>
        val elements = versionRow.getAllElements().asScala.filterNot(_.text().contains("../"))
        val nodes = versionRow.textNodes().asScala.filter(_.text().trim.nonEmpty)

        elements.zip(nodes).flatMap {
          case (element, node) =>
            val version = element.text().stripSuffix("/")

            if (version.startsWith("maven-metadata")) Nil
            else {
              node.text().trim().split("\\s+").init.toList match {
                case List(date, time) =>
                  try {
                    val parsedDate = dateTime.parse(s"$date $time")
                    List(Release(version, parsedDate))
                  } catch {
                    case NonFatal(_) => Nil
                  }
                case _ => Nil
              }
            }
        }
      }

    releases.filter(_.version == latestStableVersion).maxBy(_.lastModified.getTime)
  }
}
