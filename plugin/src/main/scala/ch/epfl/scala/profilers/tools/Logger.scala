/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala.profilers.tools

import scala.reflect.internal.util.NoPosition

final class Logger[G <: scala.tools.nsc.Global](val global: G) {
  def debug(msg: String): Unit = global.debuglog(msg)
  def success(msg: String): Unit =
    debug(wrap(msg, scala.Console.GREEN))

  def info(msg: String): Unit =
    global.reporter.info(NoPosition, msg, true)
  def info[T: pprint.TPrint](header: String, value: T): Unit = {
    val tokens = pprint.tokenize(value, height = 10000).mkString
    info(s"$header:\n$tokens")
  }

  def wrap(content: String, `with`: String): String =
    s"${`with`}$content${scala.Console.RESET}"
}
