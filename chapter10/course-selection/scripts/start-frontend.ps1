. (Join-Path $PSScriptRoot 'environment.ps1')
Push-Location (Join-Path $ProjectRoot 'frontend')
try {
    & npm ci --no-audit --no-fund
    Assert-Exit 'Frontend install'
    & npm run dev -- --host 127.0.0.1
    Assert-Exit 'Frontend server'
} finally {
    Pop-Location
}
