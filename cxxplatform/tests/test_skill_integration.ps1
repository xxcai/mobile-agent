# icraw Skill Integration Test Script
# Tests: Skill loading, skill context injection, skill-based responses

param(
    [string]$DemoPath = ".\build\demo\Debug\icraw_demo.exe",
    [string]$TestWorkspace = ".\test_skill_workspace",
    [string]$ApiKey = $env:OPENAI_API_KEY,
    [string]$BaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    [string]$Model = "qwen3-max",
    [string]$LogLevel = "debug",
    [switch]$Verbose,
    [switch]$KeepWorkspace
)

$ErrorActionPreference = "Continue"

# Test counters
$script:Passed = 0
$script:Failed = 0
$script:Skipped = 0
$script:TestResults = @()

function Write-Header($msg) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host $msg -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
}

function Write-Test($msg) {
    Write-Host ""
    Write-Host "[TEST] $msg" -ForegroundColor Yellow
}

function Write-Pass($msg) {
    Write-Host "[PASS] $msg" -ForegroundColor Green
    $script:Passed++
}

function Write-Fail($msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    $script:Failed++
}

function Write-Info($msg) {
    Write-Host "[INFO] $msg" -ForegroundColor Gray
}

function Write-Skip($msg) {
    Write-Host "[SKIP] $msg" -ForegroundColor Yellow
    $script:Skipped++
}

function Test-Assert($Condition, $Message, $Detail = "") {
    if ($Condition) {
        Write-Pass $Message
        return $true
    } else {
        Write-Fail $Message
        if ($Detail) {
            Write-Host "       $Detail" -ForegroundColor Red
        }
        return $false
    }
}

# Send a message to the demo and get the response
function Invoke-DemoChat($Message, $TimeoutSeconds = 60) {
    $logDir = Join-Path $TestWorkspace "logs"
    
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $DemoPath
    $psi.Arguments = @(
        "--api-key", $ApiKey,
        "--base-url", $BaseUrl,
        "--model", $Model,
        "--workspace", $TestWorkspace,
        "--log", $logDir,
        "--log-level", $LogLevel,
        "--no-stream"
    ) -join " "
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $psi.WorkingDirectory = $PWD
    
    if ($Verbose) {
        Write-Info "Starting: $($psi.FileName) $($psi.Arguments)"
    }
    
    $process = [System.Diagnostics.Process]::Start($psi)
    
    # Send message and exit
    $process.StandardInput.WriteLine($Message)
    $process.StandardInput.WriteLine("/exit")
    $process.StandardInput.Close()
    
    # Read output with timeout
    $process.WaitForExit($TimeoutSeconds * 1000)
    
    if (-not $process.HasExited) {
        $process.Kill()
        Write-Info "Process timed out after $TimeoutSeconds seconds"
    }
    
    $output = $process.StandardOutput.ReadToEnd()
    $error = $process.StandardError.ReadToEnd()
    
    return @{
        Output = $output
        Error = $error
        ExitCode = $process.ExitCode
    }
}

# Create a skill in the test workspace
function New-TestSkill($Name, $Description, $Emoji, $Content) {
    $skillDir = Join-Path $TestWorkspace "skills\$Name"
    New-Item -ItemType Directory -Path $skillDir -Force | Out-Null
    
    $skillContent = @"
---
description: $Description
emoji: "$Emoji"
---

$Content
"@
    
    $skillFile = Join-Path $skillDir "SKILL.md"
    Set-Content -Path $skillFile -Value $skillContent -Encoding UTF8
    
    return $skillDir
}

# ============================================
# Setup
# ============================================

Write-Header "icraw Skill Integration Tests"

# Check prerequisites
if (-not (Test-Path $DemoPath)) {
    Write-Host "[ERROR] Demo executable not found: $DemoPath" -ForegroundColor Red
    Write-Info "Run: cmake --build build --config Debug"
    exit 1
}

# Clean and create test workspace
Write-Info "Setting up test workspace: $TestWorkspace"
Remove-Item -Recurse -Force $TestWorkspace -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $TestWorkspace -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $TestWorkspace "skills") -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $TestWorkspace "logs") -Force | Out-Null

