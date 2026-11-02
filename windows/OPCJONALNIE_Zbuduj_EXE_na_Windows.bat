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
 --name ClimaGold_ZIEHL_Windows_2_7_TEST --icon assets\climagold.ico ^
 --add-data "assets;assets" --hidden-import serial.tools.list_ports launcher.pyw
if errorlevel 1 goto failed
echo Gotowe: dist\ClimaGold_ZIEHL_Windows_2_7_TEST.exe
pause
exit /b 0
:failed
echo Nie udalo sie zbudowac EXE. Sprawdz komunikaty powyzej.
pause
exit /b 1
