"""
A one-off refinement of the kart's seat, exhausts and steering column (the pictures and the model), run once and idempotent (a marker in the bbmodel says it was done):

  python tools/refine_kart_texture.py

What it does, in place, to blockbench/kart/kart_ctr.bbmodel and textures/item/kart_ctr.png (both are the author's after the first placeholder, so only these parts are touched):
- Texel density: the wheels are the reference (their face is 4.6 texels to a model pixel); the seat was 6.4 and the exhausts 5.3, which read as finer than everything else. The faces of the seat
  and the exhausts are mapped onto a smaller part of their tile (0.72 and 0.75 of it), so the same picture is 4.6 and 4.0 texels to a pixel.
- The seat's picture is repainted in that part: a vertical gradient (lighter at the top, darker at the bottom) and a soft vignette so the cushion looks padded, quilted diamonds with buttons
  where the stitching crosses and a puff in each diamond, and a rim of piping with stitching and a woven grain instead of a solid colour. Grey scale, as before (the kart's colour tints it).
- The exhausts' pipe and open end are repainted at the new density, with bands round the pipe.
- The steering column gets what a column has: a boot where it comes out of the body, a collar, a joint under the wheel with a pin on each side (cubes named column_*, in the group of steering_0;
  they stay with the kart, the wheel turns on its own).
Then run tools/make_kart_ctr.py to regenerate the model.

The picture work is done and is now part of tools/make_kart_texture.py (it paints the seat and the exhausts with the functions here, into the layered krita/items/kart_ctr.kra); the model
changes are recorded in the bbmodel. Do not run this script again on the picture: textures/item/kart_ctr.png is built from the .kra.
"""
import base64
import copy
import json
import os
import uuid

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "craftteamracing", "textures", "item", "kart_ctr.png")
BB = os.path.join(ROOT, "blockbench", "kart", "kart_ctr.bbmodel")
SEAT_SCALE = 0.72
EXHAUST_SCALE = 0.75
U = 8


