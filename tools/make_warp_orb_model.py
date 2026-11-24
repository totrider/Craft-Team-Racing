"""
The Warp Orb's stand-in model, in the spirit of Minecraft's end crystal: three nested cubes, a pale glass shell, a teal gem inside it and a bright core, which the renderer (client/WarpOrbRenderer)
turns corner-on, spins and bobs. It is only drawn when no resource pack brings the original's star (the pack has the 16 animation frames).

  python tools/make_warp_orb_model.py

Models are Blockbench's: blockbench/warp_orb/warp_crystal.bbmodel is the SOURCE (written once from the shape below if it is missing, then it is yours; this script only reads it). Three groups, one
per cube (outer, inner, core), each written as its own model (models/item/pickup_warp_crystal_<group>.json) because the renderer turns them separately. One texture slot, "crystal": 16 x 16 of
8 x 8 cells (glass shell, gem, core), the layered krita/items/warp_crystal.kra (and orb_glow.kra), written here (a modder's templates; tools/build_art.py flattens them into the PNGs). Also writes the 16 empty frame models (pickup_warp_orb_<n>, pickup_warp_orb_solid_<n>) that a pack's
star replaces. The model's middle (8, 8, 8) is the crystal's middle. Run from the project folder.
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "craftteamracing")
TEX = os.path.join(ASSETS, "textures", "item", "warp_crystal.png")
BB = os.path.join("blockbench", "warp_orb", "warp_crystal.bbmodel")
CELL = {"glass": (0, 0), "gem": (1, 0), "core": (0, 1)}


def texture_sheet():
    """The crystal's picture as layers: the glass shell (its pane, its edge and its corners), the gem (and its highlights) and the core (and its edge)."""
    sheet = kra_layers.Sheet(16, 16)
    pane, edge, corners = sheet.layer("glass pane"), sheet.layer("glass edge"), sheet.layer("glass corners")
    gem, shine = sheet.layer("gem"), sheet.layer("gem highlights")
    core, core_edge = sheet.layer("core"), sheet.layer("core edge")
    gx, gy = CELL["glass"][0] * 8, CELL["glass"][1] * 8
    border = {(i, 0) for i in range(8)} | {(i, 7) for i in range(8)} | {(0, i) for i in range(8)} | {(7, i) for i in range(8)}
    for y in range(8):
        for x in range(8):
            if (x, y) not in border:
                pane[gx + x, gy + y] = (175, 236, 236, 70)
    for x, y in sorted(border):
        edge[gx + x, gy + y] = (215, 250, 250, 235) if (x + y) % 3 else (160, 225, 230, 235)
    for x, y in ((0, 0), (7, 0), (0, 7), (7, 7)):
        corners[gx + x, gy + y] = (245, 255, 255, 255)
    mx, my = CELL["gem"][0] * 8, CELL["gem"][1] * 8
    highlights = {(2, 2), (3, 2), (2, 3)}
    for y in range(8):
        for x in range(8):
            t = (x + y) / 14.0
            if (x, y) in highlights:
                shine[mx + x, my + y] = (200, 255, 240, 235)                 # a semi-transparent highlight replaces the gem there
            else:
                gem[mx + x, my + y] = (int(110 - 80 * t), int(235 - 110 * t), int(220 - 70 * t), 235)
    cx, cy = CELL["core"][0] * 8, CELL["core"][1] * 8
    for y in range(8):
        for x in range(8):
            core[cx + x, cy + y] = (235, 255, 240, 255)
    for x, y in sorted(border):
        core_edge[cx + x, cy + y] = (150, 245, 215, 255)
    return sheet


def draw_texture():
    return texture_sheet().flatten()


def glow_sheet():
    """The halo round the orb (textures/item/orb_glow.png, drawn added to the screen): a soft round glow, white-blue in the middle, a strong blue at its edge."""
    sheet = kra_layers.Sheet(32, 32)
    px = sheet.layer("glow")
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16.0
            a = max(0.0, 1.0 - d) ** 2.2
            px[x, y] = (int(50 + 150 * a), int(100 + 120 * a), 255, int(255 * a))
    return sheet


def draw_glow():
    return glow_sheet().flatten()


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
    inputs = kra_stamp.inputs_for(__file__)
    for kra, sheet in (("krita/items/warp_crystal.kra", texture_sheet()), ("krita/items/orb_glow.kra", glow_sheet())):
        kra_layers.write_if_stale(kra, sheet, inputs)
        kra_layers.export_png(kra)                           # the PNGs now: a new bbmodel embeds them
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
