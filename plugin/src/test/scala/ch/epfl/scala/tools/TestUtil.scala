/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package newtype.tools

import scala.reflect._

object TestUtil {
  import tools.reflect.{ToolBox, ToolBoxError}

  def intercept[T <: Throwable: ClassTag](test: => Any): T = {
    try {
      test
      throw new Exception(s"Expected exception ${classTag[T]}")
    } catch {
      case t: Throwable =>
        if (classTag[T].runtimeClass != t.getClass) throw t
        else t.asInstanceOf[T]
    }
  }

  def eval(code: String, compileOptions: String = ""): Any = {
    val tb = mkToolbox(compileOptions)
    tb.eval(tb.parse(code))
  }

  def mkToolbox(compileOptions: String = ""): ToolBox[_ <: scala.reflect.api.Universe] = {
    val m = scala.reflect.runtime.currentMirror
    import scala.tools.reflect.ToolBox
    m.mkToolBox(options = compileOptions)
  }

  def getResourceContent(resourceName: String): String = {
    val resource = getClass.getClassLoader.getResource(resourceName)
    val file = scala.io.Source.fromFile(resource.toURI)
    file.getLines().mkString("")
  }

  lazy val toolboxClasspath: String = getResourceContent("toolbox.classpath")
  lazy val toolboxPluginOptions: String = getResourceContent("toolbox.extra")

  def expectError(
      errorSnippet: String,
      compileOptions: String = "",
      baseCompileOptions: String = s"-cp $toolboxClasspath $toolboxPluginOptions"
  )(code: String): Unit = {
    val errorMessage = intercept[ToolBoxError] {
      eval(code, s"$compileOptions $baseCompileOptions")
    }.getMessage
    val userMessage =
      s"""
         |FOUND: $errorMessage
         |EXPECTED: $errorSnippet
      """.stripMargin
    assert(errorMessage.contains(errorSnippet), userMessage)
  }

  def expectWarning(
      errorSnippet: String,
      compileOptions: String = "",
      baseCompileOptions: String = s"-cp $toolboxClasspath $toolboxPluginOptions"
  )(code: String): Unit = {
    expectError(errorSnippet, compileOptions + "-Xfatal-warnings", baseCompileOptions)(code)
  }
}
