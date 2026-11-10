$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw '请先安装并启动 Docker Desktop。'
    }
    docker compose up --build -d
    if ($LASTEXITCODE -ne 0) {
        throw '启动失败，请检查 Docker 输出。'
    }
    Write-Host '正在启动。默认页面：http://localhost:8081；后端日志：docker compose logs -f backend'
} finally {
    Pop-Location
}
