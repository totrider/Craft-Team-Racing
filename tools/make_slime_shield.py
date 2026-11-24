"""
The Slime Shield: the mod's own Power Shield bubble, drawn the way the original game draws it (a coloured layer added to the screen, a dark layer taken from it, a highlight that sweeps
over it; client/ShieldBubble) but in Minecraft's own vocabulary: a slime. Green is the slime itself; the blue one (the shield that stays until it is hit) is the same slime tinted blue.

  python tools/make_slime_shield.py

1. Draws the layers' pictures as layered files (krita/items/slime_shield_{green,blue,dark,sheen}.kra, a modder's templates: original pixel art, look-alikes of a slime block's translucent
   shell; tools/build_art.py flattens them into textures/item/slime_shield_*.png).
2. Creates blender/shield/slime_shield.blend once if it is missing (with Blender, through tools/blender/blender_create_slime_shield.py); after that the .blend is YOURS and is never overwritten.
3. Exports every .blend under blender/ to models (tools/export_blender_models.py): models/item/shield_{green,blue,dark,sheen_0..7}.obj and the pickup_shield_*.json that point at them.
A resource pack that brings the original's models (tools/ctr_disc/extract_ctr_textures.py) overrides all of these, being named the same. Run from the project folder.
"""
import os
import subprocess
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import export_blender_models as ebm  # noqa: E402
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

TEX_DIR = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "item")
BLEND = os.path.join("blender", "shield", "slime_shield.blend")

# 16 x 16, viewed from the front of the shield (the model maps it straight onto the squashed shape: the outer 3 px are the slanted rim, the middle the flat face).
# Each colour set: rim (outermost to innermost), face, face specks, highlight. Colours are the slime's, alpha is how much of it is added to the screen.
GREEN = {"rim": [((52, 140, 58), 215), ((88, 190, 82), 185), ((140, 235, 120), 170)], "face": ((112, 215, 96), 120), "dark": ((70, 170, 70), 150), "light": ((190, 255, 170), 165), "shine": (255, 255, 255)}
BLUE = {"rim": [((44, 84, 170), 215), ((70, 130, 225), 185), ((130, 190, 255), 170)], "face": ((96, 160, 245), 120), "dark": ((60, 110, 200), 150), "light": ((180, 220, 255), 165), "shine": (255, 255, 255)}
SPECKS_DARK = [(5, 6), (9, 4), (7, 10), (11, 9), (4, 11)]
SPECKS_LIGHT = [(6, 4), (10, 7), (5, 8), (8, 11), (11, 5)]


def slime_sheet(c):
    """A colour set as layers: the three rings of the rim, the face, the dark and the light specks and the glossy corner (each later one replaces what is under it)."""
    sheet = kra_layers.Sheet(16, 16)
    rims = [sheet.layer("rim %s" % n) for n in ("(outer)", "(middle)", "(inner)")]
    face = sheet.layer("face")
    for y in range(16):
        for x in range(16):
            ring = min(x, y, 15 - x, 15 - y)
            if ring < 3:
                rgb, a = c["rim"][ring]
                rims[ring][x, y] = rgb + (a,)
            else:
                rgb, a = c["face"]
                face[x, y] = rgb + (a,)
    dark, light, shine = sheet.layer("dark specks"), sheet.layer("light specks"), sheet.layer("glossy corner")
    for x, y in SPECKS_DARK:
        dark[x, y] = c["dark"][0] + (c["dark"][1],)
    for x, y in SPECKS_LIGHT:
        light[x, y] = c["light"][0] + (c["light"][1],)
    for x, y in ((4, 4), (5, 4), (4, 5)):                         # the glossy corner
        shine[x, y] = c["shine"] + (205,)
    sheet.replace_semantics()
    return sheet


def slime(c):
    return slime_sheet(c).flatten()


def core_sheet():
    """The slime's nucleus, taken from the screen: a darker, denser middle (the slime's inner cube), fading toward its edge."""
    sheet = kra_layers.Sheet(16, 16)
    px = sheet.layer("nucleus")
    for y in range(16):
        for x in range(16):
            ring = min(x, y, 15 - x, 15 - y)
            px[x, y] = (96, 112, 96, 70 if ring < 2 else 120 if ring < 4 else 165)
    return sheet


def sheen_sheet():
    """The glint that sweeps over the bubble: a soft white band, brightest down its middle."""
    sheet = kra_layers.Sheet(16, 16)
    px = sheet.layer("glint band")
    for y in range(16):
        for x in range(16):
            px[x, y] = (255, 255, 255, int(235 * max(0.0, 1.0 - abs(x - 7.5) / 7.5) ** 1.5))
    return sheet


def core():
    return core_sheet().flatten()


def sheen():
    return sheen_sheet().flatten()


def main():
    inputs = kra_stamp.inputs_for(__file__)
    for name, sheet in (("green", slime_sheet(GREEN)), ("blue", slime_sheet(BLUE)), ("dark", core_sheet()), ("sheen", sheen_sheet())):
        kra = "krita/items/slime_shield_%s.kra" % name
        kra_layers.write_if_stale(kra, sheet, inputs)
        kra_layers.export_png(kra)
    if not os.path.exists(BLEND):
        os.makedirs(os.path.dirname(BLEND), exist_ok=True)
        exe = ebm.find_blender()
        if not exe:
            sys.exit("Blender not found (set BLENDER to its blender.exe): cannot create " + BLEND)
        subprocess.run([exe, "--background", "--factory-startup", "--python", os.path.join("tools", "blender", "blender_create_slime_shield.py"), "--", BLEND], check=True)
    ebm.export_all()
    print("slime shield ok")


if __name__ == "__main__":
    main()
