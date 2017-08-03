package ch.epfl.scala.profilers.tools

final class Debugger[G <: scala.tools.nsc.Global](val global: G) {
  // In the future, `debug` will be aliased to `global.debuglog`
  def debug(msg: String): Unit = println(msg)
  def success(msg: String): Unit =
    debug(wrap(msg, scala.Console.GREEN))

  def wrap(content: String, `with`: String): String =
    s"${`with`}$content${scala.Console.RESET}"
}
