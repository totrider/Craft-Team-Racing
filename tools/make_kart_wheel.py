"""
The kart's wheel: one TEXTURED model, drawn four times by the renderer (it spins, and the front pair steers, so it cannot be part of the kart's own model).

  python tools/make_kart_wheel.py

blockbench/kart/kart_wheel.bbmodel is the source (written once from the shape below if it is missing, then it is yours; this script only reads it) and
src/main/resources/assets/craftteamracing/models/item/kart_wheel.json is generated from it. The texture, the layered krita/items/kart_wheel.kra (a modder's template; tools/build_art.py flattens it into textures/item/kart_wheel.png, 64 x 64),
has two pictures: the wheel's FACE (a disc: tyre rim, wheel, hub and lug nuts, in the top left 32 x 32; the corners outside the octagon are see-through) and the TREAD (16 x 16,
next to it). Both are painted by uv, so a finer texture works too: uv is 0-16 over the whole picture.

The shape is an octagonal prism along x (the axle) of four flat slabs at 0, 90 and +-45 degrees (the only angles a block model takes): a regular octagon is the union of four rectangles as
long as it is wide between flats and 0.414 of that across, at 45 degree steps, and their ends are its eight sides, which carry the tread. The two faces are a single textured plane each,
a hair outside the slabs' caps, the picture's own outline (alpha) cutting the octagon: so the wheel looks as the face picture says, not as boxes. The model's middle (8, 8, 8) is the
wheel's middle: the renderer puts that on the axle. Size from the author's kart_ctr model: 7 pixels across, 3 wide.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402
import kra_layers  # noqa: E402
import kra_stage  # noqa: E402
import kra_stamp  # noqa: E402
import make_kart as base  # noqa: E402

BB = os.path.join("blockbench", "kart", "kart_wheel.bbmodel")
OUT = os.path.join(base.ASSETS, "models", "item", "kart_wheel.json")
TEX = os.path.join(base.ASSETS, "textures", "item", "kart_wheel.png")
D = 7.0                                    # across the flats, pixels
THICK = 3.0
S = D * (math.sqrt(2.0) - 1.0)
FACE_UV = [0, 0, 8, 8]                     # the face picture's place in the 0-16 uv space (the top left quarter of the texture)
TREAD_UV = [8, 0, 12, 4]


def texture_sheet():
    """The placeholder picture as layers: 64 x 64, the face (32 x 32) top left in its drawing steps (tyre, sidewall, rim, dish, hub, lug nuts), the tread (16 x 16) beside it."""
    sheet = kra_layers.Sheet(64, 64)
    n = 32
    ys, xs = np.mgrid[0:n, 0:n]
    x = (xs + 0.5 - n / 2) / (n / 2)
    y = (ys + 0.5 - n / 2) / (n / 2)
    inside = (np.abs(x) <= 1) & (np.abs(y) <= 1) & (np.abs(x) + np.abs(y) <= math.sqrt(2.0))
    r = np.hypot(x, y)
    face = np.zeros((n, n, 4), dtype=np.uint8)
    fs = kra_stage.Stages(n, n)
    face[inside] = (26, 26, 30, 255)                                            # the tyre
    fs.snap("tyre", face)
    face[inside & (r < 0.80)] = (38, 38, 44, 255)                               # the sidewall, a shade lighter
    fs.snap("sidewall", face)
    face[inside & (r < 0.62)] = (150, 154, 164, 255)                            # the rim
    fs.snap("rim", face)
    face[inside & (r < 0.55)] = (112, 116, 126, 255)                            # the rim's dish
    fs.snap("rim dish", face)
    face[inside & (r < 0.22)] = (170, 174, 184, 255)                            # the hub
    fs.snap("hub", face)
    for k in range(5):                                                           # five lug nuts
        a = 2.0 * math.pi * k / 5.0 + math.pi / 2.0
        d = np.hypot(x - 0.38 * math.cos(a), y - 0.38 * math.sin(a))
        face[inside & (d < 0.085)] = (60, 62, 70, 255)
    fs.snap("lug nuts", face)
    kra_layers.place(sheet, fs.layers, (0, 0), "wheel face: ")
    tread = np.zeros((16, 16, 4), dtype=np.uint8)
    ts = kra_stage.Stages(16, 16)
    tread[:] = (22, 22, 26, 255)
    ts.snap("rubber", tread)
    for v in range(16):
        if (v // 2) % 2 == 0:
            tread[v, 2:14] = (40, 40, 46, 255)                                   # the lugs, across the tread
    ts.snap("lugs", tread)
    kra_layers.place(sheet, ts.layers, (32, 0), "tread: ")
    return sheet


def draw_texture():
    return texture_sheet().flatten()


def slab(frm, to, rotation=None):
    """A slab of the tyre: the faces at its ends (the octagon's sides) show the tread, the rest too (they are inside the other slabs)."""
    face = {"uv": list(TREAD_UV), "texture": "#wheel"}
    el = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": {f: dict(face) for f in bbmodel.FACES}}
    if rotation:
        el["rotation"] = {"origin": [8, 8, 8], "axis": "x", "angle": rotation}
    return el


def plane(x, direction):
    h = D / 2.0
    return {"from": [round(x, 4), 8 - h, 8 - h], "to": [round(x, 4), 8 + h, 8 + h], "faces": {direction: {"uv": list(FACE_UV), "texture": "#wheel"}}}


def parts():
    h, s, t, eps = D / 2.0, S / 2.0, THICK / 2.0, 0.01
    tyre = [
        slab([8 - t, 8 - h, 8 - s], [8 + t, 8 + h, 8 + s]),
        slab([8 - t + eps, 8 - s, 8 - h], [8 + t - eps, 8 + s, 8 + h]),
        slab([8 - t + 2 * eps, 8 - h, 8 - s], [8 + t - 2 * eps, 8 + h, 8 + s], rotation=45),
        slab([8 - t + 3 * eps, 8 - h, 8 - s], [8 + t - 3 * eps, 8 + h, 8 + s], rotation=-45),
    ]
    faces = [plane(8 + t + 0.02, "east"), plane(8 - t - 0.02, "west")]
    return {"tyre": tyre, "faces": faces}


def main():
    kra_layers.write_if_stale("krita/items/kart_wheel.kra", texture_sheet(), kra_stamp.inputs_for(__file__))
    kra_layers.export_png("krita/items/kart_wheel.kra")      # the PNG now: a new bbmodel embeds it
    if not os.path.exists(BB):
        bbmodel.write(BB, "kart_wheel", {"wheel": TEX}, list(parts().items()), display=base.display(), gui_light_front=False,
                      comment="The kart's wheel: an octagonal prism along x (the axle) from four slabs at 0, 90 and +-45 degrees (the tyre, its eight sides carry the tread), and a textured plane "
                              "on each face carrying the wheel picture (the top left of textures/item/kart_wheel.png; the plane's alpha cuts the octagon). The middle (8, 8, 8) is the "
                              "wheel's middle; the game draws it four times, spinning, the front two also steering. Texture slot 'wheel'.")
    model = bbmodel.read(BB)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"textures": {"wheel": base.NS + ":item/kart_wheel", "particle": base.NS + ":item/kart_wheel"}, "elements": bbmodel.elements(model), "display": model["display"]}, f, indent=2)
    print("kart_wheel written")


if __name__ == "__main__":
    main()
