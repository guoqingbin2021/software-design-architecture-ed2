@echo off
chcp 65001 >nul
powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-all.ps1" %*
set "taskExit=%errorlevel%"
echo.
if "%taskExit%"=="0" (
  echo All checks completed successfully.
) else (
  echo Run failed. Exit code: %taskExit%
)
echo Press any key to close this window.
pause >nul
exit /b %taskExit%
