@echo off
setlocal

cd /d "%~dp0"

set "DIST_ZIP=build\distributions\anything-rpg-windows-portable.zip"
set "EXPORT_DIR=exports"
set "LATEST_ZIP=%EXPORT_DIR%\anything-rpg-windows-portable_latest.zip"

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "STAMP=%%i"
set "EXPORT_ZIP=%EXPORT_DIR%\anything-rpg-windows-portable_%STAMP%.zip"

echo.
echo [1/3] Gerando pacote portatil...
call gradlew.bat packageWindowsPortable
if errorlevel 1 goto :error

if not exist "%DIST_ZIP%" (
  echo.
  echo ERRO: pacote nao encontrado em "%DIST_ZIP%".
  goto :error
)

if not exist "%EXPORT_DIR%" mkdir "%EXPORT_DIR%"

echo.
echo [2/3] Copiando pacote versionado...
copy /y "%DIST_ZIP%" "%EXPORT_ZIP%" >nul
if errorlevel 1 goto :error

echo [3/3] Atualizando pacote latest...
copy /y "%DIST_ZIP%" "%LATEST_ZIP%" >nul
if errorlevel 1 goto :error

echo.
echo Exportacao concluida com sucesso.
echo Arquivo versionado: "%EXPORT_ZIP%"
echo Arquivo latest:     "%LATEST_ZIP%"
echo.
exit /b 0

:error
echo.
echo Falha na exportacao.
exit /b 1

