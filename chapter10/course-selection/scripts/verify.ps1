. (Join-Path $PSScriptRoot 'environment.ps1')
if ($env:TEST_DB_URL -notmatch '/course_test(?:\?|$)') {
    throw 'TEST_DB_URL must point to the dedicated course_test database.'
}
if ($env:TEST_DB_URL -eq $env:DB_URL) {
    throw 'Development and test databases must differ.'
}
$evidenceDir = Join-Path $ProjectRoot 'evidence'
New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null
Push-Location $ProjectRoot
try {
    & java -version 2>&1 | Tee-Object -FilePath (Join-Path $evidenceDir 'pipeline-java-version.log')
    & $env:MAVEN_CMD -version 2>&1 | Tee-Object -FilePath (Join-Path $evidenceDir 'pipeline-maven-version.log')
    Assert-Exit 'Maven version'
    $nodeVersion = & node --version
    if ($nodeVersion -notmatch '^v(?:22|24)\.') {
        throw "Node.js 22 or 24 is required; got $nodeVersion"
    }
    $nodeVersion | Set-Content -LiteralPath (Join-Path $evidenceDir 'pipeline-node-version.log') -Encoding UTF8
    & $env:MAVEN_CMD -s backend/maven-settings.xml -f backend/pom.xml -B -ntp clean package 2>&1 | Tee-Object -FilePath (Join-Path $evidenceDir 'pipeline-maven.log')
    Assert-Exit 'MySQL tests and backend package'
    Push-Location frontend
    try {
        & npm ci --no-audit --no-fund 2>&1 | Tee-Object -FilePath (Join-Path $evidenceDir 'pipeline-npm-ci.log')
        Assert-Exit 'npm ci'
        & npm test 2>&1 | Tee-Object -FilePath (Join-Path $evidenceDir 'pipeline-npm-test.log')
        Assert-Exit 'Frontend tests'
        & npm run build 2>&1 | Tee-Object -FilePath (Join-Path $evidenceDir 'pipeline-npm-build.log')
        Assert-Exit 'Frontend production build'
    } finally {
        Pop-Location
    }
    'PASS: backend, frontend tests and builds.' | Tee-Object -FilePath (Join-Path $evidenceDir 'pipeline-result.txt')
} finally {
    Pop-Location
}
