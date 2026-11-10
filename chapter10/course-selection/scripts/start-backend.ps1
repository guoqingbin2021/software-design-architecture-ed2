. (Join-Path $PSScriptRoot 'environment.ps1')
Push-Location (Join-Path $ProjectRoot 'backend')
try {
    & $env:MAVEN_CMD -s maven-settings.xml -B -ntp -DskipTests package
    Assert-Exit 'Backend package'
    & java -jar target/course-selection-1.0.0.jar
    Assert-Exit 'Backend application'
} finally {
    Pop-Location
}
