param(
    [string]$Version = "",
    [string]$SinceRef = "",
    [string]$PatchFile = "data/patchnotes/changelog.json",
    [switch]$IncludeWorkingTree
)

$ErrorActionPreference = "Stop"

function Add-LineIfMissing {
    param(
        [System.Collections.Generic.List[string]]$Lines,
        [string]$Line
    )
    if ([string]::IsNullOrWhiteSpace($Line)) { return }
    if (-not $Lines.Contains($Line)) {
        $Lines.Add($Line) | Out-Null
    }
}

function Test-AnyPath {
    param(
        [string[]]$Files,
        [string[]]$Patterns
    )
    foreach ($file in $Files) {
        foreach ($pattern in $Patterns) {
            if ($file -match $pattern) {
                return $true
            }
        }
    }
    return $false
}

function Invoke-GitQuiet {
    param([string[]]$Args)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = & git @Args 2>$null
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    if ($exitCode -ne 0) {
        return $null
    }
    return (($output | Out-String).Trim())
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repoRoot

if (-not (Test-Path $PatchFile)) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $PatchFile) -Force | Out-Null
    $initial = [ordered]@{
        currentVersion = "1.0.0"
        baselineRef    = ""
        entries        = @()
    }
    $initial | ConvertTo-Json -Depth 8 | Set-Content -Path $PatchFile -Encoding UTF8
}

$doc = Get-Content -Path $PatchFile -Raw | ConvertFrom-Json
if ($null -eq $doc.entries) {
    $doc | Add-Member -NotePropertyName entries -NotePropertyValue @() -Force
}
if ($null -eq $doc.currentVersion) {
    $doc | Add-Member -NotePropertyName currentVersion -NotePropertyValue "1.0.0" -Force
}
if ($null -eq $doc.baselineRef) {
    $doc | Add-Member -NotePropertyName baselineRef -NotePropertyValue "" -Force
}

$resolvedVersion = $Version.Trim()
if ([string]::IsNullOrWhiteSpace($resolvedVersion)) {
    $resolvedVersion = ($doc.currentVersion | Out-String).Trim()
}
if ([string]::IsNullOrWhiteSpace($resolvedVersion)) {
    $resolvedVersion = "1.0.0"
}

$resolvedSince = $SinceRef.Trim()
if ([string]::IsNullOrWhiteSpace($resolvedSince)) {
    $resolvedSince = (($doc.baselineRef | Out-String).Trim())
}
if ([string]::IsNullOrWhiteSpace($resolvedSince)) {
    $lastTag = Invoke-GitQuiet -Args @("describe", "--tags", "--abbrev=0")
    if (-not [string]::IsNullOrWhiteSpace($lastTag)) {
        $resolvedSince = $lastTag
    }
}
if ([string]::IsNullOrWhiteSpace($resolvedSince)) {
    $previousCommit = Invoke-GitQuiet -Args @("rev-parse", "HEAD~1")
    if (-not [string]::IsNullOrWhiteSpace($previousCommit)) {
        $resolvedSince = $previousCommit
    }
}

$changedFiles = New-Object System.Collections.Generic.List[string]
if (-not [string]::IsNullOrWhiteSpace($resolvedSince)) {
    $diffRange = "$resolvedSince..HEAD"
    git diff --name-only $diffRange | ForEach-Object {
        if (-not [string]::IsNullOrWhiteSpace($_)) {
            $changedFiles.Add($_.Trim()) | Out-Null
        }
    }
}

if ($IncludeWorkingTree) {
    git diff --name-only | ForEach-Object {
        if (-not [string]::IsNullOrWhiteSpace($_)) {
            $changedFiles.Add($_.Trim()) | Out-Null
        }
    }
    git diff --name-only --cached | ForEach-Object {
        if (-not [string]::IsNullOrWhiteSpace($_)) {
            $changedFiles.Add($_.Trim()) | Out-Null
        }
    }
}

$uniqueFiles = $changedFiles | Sort-Object -Unique

$novidades = New-Object System.Collections.Generic.List[string]
$melhorias = New-Object System.Collections.Generic.List[string]
$correcoes = New-Object System.Collections.Generic.List[string]

