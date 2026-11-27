@echo off
setlocal
cd /d "%~dp0"

rem =====================================================================
rem  Endurance Strategy build script
rem  If your JDK 17 lives elsewhere, change the JDK path below.
rem  Only entry points need to be listed: -sourcepath resolves the rest.
rem =====================================================================
set "JDK=C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JDK=%JAVA_HOME%"

if not exist "%JDK%\bin\javac.exe" (
    echo [ERROR] JDK 17 not found.
    echo         Edit the JDK path at the top of build.bat, then retry.
    exit /b 1
)

echo [1/3] Cleaning output...
if exist "out\classes" rmdir /s /q "out\classes"
mkdir "out\classes"

echo [2/3] Compiling Java sources...
"%JDK%\bin\javac.exe" -encoding UTF-8 -g -d "out\classes" -sourcepath "src\main\java" ^
    --module-path "lib" --add-modules javafx.controls,javafx.graphics ^
    "src\main\java\com\endurance\App.java" ^
    "src\main\java\com\endurance\dev\SnapshotTool.java" ^
    "src\main\java\com\endurance\dev\RaceCli.java" ^
    "src\main\java\com\endurance\dev\SimDebug.java" ^
    "src\main\java\com\endurance\dev\BalanceLab.java" ^
    "src\main\java\com\endurance\dev\DataCheck.java" ^
    "src\main\java\com\endurance\dev\PlayCheck.java" ^
    "src\main\java\com\endurance\dev\AiCheck.java" ^
    "src\main\java\com\endurance\dev\AiDebug.java" ^
    "src\main\java\com\endurance\dev\ReviewCheck.java" ^
    "src\main\java\com\endurance\dev\NetCheck.java" ^
    "src\main\java\com\endurance\dev\LogSnapshot.java" ^
    "src\main\java\com\endurance\dev\RealDataCheck.java" ^
    "src\main\java\com\endurance\dev\SettingsSnapshot.java" ^
    "src\main\java\com\endurance\dev\CalendarSnapshot.java" ^
    "src\main\java\com\endurance\dev\RateCheck.java" ^
    "src\main\java\com\endurance\dev\FetchTracks.java" ^
    "src\main\java\com\endurance\dev\TrackCheck.java" ^
    "src\main\java\com\endurance\dev\ReviewSnapshot.java" ^
    "src\main\java\com\endurance\dev\ServerMain.java" ^
    "src\main\java\com\endurance\dev\ClientMain.java"
if errorlevel 1 (
    echo.
    echo [ERROR] Compilation failed.
    exit /b 1
)

echo [3/3] Copying resources...
if exist "src\main\resources" xcopy /E /I /Y /Q "src\main\resources\*" "out\classes\" >nul

echo.
echo BUILD OK  -^>  out\classes
exit /b 0
