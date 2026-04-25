param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$coreRoot = Join-Path $RepoRoot "agent-core\src\main\cpp"
$blockedTokens = @(
    "schedule_item",
    "schedule_list",
    "business_grid"
)

if (-not (Test-Path $coreRoot)) {
    throw "Core source directory not found: $coreRoot"
}

$files = Get-ChildItem -Path $coreRoot -Recurse -File |
    Where-Object { $_.Extension -in @(".cpp", ".cc", ".cxx", ".h", ".hpp", ".ipp") }

$matches = @()
foreach ($file in $files) {
    foreach ($token in $blockedTokens) {
        $hits = Select-String -Path $file.FullName -Pattern ([regex]::Escape($token)) -SimpleMatch
        foreach ($hit in $hits) {
            $matches += [pscustomobject]@{
                Token = $token
                File = $hit.Path
                Line = $hit.LineNumber
                Text = $hit.Line.Trim()
            }
        }
    }
}

if ($matches.Count -gt 0) {
    Write-Host "Detected business-specific role regressions in agent-core:" -ForegroundColor Red
    $matches |
        Sort-Object Token, File, Line |
        ForEach-Object {
            Write-Host ("[{0}] {1}:{2} {3}" -f $_.Token, $_.File, $_.Line, $_.Text) -ForegroundColor Red
        }
    exit 1
}

Write-Host "No blocked business-specific role tokens found in agent-core." -ForegroundColor Green
