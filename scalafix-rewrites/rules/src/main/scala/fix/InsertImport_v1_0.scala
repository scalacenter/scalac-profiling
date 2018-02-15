package fix

import scalafix._
import scala.meta._

case object InsertImport_v1_0 extends Rule("InsertImport_v1_0") {
  val importForCachedImplicits = q"import _root_.org.scalatest.CachedImplicits._".importers.head
  override def fix(ctx: RuleCtx): Patch = {
    ctx.addGlobalImport(importForCachedImplicits)
  }
}
