"""
The Slime Shield: the mod's own Power Shield bubble, drawn the way the original game draws it (a coloured layer added to the screen, a dark layer taken from it, a highlight that sweeps
over it; client/ShieldBubble) but in Minecraft's own vocabulary: a slime. Green is the slime itself; the blue one (the shield that stays until it is hit) is the same slime tinted blue.

  python tools/make_slime_shield.py

1. Draws the layers' pictures (textures/item/slime_shield_{green,blue,dark,sheen}.png: original pixel art, look-alikes of a slime block's translucent shell; overwritten each run, edit
   the code, not the PNGs).
2. Creates blender/shield/slime_shield.blend once if it is missing (with Blender, through tools/blender_create_slime_shield.py); after that the .blend is YOURS and is never overwritten.
3. Exports every .blend under blender/ to models (tools/export_blender_models.py): models/item/shield_{green,blue,dark,sheen_0..7}.obj and the pickup_shield_*.json that point at them.
A resource pack that brings the original's models (tools/extract_ctr_textures.py) overrides all of these, being named the same. Run from the project folder.
"""
import os
import subprocess
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import export_blender_models as ebm  # noqa: E402

TEX_DIR = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "item")
BLEND = os.path.join("blender", "shield", "slime_shield.blend")

# 16 x 16, viewed from the front of the shield (the model maps it straight onto the squashed shape: the outer 3 px are the slanted rim, the middle the flat face).
# Each colour set: rim (outermost to innermost), face, face specks, highlight. Colours are the slime's, alpha is how much of it is added to the screen.
GREEN = {"rim": [((52, 140, 58), 215), ((88, 190, 82), 185), ((140, 235, 120), 170)], "face": ((112, 215, 96), 120), "dark": ((70, 170, 70), 150), "light": ((190, 255, 170), 165), "shine": (255, 255, 255)}
BLUE = {"rim": [((44, 84, 170), 215), ((70, 130, 225), 185), ((130, 190, 255), 170)], "face": ((96, 160, 245), 120), "dark": ((60, 110, 200), 150), "light": ((180, 220, 255), 165), "shine": (255, 255, 255)}
SPECKS_DARK = [(5, 6), (9, 4), (7, 10), (11, 9), (4, 11)]
SPECKS_LIGHT = [(6, 4), (10, 7), (5, 8), (8, 11), (11, 5)]


def slime(c):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            ring = min(x, y, 15 - x, 15 - y)
            if ring < 3:
                rgb, a = c["rim"][ring]
            else:
                rgb, a = c["face"]
            im.putpixel((x, y), rgb + (a,))
    for x, y in SPECKS_DARK:
        im.putpixel((x, y), c["dark"][0] + (c["dark"][1],))
    for x, y in SPECKS_LIGHT:
        im.putpixel((x, y), c["light"][0] + (c["light"][1],))
    for x, y in ((4, 4), (5, 4), (4, 5)):                         # the glossy corner
        im.putpixel((x, y), c["shine"] + (205,))
    return im


def core():
    """The slime's nucleus, taken from the screen: a darker, denser middle (the slime's inner cube), fading toward its edge."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            ring = min(x, y, 15 - x, 15 - y)
            im.putpixel((x, y), (96, 112, 96, 70 if ring < 2 else 120 if ring < 4 else 165))
    return im


def sheen():
    """The glint that sweeps over the bubble: a soft white band, brightest down its middle."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), (255, 255, 255, int(235 * max(0.0, 1.0 - abs(x - 7.5) / 7.5) ** 1.5)))
    return im


def main():
    os.makedirs(TEX_DIR, exist_ok=True)
    slime(GREEN).save(os.path.join(TEX_DIR, "slime_shield_green.png"))
    slime(BLUE).save(os.path.join(TEX_DIR, "slime_shield_blue.png"))
    core().save(os.path.join(TEX_DIR, "slime_shield_dark.png"))
    sheen().save(os.path.join(TEX_DIR, "slime_shield_sheen.png"))
    if not os.path.exists(BLEND):
        os.makedirs(os.path.dirname(BLEND), exist_ok=True)
        exe = ebm.find_blender()
        if not exe:
            sys.exit("Blender not found (set BLENDER to its blender.exe): cannot create " + BLEND)
        subprocess.run([exe, "--background", "--factory-startup", "--python", os.path.join("tools", "blender_create_slime_shield.py"), "--", BLEND], check=True)
    ebm.export_all()
    print("slime shield ok")


if __name__ == "__main__":
    main()
