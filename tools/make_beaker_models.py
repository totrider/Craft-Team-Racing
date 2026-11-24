"""
N. Brio's Beaker, the mod's own 3D model: a round flask with a flat base, a wide bulb, a long narrow neck and a flared lip, one green and one red (the charged beaker).

  python tools/make_beaker_models.py

The model is Blender's: blender/beaker/beaker.blend (made once by this script from tools/blender/blender_create_beaker.py, yours after that) holds the collections beaker_green and beaker_red, exported
by tools/export_blender_models.py to models/item/beaker_<colour>.obj + pickup_beaker_<colour>.json (the beaker entity renderer fetches those baked models; a resource pack's original
replaces them under the same names). The shape follows the original's flask measured from its model: 0.49 wide, 0.64 high, a base 0.28 across, the bulb widest a third of the way up,
a neck 0.18 across from 0.38 to 0.57 and a lip 0.30 across. The texture, the layered krita/items/beaker.kra (a modder's template; tools/build_art.py flattens it into textures/item/beaker.png), is drawn here every run (a palette of 4 x 4 texel cells: the top two rows are the green and red
sets: liquid, shade, light, glass; edit the code, not the PNG). The bubbles that rise in it are billboards (client/BeakerBubbles): textures/entity/beaker_<colour>_bubbles.png and
beaker/beaker_<colour>_bubbles.json are drawn here every run. Run from the project folder.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "craftteamracing")
TEX = os.path.join(ASSETS, "textures", "item", "beaker.png")
BB = os.path.join("blockbench", "pickups")

# cell (column, row) -> rgb; every cell is 4 x 4 texels of the 16 x 16 sheet
PALETTE = {
    "green": ((0, 0), (62, 200, 60)), "green_dark": ((1, 0), (30, 130, 45)), "green_light": ((2, 0), (160, 240, 120)), "green_glass": ((3, 0), (238, 244, 248)),
    "red": ((0, 1), (222, 52, 40)), "red_dark": ((1, 1), (140, 24, 26)), "red_light": ((2, 1), (255, 135, 112)), "red_glass": ((3, 1), (238, 244, 248)),
    "cork": ((0, 2), (156, 104, 56)), "outline": ((1, 2), (40, 52, 48)),
}


def alpha_of(name):
    """The glass and the liquid are see-through (as the original's beaker is, every texel of it), so the bubbles show through; the lip's cork and outline cells are solid."""
    return 150 if name.endswith("_glass") else 205 if name in ("green", "green_dark", "green_light", "red", "red_dark", "red_light") else 255


def texture_sheet():
    """The beaker's palette as layers, one per cell (4 x 4 texels each; the glass and the liquid are see-through)."""
    sheet = kra_layers.Sheet(16, 16)
    for name, ((cx, cy), rgb) in PALETTE.items():
        px = sheet.layer(name)
        for y in range(cy * 4, cy * 4 + 4):
            for x in range(cx * 4, cx * 4 + 4):
                px[x, y] = rgb + (alpha_of(name),)
    return sheet


def draw_texture():
    return texture_sheet().flatten()


def bubbles_sheet(colour):
    sheet = kra_layers.Sheet(36, 18)
    sheet.add("bubbles", draw_bubbles(colour))
    return sheet


def cell(name):
    (cx, cy), _ = PALETTE[name]
    return [cx * 4 + 0.25, cy * 4 + 0.25, cx * 4 + 3.75, cy * 4 + 3.75]


# The original's flask is 98.7 units tall and 53 wide at 108.8 units to the block: 0.907 x 0.49 blocks. The shapes below were drawn 14.75 x 10 px; they are fitted to it here
# (the bbmodels are patched the same way in place, so edits survive).
FIT_XZ = 0.49 * 16 / 10.0
FIT_Y = 0.907 * 16 / 14.75


def fit(frm, to):
    f = lambda v, k: (8.0 + (v - 8.0) * FIT_XZ) if k != 1 else v * FIT_Y
    return [f(frm[0], 0), f(frm[1], 1), f(frm[2], 2)], [f(to[0], 0), f(to[1], 1), f(to[2], 2)]






BUBBLE_TEX = os.path.join(ASSETS, "textures", "entity", "beaker_%s_bubbles.png")
BUBBLE_DATA = os.path.join(ASSETS, "beaker", "beaker_%s_bubbles.json")
FRAMES = 15
# six bubbles: (rise phase in frames, x, z, size in blocks, bright?)
BUBBLES = [(0, -0.04, -0.02, 0.05, False), (3, 0.04, 0.0, 0.06, True), (6, 0.0, -0.04, 0.068, False), (8, 0.03, -0.02, 0.06, True), (11, -0.05, 0.0, 0.05, False), (13, 0.0, 0.04, 0.056, True)]


def draw_bubbles(colour):
    """The bubble sheet: a dark bubble and a bright glossy one side by side (18 x 18 texels each), in the beaker's colour."""
    liquid, dark, light = PALETTE[colour][1], PALETTE[colour + "_dark"][1], PALETTE[colour + "_light"][1]
    from PIL import ImageDraw
    n = 18 * 8
    im = Image.new("RGBA", (2 * n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for cell, bright in ((0, False), (1, True)):
        ox = cell * n
        d.ellipse([ox + 8, 8, ox + n - 8, n - 8], fill=(dark if not bright else liquid) + (255,), outline=(20, 30, 20, 255), width=8)
        if bright:
            d.ellipse([ox + 30, 28, ox + 60, 56], fill=light + (255,))
            d.ellipse([ox + 94, 96, ox + 110, 112], fill=light + (255,))
        else:
            d.ellipse([ox + 30, 28, ox + 52, 48], fill=liquid + (255,))
    return im.resize((2 * 18, 18), Image.LANCZOS)


def bubble_data(colour):
    """A generic rise through the flask: each bubble climbs from the liquid up the neck and out of the lip over the fifteen frames, quickly at first (as the original's do), and starts over."""
    out = []
    for phase, x, z, size, bright in BUBBLES:
        centres = []
        for f in range(FRAMES):
            tt = ((f + phase) % FRAMES) / (FRAMES - 1.0)
            centres.append([x * (1.0 if tt < 0.55 else 0.5), round(0.10 + 0.62 * (1.0 - (1.0 - tt) ** 2), 4), z * (1.0 if tt < 0.55 else 0.5)])   # up through the neck and out past the lip (0.64 of the block) before it starts over
        out.append({"uv": [0.5, 0.0, 1.0, 1.0] if bright else [0.0, 0.0, 0.5, 1.0], "width": size, "height": size, "centres": centres})
    return {"frames": FRAMES, "texture": "craftteamracing:textures/entity/beaker_%s_bubbles.png" % colour, "bubbles": out}


def main():
    import subprocess
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import export_blender_models as ebm
    inputs = kra_stamp.inputs_for(__file__)
    for colour in ("green", "red"):
        kra = "krita/entity/beaker_%s_bubbles.kra" % colour
        kra_layers.write_if_stale(kra, bubbles_sheet(colour), inputs)
        kra_layers.export_png(kra)
        os.makedirs(os.path.dirname(BUBBLE_DATA % colour), exist_ok=True)
        with open(BUBBLE_DATA % colour, "w", encoding="utf-8", newline="\n") as f:
            json.dump(bubble_data(colour), f, indent=1)
    kra_layers.write_if_stale("krita/items/beaker.kra", texture_sheet(), inputs)
    kra_layers.export_png("krita/items/beaker.kra")
    blend = os.path.join("blender", "beaker", "beaker.blend")
    if not os.path.exists(blend):
        os.makedirs(os.path.dirname(blend), exist_ok=True)
        exe = ebm.find_blender()
        if not exe:
            sys.exit("Blender not found (set BLENDER to its blender.exe): cannot create " + blend)
        subprocess.run([exe, "--background", "--factory-startup", "--python", os.path.join("tools", "blender", "blender_create_beaker.py"), "--", blend], check=True)
    ebm.export_all()
    print("beaker models written")


if __name__ == "__main__":
    main()
