@echo off
setlocal
cd /d "%~dp0"

rem =====================================================================
rem  Command line runner: simulate a whole race and print the report.
rem    race-cli.bat                 default track, default laps
rem    race-cli.bat list            list tracks / teams / tires
rem    race-cli.bat 60 7 sahara     laps / seed / track id
rem =====================================================================
set "JDK=C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JDK=%JAVA_HOME%"

if not exist "%JDK%\bin\java.exe" (
    echo [ERROR] JDK 17 not found. Edit the JDK path at the top of race-cli.bat.
    pause
    exit /b 1
)

if not exist "out\classes\com\endurance\dev\RaceCli.class" (
    call "%~dp0build.bat"
    if errorlevel 1 (
        echo [ERROR] Build failed.
        pause
        exit /b 1
    )
)

"%JDK%\bin\java.exe" --module-path "lib" --add-modules javafx.controls,javafx.graphics ^
    -cp "out\classes" com.endurance.dev.RaceCli %*

echo.
pause
