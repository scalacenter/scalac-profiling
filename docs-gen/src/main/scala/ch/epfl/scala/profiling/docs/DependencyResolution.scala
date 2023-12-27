package ch.epfl.scala.profiling.docs

import coursierapi.Repository
import coursierapi.error.CoursierError

import ch.epfl.scala.profiledb.utils.AbsolutePath

import scala.jdk.CollectionConverters._

// Slight modification of `bloop.DependencyResolution`
object DependencyResolution {

  /**
   * @param organization The module's organization.
   * @param module       The module's name.
   * @param version      The module's version.
   */
  final case class Artifact(organization: String, module: String, version: String)

  /**
   * Resolve the specified modules and get all the files. By default, the local Ivy
   * repository and Maven Central are included in resolution. This resolution throws
   * in case there is an error.
   *
   * @param artifacts       Artifacts to resolve
   * @param resolveSources  Resolve JAR files containing sources
   * @param additionalRepos Additional repositories to include in resolution.
   * @return All the resolved files.
   */
  def resolve(
      artifacts: List[Artifact],
      resolveSources: Boolean = false,
      additionalRepos: Seq[Repository] = Nil
  ): Array[AbsolutePath] = {
    resolveWithErrors(artifacts, resolveSources, additionalRepos) match {
      case Right(paths) => paths
      case Left(error) => throw error
    }
  }

  /**
   * Resolve the specified module and get all the files. By default, the local ivy
   * repository and Maven Central are included in resolution. This resolution is
   * pure and returns either some errors or some resolved jars.
   *
   * @param artifacts Artifacts to resolve
   * @return Either a coursier error or all the resolved files.
   */
  def resolveWithErrors(
     artifacts: List[Artifact],
     resolveSources: Boolean = false,
     additionalRepositories: Seq[Repository] = Nil
   ): Either[CoursierError, Array[AbsolutePath]] = {
    val dependencies = artifacts.map { artifact =>
      import artifact._
      val baseDep = coursierapi.Dependency.of(organization, module, version)
      if (resolveSources) baseDep.withClassifier("sources")
      else baseDep
    }
    resolveDependenciesWithErrors(dependencies, resolveSources, additionalRepositories)
  }

  /**
   * Resolve the specified dependencies and get all the files. By default, the
   * local ivy repository and Maven Central are included in resolution. This
   * resolution is pure and returns either some errors or some resolved jars.
   *
   * @param dependencies           Dependencies to resolve.
   * @param additionalRepositories Additional repositories to include in resolution.
   * @return Either a coursier error or all the resolved files.
   */
  def resolveDependenciesWithErrors(
     dependencies: Seq[coursierapi.Dependency],
     resolveSources: Boolean = false,
     additionalRepositories: Seq[Repository] = Nil
   ): Either[CoursierError, Array[AbsolutePath]] = {
    val fetch = coursierapi.Fetch
      .create()
      .withDependencies(dependencies: _*)
    if (resolveSources)
      fetch.addArtifactTypes("src", "jar")
    fetch.addRepositories(additionalRepositories: _*)

    try Right(fetch.fetch().asScala.toArray.map(f => AbsolutePath(f.toPath)))
    catch {
      case error: CoursierError => Left(error)
    }
  }
}
