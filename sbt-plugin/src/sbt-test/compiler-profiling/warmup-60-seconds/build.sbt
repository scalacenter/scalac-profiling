val checkCompilerIsWarmedUp = settingKey[Boolean]("")
checkCompilerIsWarmedUp in Global := false

scalaVersion := "2.12.3"
profilingWarmupDuration := 20
compile in Compile := {
  if (checkCompilerIsWarmedUp.value)
    sys.error("Compilation of files has been called again!")
  (compile in Compile).value
}