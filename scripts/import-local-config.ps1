param(
    [Parameter(Mandatory=$true)]
    [string]$From
)
$ErrorActionPreference = "Stop"

foreach ($name in @("local.properties", "keystore.properties")) {
    $src = Join-Path $From $name
    if (Test-Path $src) {
        Copy-Item $src ".\$name" -Force
        Write-Host "Copied $name"
    }
}

$ks = Get-ChildItem -Path $From -File -ErrorAction SilentlyContinue | Where-Object {
    $_.Extension -in @(".jks", ".keystore")
}
foreach ($file in $ks) {
    Copy-Item $file.FullName ".\$($file.Name)" -Force
    Write-Host "Copied signing file: $($file.Name)"
}
