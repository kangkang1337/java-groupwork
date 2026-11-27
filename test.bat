@echo off
setlocal
cd /d "%~dp0"

rem =====================================================================
rem  Run every self-check suite. Exits with code 0 only if all pass.
rem =====================================================================
set "JDK=C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JDK=%JAVA_HOME%"

if not exist "%JDK%\bin\java.exe" (
    echo [ERROR] JDK 17 not found. Edit the JDK path at the top of test.bat.
    exit /b 1
)

call "%~dp0build.bat"
if errorlevel 1 (
    echo [ERROR] Build failed.
    exit /b 1
)

set "FAILED=0"
call :check DataCheck
call :check PlayCheck
call :check AiCheck
call :check ReviewCheck
call :check NetCheck
call :check RealDataCheck
call :check RateCheck

echo.
if "%FAILED%"=="0" (
    echo ==================================================
    echo  ALL CHECKS PASSED
    echo ==================================================
    exit /b 0
) else (
    echo ==================================================
    echo  SOME CHECKS FAILED - see output above
    echo ==================================================
    exit /b 1
)

:check
echo.
echo ==== %1 ====
"%JDK%\bin\java.exe" --module-path "lib" --add-modules javafx.controls,javafx.graphics ^
    -cp "out\classes" com.endurance.dev.%1
if errorlevel 1 set "FAILED=1"
goto :eof
