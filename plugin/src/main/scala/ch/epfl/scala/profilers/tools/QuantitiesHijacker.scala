package ch.epfl.scala.profilers.tools

import scala.collection.mutable
import scala.tools.nsc.Global
import scala.reflect.internal.util.Statistics

object QuantitiesHijacker {
  type Quantities = mutable.HashMap[String, Statistics#Quantity]
  def getRegisteredQuantities[G <: Global](global: G): Quantities = {
    val clazz = global.statistics.getClass()
    try { // see: https://github.com/scalacenter/scalac-profiling/pull/32/files#r790111968
      val field = clazz.getField("scala$reflect$internal$util$Statistics$$qs")
      field.get(global.statistics).asInstanceOf[Quantities]
    } catch {
      case _: NoSuchFieldException =>
        val method = clazz.getMethod("scala$reflect$internal$util$Statistics$$qs")
        method.invoke(global.statistics).asInstanceOf[Quantities]
    }
  }
}
