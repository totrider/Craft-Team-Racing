Craft Team Racing - asset extractor
===================================

Makes local resource packs (sounds, music, textures) from YOUR OWN copy of the game, so the mod can look and sound like the original on your computer.
The mod itself ships none of the original's assets and neither does this folder; the packs it makes stay on your machine (do not share or upload them).

How to use it
-------------
1. Put this whole folder into your Minecraft instance folder (the one that has mods\ and resourcepacks\ in it), e.g.
       F:\ATLauncher\instances\CraftTeamRacing\asset-extractor\
2. Put the game's disc image into the iso\ folder inside it: a .bin (raw, 2352 bytes a sector, best), an .iso, or a .cue sheet with its .bin.
3. Run extract.bat. It looks in iso\, works out which game each image is from the disc itself, and runs that game's extractors. It needs Python 3 and installs
   its three small packages (numpy, pillow, soundfile) the first time.
4. Start the game, open Options > Resource Packs and enable the packs it made (put them above the Craft Team Racing mod's own).

Useful options (add them after extract.bat):
    --list              say what the discs in iso\ are, and what could be made from them
    --only sounds,music run only some steps (sounds, music, textures)
    --install PATH      install into another resourcepacks folder (may be given more than once)
    --no-install        write output\ only

How a disc is recognised
------------------------
A PlayStation disc names the program it boots in a small file (SYSTEM.CNF), and that is the game's serial number, for example SCUS-94426. The extractor reads that
(and the volume name) from the image, so the file can be called anything. games\*.json lists, per game, the serial numbers it supports and the steps to run. A disc it
does not know is reported with its serial number and skipped. Supported now:
    Crash Team Racing (USA), serial SCUS-94426

Adding a game
-------------
Write games\<name>.json (copy ctr_usa.json: id, title, platform, serials, a folder name for its scripts under "code", and the steps), and put its extractor scripts in a
folder of that name next to extract.py. Every script is called as
    python script.py --disc <image> --out <folder to write into> [--install <resourcepacks folder>]
and writes a Minecraft resource pack (assets\...) into the folder. The scripts for Crash Team Racing are in ctr\ (made by tools\ctr_disc\package_asset_extractor.py in the
mod's source tree; when this folder is run from a source checkout without ctr\, the scripts in ..\tools\ctr_disc are used).
