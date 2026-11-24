"""
The Rocket (the original's Tracking Missile), the jar's stand-in 3D model and its texture, after Minecraft's firework rocket and in the original missile's proportions.

  python tools/make_missile_model.py

The model is Blockbench's: blockbench/pickups/missile.bbmodel is the SOURCE (written once from the shape below if it is missing, then it is yours; this script only reads it). Writes
src/main/resources/assets/craftteamracing/models/item/pickup_missile.json (the missile renderer fetches that model) and textures/item/missile.png (a 16 x 16 palette of flat 4 x 4 cells, drawn
every run: edit the code, not the PNG). The model's middle (8, 8, 8) is the rocket's middle; its nose points north (-z), the way the game turns it to face where it flies. It keeps the original
missile's size, 12.5 px long and 2.5 px through, and is built like the firework rocket item: a red stepped cone for a head, a pale blue band under it, a tube of red and white stripes, a dark
nozzle, and the thin brown stick at the tail (the item's stick and fuse).
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "craftteamracing")
TEX = os.path.join(ASSETS, "textures", "item", "missile.png")
OUT = os.path.join(ASSETS, "models", "item", "pickup_missile.json")
BB = os.path.join("blockbench", "pickups", "missile.bbmodel")

# the firework rocket's colours (as the item shows them), one 4 x 4 cell each
PALETTE = {
    "red": (0, 0, (185, 41, 41)),
    "red_dark": (4, 0, (153, 41, 41)),
    "white": (8, 0, (244, 244, 244)),
    "white_dark": (12, 0, (208, 214, 216)),
    "band": (0, 4, (178, 204, 209)),
    "nozzle": (4, 4, (45, 20, 9)),
    "stick": (8, 4, (122, 82, 48)),
    "spark": (12, 4, (255, 190, 70)),
}


def texture_sheet():
    """The palette as layers: the magenta of the unused cells, then one layer per colour cell (4 x 4 texels)."""
    sheet = kra_layers.Sheet(16, 16)
    sheet.add("unused cells (magenta)", Image.new("RGBA", (16, 16), (255, 0, 255, 255)))
    for name, (cx, cy, rgb) in PALETTE.items():
        px = sheet.layer(name)
        for y in range(cy, cy + 4):
            for x in range(cx, cx + 4):
                px[x, y] = rgb + (255,)
    return sheet


def draw_texture():
    return texture_sheet().flatten()


def box(frm, to, colour):
    cx, cy, _ = PALETTE[colour]
    face = {"uv": [cx + 1, cy + 1, cx + 3, cy + 3], "texture": "#missile"}
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": {f: dict(face) for f in bbmodel.FACES}}


def parts():
    """Nose to -z. A stepped red cone (z 1.8 to 4.2), the pale band, a tube of seven stripes, the nozzle and the stick out of it to z 14.3, with the fuse's spark at its end."""
    cone = [
        box([7.0, 7.0, 3.4], [9.0, 9.0, 4.2], "red"),
        box([7.3, 7.3, 2.6], [8.7, 8.7, 3.4], "red_dark"),
        box([7.65, 7.65, 1.8], [8.35, 8.35, 2.6], "red"),
    ]
    band = [box([6.8, 6.8, 4.2], [9.2, 9.2, 5.0], "band")]
    tube = []
    z = 5.0
    for i in range(7):
        tube.append(box([6.75, 6.75, z], [9.25, 9.25, z + 0.95], "red" if i % 2 == 0 else "white"))
        z += 0.95
    nozzle = [box([7.1, 7.1, 11.65], [8.9, 8.9, 12.4], "nozzle")]
    stick = [box([7.7, 7.7, 12.4], [8.3, 8.3, 14.3], "stick"), box([7.75, 7.75, 14.3], [8.25, 8.25, 14.55], "spark")]
    return {"cone": cone, "band": band, "tube": tube, "nozzle": nozzle, "stick": stick}


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.8, 0.8, 0.8]}, "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}}


def main():
    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    kra_layers.write_if_stale("krita/items/missile.kra", texture_sheet(), kra_stamp.inputs_for(__file__))
    kra_layers.export_png("krita/items/missile.kra")
    if not os.path.exists(BB):
        bbmodel.write(BB, "missile", {"missile": TEX}, list(parts().items()), display=display(), gui_light_front=False,
                      comment="Stand-in Rocket after Minecraft's firework rocket, in the original missile's proportions (12.5 x 2.5 px): red cone, pale band, red and white tube, nozzle, stick. "
                              "Nose to -z; the model's middle (8, 8, 8) is the rocket's middle. Palette cells in textures/item/missile.png, see tools/make_missile_model.py.")
    model = bbmodel.read(BB)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"textures": {"missile": "craftteamracing:item/missile", "particle": "craftteamracing:item/missile"}, "elements": bbmodel.elements(model), "display": model["display"] or display()}, f, indent=2)
    print("missile written")


if __name__ == "__main__":
    main()
