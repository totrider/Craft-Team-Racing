"""
The jar's own crate break-up (a stand-in): 18 splinters of wood thrown out of the crate over 12 frames, as the data the client's CrateBreakParticle plays:
  src/main/resources/assets/craftteamracing/textures/entity/crate_break.png   a small wood picture (orange planks with a dark grain)
  src/main/resources/assets/craftteamracing/crate_break/crate_break.json      {frames, texture, triangles: [{uv: [6 numbers], pos: [[9 numbers] per frame]}]}, blocks, the crate's
                                                                         middle at (0, 0.5, 0), its base on y = 0
The original's 22 pieces (the shared pack's crate explosion) replace both in the local resource pack made by tools/ctr_disc/extract_ctr_textures.py. Written once: delete a file to
have it made again. Deterministic (a fixed seed). Needs numpy and Pillow.
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "craftteamracing")
TEX = os.path.join(ASSETS, "textures", "entity", "crate_break.png")
DATA = os.path.join(ASSETS, "crate_break", "crate_break.json")
FRAMES = 12
COUNT = 18


def texture():
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    for y in range(16):
        for x in range(16):
            grain = 0.82 + 0.18 * math.sin(y * 1.9 + math.sin(x * 0.7) * 1.5)
            img[y, x] = (int(214 * grain), int(128 * grain), int(40 * grain), 255)
    return Image.fromarray(img, "RGBA")


def texture_sheet():
    """The splinters' wood picture (16 x 16), one layer: orange planks with a dark grain."""
    sheet = kra_layers.Sheet(16, 16)
    sheet.add("wood grain", texture())
    return sheet


def data():
    rng = np.random.default_rng(7)
    tris = []
    for _ in range(COUNT):
        start = rng.uniform(-0.4, 0.4, 3) + np.array([0.0, 0.5, 0.0])
        out = np.array([start[0], 0.0, start[2]])
        n = np.linalg.norm(out) or 1.0
        vel = out / n * rng.uniform(0.12, 0.3) + np.array([rng.uniform(-0.05, 0.05), rng.uniform(0.2, 0.42), rng.uniform(-0.05, 0.05)])
        size = rng.uniform(0.14, 0.26)
        base = np.array([[0.0, 0.0, 0.0], [size, 0.0, 0.0], [0.0, size * rng.uniform(0.6, 1.3), 0.0]])
        axis = rng.normal(size=3)
        axis /= np.linalg.norm(axis)
        spin = rng.uniform(0.3, 0.8) * rng.choice([-1, 1])
        pos = []
        for f in range(FRAMES):
            p = start + vel * f + np.array([0.0, -0.02, 0.0]) * f * f / 2.0
            p[1] = max(p[1], 0.02)
            a = spin * f
            k = np.array([[0, -axis[2], axis[1]], [axis[2], 0, -axis[0]], [-axis[1], axis[0], 0]])
            rot = np.eye(3) + math.sin(a) * k + (1 - math.cos(a)) * (k @ k)
            pts = (base - base.mean(axis=0)) @ rot.T + p
            pos.append([round(float(c), 4) for q in pts for c in q])
        tris.append({"uv": [0.1, 0.1, 0.9, 0.1, 0.1, 0.9], "pos": pos})
    return {"frames": FRAMES, "texture": "craftteamracing:textures/entity/crate_break.png", "triangles": tris}


def main():
    kra_layers.write_if_stale("krita/entity/crate_break.kra", texture_sheet(), kra_stamp.inputs_for(__file__))
    os.makedirs(os.path.dirname(DATA), exist_ok=True)
    if not os.path.exists(DATA):
        with open(DATA, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data(), f)


if __name__ == "__main__":
    main()