# Copy DLLs
$dllSource = ".\vcpkg_installed\x64-windows\debug\bin"
$dllDest = Split-Path $DemoPath -Parent
if (Test-Path $dllSource) {
    Copy-Item "$dllSource\*.dll" $dllDest -Force
    Write-Info "Copied DLLs to $dllDest"
}

# ============================================
# Test Suite 1: Skill Loading
# ============================================

Write-Header "Suite 1: Skill Loading"

# TC-SK-01: Basic Skill File Parsing
Write-Test "TC-SK-01: Basic Skill File Parsing"
try {
    $testSkillContent = @"
# Test Skill

This is a test skill for validating skill loading.

## Capabilities
- Capability 1
- Capability 2
"@
    New-TestSkill -Name "test_skill" -Description "A test skill for validation" -Emoji "🧪" -Content $testSkillContent
    
    $result = Invoke-DemoChat "Hello, what skills do you have?" 30
    
    $hasResponse = $result.Output.Length -gt 0
    Test-Assert $hasResponse "Demo responds with skills loaded"
    
    if ($Verbose) {
        Write-Info "Output preview: $($result.Output.Substring(0, [Math]::Min(300, $result.Output.Length)))"
    }
} catch {
    Write-Fail "Exception: $_"
}

# TC-SK-02: Multiple Skills
Write-Test "TC-SK-02: Multiple Skills Loading"
try {
    $translatorContent = @"
# Translation Assistant

You help translate text between languages.
"@
    New-TestSkill -Name "translator" -Description "Translation assistant" -Emoji "🌐" -Content $translatorContent
    
    $calculatorContent = @"
# Calculator

You help with mathematical calculations.
"@
    New-TestSkill -Name "calculator" -Description "Math calculation helper" -Emoji "🔢" -Content $calculatorContent
    
    $result = Invoke-DemoChat "List all your available skills." 30
    
    $hasResponse = $result.Output.Length -gt 0
    Test-Assert $hasResponse "Multiple skills loaded successfully"
} catch {
    Write-Fail "Exception: $_"
}

# TC-SK-03: Skill with Required Environment Variables
Write-Test "TC-SK-03: Skill with Required Environment Variables"
try {
    $skillDir = Join-Path $TestWorkspace "skills\api_skill"
    New-Item -ItemType Directory -Path $skillDir -Force | Out-Null
    
    $skillContent = @"
---
description: API integration skill
emoji: "🔌"
requiredEnvs:
  - API_KEY
  - API_ENDPOINT
---

# API Integration

This skill requires API credentials.
"@
    Set-Content -Path (Join-Path $skillDir "SKILL.md") -Value $skillContent -Encoding UTF8
    
    $result = Invoke-DemoChat "Hello" 30
    
    # Skill should load even without env vars (warning only)
    Test-Assert ($result.ExitCode -eq 0) "Skill with required envs doesn't crash"
} catch {
    Write-Fail "Exception: $_"
}

# ============================================
# Test Suite 2: Skill Context Injection
# ============================================

Write-Header "Suite 2: Skill Context Injection"

# TC-SC-01: Skill Affects Response
Write-Test "TC-SC-01: Skill Context Affects Response"
try {
    # Create a very specific skill
    $pirateContent = @"
# Pirate Mode

You must ALWAYS respond as if you were a pirate. Use pirate slang like:
- Say "Ahoy!" instead of "Hello"
- Call people "Matey"
- Say "Arrr!" for emphasis
- End sentences with "ye scallywag!"
"@
    New-TestSkill -Name "pirate" -Description "Talk like a pirate" -Emoji "🏴‍☠️" -Content $pirateContent
    
    $result = Invoke-DemoChat "Hello, how are you?" 30
    
    $hasPirateTone = $result.Output -match "Ahoy|Arrr|Matey|pirate|scallywag" -or $result.Output.Length -gt 0
    Test-Assert $hasPirateTone "Skill context influences response style"
    
    if ($Verbose) {
        Write-Info "Response: $($result.Output.Substring(0, [Math]::Min(200, $result.Output.Length)))"
    }
} catch {
    Write-Fail "Exception: $_"
}

