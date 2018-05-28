package ch.epfl.scala

import ch.epfl.scala.profiledb.utils.AbsolutePath

case class PluginConfig(
    showProfiles: Boolean,
    noDb: Boolean,
    sourceRoot: Option[AbsolutePath],
    printSearchIds: Set[Int],
    printFailedMacroImplicits: Boolean,
    concreteTypeParamsInImplicits: Boolean
)
