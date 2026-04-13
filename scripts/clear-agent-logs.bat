@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "PS_SCRIPT=%SCRIPT_DIR%clear-agent-logs.ps1"

if not exist "%PS_SCRIPT%" (
    echo PowerShell script not found: %PS_SCRIPT%
    pause
    exit /b 1
)

if "%~1"=="" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%"
) else (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%" %*
)
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo Failed to clear Android logcat. Exit code: %EXIT_CODE%
    echo If multiple devices are connected, run:
    echo   %~nx0 -Serial ^<device_serial^>
    pause
    exit /b %EXIT_CODE%
)

echo.
echo Done.
pause
exit /b 0

