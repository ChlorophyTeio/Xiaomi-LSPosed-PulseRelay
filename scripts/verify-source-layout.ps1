$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$kotlin = Join-Path $root "app\src\main\kotlin\website\xihan\pbra"
$forbidden = @(
    "LanHttpClient.kt",
    "HttpClient.kt",
    "HeartRateHttpServer.kt"
)
$bad = @()
foreach ($name in $forbidden) {
    $path = Join-Path $kotlin $name
    if (Test-Path $path) { $bad += $path }
}
if ($bad.Count -gt 0) {
    Write-Host "[FAIL] Stale HTTP files found:" -ForegroundColor Red
    $bad | ForEach-Object { Write-Host "  $_" }
    exit 1
}
if (-not (Test-Path (Join-Path $kotlin "PulseUdp.kt"))) {
    Write-Host "[FAIL] PulseUdp.kt is missing." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] PulseRelay UDP source layout is clean." -ForegroundColor Green
