@echo off
setlocal
cd /d "%~dp0"

set "JDK=C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JDK=%JAVA_HOME%"

if not exist "%JDK%\bin\java.exe" (
    echo [ERROR] JDK 17 not found. Edit the JDK path at the top of run-tests.bat.
    pause
    exit /b 1
)

if not exist "out\classes\com\pixelforge\App.class" (
    call "%~dp0build.bat"
    if errorlevel 1 (
        echo [ERROR] Build failed.
        pause
        exit /b 1
    )
)

echo.
echo ==================== 1/2 PipelineTest ====================
"%JDK%\bin\java.exe" --module-path "lib" --add-modules javafx.controls,javafx.graphics "-Djavafx.cachedir=%TEMP%\PixelForge-cache" ^
    -cp "out\classes" com.pixelforge.dev.PipelineTest "out-test"
set PIPELINE=%errorlevel%

echo.
echo ==================== 2/2 ToolSmokeTest ====================
"%JDK%\bin\java.exe" --module-path "lib" --add-modules javafx.controls,javafx.graphics "-Djavafx.cachedir=%TEMP%\PixelForge-cache" ^
    -cp "out\classes" com.pixelforge.dev.ToolSmokeTest
set TOOLS=%errorlevel%

echo.
if %PIPELINE%==0 if %TOOLS%==0 (
    echo ALL TESTS PASSED
    exit /b 0
)
echo SOME TESTS FAILED  ^(pipeline=%PIPELINE%, tools=%TOOLS%^)
exit /b 1
