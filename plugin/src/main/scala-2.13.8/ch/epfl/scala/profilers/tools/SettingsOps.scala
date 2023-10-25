package ch.epfl.scala.profilers.tools

import scala.tools.nsc.Global

object SettingsOps {
  def areStatisticsEnabled(g: Global): Boolean =
    g.settings.areStatisticsEnabled
}
