@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if not errorlevel 1 (set "PY=py -3") else (set "PY=python")
if not exist ".build-venv\Scripts\python.exe" (
  %PY% -m venv .build-venv
  if errorlevel 1 goto failed
)
set "BUILD_PY=.build-venv\Scripts\python.exe"
"%BUILD_PY%" -m pip install -r requirements-build.txt
if errorlevel 1 goto failed
"%BUILD_PY%" -m unittest discover -s tests -v
if errorlevel 1 goto failed
"%BUILD_PY%" -m PyInstaller --clean --noconfirm --onefile --windowed ^
 --name ClimaGold_ZiehlAbegg_102 --icon assets\climagold_dark.ico ^
 --add-data "assets;assets" --hidden-import serial.tools.list_ports launcher.pyw
if errorlevel 1 goto failed
echo Gotowe: dist\ClimaGold_ZiehlAbegg_102.exe
pause
exit /b 0
:failed
echo Nie udalo sie zbudowac EXE. Sprawdz komunikaty powyzej.
pause
exit /b 1
