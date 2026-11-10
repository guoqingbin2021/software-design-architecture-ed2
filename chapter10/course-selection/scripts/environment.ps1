$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $ProjectRoot '.env'
if (!(Test-Path -LiteralPath $envFile)) {
    throw 'Copy .env.example to .env and configure the dedicated databases first.'
}
foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
    $entry = $line.Trim()
    if (!$entry -or $entry.StartsWith('#')) {
        continue
    }
    if ($entry -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') {
        throw 'Invalid .env line.'
    }
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
}
if (!$env:MAVEN_CMD) {
    $env:MAVEN_CMD = 'mvn'
}
if (!$env:PORT) {
    $env:PORT = '18080'
}
function Assert-Exit([string]$Step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}
