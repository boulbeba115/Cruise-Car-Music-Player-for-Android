<#
.SYNOPSIS
    Builds Cruise, starts the "car head unit" emulator (1280x720 landscape, Android 11) and opens the app.

.EXAMPLE
    .\scripts\run-emulator.ps1            # debug build
    .\scripts\run-emulator.ps1 -NoBuild   # reinstall the last build only
#>
param(
    [switch]$NoBuild,
    [string]$Avd = "Cruise_HeadUnit_1280x720"
)

# Native tools (adb, gradle) print progress on stderr; Windows PowerShell would turn that into errors
# under "Stop", so failures are detected through exit codes instead.
$ErrorActionPreference = "Continue"
$env:JAVA_HOME = "D:\dev\android-studio\jbr"
$env:ANDROID_HOME = "D:\dev\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$emulator = Join-Path $env:ANDROID_HOME "emulator\emulator.exe"
$root = Split-Path $PSScriptRoot -Parent

if (-not (Test-Path $emulator)) { throw "Emulator not installed in $env:ANDROID_HOME (see docs/toolchain.md)." }

if (-not $NoBuild) {
    Push-Location $root
    try { & .\gradlew.bat :app:assembleDebug --console=plain; if ($LASTEXITCODE) { throw "Build failed." } }
    finally { Pop-Location }
}

& $adb start-server 2>$null | Out-Null
$running = & $adb devices | Select-String "emulator-\d+\s+device"
if (-not $running) {
    Write-Host "Starting emulator $Avd ..."
    Start-Process $emulator -ArgumentList "-avd", $Avd, "-no-boot-anim", "-gpu", "auto"
    & $adb wait-for-device
    do { Start-Sleep -Seconds 2 } until ((& $adb shell getprop sys.boot_completed 2>$null) -match "1")
}

$apk = Join-Path $root "app\build\outputs\apk\debug\app-x86_64-debug.apk"
Write-Host "Installing $apk ..."
& $adb install -r $apk
if ($LASTEXITCODE) { throw "Install failed." }
& $adb shell am start -n "com.boulbeba.cruise.debug/com.boulbeba.cruise.MainActivity"
Write-Host "Cruise is running in the emulator."
