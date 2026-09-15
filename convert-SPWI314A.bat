@echo off
setlocal

cd /d "%~dp0"
java -cp "NearInfinity.jar" org.infinity.cli.ImageSequenceToBam "input.json"
if errorlevel 1 (
  echo Conversion failed.
  exit /b 1
)

echo Created SPWI314A.BAM
endlocal
