@echo off
rem The asset extractor: make local resource packs from YOUR OWN game disc (see README.txt in this folder).
rem Put this folder into your Minecraft instance folder, put the disc image into iso\, then run this file. Extra options go straight through (extract.bat --list).
cd /d "%~dp0"
set PY=
where py >nul 2>nul && set PY=py -3
if not defined PY where python >nul 2>nul && set PY=python
if not defined PY (
  echo Python 3 was not found. Install it from https://www.python.org/downloads/ ^(tick "Add python.exe to PATH"^) and run this again.
  pause
  exit /b 1
)
%PY% -c "import numpy, PIL, soundfile" >nul 2>nul
if errorlevel 1 (
  echo Installing the packages the extractor needs ^(numpy, pillow, soundfile^) ...
  %PY% -m pip install --quiet numpy pillow soundfile
  if errorlevel 1 (
    echo Could not install them. Run:  pip install numpy pillow soundfile
    pause
    exit /b 1
  )
)
%PY% extract.py %*
echo.
pause
