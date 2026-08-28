$ErrorActionPreference = "Stop"
Write-Host "正在监听 PulseRelay。Ctrl+C 退出。"
adb logcat -v time PulseRelay:D '*:S'
