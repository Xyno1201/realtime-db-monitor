@echo off
rem Usage (from cmd or the Antigravity/PowerShell terminal):
rem   .\run.cmd server       - the server (poller + TCP server on port 5050)
rem   .\run.cmd admin        - the Admin window (operator console: alerts, changes, rules, log)
rem   .\run.cmd employee     - the Employee app (product catalogue: add / edit / delete)
rem   .\run.cmd client       - the Swing dashboard (live alert cards; run several)
rem   .\run.cmd dashboard    - text-only test dashboard (test\ConsoleDashboard)
rem   .\run.cmd checks       - all 4 checks: Phase12, Dao, Rule, Detection (run with the server STOPPED)
cd /d "%~dp0"
if not exist out (
    echo Not built yet - run .\build.cmd first.
    exit /b 1
)
if /i "%~1"=="server" (
    java -cp "out;lib/*" dbmonitor.server.ServerMain
    goto :eof
)
if /i "%~1"=="admin" (
    java -cp "out;lib/*" dbmonitor.admin.AdminMain
    goto :eof
)
if /i "%~1"=="employee" (
    java -cp "out;lib/*" dbmonitor.employee.EmployeeMain
    goto :eof
)
if /i "%~1"=="client" (
    java -cp "out;lib/*" dbmonitor.client.ClientMain
    goto :eof
)
if /i "%~1"=="dashboard" (
    java -cp "out;lib/*" ConsoleDashboard
    goto :eof
)
if /i "%~1"=="checks" (
    java -cp "out;lib/*" Phase12Check
    echo.
    java -cp "out;lib/*" DaoCheck
    echo.
    java -cp "out;lib/*" RuleCheck
    echo.
    java -cp "out;lib/*" DetectionCheck
    goto :eof
)
echo Usage: .\run.cmd server ^| admin ^| employee ^| client ^| dashboard ^| checks
