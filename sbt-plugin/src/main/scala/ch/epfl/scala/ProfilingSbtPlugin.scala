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
