param(
    [string]$Serial = "",
    [string]$AdbPath = "",
    [switch]$ListDevices
)

$ErrorActionPreference = "Stop"

function Resolve-AdbPath {
    param([string]$RequestedPath)

    if (-not [string]::IsNullOrWhiteSpace($RequestedPath)) {
        if (Test-Path -LiteralPath $RequestedPath) {
            return (Resolve-Path -LiteralPath $RequestedPath).Path
        }
        throw "ADB not found at requested path: $RequestedPath"
    }

    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe")
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe")
    }
    $candidates += "D:\Sur\Android\platform-tools\adb.exe"

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    $command = Get-Command "adb.exe" -ErrorAction SilentlyContinue
    if ($command -ne $null) {
        return $command.Source
    }

    throw "ADB not found. Pass -AdbPath or set ANDROID_HOME / ANDROID_SDK_ROOT."
}

function Get-OnlineDevices {
    param([string]$ResolvedAdbPath)

    $lines = & $ResolvedAdbPath devices | Select-Object -Skip 1
    $devices = @()
    foreach ($line in $lines) {
        $trimmed = $line.Trim()
        if ($trimmed -eq "") {
            continue
        }
        $parts = $trimmed -split "\s+"
        if ($parts.Count -ge 2 -and $parts[1] -eq "device") {
            $devices += $parts[0]
        }
    }
    return $devices
}

$adb = Resolve-AdbPath -RequestedPath $AdbPath
$devices = @(Get-OnlineDevices -ResolvedAdbPath $adb)

if ($ListDevices) {
    Write-Host "ADB: $adb"
    if ($devices.Count -eq 0) {
        Write-Host "No online Android devices found."
    } else {
        Write-Host "Online Android devices:"
        foreach ($device in $devices) {
            Write-Host "  $device"
        }
    }
    exit 0
}

if ($devices.Count -eq 0) {
    throw "No online Android devices found. Connect a device and try again."
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    if ($devices.Count -gt 1) {
        throw "Multiple devices found: $($devices -join ', '). Please pass -Serial <device_serial>."
    }
    $Serial = $devices[0]
}

if ($devices -notcontains $Serial) {
    if ($devices.Count -eq 1) {
        Write-Host "Requested device '$Serial' is not online. Using the only online device '$($devices[0])'."
        $Serial = $devices[0]
    } else {
        throw "Device '$Serial' is not online. Online devices: $($devices -join ', ')"
    }
}

& $adb -s $Serial logcat -c
if ($LASTEXITCODE -ne 0) {
    throw "Failed to clear logcat for device '$Serial'."
}

Write-Host "Cleared logcat for device '$Serial' using '$adb'."


