/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala.profiledb

import ch.epfl.scala.profiledb.utils.{AbsolutePath, RelativePath}

final class ProfileDbPath private (outputDir: AbsolutePath, targetPath: RelativePath) {
  lazy val target: AbsolutePath = {
    require(ProfileDbPath.hasDbExtension(targetPath))
    require(targetPath.underlying.startsWith(ProfileDbPath.Prefix.underlying))
    targetPath.toAbsolute(outputDir)
  }
}

object ProfileDbPath {
  def apply(outputDir: AbsolutePath, targetPath: RelativePath): ProfileDbPath =
    new ProfileDbPath(outputDir, targetPath)

  private[profiledb] final val ProfileDbName = "profiledb"
  private[profiledb] final val ProfileDbExtension = s".$ProfileDbName"
  private[profiledb] final val Prefix = RelativePath("META-INF").resolve(s"$ProfileDbName")
  final val GlobalProfileDbRelativePath = toProfileDbPath(RelativePath("global"))
  final val GraphsProfileDbRelativePath = Prefix.resolveRelative(RelativePath("graphs"))

  private[profiledb] def hasDbExtension(path: RelativePath): Boolean =
    path.underlying.getFileName.toString.endsWith(ProfileDbExtension)

  def toProfileDbPath(relativeSourceFile: RelativePath): RelativePath =
    Prefix.resolveRelative(addDbExtension(relativeSourceFile))

  private[profiledb] def addDbExtension(path: RelativePath): RelativePath = {
    val realPath = path.underlying
    val extendedName = realPath.getFileName.toString + ProfileDbExtension
    val parent = path.underlying.getParent
    if (parent == null) RelativePath(extendedName)
    else RelativePath(parent.resolve(extendedName))
  }
}