def paint_seat(rng):
    n = 46
    a = np.zeros((n, n, 4), dtype=np.int32)
    rim = 5
    for y in range(n):
        for x in range(n):
            d = min(x, y, n - 1 - x, n - 1 - y)
            v = 238.0 - 30.0 * (y / (n - 1)) ** 1.3                    # lighter at the top, darker at the bottom
            if d < rim:
                # the piping: woven grain, a darker outer edge, a stitched line, a highlight where it meets the cushion
                v = 218.0 - 8.0 * (y / (n - 1)) + (6 if (x + y) % 2 == 0 else -4)
                if d == 0:
                    v -= 26
                elif d == 1:
                    v -= 8
                elif d == 2 and (x // 3 + y // 3) % 2 == 0:
                    v -= 52                                              # the stitches
                elif d == rim - 1:
                    v += 24
            else:
                if d < 14:
                    v -= (14 - d) * 1.1                                  # the vignette: the cushion is fuller in the middle
                u, w = x - rim, y - rim
                line = min((u + w) % 10, 10 - (u + w) % 10, (u - w) % 10, 10 - (u - w) % 10)
                if line == 0:
                    v -= 44
                elif line == 1:
                    v -= 14
                du = ((u + w) % 10) - 5
                dw = ((u - w) % 10) - 5
                v += 9.0 * max(0.0, 1.0 - (abs(du) + abs(dw)) / 7.0)    # a puff in each diamond
                if u % 5 == 0 and w % 5 == 0 and (u + w) % 10 == 0:
                    v -= 52                                              # a button where the stitches cross
                if (u - 1) % 5 == 0 and (w - 1) % 5 == 0 and (u + w - 2) % 10 == 0:
                    v += 12
                v += rng.integers(-3, 4)
            g = int(max(0, min(255, v)))
            a[y, x] = (g, g, min(255, g + 3), 255)
    return a


def paint_exhaust(rng):
    w, h = 12, 24
    a = np.zeros((h, w, 4), dtype=np.int32)
    for y in range(h):
        for x in range(w):
            k = abs((x + 0.5) / (w / 2.0) - 1.0)
            v = 176 - 70 * k * k
            if y % 8 == 0:
                v -= 32                                                  # a band round the pipe
            elif y % 8 == 1:
                v += 10
            r, g, b = v, v + 3, v + 12
            if y < 4:
                b += 30 * (1 - y / 4.0)
                r -= 25 * (1 - y / 4.0)                                   # heat-tinted near the open end
            a[y, x] = (int(max(0, min(255, r))), int(max(0, min(255, g))), int(max(0, min(255, b))), 255)
    return a


def paint_exhaust_end():
    n = 12
    a = np.zeros((n, n, 4), dtype=np.int32)
    for y in range(n):
        for x in range(n):
            r = np.hypot(x + 0.5 - n / 2.0, y + 0.5 - n / 2.0)
            col = (140, 144, 154)
            if r < 4.9:
                col = (108, 112, 122)
            if r < 3.7:
                col = (16, 16, 20)
            if 3.7 <= r < 4.2:
                col = (74, 76, 84)
            a[y, x] = col + (255,)
    return a


def put(img, texel_x, texel_y, arr):
    h, w = arr.shape[:2]
    img[texel_y:texel_y + h, texel_x:texel_x + w] = arr


def scale_uv(uv, ox, oy, s):
    return [round(ox + (uv[0] - ox) * s, 4), round(oy + (uv[1] - oy) * s, 4), round(ox + (uv[2] - ox) * s, 4), round(oy + (uv[3] - oy) * s, 4)]


def boxes(group_name_ref, template):
    """The column's extra parts, in the rod's own frame (the rod is x 7 to 9, y 2.5 to 4.5, z -1 to 7, turned -45 degrees about its low end): a boot, a collar, a joint and two pins."""
    def part(name, frm, to, tile):
        e = copy.deepcopy(template)
        e["name"] = name
        e["from"] = frm
        e["to"] = to
        e["uuid"] = str(uuid.uuid4())
        for f in e["faces"].values():
            f["uv"] = list(tile)
        return e
    column, edge = (8, 12, 10, 14), (8, 14, 10, 16)
    return [
        part("column_boot", [6.2, 1.8, -1.3], [9.8, 5.2, 1.0], edge),
        part("column_boot_lip", [6.6, 2.2, 1.0], [9.4, 4.8, 1.5], column),
        part("column_collar", [6.5, 2.0, 2.6], [9.5, 5.0, 3.4], edge),
        part("column_joint", [6.3, 1.9, 6.1], [9.7, 5.1, 7.4], edge),
        part("column_pin_l", [5.6, 3.0, 6.5], [6.3, 4.0, 7.1], column),
        part("column_pin_r", [9.7, 3.0, 6.5], [10.4, 4.0, 7.1], column),
    ]


def main():
    with open(BB, encoding="utf-8") as f:
        m = json.load(f)
    done = m.get("meta", {}).get("ctr_refined", 0)
    rng = np.random.default_rng(5)
    img = np.array(Image.open(TEX).convert("RGBA"), dtype=np.int32)
    if done < 1:
        # the pictures
        seat = paint_seat(rng)
        img[0:64, 64:128] = (0, 0, 0, 0)
        put(img, 64, 0, seat)
        ex = paint_exhaust(rng)
        img[64:96, 96:112] = (0, 0, 0, 0)
        put(img, 96, 64, ex)
        end = paint_exhaust_end()
        img[64:80, 112:128] = (0, 0, 0, 0)
        put(img, 112, 64, end)
        Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(TEX)
        # the uv of the faces onto the smaller parts
        for e in m["elements"]:
            name = str(e.get("name", ""))
            for face in e.get("faces", {}).values():
                uv = face.get("uv")
                if not uv:
                    continue
                if name.startswith("seat_") and uv[0] >= 8 and uv[1] < 8:
                    face["uv"] = scale_uv(uv, 8, 0, SEAT_SCALE)
                elif name.startswith("exhaust_") and uv[0] >= 14 and 8 <= uv[1] < 10:
                    face["uv"] = scale_uv(uv, 14, 8, EXHAUST_SCALE)
                elif name.startswith("exhaust_") and 12 <= uv[0] < 14 and 8 <= uv[1] < 12:
                    face["uv"] = scale_uv(uv, 12, 8, EXHAUST_SCALE)
        # the column
        rod = next(e for e in m["elements"] if e.get("name") == "steering_0")
        extra = boxes("steering_0", rod)
        m["elements"].extend(extra)

        def add(nodes):
            for n in nodes:
                if isinstance(n, dict):
                    if rod["uuid"] in n.get("children", []):
                        n["children"].extend(e["uuid"] for e in extra)
                        return True
                    if add(n.get("children", [])):
                        return True
            return False
        if not add(m["outliner"]):
            m["outliner"].extend(e["uuid"] for e in extra)
        m.setdefault("meta", {})["ctr_refined"] = 1
    # the model carries its own copy of the picture
    with open(TEX, "rb") as f:
        src = "data:image/png;base64," + base64.b64encode(f.read()).decode()
    for t in m["textures"]:
        if t.get("name") == "kart_ctr.png":
            t["source"] = src
    with open(BB, "w", encoding="utf-8", newline="\n") as f:
        json.dump(m, f, indent=1)
    print("kart refined" if done < 1 else "kart already refined (picture copy refreshed)")


if __name__ == "__main__":
    main()
