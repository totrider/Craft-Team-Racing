"""
The light that swirls round a kart under the Totem (Mask) power-up: three tapering ribbons winding up the ring the head orbits on, 24 animation frames like the original's (akubeam /
ukabeam), drawn added to the screen for the good totem and taken from it for the evil one (client/MaskHead).

  python tools/make_mask_beam.py

1. Draws the ribbon's picture for each (textures/item/mask_beam_{aku,uka}.png: a soft band, bright down its middle and fading at both ends; overwritten each run, edit the code).
2. Creates blender/mask/mask_beam.blend once if it is missing (tools/blender_create_mask_beam.py); after that the .blend is YOURS and is never overwritten.
3. Exports every .blend under blender/ (tools/export_blender_models.py): models/item/mask_beam_{aku,uka}_{0..23}.obj and the pickup_mask_beam_*.json.
A resource pack with the original's beam (tools/extract_ctr_textures.py) overrides these. Run from the project folder.
"""
import os
import subprocess
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import export_blender_models as ebm  # noqa: E402

TEX_DIR = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "item")
BLEND = os.path.join("blender", "mask", "mask_beam.blend")
# the colour at the edge of the band and the one down its middle
COLOURS = {"aku": ((90, 220, 90), (235, 255, 220)), "uka": ((150, 235, 150), (255, 255, 255))}


def band(rim, core):
    """32 texels along the ribbon (u), 16 across it (v)."""
    im = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
    for x in range(32):
        along = min(1.0, min(x, 31 - x) / 6.0)
        for y in range(16):
            across = 1.0 - abs((y - 7.5) / 7.5)
            k = across ** 1.6
            c = tuple(int(rim[i] + (core[i] - rim[i]) * across ** 3) for i in range(3))
            im.putpixel((x, y), c + (int(235 * k * along),))
    return im


def main():
    os.makedirs(TEX_DIR, exist_ok=True)
    for who, (rim, core) in COLOURS.items():
        band(rim, core).save(os.path.join(TEX_DIR, "mask_beam_%s.png" % who))
    if not os.path.exists(BLEND):
        os.makedirs(os.path.dirname(BLEND), exist_ok=True)
        exe = ebm.find_blender()
        if not exe:
            sys.exit("Blender not found (set BLENDER to its blender.exe): cannot create " + BLEND)
        subprocess.run([exe, "--background", "--factory-startup", "--python", os.path.join("tools", "blender_create_mask_beam.py"), "--", BLEND], check=True)
    ebm.export_all()
    print("mask beam ok")


if __name__ == "__main__":
    main()
