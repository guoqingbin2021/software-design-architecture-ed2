$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    docker compose down
    if ($LASTEXITCODE -ne 0) {
        throw '停止失败。'
    }
    Write-Host '已停止服务，教学数据库数据卷保留。'
} finally {
    Pop-Location
}
