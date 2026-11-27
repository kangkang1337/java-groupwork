@echo off
setlocal
cd /d "%~dp0"

rem =====================================================================
rem  Multiplayer server.
rem    server.bat                  port 7777, 20 laps, 800 ms per lap
rem    server.bat 7777 40 500      port / laps / ms per lap
rem =====================================================================
set "JDK=C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JDK=%JAVA_HOME%"

if not exist "%JDK%\bin\java.exe" (
    echo [ERROR] Java 17 not found. Edit the JDK path at the top of server.bat.
    pause
    exit /b 1
)

if not exist "out\classes\com\endurance\dev\ServerMain.class" (
    call "%~dp0build.bat"
    if errorlevel 1 (
        echo [ERROR] Build failed.
        pause
        exit /b 1
    )
)

"%JDK%\bin\java.exe" --module-path "lib" --add-modules javafx.controls,javafx.graphics ^
    -cp "out\classes" com.endurance.dev.ServerMain %*
pause
