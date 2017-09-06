/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala.profiledb

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Path, Paths}

import ch.epfl.scala.profiledb.utils.{AbsolutePath, RelativePath}
import ch.epfl.scala.profiledb.{profiledb => schema}
import com.google.protobuf.{CodedInputStream, CodedOutputStream}

import scala.util.Try

object ProfileDb {
  private final val Id = "profiledb"
  private final val Extension = s".$Id"
  private final val Prefix = RelativePath("META-INF").resolve(s"$Id")

  def read(fromClassesDir: AbsolutePath): Try[schema.Database] = Try {
    val target = Prefix.toAbsolute(fromClassesDir)
    val inputStream = Files.newInputStream(target.underlying)
    val reader = CodedInputStream.newInstance(inputStream)
    schema.Database.parseFrom(reader)
  }

  def write(database: schema.Database, path: Path): Try[Unit] = Try {
    val outputStream = Files.newOutputStream(path)
    val writer = CodedOutputStream.newInstance(outputStream)
    database.writeTo(writer)
  }
}