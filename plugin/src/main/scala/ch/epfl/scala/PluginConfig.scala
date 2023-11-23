package ch.epfl.scala

import ch.epfl.scala.profiledb.utils.AbsolutePath

final case class PluginConfig(
    showProfiles: Boolean,
    generateDb: Boolean,
    sourceRoot: AbsolutePath,
    printSearchIds: Set[Int],
    generateMacroFlamegraph: Boolean,
    generateGlobalFlamegraph: Boolean,
    printFailedMacroImplicits: Boolean,
    concreteTypeParamsInImplicits: Boolean
)
