#!/usr/bin/env pwsh
<#
.DESCRIPTION
Compila o Anything RPG e cria um arquivo ZIP portável com Java embutido
#>

$ErrorActionPreference = "Stop"

Write-Host "`n" -ForegroundColor Green
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  COMPILADOR - Anything RPG" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "" -ForegroundColor Green

try {
    Write-Host "⏳ Compilando e empacotando..." -ForegroundColor Yellow
    & .\gradlew.bat clean packageWindowsPortable
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n" -ForegroundColor Green
        Write-Host "✓ Compilação concluída com sucesso!" -ForegroundColor Green
        Write-Host "`n" -ForegroundColor Green
        
        # Abre a pasta de distribuição
        Write-Host "🔍 Abrindo pasta de saída..." -ForegroundColor Yellow
        Start-Process "build\distributions"
        
        Write-Host "`n" -ForegroundColor Green
        Write-Host "📦 Procure pelo arquivo: anything-windows-portable.zip" -ForegroundColor Green
        Write-Host "`n" -ForegroundColor Green
        Write-Host "Para usar em outro PC:" -ForegroundColor Cyan
        Write-Host "  1. Extraia o ZIP" -ForegroundColor Gray
        Write-Host "  2. Execute o arquivo run-anything.cmd" -ForegroundColor Gray
        Write-Host "  3. Não é necessário instalar Java!" -ForegroundColor Gray
        Write-Host "" -ForegroundColor Green
    }
    else {
        Write-Host "`n" -ForegroundColor Red
        Write-Host "✗ Erro na compilação!" -ForegroundColor Red
        Write-Host "" -ForegroundColor Red
        exit 1
    }
}
catch {
    Write-Host "`n" -ForegroundColor Red
    Write-Host "✗ Erro: $_" -ForegroundColor Red
    Write-Host "" -ForegroundColor Red
    exit 1
}
