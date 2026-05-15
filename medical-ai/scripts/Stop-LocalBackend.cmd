@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "SCRIPT_PATH=%SCRIPT_DIR%Stop-LocalBackend.ps1"

if not exist "%SCRIPT_PATH%" (
    echo Stop script not found: "%SCRIPT_PATH%"
    pause
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_PATH%" %*
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo Stop-LocalBackend failed with exit code %EXIT_CODE%.
    pause
)

exit /b %EXIT_CODE%
