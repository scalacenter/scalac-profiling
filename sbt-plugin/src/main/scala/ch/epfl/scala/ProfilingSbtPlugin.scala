/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package ch.epfl.scala

import sbt.{AutoPlugin, Def, Keys, PluginTrigger, Plugins}

object ProfilingSbtPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  val autoImport = BuildKeys

  override def globalSettings: Seq[Def.Setting[_]] =
    ProfilingPluginImplementation.globalSettings
  override def buildSettings: Seq[Def.Setting[_]] =
    ProfilingPluginImplementation.buildSettings
  override def projectSettings: Seq[Def.Setting[_]] =
    ProfilingPluginImplementation.projectSettings
}

object BuildKeys

object ProfilingPluginImplementation {
  val globalSettings: Seq[Def.Setting[_]] = Nil
  val projectSettings: Seq[Def.Setting[_]] = Nil
  val buildSettings: Seq[Def.Setting[_]] = Nil
}
