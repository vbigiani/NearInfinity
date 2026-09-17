@echo off
setlocal

cd /d "%~dp0"
java -jar "..\..\..\NearInfinity.jar" --run-tool PrintGameObjectsToJson "PrintGameObjectsToJson.json"
if errorlevel 1 (
  echo Resource export failed.
  exit /b 1
)

echo Created resource XML files in the output folder from PrintGameObjectsToJson.json
endlocal
