"""
The asset extractor: finds the game disc images in iso/, works out which game each one is from the disc itself (disc.py), and runs that game's extractors (games/<game>.json),
writing local resource packs into the Minecraft instance this folder sits in (instance/resourcepacks). Nothing leaves this machine; the packs are made from YOUR copy of the game.

  extract.bat                       everything that can be extracted from the discs in iso/
  extract.bat --list                what is in iso/ and what it is
  extract.bat --only sounds,music   only some steps
  extract.bat --install PATH        install into this resourcepacks folder instead of the instance's (may be given more than once)
  extract.bat --no-install          write the packs to output/ only

Put this folder into the instance folder (the one with mods/ and resourcepacks/), the disc image (.bin / .iso / .cue) into iso/, run extract.bat, then enable the packs under
Options > Resource Packs. To support another game, add games/<name>.json (its serial number as printed by --list, and its extractor scripts).
"""
import argparse
import glob
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import disc  # noqa: E402

IMAGE_PATTERNS = ("*.cue", "*.bin", "*.iso", "*.img")


def instance_resourcepacks():
    """The resourcepacks folder of the instance this folder sits in (its parent), or None."""
    root = os.path.dirname(HERE)
    for sub in ("resourcepacks", os.path.join(".minecraft", "resourcepacks"), os.path.join("minecraft", "resourcepacks")):
        p = os.path.join(root, sub)
        if os.path.isdir(p):
            return p
    # an instance that has no resourcepacks folder yet: it is one when it has mods or a config folder
    for marker in ("mods", "config", "saves", "options.txt"):
        if os.path.exists(os.path.join(root, marker)):
            p = os.path.join(root, "resourcepacks")
            os.makedirs(p, exist_ok=True)
            return p
    return None


def find_images():
    """The disc images in iso/: a .cue stands for its .bin files, which are then not listed again."""
    iso = os.path.join(HERE, "iso")
    found = []
    seen = set()
    for pattern in IMAGE_PATTERNS:
        for p in sorted(glob.glob(os.path.join(iso, pattern))):
            if p.lower().endswith(".cue"):
                for b in disc.image_files(p):
                    seen.add(os.path.normcase(os.path.abspath(b)))
            found.append(p)
    return [p for p in found if p.lower().endswith(".cue") or os.path.normcase(os.path.abspath(p)) not in seen]


def load_games():
    games = []
    for p in sorted(glob.glob(os.path.join(HERE, "games", "*.json"))):
        with open(p, encoding="utf-8") as f:
            games.append(json.load(f))
    return games


def code_dir(game):
    """Where the game's extractor scripts are: the packaged folder next to this one, or the project's tools/ctr_disc/ when run from a checkout."""
    packaged = os.path.join(HERE, game.get("code", game["id"]))
    if os.path.isdir(packaged):
        return packaged
    tools = os.path.join(os.path.dirname(HERE), "tools", "ctr_disc")
    return tools if os.path.isdir(tools) else None


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0], formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--list", action="store_true", help="only say what the discs in iso/ are")
    ap.add_argument("--iso", action="append", help="a disc image to use instead of looking in iso/ (may be given more than once)")
    ap.add_argument("--only", help="comma separated steps to run (sounds, music, textures ...)")
    ap.add_argument("--install", action="append", help="a resourcepacks folder to install into (default: the instance this folder is in)")
    ap.add_argument("--no-install", action="store_true", help="do not install, only write output/")
    ap.add_argument("--jobs", type=int, help="processes for the music rendering")
    args = ap.parse_args()

    images = args.iso or find_images()
    if not images:
        print("No disc image found. Put the game's .bin / .iso / .cue file into the iso folder:\n  %s" % os.path.join(HERE, "iso"))
        return 1
    games = load_games()
    installs = [] if args.no_install else (args.install or [])
    if not args.no_install and not args.install:
        target = instance_resourcepacks()
        if target:
            installs = [target]
        elif not args.list:
            print("This folder is not inside a Minecraft instance (no mods or resourcepacks folder next to it): the packs are written to output/ only.\n"
                  "Put the folder into the instance folder, or use --install PATH.\n")
    ran = 0
    for image in images:
        info = disc.identify(image)
        if info is None:
            print("%s: not a disc image this tool can read (no ISO 9660 volume)." % os.path.basename(image))
            continue
        game = next((g for g in games if info["serial"] and info["serial"] in g.get("serials", [])), None)
        label = "%s  (%s, boot file %s, volume \"%s\")" % (os.path.basename(info["file"]), info["serial"] or "no serial", info["boot"] or "?", info["volume"])
        if game is None:
            print("%s\n    unknown game: no profile in games/ lists the serial %s.\n    To add support, write games/<name>.json for it." % (label, info["serial"]))
            continue
        print("%s\n    = %s" % (label, game["title"]))
        if args.list:
            for step in game["steps"]:
                print("      %-9s %s" % (step["name"], step.get("makes", "")))
            continue
        scripts = code_dir(game)
        if scripts is None:
            print("    the extractor scripts for %s are missing (expected %s/ next to extract.py, or the project's tools/)." % (game["title"], game.get("code", game["id"])))
            continue
        wanted = set(args.only.split(",")) if args.only else None
        out_root = os.path.join(HERE, "output", game["id"])
        for step in game["steps"]:
            if wanted and step["name"] not in wanted:
                continue
            if step.get("needs_raw") and info["layout"] != "raw":
                print("    [%s] skipped: this step needs a raw (2352 bytes a sector) .bin image, this one is a plain .iso." % step["name"])
                continue
            cmd = [sys.executable, os.path.join(scripts, step["script"]), "--disc", info["file"], "--out", os.path.join(out_root, step["name"])]
            for target in installs:
                cmd += ["--install", target]
            if step["name"] == "music" and args.jobs:
                cmd += ["--jobs", str(args.jobs)]
            print("\n[%s] %s" % (step["name"], step.get("makes", "")))
            env = dict(os.environ, PYTHONIOENCODING="utf-8")
            code = subprocess.call(cmd, env=env)
            if code != 0:
                print("[%s] failed (exit code %d)." % (step["name"], code))
            else:
                ran += 1
    if args.list:
        return 0
    if ran:
        print("\nDone. %s" % ("Enable the packs under Options > Resource Packs (above the Craft Team Racing mod's own)." if installs else "The packs are in %s." % os.path.join(HERE, "output")))
    return 0 if ran else 1


if __name__ == "__main__":
    sys.exit(main())
