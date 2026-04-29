@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

set "JAVA_EXE=%SCRIPT_DIR%runtime\bin\java.exe"
if not exist "%JAVA_EXE%" (
  echo Runtime Java nao encontrado: "%JAVA_EXE%"
  exit /b 1
)

"%JAVA_EXE%" -Dfile.encoding=UTF-8 -cp "%SCRIPT_DIR%lib\*" rpg.MainKt
exit /b %ERRORLEVEL%
