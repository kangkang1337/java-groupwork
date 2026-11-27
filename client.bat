@echo off
setlocal
cd /d "%~dp0"

rem =====================================================================
rem  Multiplayer client (console).
rem    client.bat                         connect 127.0.0.1:7777
rem    client.bat 127.0.0.1 7777 小王    host / port / name
rem  Commands: ready | pit <tire> <kg> | ping | quit
rem =====================================================================
set "JDK=C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JDK=%JAVA_HOME%"

if not exist "%JDK%\bin\java.exe" (
    echo [ERROR] Java 17 not found. Edit the JDK path at the top of client.bat.
    pause
    exit /b 1
)

if not exist "out\classes\com\endurance\dev\ClientMain.class" (
    call "%~dp0build.bat"
    if errorlevel 1 (
        echo [ERROR] Build failed.
        pause
        exit /b 1
    )
)

"%JDK%\bin\java.exe" --module-path "lib" --add-modules javafx.controls,javafx.graphics ^
    -cp "out\classes" com.endurance.dev.ClientMain %*
pause
