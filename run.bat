@echo off
setlocal
cd /d "%~dp0"

rem =====================================================================
rem  Endurance Strategy launcher
rem  Must be started from the folder that contains data/ and out/classes.
rem  Works both in the source tree and in the packaged distribution.
rem =====================================================================
set "JDK=C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JDK=%JAVA_HOME%"

if not exist "%JDK%\bin\java.exe" (
    echo [ERROR] Java 17 not found.
    echo         Install a JDK/JRE 17 and set JAVA_HOME, or edit the JDK path in run.bat.
    pause
    exit /b 1
)

if not exist "out\classes\com\endurance\App.class" (
    if exist "build.bat" (
        call "%~dp0build.bat"
        if errorlevel 1 (
            echo.
            echo [ERROR] Build failed, application not started.
            pause
            exit /b 1
        )
    ) else (
        echo [ERROR] out\classes\com\endurance\App.class is missing.
        echo         Run build.bat in the source folder first.
        pause
        exit /b 1
    )
)

"%JDK%\bin\java.exe" --module-path "lib" --add-modules javafx.controls,javafx.graphics ^
    -cp "out\classes" com.endurance.App
if errorlevel 1 (
    echo.
    echo [ERROR] Application exited with an error. See logs\app.log for details.
    pause
)
