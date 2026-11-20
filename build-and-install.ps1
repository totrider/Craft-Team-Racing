# Builds Craft Team Racing and copies the jar into the ATLauncher test instance.
# Run it from anywhere:  powershell -ExecutionPolicy Bypass -File "H:\Craft Team Racing\build-and-install.ps1"
# (or just double-click build-and-install.bat)

$ErrorActionPreference = "Stop"

# ---- settings -------------------------------------------------------------
$Instances = @(
    "F:\ATLauncher\instances\CraftTeamRacing",
    "F:\ATLauncher\instances\CraftTeamRacingMP"
)
# ---------------------------------------------------------------------------

Set-Location $PSScriptRoot

$javaw = Get-Process javaw -ErrorAction SilentlyContinue
if ($javaw) {
    Write-Host "Warning: Java (javaw) is running. If Minecraft is open, close it first or the copy may fail." -ForegroundColor Yellow
}

# Model and sound generators (tools\*.py): the kart model from blockbench/kart/kart.bbmodel, the synthesised placeholder sounds.
# They need Python 3 with Pillow (kart texture) and numpy + Krita (sounds, see tools/make_sounds.py); if Python is missing the
# committed outputs under src/main/resources are used as they are.
# A generator that fails on a file the system briefly holds (Windows now and then refuses a write with "Invalid argument") is run again, up to four times.
function Run-Gen($name) {
    for ($i = 0; $i -lt 4; $i++) {
        cmd /c "python tools\$name 2>&1"
        if ($LASTEXITCODE -eq 0) { return }
        Write-Host "$name failed, running it again" -ForegroundColor Yellow
        Start-Sleep -Milliseconds 500
    }
    Write-Host "$name FAILED" -ForegroundColor Red
}

if (Get-Command python -ErrorAction SilentlyContinue) {
    Write-Host "Generating model and sounds..." -ForegroundColor Cyan
    Run-Gen "make_kart.py"
    Run-Gen "make_kart_ctr.py"
    Run-Gen "make_kart_wheel.py"
    Run-Gen "make_boost_flame.py"
    Run-Gen "make_kart_shadow.py"
    Run-Gen "make_boost_pad.py"
    Run-Gen "make_track_items.py"
    Run-Gen "make_pickup_spots.py"
    Run-Gen "make_kart_fx.py"
    Run-Gen "make_pickup_models.py"
    Run-Gen "make_beaker_models.py"
    Run-Gen "make_power_icons.py"
    Run-Gen "make_evil_totem.py"
    Run-Gen "make_power_sprites.py"
    Run-Gen "make_slime_shield.py"
    Run-Gen "make_warp_orb_model.py"
    Run-Gen "make_mask_models.py"
    Run-Gen "make_mask_beam.py"
    Run-Gen "make_bomb_model.py"
    Run-Gen "make_tnt_models.py"
    Run-Gen "make_crate_sprites.py"
    Run-Gen "make_missile_model.py"
    Run-Gen "make_rocket_flame.py"
    Run-Gen "make_shield_model.py"
    Run-Gen "make_burst_sprites.py"
    Run-Gen "make_crate_break.py"
    Run-Gen "make_track_overlays.py"
    Run-Gen "make_sounds.py"
    Run-Gen "make_survival_assets.py"
}

Write-Host "Building..." -ForegroundColor Cyan
cmd /c ".\gradlew.bat build --no-daemon 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBUILD FAILED - nothing was copied." -ForegroundColor Red
    exit 1
}

$jar = Get-ChildItem "build\libs\*.jar" |
    Where-Object { $_.Name -notmatch "sources|javadoc" } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) {
    Write-Host "No jar found in build\libs." -ForegroundColor Red
    exit 1
}

$installed = 0
foreach ($instance in $Instances) {
    $mods = Join-Path $instance "mods"
    if (-not (Test-Path $mods)) {
        Write-Host "Skipping $instance (no mods folder)" -ForegroundColor Yellow
        continue
    }
    # remove older craftteamracing jars so two versions never load together
    Get-ChildItem $mods -Filter "craftteamracing-*.jar" | Remove-Item -Force
    Get-ChildItem $mods -Filter "ctrcraft-*.jar" | Remove-Item -Force   # the jar's old name
    Copy-Item $jar.FullName $mods -Force
    Write-Host "`nInstalled $($jar.Name) -> $mods" -ForegroundColor Green
    $installed++
}
if ($installed -eq 0) {
    Write-Host "No instance found: edit `$Instances at the top of this script." -ForegroundColor Red
    exit 1
}
Write-Host "Launch the instance to test."
