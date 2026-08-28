$ErrorActionPreference = "Stop"
$apk = ".\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) {
    throw "找不到 $apk，请先运行 .\gradlew.bat assembleDebug"
}
adb install -r $apk
if ($LASTEXITCODE -ne 0) {
    Write-Host "如果提示 INSTALL_FAILED_UPDATE_INCOMPATIBLE，说明旧模块签名不同。"
    Write-Host "可先备份配置后卸载旧版：adb uninstall website.xihan.pbra"
    exit $LASTEXITCODE
}
adb shell am force-stop com.mi.health
Write-Host "安装完成。请确认 LSPosed 作用域后重新打开小米运动健康。"