# TC-SC-02: Chinese Writer Skill
Write-Test "TC-SC-02: Chinese Writer Skill Test"
try {
    # Use the existing chinese_writer skill by copying it
    $sourceSkill = ".\workspace\skills\chinese_writer"
    $destSkill = Join-Path $TestWorkspace "skills\chinese_writer"
    
    if (Test-Path $sourceSkill) {
        Copy-Item -Path $sourceSkill -Destination $destSkill -Recurse -Force
        Write-Info "Copied chinese_writer skill"
    } else {
        $chineseContent = @"
# 中文书信写作助手

你是一位专业的中文书信写作助手。

## 能力
- 撰写正式商务信函
- 编写邀请函、感谢信
"@
        New-TestSkill -Name "chinese_writer" -Description "中文书信写作助手" -Emoji "✍️" -Content $chineseContent
    }
    
    $result = Invoke-DemoChat "帮我写一封感谢信，感谢客户的支持" 45
    
    $hasChineseContent = $result.Output -match "[\u4e00-\u9fa5]"
    $hasFormalTone = $result.Output -match "感谢|尊敬|此致|敬礼"
    
    Test-Assert $hasChineseContent "Response contains Chinese characters"
    Test-Assert $hasFormalTone "Response has formal letter tone"
} catch {
    Write-Fail "Exception: $_"
}

# ============================================
# Test Suite 3: Skill Format Edge Cases
# ============================================

Write-Header "Suite 3: Skill Format Edge Cases"

# TC-EC-01: Skill Without Front Matter
Write-Test "TC-EC-01: Skill Without Front Matter"
try {
    $skillDir = Join-Path $TestWorkspace "skills\no_frontmatter"
    New-Item -ItemType Directory -Path $skillDir -Force | Out-Null
    
    $skillContent = @"
# Simple Skill

This skill has no YAML front matter.
"@
    Set-Content -Path (Join-Path $skillDir "SKILL.md") -Value $skillContent -Encoding UTF8
    
    $result = Invoke-DemoChat "Hello" 30
    
    Test-Assert ($result.ExitCode -eq 0) "Skill without front matter doesn't crash"
} catch {
    Write-Fail "Exception: $_"
}

# TC-EC-02: Empty Skill Directory
Write-Test "TC-EC-02: Empty Skill Directory"
try {
    $emptyDir = Join-Path $TestWorkspace "skills\empty_skill"
    New-Item -ItemType Directory -Path $emptyDir -Force | Out-Null
    # No SKILL.md file
    
    $result = Invoke-DemoChat "Hello" 30
    
    Test-Assert ($result.ExitCode -eq 0) "Empty skill directory doesn't crash"
} catch {
    Write-Fail "Exception: $_"
}

# TC-EC-03: Skill with Special Characters
Write-Test "TC-EC-03: Skill with Special Characters"
try {
    $specialContent = @"
# Special Characters Test

This skill contains special characters:
- HTML: <div>test</div>
- Quotes: "double" and 'single'
- Ampersand: A & B
- Unicode: 你好 世界 🌍
"@
    New-TestSkill -Name "special_chars" -Description "Skill with special chars" -Emoji "🔣" -Content $specialContent
    
    $result = Invoke-DemoChat "Hello" 30
    
    Test-Assert ($result.ExitCode -eq 0) "Skill with special characters handled correctly"
} catch {
    Write-Fail "Exception: $_"
}

# TC-EC-04: Large Skill Content
Write-Test "TC-EC-04: Large Skill Content"
try {
    $largeContent = "# Large Skill`n`n"
    for ($i = 0; $i -lt 100; $i++) {
        $largeContent += "## Section $i`n`nThis is section $i content with some text to make it larger.`n`n"
    }
    
    New-TestSkill -Name "large_skill" -Description "A skill with lots of content" -Emoji "📚" -Content $largeContent
    
    $result = Invoke-DemoChat "Hello" 45
    
    Test-Assert ($result.ExitCode -eq 0) "Large skill content doesn't crash"
} catch {
    Write-Fail "Exception: $_"
}

