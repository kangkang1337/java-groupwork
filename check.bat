@echo off
setlocal
cd /d "%~dp0"

rem =====================================================================
rem  Endurance Strategy self-check
rem  Runs the JSON/data layer test suite.
rem =====================================================================
set "JDK=C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JDK=%JAVA_HOME%"

if not exist "%JDK%\bin\java.exe" (
    echo [ERROR] JDK 17 not found. Edit the JDK path at the top of check.bat.
    pause
    exit /b 1
)

if not exist "out\classes\com\endurance\dev\DataCheck.class" (
    call "%~dp0build.bat"
    if errorlevel 1 (
        echo [ERROR] Build failed.
        pause
        exit /b 1
    )
)

"%JDK%\bin\java.exe" --module-path "lib" --add-modules javafx.controls,javafx.graphics ^
    -cp "out\classes" com.endurance.dev.DataCheck
set RESULT=%errorlevel%

echo.
if %RESULT%==0 (
    echo ALL CHECKS PASSED
) else (
    echo SOME CHECKS FAILED
)
pause
exit /b %RESULT%
