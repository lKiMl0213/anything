@echo off
setlocal enabledelayedexpansion
color 0A
title Compilador - Anything RPG

echo.
echo ====================================
echo   COMPILANDO E EMPACOTANDO JOGO
echo ====================================
echo.

REM Executa a compilação
call .\gradlew.bat clean packageWindowsPortable

if %errorlevel% neq 0 (
    echo.
    echo [ERRO] Compilação falhou!
    echo.
    pause
    exit /b 1
)

REM Abre a pasta de distribuição
echo.
echo [OK] Compilação concluída com sucesso!
echo.
echo Abrindo pasta de saída...
start "" "build\distributions"

echo.
echo Procure pelo arquivo: anything-windows-portable.zip
echo.
pause
