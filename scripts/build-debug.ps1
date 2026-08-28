$ErrorActionPreference = "Stop"

if (-not (Test-Path ".\local.properties")) {
    Write-Host "local.properties 不存在。请创建并填写 sdk.dir，例如："
    Write-Host "sdk.dir=C:/Users/yourname/AppData/Local/Android/Sdk"
    exit 1
}

.\gradlew.bat clean assembleDebug

$apk = ".\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    Write-Host "BUILD OK: $apk"
} else {
    throw "构建结束但未找到 APK: $apk"
}
