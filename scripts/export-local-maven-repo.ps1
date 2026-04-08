param(
    [switch]$SkipZip = $false
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

function Resolve-GradleInvocation {
    param(
        [string]$RepoRoot
    )

    $wrapperBat = Join-Path $RepoRoot "gradlew.bat"
    if (Test-Path $wrapperBat) {
        return @{
            Command = $wrapperBat
            PrefixArgs = @()
        }
    }

    $wrapperJar = Join-Path $RepoRoot "gradle\wrapper\gradle-wrapper.jar"
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java -and (Test-Path $wrapperJar)) {
        return @{
            Command = $java.Source
            PrefixArgs = @("-classpath", $wrapperJar, "org.gradle.wrapper.GradleWrapperMain")
        }
    }

    $wrapperShell = Join-Path $RepoRoot "gradlew"
    $bash = Get-Command bash -ErrorAction SilentlyContinue
    if ($bash -and (Test-Path $wrapperShell)) {
        return @{
            Command = $bash.Source
            PrefixArgs = @($wrapperShell)
        }
    }

    $gradle = Get-Command gradle -ErrorAction SilentlyContinue
    if ($gradle) {
        return @{
            Command = $gradle.Source
            PrefixArgs = @()
        }
    }

    throw "未找到可用的 Gradle 命令。请安装 Java/Gradle，或者先在本机跑通 wrapper。"
}

$gradleArgs = @("exportLocalMavenRepo", "--offline")
if (-not $SkipZip) {
    $gradleArgs = @("zipLocalMavenRepo", "--offline")
}

$invocation = Resolve-GradleInvocation -RepoRoot $repoRoot
Write-Host "Running Gradle command: $($invocation.Command) $((@($invocation.PrefixArgs) + $gradleArgs) -join ' ')" -ForegroundColor Cyan

Push-Location $repoRoot
try {
    & $invocation.Command @($invocation.PrefixArgs + $gradleArgs)
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle exited with code $LASTEXITCODE"
    }

    $repoDir = Join-Path $repoRoot "build\\local-maven-repo"
    $zipDir = Join-Path $repoRoot "build\\distributions"

    Write-Host "Local Maven repo exported to: $repoDir" -ForegroundColor Green
    if (-not $SkipZip) {
        Write-Host "Zip bundle exported to: $zipDir" -ForegroundColor Green
    }
} finally {
    Pop-Location
}
