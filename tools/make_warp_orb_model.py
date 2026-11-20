"""
The Warp Orb's stand-in model, in the spirit of Minecraft's end crystal: three nested cubes, a pale glass shell, a teal gem inside it and a bright core, which the renderer (client/WarpOrbRenderer)
turns corner-on, spins and bobs. It is only drawn when no resource pack brings the original's star (the pack has the 16 animation frames).

  python tools/make_warp_orb_model.py

Models are Blockbench's: blockbench/warp_orb/warp_crystal.bbmodel is the SOURCE (written once from the shape below if it is missing, then it is yours; this script only reads it). Three groups, one
per cube (outer, inner, core), each written as its own model (models/item/pickup_warp_crystal_<group>.json) because the renderer turns them separately. One texture slot, "crystal": 16 x 16 of
8 x 8 cells (glass shell, gem, core), drawn here every run (edit the code, not the PNG). Also writes the 16 empty frame models (pickup_warp_orb_<n>, pickup_warp_orb_solid_<n>) that a pack's
star replaces. The model's middle (8, 8, 8) is the crystal's middle. Run from the project folder.
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "craftteamracing")
TEX = os.path.join(ASSETS, "textures", "item", "warp_crystal.png")
BB = os.path.join("blockbench", "warp_orb", "warp_crystal.bbmodel")
CELL = {"glass": (0, 0), "gem": (1, 0), "core": (0, 1)}


def draw_texture():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    glass = Image.new("RGBA", (8, 8), (175, 236, 236, 70))
    for i in range(8):
        for x, y in ((i, 0), (i, 7), (0, i), (7, i)):
            glass.putpixel((x, y), (215, 250, 250, 235) if (x + y) % 3 else (160, 225, 230, 235))
    for x, y in ((0, 0), (7, 0), (0, 7), (7, 7)):
        glass.putpixel((x, y), (245, 255, 255, 255))
    gem = Image.new("RGBA", (8, 8), (0, 0, 0, 255))
    for y in range(8):
        for x in range(8):
            t = (x + y) / 14.0
            gem.putpixel((x, y), (int(110 - 80 * t), int(235 - 110 * t), int(220 - 70 * t), 235))
    for x, y in ((2, 2), (3, 2), (2, 3)):
        gem.putpixel((x, y), (200, 255, 240, 235))
    core = Image.new("RGBA", (8, 8), (235, 255, 240, 255))
    for i in range(8):
        for x, y in ((i, 0), (i, 7), (0, i), (7, i)):
            core.putpixel((x, y), (150, 245, 215, 255))
    for name, img in (("glass", glass), ("gem", gem), ("core", core)):
        cx, cy = CELL[name]
        im.paste(img, (cx * 8, cy * 8))
    return im


def draw_glow():
    """The halo round the orb (textures/item/orb_glow.png, drawn added to the screen): a soft round glow, white-blue in the middle, a strong blue at its edge."""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16.0
            a = max(0.0, 1.0 - d) ** 2.2
            im.putpixel((x, y), (int(50 + 150 * a), int(100 + 120 * a), 255, int(255 * a)))
    return im


def box(frm, to, cell):
    cx, cy = CELL[cell]
    return {"from": frm, "to": to, "faces": {f: {"uv": [cx * 8, cy * 8, cx * 8 + 8, cy * 8 + 8], "texture": "#crystal"} for f in bbmodel.FACES}}


def shape():
    """Nested cubes about the middle (8, 8, 8): 10, 7 and 4 pixels across."""
    return [("outer", [box([3, 3, 3], [13, 13, 13], "glass")]), ("inner", [box([4.5, 4.5, 4.5], [11.5, 11.5, 11.5], "gem")]), ("core", [box([6, 6, 6], [10, 10, 10], "core")])]


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.6, 0.6, 0.6]}, "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}}


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


def main():
    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    draw_texture().save(TEX)
    draw_glow().save(os.path.join(os.path.dirname(TEX), "orb_glow.png"))
    if not os.path.exists(BB):
        bbmodel.write(BB, "warp_crystal", {"crystal": TEX}, shape(), display=display(), gui_light_front=False,
                      comment="Stand-in Warp Orb in the end crystal's spirit: nested cubes (outer glass, inner gem, core), one group each; the renderer turns them separately. Texture slot 'crystal' (see tools/make_warp_orb_model.py).")
    model = bbmodel.read(BB)
    for group in ("outer", "inner", "core"):
        dump(os.path.join(ASSETS, "models", "item", "pickup_warp_crystal_%s.json" % group), {
            "textures": {"crystal": "craftteamracing:item/warp_crystal", "particle": "craftteamracing:item/warp_crystal"},
            "elements": bbmodel.elements(model, group),
            "display": model["display"] or display(),
        })
    for n in range(16):
        dump(os.path.join(ASSETS, "models", "item", "pickup_warp_orb_%d.json" % n), {"elements": []})
        dump(os.path.join(ASSETS, "models", "item", "pickup_warp_orb_solid_%d.json" % n), {"elements": []})          # a pack's original has the star
    print("warp orb models written")


if __name__ == "__main__":
    main()
