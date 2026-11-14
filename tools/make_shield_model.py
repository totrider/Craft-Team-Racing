"""
The Power Shield bubble: the jar's stand-in 3D model (a translucent ball) and its texture.

  python tools/make_shield_model.py

The model is Blockbench's: blockbench/pickups/shield.bbmodel is the source (written once from the shape below if it is missing, then it is yours; this script only reads it). Writes
src/main/resources/assets/ctrcraft/models/item/pickup_shield.json and, once if missing, textures/item/shield.png (a 16 x 16 pale, see-through picture with a brighter patch, yours to
repaint; the game tints it green or blue and scales it). The ball is built as the Bomb's is: five octagonal prisms along x narrowing to the poles, each of four slabs (0, 90 and +-45
degrees), so it reads round from every side. The model's middle (8, 8, 8) is the ball's middle and it is 12 px across; the renderer scales it (client/ShieldBubble). The original's
bubble is three layered models (a coloured one added to the screen, a dark one taken from it and a highlight that sweeps over it) in the local resource pack when that is built.
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "ctrcraft")
TEX = os.path.join(ASSETS, "textures", "item", "shield.png")
OUT = os.path.join(ASSETS, "models", "item", "pickup_shield.json")
BB = os.path.join("blockbench", "pickups", "shield.bbmodel")

RINGS = [(2.0, 3.3, 6.0), (3.3, 5.0, 10.0), (5.0, 11.0, 12.0), (11.0, 12.7, 10.0), (12.7, 14.0, 6.0)]


def draw_texture():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            a = 150 + (40 if (x // 4 + y // 4) % 2 == 0 else 0)
            im.putpixel((x, y), (235, 245, 235, a))
    for y in range(2, 6):
        for x in range(2, 7):
            im.putpixel((x, y), (255, 255, 255, 235))              # the highlight
    return im


def box(frm, to, rotation=None):
    face = {"uv": [0, 0, 16, 16], "texture": "#shield"}
    el = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": {f: dict(face) for f in bbmodel.FACES}}
    if rotation:
        el["rotation"] = {"origin": [8, 8, 8], "axis": "x", "angle": rotation}
    return el


def parts():
    eps = 0.02
    slabs = []
    for x0, x1, d in RINGS:
        h, s = d / 2.0, d * (math.sqrt(2.0) - 1.0) / 2.0
        slabs += [
            box([x0, 8 - h, 8 - s], [x1, 8 + h, 8 + s]),
            box([x0 + eps, 8 - s, 8 - h], [x1 - eps, 8 + s, 8 + h]),
            box([x0 + 2 * eps, 8 - h, 8 - s], [x1 - 2 * eps, 8 + h, 8 + s], rotation=45),
            box([x0 + 3 * eps, 8 - h, 8 - s], [x1 - 3 * eps, 8 + h, 8 + s], rotation=-45),
        ]
    return {"bubble": slabs}


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.6, 0.6, 0.6]}, "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}}


def main():
    if not os.path.exists(TEX):
        os.makedirs(os.path.dirname(TEX), exist_ok=True)
        draw_texture().save(TEX)
    if not os.path.exists(BB):
        bbmodel.write(BB, "shield", {"shield": TEX}, list(parts().items()), display=display(), gui_light_front=False,
                      comment="Stand-in Power Shield bubble: a ball of five octagonal prisms along x (12 px across), middle (8, 8, 8). The game tints it green or blue, scales it "
                              "(grow, pulse, pop) and draws it see-through. Texture slot 'shield'. See tools/make_shield_model.py.")
    model = bbmodel.read(BB)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"textures": {"shield": "ctrcraft:item/shield", "particle": "ctrcraft:item/shield"}, "elements": bbmodel.elements(model), "display": model["display"] or display()}, f, indent=2)
    print("shield written")


if __name__ == "__main__":
    main()
