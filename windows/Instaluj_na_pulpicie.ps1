$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
function Pokaz([string]$text, [string]$icon='Information') {
    [System.Windows.Forms.MessageBox]::Show($text, 'Clima Gold Windows 2.6 TEST', 'OK', $icon) | Out-Null
}
try {
    $source = $PSScriptRoot
    $dest = Join-Path $env:LOCALAPPDATA 'Programs\ClimaGold_ZIEHL'
    $desktop = [Environment]::GetFolderPath('DesktopDirectory')
    if (-not (Test-Path $desktop)) { throw 'Nie znaleziono pulpitu Windows.' }
    $python = $null
    foreach ($command in @('py.exe','python.exe','python3.exe')) {
        $found = Get-Command $command -ErrorAction SilentlyContinue
        if (-not $found) { continue }
        $prefix = @()
        if ($command -eq 'py.exe') { $prefix = @('-3') }
        $candidate = & $found.Source @prefix -c 'import sys, tkinter; assert sys.version_info >= (3,10); print(sys.executable)' 2>$null
        if ($LASTEXITCODE -eq 0 -and $candidate) {
            $python = ($candidate | Select-Object -First 1).Trim()
            break
        }
    }
    if (-not $python) {
        throw 'Zainstaluj Python 3.10 lub nowszy z Tcl/Tk ze strony python.org/downloads/windows/. Potem uruchom instalator ponownie. Wersja przenosna EXE nie wymaga Pythona.'
    }
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    foreach ($file in @('protocol.py','model_profile.py','serial_service.py','scan_service.py','simple_write.py','simple_app.py','launcher.pyw','resources.py','requirements.txt')) {
        Copy-Item -LiteralPath (Join-Path $source $file) -Destination (Join-Path $dest $file) -Force
    }
    $assets = Join-Path $dest 'assets'
    New-Item -ItemType Directory -Path $assets -Force | Out-Null
    Copy-Item -Path (Join-Path $source 'assets\*') -Destination $assets -Force -Recurse
    $venv = Join-Path $dest '.venv'
    if (-not (Test-Path (Join-Path $venv 'Scripts\python.exe'))) {
        & $python -m venv $venv
        if ($LASTEXITCODE -ne 0) { throw 'Nie udalo sie przygotowac srodowiska Python.' }
    }
    $pythonExe = Join-Path $venv 'Scripts\python.exe'
    $pythonwExe = Join-Path $venv 'Scripts\pythonw.exe'
    $installLog = Join-Path $dest 'instalacja.log'
    & $pythonExe -m pip install --disable-pip-version-check -r (Join-Path $dest 'requirements.txt') *> $installLog
    if ($LASTEXITCODE -ne 0) { throw "Nie udalo sie pobrac pyserial. Sprawdz internet. Raport: $installLog" }
    & $pythonExe -c 'import serial, tkinter' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Brak serial lub tkinter po instalacji.' }
    $wsh = New-Object -ComObject WScript.Shell
    $shortcut = $wsh.CreateShortcut((Join-Path $desktop 'Clima Gold - ZIEHL-ABEGG.lnk'))
    $shortcut.TargetPath = $pythonwExe
    $shortcut.Arguments = '"' + (Join-Path $dest 'launcher.pyw') + '"'
    $shortcut.WorkingDirectory = $dest
    $shortcut.IconLocation = (Join-Path $assets 'climagold.ico') + ',0'
    $shortcut.Description = 'Clima Gold Windows 2.6 TEST'
    $shortcut.Save()
    Pokaz 'Zainstalowano. Uruchom skrot Clima Gold - ZIEHL-ABEGG na pulpicie. Program otwiera sie bez czarnej konsoli.'
} catch {
    Pokaz $_.Exception.Message 'Error'
    exit 1
}