$hasAndroidUi = Test-AnyPath -Files $uniqueFiles -Patterns @("^app-android/", "ui/components", "screens/")
$hasGlobalBoss = Test-AnyPath -Files $uniqueFiles -Patterns @("globalboss", "global_boss")
$hasProduction = Test-AnyPath -Files $uniqueFiles -Patterns @("production", "craft", "gather", "hunting")
$hasQuest = Test-AnyPath -Files $uniqueFiles -Patterns @("quest", "progress")
$hasCombat = Test-AnyPath -Files $uniqueFiles -Patterns @("combat")
$hasSave = Test-AnyPath -Files $uniqueFiles -Patterns @("save", "autosave")
$hasPatchSystem = Test-AnyPath -Files $uniqueFiles -Patterns @("patchnotes", "changelog")

if ($hasGlobalBoss) {
    Add-LineIfMissing -Lines $novidades -Line "Boss Global semanal/mensal recebeu ajustes de navegacao e leitura no Android."
}
if ($hasPatchSystem) {
    Add-LineIfMissing -Lines $novidades -Line "Sistema de patchnotes automatico adicionado com exibicao unica por versao."
}
if ($hasAndroidUi) {
    Add-LineIfMissing -Lines $melhorias -Line "Botoes, popups e navegacao mobile foram reorganizados para uso com uma mao."
}
if ($hasProduction) {
    Add-LineIfMissing -Lines $melhorias -Line "Fluxos de producao e craft ficaram mais objetivos e com melhor clareza visual."
}
if ($hasQuest) {
    Add-LineIfMissing -Lines $correcoes -Line "Sinalizacoes de quests foram ajustadas para destacar apenas conteudo coletavel."
}
if ($hasCombat) {
    Add-LineIfMissing -Lines $correcoes -Line "Leitura do combate foi refinada para reduzir poluicao visual e melhorar consistencia."
}
if ($hasSave) {
    Add-LineIfMissing -Lines $correcoes -Line "Persistencia de save/autosave recebeu ajustes para manter progresso mais seguro."
}

if ($novidades.Count -eq 0 -and $melhorias.Count -eq 0 -and $correcoes.Count -eq 0) {
    Add-LineIfMissing -Lines $melhorias -Line "Ajustes gerais de estabilidade e qualidade de vida."
    Add-LineIfMissing -Lines $correcoes -Line "Correcao de bugs menores reportados na versao anterior."
}

if ($novidades.Count -eq 0) {
    Add-LineIfMissing -Lines $novidades -Line "Melhorias internas prepararam o jogo para os proximos conteudos."
}
if ($melhorias.Count -eq 0) {
    Add-LineIfMissing -Lines $melhorias -Line "Interface recebeu refinamentos de legibilidade e padronizacao."
}
if ($correcoes.Count -eq 0) {
    Add-LineIfMissing -Lines $correcoes -Line "Ajustes de consistencia foram aplicados em fluxos de jogo e menus."
}

$today = Get-Date -Format "dd/MM/yy"
$entry = [ordered]@{
    version   = $resolvedVersion
    date      = $today
    novidades = @($novidades)
    melhorias = @($melhorias)
    correcoes = @($correcoes)
}

$existingEntries = @($doc.entries)
$filteredEntries = @()
foreach ($item in $existingEntries) {
    if (($item.version | Out-String).Trim() -ne $resolvedVersion) {
        $filteredEntries += $item
    }
}

$doc.currentVersion = $resolvedVersion
$doc.entries = @($entry) + $filteredEntries
$doc.baselineRef = (git rev-parse HEAD).Trim()

$doc | ConvertTo-Json -Depth 20 | Set-Content -Path $PatchFile -Encoding UTF8

Write-Host "Patchnotes atualizadas em $PatchFile"
Write-Host "Versao: $resolvedVersion"
Write-Host "Arquivos considerados: $($uniqueFiles.Count)"
if (-not [string]::IsNullOrWhiteSpace($resolvedSince)) {
    Write-Host "Comparado desde: $resolvedSince"
}
