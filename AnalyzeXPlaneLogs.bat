@echo off
setlocal

cd /d "%~dp0"

echo [X-Plane Analysis] Compiling Java analysis tools...
javac --release 21 -d build_tmp src\com\example\ai\XPlane*.java
if errorlevel 1 (
    echo.
    echo [X-Plane Analysis] Compile failed.
    pause
    exit /b 1
)

echo.
if "%~1"=="" (
    echo [X-Plane Analysis] Analyzing all compatible sessions in logs\xplane ...
    java -cp build_tmp com.example.ai.XPlaneBatchAnalysisMain logs\xplane
) else (
    echo [X-Plane Analysis] Analyzing selected input:
    echo %*
    java -cp build_tmp com.example.ai.XPlaneBatchAnalysisMain %*
)

if errorlevel 1 (
    echo.
    echo [X-Plane Analysis] Analysis failed.
    pause
    exit /b 1
)

echo.
echo [X-Plane Analysis] Done.
echo Exported CSV files are in logs\xplane, or in the same folder as the selected session CSV.
pause
