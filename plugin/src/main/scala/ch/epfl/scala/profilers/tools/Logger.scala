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
