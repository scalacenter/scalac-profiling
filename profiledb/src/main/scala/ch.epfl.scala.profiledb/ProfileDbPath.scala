/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala.profiledb

import java.nio.file.Path

import ch.epfl.scala.profiledb.utils.{AbsolutePath, RelativePath}

final class ProfileDbPath(classesDir: AbsolutePath, targetPath: RelativePath) {
  def target: AbsolutePath = {
    import ProfileDbPath.{Prefix, ProfileDbName, extension}
    require(targetPath.underlying.startsWith(Prefix.underlying))
    require(extension(targetPath.underlying) == ProfileDbName)
    targetPath.toAbsolute(classesDir)
  }
}

object ProfileDbPath {
  private[profiledb] final val ProfileDbName = "profiledb"
  private[profiledb] final val Prefix = RelativePath("META-INF").resolve(s"$ProfileDbName")

  def extension(path: Path): String = {
    val filename = path.getFileName.toString
    val idx = filename.lastIndexOf('.')
    if (idx == -1) ""
    else filename.substring(idx + 1)
  }
}
