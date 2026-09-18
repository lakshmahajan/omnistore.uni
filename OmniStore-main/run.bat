@echo off
setlocal enabledelayedexpansion

REM OmniStore Execution Script
cd /d "%~dp0"

echo =========================================================================
echo               OMNISTORE: BUILD ^& RUN RUNNER
echo =========================================================================

REM 1. Check if javac is available on PATH
where javac >nul 2>nul
if %errorlevel% equ 0 (
    set "JAVAC_CMD=javac"
    set "JAVA_CMD=java"
    goto :COMPILE
)

REM 2. Fallback to installed JDK 21
set "JDK21_BIN=C:\Users\Lenovo\.antigravity-ide\extensions\redhat.java-1.55.0-win32-x64\jre\21.0.11-win32-x86_64\bin"
if exist "%JDK21_BIN%\javac.exe" (
    set "JAVAC_CMD=%JDK21_BIN%\javac.exe"
    set "JAVA_CMD=%JDK21_BIN%\java.exe"
    echo Using detected JDK 21: !JDK21_BIN!
    goto :COMPILE
)

set "ALT_JDK_BIN=C:\Users\Lenovo\.antigravity\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64\bin"
if exist "%ALT_JDK_BIN%\javac.exe" (
    set "JAVAC_CMD=%ALT_JDK_BIN%\javac.exe"
    set "JAVA_CMD=%ALT_JDK_BIN%\java.exe"
    echo Using detected JDK 21: !ALT_JDK_BIN!
    goto :COMPILE
)

echo [ERROR] Could not find javac.exe. Please ensure JDK 17+ is installed.
pause
exit /b 1

:COMPILE
if not exist "bin" mkdir "bin"

echo Compiling Java source files...
dir /s /b "src\*.java" > sources.txt
"%JAVAC_CMD%" -d bin @sources.txt
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed.
    if exist sources.txt del sources.txt
    pause
    exit /b %errorlevel%
)
if exist sources.txt del sources.txt

echo.
echo Compilation successful. Running com.omnistore.Main...
echo.
"%JAVA_CMD%" -cp bin com.omnistore.Main
echo.
pause