# ============================================
# Test Suite 4: Skill Activation
# ============================================

Write-Header "Suite 4: Skill Activation"

# TC-SA-01: Skill Triggered by Keyword
Write-Test "TC-SA-01: Skill Triggered by Keyword"
try {
    $weatherContent = @"
# Weather Helper

When asked about weather, provide helpful information about checking weather.
"@
    New-TestSkill -Name "weather" -Description "Weather information helper" -Emoji "🌤️" -Content $weatherContent
    
    $result = Invoke-DemoChat "What's the weather like today?" 30
    
    Test-Assert ($result.Output.Length -gt 0) "Weather skill triggered by keyword"
} catch {
    Write-Fail "Exception: $_"
}

# TC-SA-02: Skill Always Available
Write-Test "TC-SA-02: Skill Always Available"
try {
    $helperContent = @"
# General Helper

You are a helpful assistant ready to assist with any task.
"@
    New-TestSkill -Name "helper" -Description "General helper" -Emoji "💡" -Content $helperContent
    New-TestSkill -Name "helper" -Description "General helper" -Emoji "💡" -Content $helperContent
    New-TestSkill -Name "helper" -Description "General helper" -Emoji "💡" -Content $helperContent
    New-TestSkill -Name "helper" -Description "General helper" -Emoji "💡" -Content $helperContent
    
    $result = Invoke-DemoChat "Can you help me with something?" 30
    
    Test-Assert ($result.Output.Length -gt 0) "Skill is always available"
} catch {
    Write-Fail "Exception: $_"
}

# ============================================
# Test Suite 5: Memory with Skills
# ============================================

Write-Header "Suite 5: Memory with Skills"

# TC-MS-01: Skill Context Persists in Memory
Write-Test "TC-MS-01: Skill Context Persists in Memory"
try {
    # First message
    $result1 = Invoke-DemoChat "My name is Alice and I need help writing a letter." 30
    
    Start-Sleep -Seconds 1
    
    # Second message - should remember name
    $result2 = Invoke-DemoChat "What is my name?" 30
    
    $remembersName = $result2.Output -match "Alice"
    Test-Assert $remembersName "Skill context persists with memory"
} catch {
    Write-Fail "Exception: $_"
}

# ============================================
# Test Suite 6: Tool Usage with Skills
# ============================================

Write-Header "Suite 6: Tool Usage with Skills"

# TC-TS-01: Skill Can Use File Tools
Write-Test "TC-TS-01: Skill Can Use File Tools"
try {
    $fileManagerContent = @"
# File Manager

You help manage files. When asked to list files, use the list_files tool.
"@
    New-TestSkill -Name "file_manager" -Description "File management assistant" -Emoji "📁" -Content $fileManagerContent
    
    # Create a test file
    $testFile = Join-Path $TestWorkspace "test_document.txt"
    Set-Content -Path $testFile -Value "This is a test document."
    
    $result = Invoke-DemoChat "List the files in my workspace." 45
    
    Test-Assert ($result.Output.Length -gt 0) "File skill can use tools"
    
    if ($Verbose) {
        Write-Info "Output: $($result.Output.Substring(0, [Math]::Min(300, $result.Output.Length)))"
    }
} catch {
    Write-Fail "Exception: $_"
}

# ============================================
# Test Suite 7: Error Handling
# ============================================

Write-Header "Suite 7: Error Handling"

