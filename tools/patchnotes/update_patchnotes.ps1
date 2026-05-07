param(
    [string]$Version = "",
    [string]$SinceRef = "",
    [string]$PatchFile = "data/patchnotes/changelog.json",
    [switch]$IncludeWorkingTree,
    [switch]$OverwriteEntry
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

function Convert-SlugToLabel {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return "" }
    $clean = $Value.ToLowerInvariant().Replace(".json", "")
    $clean = $clean -replace "^(class|subclass|specialization|specializacao|item|monster|mob|npc)_", ""
    $parts = $clean -split "[^a-z0-9]+"
    $words = @()
    foreach ($part in $parts) {
        if ([string]::IsNullOrWhiteSpace($part)) { continue }
        $words += ($part.Substring(0, 1).ToUpperInvariant() + $part.Substring(1))
    }
    return ($words -join " ").Trim()
}

function Join-Names {
    param([string[]]$Names)
    if ($Names.Count -eq 0) { return "" }
    if ($Names.Count -eq 1) { return $Names[0] }
    if ($Names.Count -eq 2) { return "$($Names[0]) e $($Names[1])" }
    return "$($Names[0]), $($Names[1]) e $($Names[2])"
}

function Get-ChangedEntityNames {
    param(
        [string[]]$Files,
        [string]$RegexPattern,
        [int]$Max = 3
    )
    $result = New-Object System.Collections.Generic.List[string]
    foreach ($file in $Files) {
        if ($file -match $RegexPattern) {
            $name = Convert-SlugToLabel -Value ([System.IO.Path]::GetFileNameWithoutExtension($file))
            if (-not [string]::IsNullOrWhiteSpace($name) -and -not $result.Contains($name)) {
                $result.Add($name) | Out-Null
            }
        }
    }
    return @($result | Select-Object -First $Max)
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
$hasMilestones = Test-AnyPath -Files $uniqueFiles -Patterns @("milestone")
$hasClasses = Test-AnyPath -Files $uniqueFiles -Patterns @("^data/classes/", "^data/subclasses/", "^data/specializations/", "^data/talent_trees/")
$hasItems = Test-AnyPath -Files $uniqueFiles -Patterns @("^data/items/", "^data/item_templates/", "^data/shop/")
$hasMonsters = Test-AnyPath -Files $uniqueFiles -Patterns @("^data/monster_", "^data/maps/", "^data/drop_tables/", "^data/biomes/")

$classNames = Get-ChangedEntityNames -Files $uniqueFiles -RegexPattern "^data/(classes|subclasses|specializations)/.+\.json$" -Max 3
$itemNames = Get-ChangedEntityNames -Files $uniqueFiles -RegexPattern "^data/(items|item_templates)/.+\.json$" -Max 3
$monsterNames = Get-ChangedEntityNames -Files $uniqueFiles -RegexPattern "^data/(monster_types|monster_archetypes|monster_behaviors|monster_modifiers)/.+\.json$" -Max 3

if ($hasGlobalBoss) {
    Add-LineIfMissing -Lines $novidades -Line "Boss global semanal/mensal adicionado."
}
if ($hasGlobalBoss -or $hasMilestones) {
    Add-LineIfMissing -Lines $novidades -Line "Milestones com recompensas por pontuacao."
}
if ($hasPatchSystem) {
    Add-LineIfMissing -Lines $novidades -Line "Patchnotes agora aparecem automaticamente apos atualizacao."
}
if ($hasClasses -and $classNames.Count -gt 0) {
    Add-LineIfMissing -Lines $novidades -Line "Ajustes em classes/especializacoes: $(Join-Names -Names $classNames)."
}
if ($hasItems -and $itemNames.Count -gt 0) {
    Add-LineIfMissing -Lines $novidades -Line "Novos itens/ajustes de equipamentos: $(Join-Names -Names $itemNames)."
}
if ($hasMonsters -and $monsterNames.Count -gt 0) {
    Add-LineIfMissing -Lines $novidades -Line "Novos monstros/variantes em destaque: $(Join-Names -Names $monsterNames)."
}
if ($hasClasses -and $classNames.Count -eq 0) {
    Add-LineIfMissing -Lines $melhorias -Line "Classes e talentos receberam ajustes de balanceamento."
}
if ($hasItems -and $itemNames.Count -eq 0) {
    Add-LineIfMissing -Lines $melhorias -Line "Itens e recompensas receberam ajustes de progressao."
}
if ($hasMonsters -and $monsterNames.Count -eq 0) {
    Add-LineIfMissing -Lines $melhorias -Line "Monstros e encontros tiveram ajustes de dificuldade."
}
if ($hasAndroidUi) {
    Add-LineIfMissing -Lines $melhorias -Line "Botoes reorganizados em menus principais para leitura mais rapida."
}
if ($hasProduction) {
    Add-LineIfMissing -Lines $melhorias -Line "Fluxo de producao/craft ficou mais claro e objetivo."
}
if ($hasQuest) {
    Add-LineIfMissing -Lines $correcoes -Line "Corrigido bug de quests sem notificacao correta."
}
if ($hasCombat) {
    Add-LineIfMissing -Lines $correcoes -Line "Melhorada leitura do combate e do historico de acoes."
}
if ($hasSave) {
    Add-LineIfMissing -Lines $correcoes -Line "Corrigido save para manter progresso consistente entre sessoes."
}

if ($novidades.Count -eq 0 -and $melhorias.Count -eq 0 -and $correcoes.Count -eq 0) {
    Add-LineIfMissing -Lines $melhorias -Line "Melhorias gerais de usabilidade e fluidez da interface."
    Add-LineIfMissing -Lines $correcoes -Line "Correcao de bugs menores reportados pelos testers."
}

if ($novidades.Count -eq 0) {
    Add-LineIfMissing -Lines $novidades -Line "Novos ajustes de conteudo foram adicionados."
}
if ($melhorias.Count -eq 0) {
    Add-LineIfMissing -Lines $melhorias -Line "Interface recebeu refinamentos de legibilidade."
}
if ($correcoes.Count -eq 0) {
    Add-LineIfMissing -Lines $correcoes -Line "Ajustes de estabilidade foram aplicados em menus e fluxo de jogo."
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
$existingSameVersion = $existingEntries | Where-Object { (($_.version | Out-String).Trim()) -eq $resolvedVersion }
$keepExistingManual = ($existingSameVersion.Count -gt 0) -and (-not $OverwriteEntry)

if ($keepExistingManual) {
    $doc.currentVersion = $resolvedVersion
} else {
    $filteredEntries = @()
    foreach ($item in $existingEntries) {
        if (($item.version | Out-String).Trim() -ne $resolvedVersion) {
            $filteredEntries += $item
        }
    }
    $doc.currentVersion = $resolvedVersion
    $doc.entries = @($entry) + $filteredEntries
}
$doc.baselineRef = (git rev-parse HEAD).Trim()

$doc | ConvertTo-Json -Depth 20 | Set-Content -Path $PatchFile -Encoding UTF8

Write-Host "Patchnotes atualizadas em $PatchFile"
Write-Host "Versao: $resolvedVersion"
if ($keepExistingManual) {
    Write-Host "Entrada existente mantida (edicao manual preservada). Use -OverwriteEntry para regenerar."
}
Write-Host "Arquivos considerados: $($uniqueFiles.Count)"
if (-not [string]::IsNullOrWhiteSpace($resolvedSince)) {
    Write-Host "Comparado desde: $resolvedSince"
}
