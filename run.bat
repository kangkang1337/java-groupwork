@echo off
setlocal
cd /d "%~dp0"

set "JDK=C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JDK=%JAVA_HOME%"

if not exist "%JDK%\bin\java.exe" (
    echo [ERROR] JDK 17 not found. Edit the JDK path at the top of run.bat.
    pause
    exit /b 1
)

if not exist "out\classes\com\pixelforge\App.class" (
    call "%~dp0build.bat"
    if errorlevel 1 (
        echo.
        echo [ERROR] Build failed, application not started.
        pause
        exit /b 1
    )
)

"%JDK%\bin\java.exe" --module-path "lib" --add-modules javafx.controls,javafx.graphics ^
    -cp "out\classes" com.pixelforge.App
if errorlevel 1 (
    echo.
    echo [ERROR] Application exited with an error. See logs\app.log for details.
    pause
)
