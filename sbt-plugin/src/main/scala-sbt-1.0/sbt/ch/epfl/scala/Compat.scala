package sbt.ch.epfl.scala

object Compat {
  type ExecCommand = sbt.Exec
  implicit def command2String(command: ExecCommand) = command.commandLine
  implicit def string2Exex(s: String): ExecCommand = sbt.Exec(s, None, None)
}
