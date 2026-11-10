param([string]$JdkHome = '')
$ErrorActionPreference = 'Stop'
if (-not $JdkHome) {
    $JdkHome = $env:JDK_HOME
}
if (-not $JdkHome) {
    $JdkHome = $env:JAVA_HOME
}
if ($JdkHome) {
    $taskJava = Join-Path $JdkHome 'bin\java.exe'
    $taskJavac = Join-Path $JdkHome 'bin\javac.exe'
    if (-not (Test-Path -LiteralPath $taskJava) -or -not (Test-Path -LiteralPath $taskJavac)) {
        throw '请指定同时包含java和javac的JDK目录。'
    }
} else {
    $taskJava = (Get-Command java -ErrorAction Stop).Source
    $taskJavac = (Get-Command javac -ErrorAction Stop).Source
}

function Invoke-Program([string]$taskProgram, [string[]]$taskArguments) {
    $taskPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $taskOutput = @(& $taskProgram @taskArguments 2>&1)
        $taskExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $taskPreference
    }
    $taskOutput | ForEach-Object { Write-Output $_.ToString() }
    if ($taskExit -ne 0) {
        throw "运行失败，退出码：$taskExit"
    }
}

$taskTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$taskRun = Join-Path $taskTempRoot ('chapter6-' + [Guid]::NewGuid().ToString('N'))
$taskTeacher = Join-Path $taskRun 'teacher'

New-Item -ItemType Directory -Path $taskTeacher -Force | Out-Null
Push-Location -LiteralPath $PSScriptRoot
try {
    Invoke-Program $taskJavac @('-version')
    Invoke-Program $taskJavac @('--release', '17', '-encoding', 'UTF-8', '-d', $taskTeacher,
        'Chapter6BodyReference.java', 'TextbookCore.java', 'Chapter6SupportingExamples.java')
    Invoke-Program $taskJava @('-Dfile.encoding=UTF-8', '-cp', $taskTeacher, 'TextbookCore')
    Invoke-Program $taskJava @('-Dfile.encoding=UTF-8', '-cp', $taskTeacher, 'Chapter6BodyReference')
    Invoke-Program $taskJava @('-Dfile.encoding=UTF-8', '-cp', $taskTeacher, 'Chapter6SupportingExamples')
    Write-Output '运行完成。'
} finally {
    Pop-Location
    # 只清理本次创建的临时编译目录。
    $taskResolvedRun = [IO.Path]::GetFullPath($taskRun)
    $taskResolvedParent = [IO.Path]::GetDirectoryName($taskResolvedRun).TrimEnd('\')
    if ($taskResolvedParent.Equals($taskTempRoot.TrimEnd('\'), [StringComparison]::OrdinalIgnoreCase) -and
        [IO.Path]::GetFileName($taskResolvedRun) -match '^chapter6-[0-9a-f]{32}$') {
        Remove-Item -LiteralPath $taskResolvedRun -Recurse -Force
    }
}
