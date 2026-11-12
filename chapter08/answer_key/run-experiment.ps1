param([Parameter(Mandatory=$true)][string]$DriverJar)
$ErrorActionPreference = 'Stop'
$driver = [IO.Path]::GetFullPath($DriverJar)
if (!(Test-Path -LiteralPath $driver)) { throw 'MySQL JDBC driver not found' }
if ($env:ANSWER_DB_URL -notmatch '/answer8_test(?:\?|$)') { throw 'Use the dedicated answer8_test database' }
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Path out -Force | Out-Null
    & javac -encoding UTF-8 --release 17 -d out MySqlExperiment.java
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
    & java -cp ("out" + [IO.Path]::PathSeparator + $driver) MySqlExperiment
    if ($LASTEXITCODE -ne 0) { throw 'Database experiment failed' }
} finally { Pop-Location }
