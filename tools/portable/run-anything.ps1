$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$javaExe = Join-Path $scriptDir "runtime\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    Write-Host "Runtime Java nao encontrado: $javaExe"
    exit 1
}

$classPath = Join-Path $scriptDir "lib\*"
& $javaExe "-Dfile.encoding=UTF-8" "-cp" $classPath "rpg.MainKt"
exit $LASTEXITCODE
