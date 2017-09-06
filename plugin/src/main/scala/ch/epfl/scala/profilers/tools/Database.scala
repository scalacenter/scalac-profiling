/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala.profilers.tools

import java.nio.file.{Path, Paths}

import scala.reflect.internal.util.Statistics
import scala.util.Try

/**
  * The persistence layer is the responsible of saving the statistics collected by
  * the Scalac profiling plugin.
  *
  * Statistics or profiles can be collected in two ways: either globally or per
  * compilation unit. This also matches the way this data is persisted in the database.
  *
  * The file structure of the database is the following:
  *
  * ```
  * classes/
  *   -> META-INF/
  *      -> profiledb/
  *         -> global.profiledb
  *         -> source1.profiledb
  *         -> source2.profiledb
  *         -> ...
  * ```
  *
  * On the one hand, `global.profiledb` contains statistics and data that are not
  * dependent on a concrete compilation unit, like phase counters and timers.
  *
  * On the other hand, per-compilation-unit profiledb's like `source1.profiledb`
  * contain compilation data strictly related to that compilation unit. For example,
  * macro and implicit search profiles are stored in this semanticdb.
  *
  * ``
  *
  * @param statistics Global statistics that are collected by the scalac infrastructure.
  * @param outputDir The output directory where classes are stored.
  */
abstract class Database(statistics: Statistics, outputDir: Path) {
  def persist: Try[Unit] = ???
}
