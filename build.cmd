@echo off
rem Builds everything into out\  (Windows - works from cmd or the Antigravity/PowerShell terminal: .\build.cmd)
cd /d "%~dp0"
if exist out rmdir /s /q out
javac -encoding US-ASCII -d out -cp "lib/*" src\dbmonitor\common\*.java src\dbmonitor\db\*.java src\dbmonitor\rules\*.java src\dbmonitor\server\*.java src\dbmonitor\admin\*.java src\dbmonitor\employee\*.java src\dbmonitor\client\*.java test\*.java
if errorlevel 1 (
    echo.
    echo BUILD FAILED - see the errors above.
    exit /b 1
)
echo BUILD OK
