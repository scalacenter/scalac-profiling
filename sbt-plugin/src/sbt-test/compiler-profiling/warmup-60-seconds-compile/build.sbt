scalaVersion := "2.12.4"
profilingWarmupDuration := 20

////////////////////////////////////////////////////////////
val checkCompilerIsWarmedUp = settingKey[Boolean]("")
checkCompilerIsWarmedUp in Global := false

compile in Compile := {
  if (checkCompilerIsWarmedUp.value)
    sys.error("Compilation of files has been called again!")
  (compile in Compile).value
}
