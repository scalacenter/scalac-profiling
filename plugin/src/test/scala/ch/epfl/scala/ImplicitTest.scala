package shapeless {
  sealed trait HList extends Product with Serializable

  final case class ::[+H, +T <: HList](head : H, tail : T) extends HList {
    def ::[HH](h : HH) : HH :: H :: T = shapeless.::(h, this)

    override def toString = head match {
      case _: ::[_, _] => "("+head.toString+") :: "+tail.toString
      case _ => head.toString+" :: "+tail.toString
    }
  }

  sealed trait HNil extends HList {
    def ::[H](h : H) = shapeless.::(h, this)
    override def toString = "HNil"
  }

  case object HNil extends HNil

  trait Selector[L <: HList, U] {
    def apply(l: L): U
  }

  object Selector {
    def apply[L <: HList, U](implicit selector: Selector[L, U]): Selector[L, U] = selector

    implicit def inHead[H, T <: HList]: Selector[H :: T, H] =
      new Selector[H :: T, H] {
        def apply(l : H :: T) = l.head
      }

    implicit def inTail[H, T <: HList, U]
      (implicit st : Selector[T, U]): Selector[H :: T, U] =
        new Selector[H :: T, U] {
          def apply(l : H :: T) = st(l.tail)
        }
  }
}

import shapeless._

object Test extends App {
  val sel = Selector[L, Boolean]

//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
//     Int ::
// //
  type L =
    Int ::
    Int ::
    Int ::
    Int ::
    Int ::
    Int ::
    Int ::
    Int ::
    Int ::
    Int ::
// 
    Boolean ::
    HNil
}