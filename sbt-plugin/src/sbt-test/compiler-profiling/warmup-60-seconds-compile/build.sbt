scalaVersion := "2.12.15"
profilingWarmupDuration := 20

////////////////////////////////////////////////////////////
val checkCompilerIsWarmedUp = settingKey[Boolean]("")
Global / checkCompilerIsWarmedUp := false

Compile / compile := {
  if (checkCompilerIsWarmedUp.value)
    sys.error("Compilation of files has been called again!")
  (Compile / compile).value
}
