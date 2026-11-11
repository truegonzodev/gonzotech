param([Parameter(Mandatory=$true)][ValidateSet('A','B')][string]$Variant)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$snapshot = Join-Path $repo 'pipe-snapshots\339-AB'
$manifest = Get-Content -LiteralPath (Join-Path $snapshot 'manifest.json') -Raw | ConvertFrom-Json
$safe = 'safe.directory=' + ($repo -replace '\\','/')
$head = & git -c $safe -C $repo rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $head -ne $manifest.sourceCommit) {
    throw 'Source commit differs from snapshot. Use the saved JAR or restore the snapshot into a separate directory.'
}
$dirty = & git -c $safe -C $repo status --porcelain -- src build.gradle settings.gradle gradle.properties gradle audit/run_pipe_snapshot.ps1
if ($LASTEXITCODE -ne 0 -or $dirty) { throw 'Runtime/build files differ from snapshot; refusing a mislabeled run.' }
if ($env:JAVA_TOOL_OPTIONS -match 'gonzotech.pipe' -or $env:JDK_JAVA_OPTIONS -match 'gonzotech.pipe' -or $env:_JAVA_OPTIONS -match 'gonzotech.pipe') {
    throw 'Remove gonzotech.pipe overrides from Java environment options before comparison.'
}
$jdk = 'C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot'
if (Test-Path -LiteralPath $jdk) { $env:JAVA_HOME = $jdk }
$mode = if ($Variant -eq 'A') { 'baseline' } else { 'experimental' }
$logDir = Join-Path $snapshot 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$start = Get-Date
$stamp = $start.ToString('yyyyMMdd-HHmmss-fff')
Push-Location -LiteralPath $repo
try {
    & gradle runClient --offline --no-configuration-cache "-PpipeVariant=$mode" '-PpipeProfile=true'
    $result = $LASTEXITCODE
} finally {
    Pop-Location
    $latest = Join-Path $repo 'run\logs\latest.log'
    if ((Test-Path -LiteralPath $latest) -and (Get-Item -LiteralPath $latest).LastWriteTime -ge $start) {
        $dest = Join-Path $logDir "$Variant-$stamp.log"
        Copy-Item -LiteralPath $latest -Destination $dest
        Write-Host "Saved log: $dest"
    }
}
exit $result
