# Upgrade path smoke test: install legacy APK chain on emulator (requires adb + APK files in BUILDS/)
param(
    [string]$Device = "emulator-5554",
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$builds = Join-Path (Split-Path $PSScriptRoot -Parent) "..\BUILDS"
$chain = @(
    "PROTO-1.1.4-Android.apk",
    "PROTO-1.1.5-Android.apk",
    "PROTO-1.1.6-Android.apk"
)

if (-not (Test-Path $Adb)) { Write-Error "adb not found at $Adb"; exit 1 }

$fail = 0
foreach ($apk in $chain) {
    $path = Join-Path $builds $apk
    if (-not (Test-Path $path)) {
        Write-Warning "SKIP missing $apk"
        continue
    }
    Write-Host "== Install $apk =="
    & $Adb -s $Device install -r $path
    if ($LASTEXITCODE -ne 0) { $fail++; continue }
    & $Adb -s $Device logcat -c
    & $Adb -s $Device shell am force-stop org.assistix.proto
    & $Adb -s $Device shell am start -n org.assistix.proto/org.assistix.proto.nativeapp.MainActivity
    Start-Sleep -Seconds 12
    $pid = & $Adb -s $Device shell pidof org.assistix.proto
    $crashes = & $Adb -s $Device logcat -d -b crash 2>$null | Select-String "org.assistix.proto"
    if (-not $pid) {
        Write-Host "FAIL: process died after $apk"
        $fail++
    } elseif ($crashes) {
        Write-Host "WARN: crash buffer entries after $apk"
    } else {
        Write-Host "OK: $apk running pid=$pid"
    }
}

if ($fail -gt 0) { exit 1 }
Write-Host "Upgrade path OK"