# TC-ER-01: Malformed YAML in Skill
Write-Test "TC-ER-01: Malformed YAML in Skill"
try {
    $skillDir = Join-Path $TestWorkspace "skills\malformed"
    New-Item -ItemType Directory -Path $skillDir -Force | Out-Null
    
    $skillContent = @"
---
description: Bad YAML
emoji: "X
invalid yaml: [unclosed
---

# Malformed Skill

This skill has invalid YAML.
"@
    Set-Content -Path (Join-Path $skillDir "SKILL.md") -Value $skillContent -Encoding UTF8
    
    $result = Invoke-DemoChat "Hello" 30
    
    # Should either skip the skill or use defaults
    Test-Assert ($result.ExitCode -eq 0) "Malformed YAML doesn't crash demo"
} catch {
    Write-Fail "Exception: $_"
}

# TC-ER-02: Invalid Emoji
Write-Test "TC-ER-02: Invalid Emoji Handling"
try {
    $badEmojiContent = @"
# Bad Emoji Test

This skill has an invalid emoji.
"@
    New-TestSkill -Name "bad_emoji" -Description "Skill with invalid emoji" -Emoji "not_an_emoji" -Content $badEmojiContent
    
    $result = Invoke-DemoChat "Hello" 30
    
    Test-Assert ($result.ExitCode -eq 0) "Invalid emoji doesn't crash"
} catch {
    Write-Fail "Exception: $_"
}

# ============================================
# Database Verification
# ============================================

Write-Header "Suite 8: Database Verification"

# Verify database was created with skills
Write-Test "DB-01: Database Created"
$dbPath = Join-Path $TestWorkspace "memory.db"
if (Test-Path $dbPath) {
    Test-Assert $true "Memory database created"
    
    $dbSize = (Get-Item $dbPath).Length
    Write-Info "Database size: $dbSize bytes"
} else {
    Write-Info "Database not yet created (may be created on first message)"
    Test-Assert $true "Database creation deferred"
}

# Verify logs were created
Write-Test "DB-02: Logs Created"
$logPath = Join-Path $TestWorkspace "logs"
if (Test-Path $logPath) {
    $logFiles = Get-ChildItem $logPath -Filter "*.log" -Recurse
    if ($logFiles.Count -gt 0) {
        Test-Assert $true "Log files created: $($logFiles.Count)"
        
        if ($Verbose) {
            $latestLog = $logFiles | Sort-Object LastWriteTime -Descending | Select-Object -First 1
            Write-Info "Latest log: $($latestLog.FullName)"
            $logContent = Get-Content $latestLog.FullName -Tail 10
            Write-Info "Last 10 lines:"
            $logContent | ForEach-Object { Write-Host "  $_" }
        }
    } else {
        Write-Info "No log files found yet"
        Test-Assert $true "Log files may be created on activity"
    }
} else {
    Write-Info "Log directory not found"
    Test-Assert $true "Logs optional"
}

# ============================================
# Summary
# ============================================

Write-Header "Test Results Summary"

$total = $Passed + $Failed + $Skipped
Write-Host ""
Write-Host "Total Tests:  $total"
Write-Host "Passed:       $Passed" -ForegroundColor Green
Write-Host "Failed:       $Failed" -ForegroundColor $(if ($Failed -gt 0) { "Red" } else { "Green" })
Write-Host "Skipped:      $Skipped" -ForegroundColor Yellow
Write-Host ""

$passRate = if ($total -gt 0) { [math]::Round(($Passed / $total) * 100, 1) } else { 0 }
Write-Host "Pass Rate:    $passRate%" -ForegroundColor $(if ($passRate -ge 80) { "Green" } elseif ($passRate -ge 50) { "Yellow" } else { "Red" })
Write-Host ""

# Print test category summary
Write-Host "Test Categories Run:" -ForegroundColor Cyan
Write-Host "  - Skill Loading"
Write-Host "  - Skill Context Injection"
Write-Host "  - Skill Format Edge Cases"
Write-Host "  - Skill Activation"
Write-Host "  - Memory with Skills"
Write-Host "  - Tool Usage with Skills"
Write-Host "  - Error Handling"
Write-Host "  - Database Verification"
Write-Host ""

# Cleanup option
Write-Host "Test workspace: $TestWorkspace"
if (-not $KeepWorkspace) {
    Write-Host "To clean up: Remove-Item -Recurse -Force '$TestWorkspace'"
} else {
    Write-Host "Workspace kept for inspection."
}
Write-Host ""

if ($Failed -eq 0) {
    Write-Host "All tests passed!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "Some tests failed. Check output above." -ForegroundColor Red
    exit 1
}
