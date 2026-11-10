# 习题10-10：在临时项目副本运行，原工程不写入编译产物或补充测试。
param([string]$MavenCommand = 'mvn', [string]$WorkDirectory)
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\course-selection'))
if (!(Test-Path -LiteralPath (Join-Path $project 'backend\pom.xml'))) {
    throw 'Missing chapter10 project'
}
if ($env:TEST_DB_URL -notmatch '/course_test(?:\?|$)') {
    throw 'Set TEST_DB_URL to a dedicated course_test database first'
}
if ($env:DB_URL -and $env:DB_URL -eq $env:TEST_DB_URL) {
    throw 'Development and test database must differ'
}
if (!$env:TEST_DB_USER -or !$env:TEST_DB_PASSWORD) {
    throw 'Set TEST_DB_USER and TEST_DB_PASSWORD for the dedicated test database'
}
if (!$WorkDirectory) {
    $WorkDirectory = Join-Path ([IO.Path]::GetTempPath()) ('chapter10-answer-' + [Guid]::NewGuid().ToString('N'))
}
$destination = [IO.Path]::GetFullPath($WorkDirectory)
if (Test-Path -LiteralPath $destination) {
    throw 'WorkDirectory must be new'
}
New-Item -ItemType Directory -Path $destination | Out-Null
Copy-Item -LiteralPath (Join-Path $project 'backend') -Destination (Join-Path $destination 'backend') -Recurse
$testDir = Join-Path $destination 'backend\src\test\java\edu\textbook\selection'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'HistoryIsolationAnswerTest.java') -Destination $testDir
& $MavenCommand -s (Join-Path $destination 'backend\maven-settings.xml') -f (Join-Path $destination 'backend\pom.xml') -B -ntp '-Dtest=CourseSelectionIntegrationTest,HistoryIsolationAnswerTest#retryingOldDeleteDoesNotWithdrawNewEnrollment' test
if ($LASTEXITCODE -ne 0) {
    throw "Tests failed. Inspect $destination"
}
Write-Output ('JUnit reports: ' + (Join-Path $destination 'backend\target\surefire-reports'))
Write-Output 'Use invariants.sql for independent data checks; keep HTTP responses and database evidence.'
