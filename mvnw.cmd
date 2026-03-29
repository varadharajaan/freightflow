@ECHO OFF
SETLOCAL

SET BASE_DIR=%~dp0
SET WRAPPER_DIR=%BASE_DIR%.mvn\wrapper
SET WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar
SET WRAPPER_PROPS=%WRAPPER_DIR%\maven-wrapper.properties

IF NOT EXIST "%WRAPPER_PROPS%" (
  ECHO Missing "%WRAPPER_PROPS%"
  EXIT /B 1
)

IF NOT EXIST "%WRAPPER_JAR%" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$props = Get-Content '%WRAPPER_PROPS%';" ^
    "$wrapperUrl = ($props | Where-Object { $_ -like 'wrapperUrl=*' } | ForEach-Object { $_.Substring(11) });" ^
    "if (-not $wrapperUrl) { Write-Host 'wrapperUrl missing in %WRAPPER_PROPS%'; exit 1 };" ^
    "$ProgressPreference='SilentlyContinue';" ^
    "Invoke-WebRequest -Uri $wrapperUrl -OutFile '%WRAPPER_JAR%'"

  IF NOT EXIST "%WRAPPER_JAR%" (
    ECHO Failed to download Maven wrapper jar.
    EXIT /B 1
  )
)

IF NOT "%JAVA_HOME%"=="" (
  SET JAVACMD=%JAVA_HOME%\bin\java.exe
) ELSE (
  SET JAVACMD=java
)

"%JAVACMD%" -Dmaven.multiModuleProjectDirectory="%BASE_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
IF ERRORLEVEL 1 EXIT /B 1

ENDLOCAL
