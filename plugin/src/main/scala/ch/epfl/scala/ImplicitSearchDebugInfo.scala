package ch.epfl.scala

final case class ImplicitSearchDebugInfo private (firings: Int, sourceFiles: List[String]) {
  override def toString: String =
    s"""
       |{
       |  "firings": $firings,
       |  "sourceFiles": [
       |    ${sourceFiles.mkString("", ",\n", "")}
       |  ]
       |}""".stripMargin
}

object ImplicitSearchDebugInfo {
  def apply(firings: Int, sourceFiles: List[String]): Option[ImplicitSearchDebugInfo] =
    if (firings > 0 && sourceFiles.nonEmpty)
      Some(new ImplicitSearchDebugInfo(firings, sourceFiles))
    else
      None
}
