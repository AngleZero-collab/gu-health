param([switch]$SkipTests, [switch]$SkipLint, [switch]$BuildDeviceTest, [string]$ModelPath)
$ErrorActionPreference = 'Stop'
$sourceRoot = $PSScriptRoot
$apkVariant = 'basic'
$modelArgument = @()
if ($ModelPath) {
    $resolvedModel = (Resolve-Path -LiteralPath $ModelPath).ProviderPath
    $modelArgument = @("-PguhealthModel=$resolvedModel")
    $apkVariant = 'local-ai-arm64'
}
$jdkCandidates = @($env:JAVA_HOME, "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2")
$selectedJdk = $null
foreach ($jdkCandidate in $jdkCandidates) {
    if ($jdkCandidate -and (Test-Path -LiteralPath "$jdkCandidate\bin\javac.exe")) {
        $jdkVersion = (& "$jdkCandidate\bin\javac.exe" -version 2>&1 | Out-String)
        if ($jdkVersion -match 'javac (17|18|19|20|21|22|23|24)\.') { $selectedJdk = $jdkCandidate; break }
    }
}
if (-not $selectedJdk) { throw 'Please install JDK 17-24 (recommended: JDK 21) and set JAVA_HOME.' }
$env:JAVA_HOME = $selectedJdk
$stageRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('guhealth-native-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stageRoot | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stageRoot 'app') | Out-Null
foreach ($entry in @('settings.gradle','build.gradle','gradle.properties','gradlew','gradlew.bat','gradle')) {
    Copy-Item -LiteralPath (Join-Path $sourceRoot $entry) -Destination $stageRoot -Recurse
}
Copy-Item -LiteralPath (Join-Path $sourceRoot 'app\src') -Destination (Join-Path $stageRoot 'app') -Recurse
Copy-Item -LiteralPath (Join-Path $sourceRoot 'app\build.gradle') -Destination (Join-Path $stageRoot 'app')
if (Test-Path -LiteralPath (Join-Path $sourceRoot 'local.properties')) {
    Copy-Item -LiteralPath (Join-Path $sourceRoot 'local.properties') -Destination $stageRoot
} elseif (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
$tasks = @(':app:assembleDebug')
if (-not $SkipTests) { $tasks += ':app:testDebugUnitTest' }
if (-not $SkipLint) { $tasks += ':app:lintDebug' }
if ($BuildDeviceTest) { $tasks += ':app:assembleDebugAndroidTest' }
Write-Output "Building Android app in: $stageRoot"
Push-Location $stageRoot
try {
    & .\gradlew.bat @tasks @modelArgument --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Android build failed. Reports remain in $stageRoot\app\build\reports" }
} finally { Pop-Location }
$distRoot = Join-Path $sourceRoot 'dist'
New-Item -ItemType Directory -Path $distRoot -Force | Out-Null
$apk = Join-Path $distRoot "GuHealth-Android-2.1-$apkVariant-debug.apk"
Copy-Item -LiteralPath (Join-Path $stageRoot 'app\build\outputs\apk\debug\app-debug.apk') -Destination $apk -Force
if ($BuildDeviceTest) {
    Copy-Item -LiteralPath (Join-Path $stageRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk') `
        -Destination (Join-Path $distRoot "GuHealth-Android-2.1-$apkVariant-device-test.apk") -Force
}
foreach ($folder in @('reports','screenshots','test-results')) {
    $generated = Join-Path $stageRoot "app\build\$folder"
    if (Test-Path -LiteralPath $generated) { Copy-Item -LiteralPath $generated -Destination $distRoot -Recurse -Force }
}
$bundleRoot = Join-Path $stageRoot 'source-package\GuHealth-Android'
New-Item -ItemType Directory -Path (Join-Path $bundleRoot 'app') -Force | Out-Null
foreach ($entry in @('settings.gradle','build.gradle','gradle.properties','gradlew','gradlew.bat','gradle','README.md','SOURCES.md','build-android.ps1','.gitignore')) {
    Copy-Item -LiteralPath (Join-Path $sourceRoot $entry) -Destination $bundleRoot -Recurse
}
Copy-Item -LiteralPath (Join-Path $sourceRoot 'app\src') -Destination (Join-Path $bundleRoot 'app') -Recurse
Copy-Item -LiteralPath (Join-Path $sourceRoot 'app\build.gradle') -Destination (Join-Path $bundleRoot 'app')
$sourceZip = Join-Path $stageRoot 'GuHealth-Android-Source.zip'
Compress-Archive -LiteralPath $bundleRoot -DestinationPath $sourceZip
Copy-Item -LiteralPath $sourceZip -Destination $distRoot -Force
Write-Output "APK ready: $apk"
Get-FileHash -LiteralPath $apk -Algorithm SHA256
