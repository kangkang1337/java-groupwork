@echo off
setlocal
cd /d "%~dp0"

rem =====================================================================
rem  Build a self-contained folder + zip that can be handed to someone else.
rem  The target machine only needs a Java 17 runtime (no Maven, no JDK tools).
rem =====================================================================
call "%~dp0build.bat"
if errorlevel 1 (
    echo [ERROR] Build failed, nothing packaged.
    exit /b 1
)

set "NAME=endurance-strategy-v1.0"
set "DIST=dist\%NAME%"
set "ZIP=dist\%NAME%.zip"

if exist "dist" rmdir /s /q "dist"
mkdir "%DIST%"

echo.
echo [1/4] Copying classes...
xcopy /E /I /Q /Y "out\classes" "%DIST%\out\classes" >nul

echo [2/4] Copying runtime jars and data...
xcopy /E /I /Q /Y "lib" "%DIST%\lib" >nul
xcopy /E /I /Q /Y "data" "%DIST%\data" >nul

echo [3/4] Copying docs, launcher, screenshots...
copy /Y "README.md" "%DIST%\README.md" >nul
copy /Y "RULES.md" "%DIST%\RULES.md" >nul
copy /Y "run.bat" "%DIST%\run.bat" >nul
copy /Y "server.bat" "%DIST%\server.bat" >nul
copy /Y "client.bat" "%DIST%\client.bat" >nul
xcopy /E /I /Q /Y "screenshots" "%DIST%\screenshots" >nul

echo [4/4] Compressing...
powershell -NoProfile -Command "Compress-Archive -Path '%DIST%\*' -DestinationPath '%ZIP%' -Force"
if errorlevel 1 (
    echo [ERROR] Compress-Archive failed.
    exit /b 1
)

echo.
echo ==================================================
echo  Package folder : %DIST%
echo  Package zip    : %ZIP%
echo  Target machine : Java 17 runtime only
echo ==================================================
