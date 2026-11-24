@echo off
rem Extracts CTR's original textures (crates, power-up icons, Wumpa fruit, start lamps) from YOUR OWN disc image into two local resource packs
rem (Minecraft size, and original size; see tools\ctr_disc\extract_ctr_textures.py) and copies them into the test instances' resourcepacks folders.
rem The packs stay on this machine (git-ignored, never in the jar). Enable ONE of them in Options > Resource Packs.
powershell -NoProfile -ExecutionPolicy Bypass -Command "Set-Location '%~dp0..'; python tools\ctr_disc\extract_ctr_textures.py --install 'F:\ATLauncher\instances\CraftTeamRacing\resourcepacks' --install 'F:\ATLauncher\instances\CraftTeamRacingMP\resourcepacks'"
echo.
pause
