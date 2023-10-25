scalaVersion := "2.12.15"
profilingWarmupDuration := 20

////////////////////////////////////////////////////////////
val checkCompilerIsWarmedUp = settingKey[Boolean]("")
Global / checkCompilerIsWarmedUp := false

Test / compile := {
  if (checkCompilerIsWarmedUp.value)
    sys.error("Compilation of files has been called again!")
  (Test / compile).value
}
