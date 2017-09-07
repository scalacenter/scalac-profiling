/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala.profiledb

import java.nio.file.Files

import ch.epfl.scala.profiledb.{profiledb => schema}
import com.google.protobuf.{CodedInputStream, CodedOutputStream}

import scala.util.Try

object ProfileDb {
  def read(path: ProfileDbPath): Try[schema.Database] = Try {
    val inputStream = Files.newInputStream(path.target.underlying)
    val reader = CodedInputStream.newInstance(inputStream)
    schema.Database.parseFrom(reader)
  }

  def write(database: schema.Database, path: ProfileDbPath): Try[schema.Database] = Try {
    val targetPath = path.target.underlying
    if (!Files.exists(targetPath))
      Files.createDirectories(targetPath.getParent())
    val outputStream = Files.newOutputStream(targetPath)
    val writer = CodedOutputStream.newInstance(outputStream)
    database.writeTo(writer)
    writer.flush()
    database
  }
}