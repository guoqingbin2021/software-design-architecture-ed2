param([switch]$Integration)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $projectPath 'frontend')
try {
    npm.cmd ci
    if ($LASTEXITCODE -ne 0) {
        throw '前端依赖安装失败。'
    }
    npm.cmd test
    if ($LASTEXITCODE -ne 0) {
        throw '前端测试失败。'
    }
    npm.cmd run build
    if ($LASTEXITCODE -ne 0) {
        throw '前端构建失败。'
    }
} finally {
    Pop-Location
}
Push-Location (Join-Path $projectPath 'backend')
try {
    if ($Integration) {
        mvn.cmd verify
    } else {
        mvn.cmd test
    }
    if ($LASTEXITCODE -ne 0) {
        throw '后端检查失败，请查看 Maven 输出。'
    }
} finally {
    Pop-Location
}
