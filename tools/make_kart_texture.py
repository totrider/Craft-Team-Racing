"""
The kart's texture, and the uv mapping of blockbench/kart/kart_ctr.bbmodel onto it.

  python tools/make_kart_texture.py

Writes the layered krita/items/kart_ctr.kra (128 x 128, a modder's template: one layer per tile) and kart_ctr_wheel.kra (the steering wheel's own picture); tools/build_art.py flattens them
into textures/item/kart_ctr.png and kart_ctr_wheel.png. It also, ONCE, maps the faces of the cubes in
kart_ctr.bbmodel onto it by their names (it does nothing to a model whose texture is already kart_ctr.png: after that the uvs are yours). The picture is 16 x 16 uv units, 8 texels to a
unit; each part has a tile:

  tintable (grey scale, multiplied by the kart's colour in the game: tintindex 0 the body colour, 1 the seat's)       tub_*  body panel       strut_*  bar with clamp rings       seat_*  quilted seat
  fixed colours                                                                       engine_*  vents   exhaust_*  pipe and its open end   steering_0  column   steering_1  the wheel

The steering wheel is the oblong, flat bottomed kind of a racing car, drawn on both big faces of its plate with see-through openings (the game draws cutouts), carbon centre, suede
grips; the plate's edges are dark.
"""
import base64
import io
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stage  # noqa: E402
import kra_stamp  # noqa: E402
import refine_kart_texture as refine  # noqa: E402  (the seat's and the exhausts' pictures, painted at the wheels' texel density)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "craftteamracing", "textures", "item", "kart_ctr.png")
BB = os.path.join(ROOT, "blockbench", "kart", "kart_ctr.bbmodel")
N = 128
U = 8                       # texels to a uv unit

# tile -> (u0, v0, u1, v1) in uv units
REGIONS = {
    "body": (0, 0, 8, 8),
    "seat": (8, 0, 16, 8),
    "strut": (0, 8, 8, 16),
    "engine": (8, 8, 12, 12),
    "exhaust": (12, 8, 14, 12),
    "exhaust_end": (14, 8, 16, 10),
    "column": (8, 12, 10, 14),
    "edge": (8, 14, 10, 16),
    "wheel": (10, 12, 16, 16),
}


def px(region):
    u0, v0, u1, v1 = region
    return u0 * U, v0 * U, u1 * U, v1 * U


def noise(rng, shape, amp):
    return rng.integers(-amp, amp + 1, size=shape)


TILE_NAMES = {REGIONS["body"]: "body panel (tintable)", REGIONS["seat"]: "seat (tintable)", REGIONS["strut"]: "strut (tintable)", REGIONS["engine"]: "engine",
              REGIONS["exhaust"]: "exhaust pipe", REGIONS["exhaust_end"]: "exhaust end", REGIONS["column"]: "steering column", REGIONS["edge"]: "steering plate edge"}


REFINED = {REGIONS["seat"], REGIONS["exhaust"], REGIONS["exhaust_end"]}          # these three tiles are repainted below (the first draw only keeps the random numbers in step)


def paint_layers():
    """The kart's picture as layers, one per tile (the steering wheel's tile in its drawing steps): a list of (name, 128 x 128 image), bottom first."""
    rng = np.random.default_rng(3)
    im = np.zeros((N, N, 4), dtype=np.int32)
    im[:, :, 3] = 0
    stages = kra_stage.Stages(N, N)

    def put(region, arr):
        x0, y0, x1, y1 = px(region)
        im[y0:y1, x0:x1] = arr
        if region in TILE_NAMES and region not in REFINED:
            view = im.copy()
            for r in REFINED:                                # the first drawing of the repainted tiles is not a layer
                rx0, ry0, rx1, ry1 = px(r)
                view[ry0:ry1, rx0:rx1] = 0
            stages.snap(TILE_NAMES[region], view)

    def solid(w, h, rgb):
        a = np.zeros((h, w, 4), dtype=np.int32)
        a[:, :] = list(rgb) + [255]
        return a

    # body panel (tintable): light grey, a rim, rivets, one seam
    t = solid(64, 64, (234, 234, 238))
    t[:, :, :3] += noise(rng, (64, 64, 1), 3)
    t[0:2, :, :3] = 206
    t[-2:, :, :3] = 206
    t[:, 0:2, :3] = 206
    t[:, -2:, :3] = 206
    t[31:33, :, :3] = 218
    for cx in (6, 57):
        for cy in (6, 57):
            t[cy - 1:cy + 2, cx - 1:cx + 2, :3] = 190
            t[cy, cx, :3] = 245
    put(REGIONS["body"], t)

    # seat (tintable): a padded cushion with stitched diamonds and a lighter bolster round the edge
    t = solid(64, 64, (224, 224, 228))
    t[:, :, :3] += noise(rng, (64, 64, 1), 3)
    for i in range(-64, 128, 16):
        for k in range(64):
            for (x, y) in ((k, (k + i) % 64), (k, (-k + i) % 64)):
                t[y, x, :3] = 186
    t[0:6, :, :3] = 240
    t[-6:, :, :3] = 240
    t[:, 0:6, :3] = 240
    t[:, -6:, :3] = 240
    put(REGIONS["seat"], t)

    # strut (tintable): a round bar seen side on (darker towards both edges) with a clamp ring now and then
    t = solid(64, 64, (236, 236, 240))
    for y in range(64):
        k = abs((y + 0.5) / 32.0 - 1.0)
        t[y, :, :3] = int(236 - 54 * k * k)
    for x in list(range(10, 16)) + list(range(48, 54)):
        t[:, x, :3] = (t[:, x, :3] * 0.82).astype(np.int32)
    put(REGIONS["strut"], t)

    # engine (fixed): dark metal with cooling slats and bolts
    t = solid(32, 32, (62, 64, 70))
    t[:, :, :3] += noise(rng, (32, 32, 1), 3)
    for y in range(6, 27, 4):
        t[y, 3:29, :3] = (96, 98, 106)
        t[y + 1, 3:29, :3] = (40, 41, 46)
    for cx in (3, 28):
        for cy in (3, 28):
            t[cy - 1:cy + 1, cx - 1:cx + 1, :3] = (140, 142, 150)
    put(REGIONS["engine"], t)

    # exhaust (fixed): a metal pipe, brighter down its middle, heat-tinted near its end
    t = solid(16, 32, (150, 154, 164))
    for x in range(16):
        k = abs((x + 0.5) / 8.0 - 1.0)
        t[:, x, :3] = int(176 - 70 * k * k)
    t[0:6, :, 2] = np.clip(t[0:6, :, 2] + 30, 0, 255)
    t[0:6, :, 0] = np.clip(t[0:6, :, 0] - 25, 0, 255)
    put(REGIONS["exhaust"], t)
    t = solid(16, 16, (140, 144, 154))
    yy, xx = np.mgrid[0:16, 0:16]
    r = np.hypot(xx + 0.5 - 8, yy + 0.5 - 8)
    t[r < 6.5, :3] = (108, 112, 122)
    t[r < 5.0, :3] = (16, 16, 20)
    put(REGIONS["exhaust_end"], t)

    # steering column and the plate's edge
    t = solid(16, 16, (68, 70, 78))
    t[:, 6:9, :3] = (96, 98, 108)
    put(REGIONS["column"], t)
    put(REGIONS["edge"], solid(16, 16, (30, 30, 34)))

    # the wheel: 48 x 32, an oblong racing wheel (grips at the sides with horns at the top, a carbon plate narrow at the top, a flat bottom bar), the openings see-through
    W, H = 48, 32
    ys, xs = np.mgrid[0:H, 0:W]
    x = (xs + 0.5) / W
    y = (ys + 0.5) / H

    def poly(points):
        mask = np.zeros((H, W), dtype=bool)
        n = len(points)
        for yy_ in range(H):
            for xx_ in range(W):
                px_, py_ = (xx_ + 0.5) / W, (yy_ + 0.5) / H
                inside = False
                j = n - 1
                for i in range(n):
                    xi, yi = points[i]
                    xj, yj = points[j]
                    if (yi > py_) != (yj > py_) and px_ < (xj - xi) * (py_ - yi) / (yj - yi) + xi:
                        inside = not inside
                    j = i
                mask[yy_, xx_] = inside
        return mask

    suede = (26, 26, 28)
    grip_l = poly([(0.02, 0.18), (0.10, 0.06), (0.20, 0.10), (0.20, 0.30), (0.26, 0.70), (0.30, 0.94), (0.08, 0.94), (0.02, 0.60)])
    grip_r = poly([(0.98, 0.18), (0.90, 0.06), (0.80, 0.10), (0.80, 0.30), (0.74, 0.70), (0.70, 0.94), (0.92, 0.94), (0.98, 0.60)])
    bottom = poly([(0.20, 0.80), (0.80, 0.80), (0.82, 0.96), (0.18, 0.96)])
    plate = poly([(0.36, 0.14), (0.64, 0.14), (0.78, 0.30), (0.80, 0.80), (0.20, 0.80), (0.22, 0.30)])
    wheel = np.zeros((H, W, 4), dtype=np.int32)
    ws = kra_stage.Stages(W, H)
    wheel[grip_l | grip_r | bottom, :3] = suede
    wheel[grip_l | grip_r | bottom, 3] = 255
    ws.snap("wheel: grips and bottom bar", wheel)
    weave = ((xs // 2 + ys // 2) % 2 == 0)
    wheel[plate & weave, :3] = (84, 88, 96)
    wheel[plate & ~weave, :3] = (56, 60, 68)
    wheel[plate, 3] = 255
    ws.snap("wheel: carbon plate", wheel)
    wheel[(abs(y - 0.22) < 0.03) & (abs(x - 0.5) < 0.12) & plate, :3] = (228, 228, 232)   # the maker's stripe on the narrow top of the plate (the 3D bar covers the middle)
    ws.snap("wheel: maker's stripe", wheel)
    for bx, col in ((0.32, (200, 40, 40)), (0.68, (230, 190, 40))):                         # two buttons
        b = np.hypot((xs + 0.5) / W - bx, ((ys + 0.5) / H - 0.70) * 0.66) < 0.035
        wheel[b, :3] = col
        wheel[b, 3] = 255
    ws.snap("wheel: buttons", wheel)
    put(REGIONS["wheel"], wheel)
    # the seat and the exhausts, repainted at the texel density of the wheels (the picture work of tools/refine_kart_texture.py, whose model changes are done): the seat's cushion with quilted
    # diamonds in the top left 46 x 46 of its tile, the pipe and its open end smaller than their tiles
    rng5 = np.random.default_rng(5)
    view = im.copy()
    for r in REFINED:
        rx0, ry0, rx1, ry1 = px(r)
        view[ry0:ry1, rx0:rx1] = 0
    for region, arr, name in ((REGIONS["seat"], refine.paint_seat(rng5), "seat (tintable)"), (REGIONS["exhaust"], refine.paint_exhaust(rng5), "exhaust pipe"),
                              (REGIONS["exhaust_end"], refine.paint_exhaust_end(), "exhaust end")):
        x0, y0, x1, y1 = px(region)
        refine.put(view, x0, y0, arr)
        stages.snap(name, view)
    sheet = kra_layers.Sheet(N, N)
    for name, img in stages.layers:
        sheet.add(name, img)
    x0, y0, _, _ = px(REGIONS["wheel"])
    kra_layers.place(sheet, ws.layers, (x0, y0))
    return sheet


def paint():
    """The kart's picture flattened (128 x 128)."""
    return paint_layers().flatten()


WHEEL_TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "craftteamracing", "textures", "item", "kart_ctr_wheel.png")
WHEEL_FRONT = (0, 0, 6, 4)          # the face towards the driver (uv units of the wheel picture, 8 texels to a unit): with the buttons and the maker's stripe
WHEEL_BACK = (0, 4, 6, 8)           # the face towards the front of the kart: the same wheel, plain


def wheel_face(buttons, ws=None):
    """48 x 32: an oblong racing wheel (grips at the sides with horns at the top, a carbon plate narrow at the top, a flat bottom bar); the openings are see-through. With {buttons} the
    shift lights, the maker's stripe, the buttons and the hub, as the driver sees it; without, the plain back. Detail: perforated, stitched grips with finger grooves and a lit edge; a
    carbon twill plate with a sheen, a bevel and bolts."""
    W, H = 48, 32
    ys, xs = np.mgrid[0:H, 0:W]
    x = (xs + 0.5) / W
    y = (ys + 0.5) / H

    def poly(points):
        mask = np.zeros((H, W), dtype=bool)
        n = len(points)
        for yy_ in range(H):
            for xx_ in range(W):
                px_, py_ = (xx_ + 0.5) / W, (yy_ + 0.5) / H
                inside = False
                j = n - 1
                for i in range(n):
                    xi, yi = points[i]
                    xj, yj = points[j]
                    if (yi > py_) != (yj > py_) and px_ < (xj - xi) * (py_ - yi) / (yj - yi) + xi:
                        inside = not inside
                    j = i
                mask[yy_, xx_] = inside
        return mask

    def erode(mask, k):
        out = mask.copy()
        for _ in range(k):
            m = out.copy()
            m[1:, :] &= out[:-1, :]
            m[:-1, :] &= out[1:, :]
            m[:, 1:] &= out[:, :-1]
            m[:, :-1] &= out[:, 1:]
            out = m
        return out

    grip_l = poly([(0.02, 0.18), (0.10, 0.06), (0.20, 0.10), (0.20, 0.30), (0.26, 0.70), (0.30, 0.94), (0.08, 0.94), (0.02, 0.60)])
    grip_r = poly([(0.98, 0.18), (0.90, 0.06), (0.80, 0.10), (0.80, 0.30), (0.74, 0.70), (0.70, 0.94), (0.92, 0.94), (0.98, 0.60)])
    bottom = poly([(0.20, 0.80), (0.80, 0.80), (0.82, 0.96), (0.18, 0.96)])
    plate = poly([(0.36, 0.14), (0.64, 0.14), (0.78, 0.30), (0.80, 0.80), (0.20, 0.80), (0.22, 0.30)])
    wheel = np.zeros((H, W, 4), dtype=np.int32)

    # the grips and the bottom bar: dark leather with perforations, finger grooves, a stitched seam inside the edge and a lit edge on the upper left
    grips = grip_l | grip_r | bottom
    inner1 = erode(grips, 1)
    inner2 = erode(grips, 2)
    base = np.where(((xs % 3 == 1) & (ys % 3 == 1)), 44, 27)                       # perforations
    base = base + np.where((ys % 6 == 0) & grips, -7, 0) + np.where((ys % 6 == 1) & grips, 4, 0)       # finger grooves
    base = base + np.round(12 * np.clip(1.3 - (x + y), 0, 1)).astype(int)         # lighter towards the upper left
    for c in range(3):
        wheel[grips, c] = base[grips] + (1 if c == 2 else 0)
    edge = grips & ~inner1
    lit = edge & ((xs + ys) < 26)
    wheel[lit, :3] = (78, 80, 88)
    wheel[edge & ~lit, :3] = (14, 14, 16)
    seam = inner1 & ~inner2
    dashes = seam & (((xs + ys) // 2) % 2 == 0)
    wheel[dashes, :3] = (88, 84, 76)                                                # the stitching
    wheel[grips, 3] = 255
    if ws:
        ws.snap("grips and bottom bar", wheel)

    # the carbon plate: a twill weave with a sheen, a bevelled edge and bolts
    weave = ((xs // 2 + ys // 2) % 2 == 0)
    twill = ((xs + ys) % 4 == 0)
    v = np.where(weave, 74, 50) + np.where(twill, 8, 0) + np.round(30 * np.clip(1.15 - (x * 0.8 + y * 1.2), 0, 1)).astype(int)
    pin = erode(plate, 1)
    for c in range(3):
        wheel[plate, c] = v[plate] + (4 if c == 2 else 0)
    pedge = plate & ~pin
    wheel[pedge & ((xs + ys) < 28), :3] = (120, 124, 134)
    wheel[pedge & ((xs + ys) >= 28), :3] = (24, 26, 30)
    wheel[plate, 3] = 255
    if ws:
        ws.snap("carbon plate", wheel)
    for bx, by in ((0.26, 0.74), (0.74, 0.74), (0.40, 0.20), (0.60, 0.20)):         # four bolts
        cx, cy = int(bx * W), int(by * H)
        if plate[cy, cx]:
            wheel[cy:cy + 2, cx:cx + 2, :3] = (150, 154, 164)
            wheel[cy + 1, cx + 1, :3] = (70, 72, 80)
            wheel[cy, cx, :3] = (210, 214, 224)
    if ws:
        ws.snap("bolts", wheel)

    if buttons:
        # shift lights along the narrow top of the plate: three green, two yellow, two red, with dark gaps
        for i, col in enumerate(((60, 220, 90), (60, 220, 90), (60, 220, 90), (240, 210, 50), (240, 210, 50), (230, 60, 50), (230, 60, 50))):
            lx = 17 + i * 2
            wheel[6:8, lx:lx + 1, :3] = col
        if ws:
            ws.snap("shift lights", wheel)
        wheel[(abs(y - 0.30) < 0.018) & (abs(x - 0.5) < 0.13) & plate, :3] = (226, 226, 232)      # the maker's stripe
        if ws:
            ws.snap("maker's stripe", wheel)
        for bx, col in ((0.34, (206, 44, 44)), (0.66, (236, 196, 48))):                          # two buttons, ringed, with a glint
            r = np.hypot((xs + 0.5) / W - bx, ((ys + 0.5) / H - 0.66) * 0.66)
            wheel[r < 0.048, :3] = (16, 16, 18)
            wheel[r < 0.037, :3] = col
            glint = (r < 0.037) & (xs + 0.5 < bx * W - 0.5) & (ys + 0.5 < 0.66 * H - 0.5)
            wheel[glint, :3] = (255, 250, 235)
        if ws:
            ws.snap("buttons", wheel)
        hub = np.hypot((xs + 0.5) / W - 0.5, ((ys + 0.5) / H - 0.50) * 0.66)             # the hub: a ringed disc with a small red mark
        wheel[hub < 0.05, :3] = (20, 22, 26)
        wheel[hub < 0.036, :3] = (96, 100, 110)
        wheel[hub < 0.014, :3] = (206, 44, 44)
        if ws:
            ws.snap("hub", wheel)
    return wheel


def paint_wheel_layers():
    """The steering wheel's own picture (128 x 128, uv 0-16) as layers: the driver's face top left, the back face under it, each in its drawing steps."""
    sheet = kra_layers.Sheet(N, N)
    for region, buttons, prefix in ((WHEEL_FRONT, True, "driver's face: "), (WHEEL_BACK, False, "back face: ")):
        x0, y0, x1, y1 = px(region)
        ws = kra_stage.Stages(x1 - x0, y1 - y0)
        wheel_face(buttons, ws)
        kra_layers.place(sheet, ws.layers, (x0, y0), prefix)
    return sheet


def paint_wheel():
    """The steering wheel's picture flattened (128 x 128)."""
    return paint_wheel_layers().flatten()


# the faces of a cube: the two sizes its picture has (px), by face
def face_sizes(frm, to):
    dx, dy, dz = (to[0] - frm[0]), (to[1] - frm[1]), (to[2] - frm[2])
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}


def window(region, w, h):
    u0, v0, u1, v1 = region
    return [u0, v0, round(u0 + min(max(w, 0.5), u1 - u0), 3), round(v0 + min(max(h, 0.5), v1 - v0), 3)]


def map_bbmodel(path):
    with open(path, encoding="utf-8") as f:
        d = json.load(f)
    tex = d["textures"][0]
    if tex.get("name") == "kart_ctr.png":
        print("the model is already on kart_ctr.png: its uvs are left as they are")
        return False
    changed = []
    for e in d["elements"]:
        name = str(e.get("name", ""))
        if name.startswith("guide_") or "marker" in name:
            continue
        sizes = face_sizes(e["from"], e["to"])
        for face, f in e["faces"].items():
            w, h = sizes[face]
            tint = -1
            if name.startswith("tub"):
                region, tint = REGIONS["body"], 0
            elif name.startswith("strut"):
                region, tint = REGIONS["strut"], 0
            elif name.startswith("seat"):
                region, tint = REGIONS["seat"], 1
            elif name.startswith("engine"):
                region = REGIONS["engine"]
            elif name.startswith("exhaust"):
                region = REGIONS["exhaust_end"] if face in ("up", "down") else REGIONS["exhaust"]
            elif name == "steering_0" or name.startswith("steering_0"):
                region = REGIONS["column"]
            elif name.startswith("steering_2"):
                region = REGIONS["edge"]                                # the 3D bar across the wheel
            elif name.startswith("steering_3"):
                region = REGIONS["column"]                              # the pivot box between the rod and the bar
            elif name.startswith("steering_1"):
                region = REGIONS["wheel"] if face in ("north", "south") else REGIONS["edge"]
                if face in ("north", "south"):
                    f["uv"] = list(region)
                    f["tint"] = -1
                    f["texture"] = 0
                    continue
            else:
                continue
            f["uv"] = window(region, w, h)
            f["tint"] = tint
            f["texture"] = 0
        changed.append(name)
    buf = io.BytesIO()
    Image.open(TEX).save(buf, "PNG")
    tex.update({"name": "kart_ctr.png", "relative_path": "../../src/main/resources/assets/craftteamracing/textures/item/kart_ctr.png", "width": N, "height": N, "uv_width": 16, "uv_height": 16,
                "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii")})
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(d, f, separators=(",", ":"), ensure_ascii=False)
    print("mapped:", ", ".join(changed))
    return True


def refresh_wheel_copy():
    """The model carries its own copy of the wheel's picture: refresh it (nothing else in the bbmodel is touched)."""
    with open(BB, encoding="utf-8") as f:
        d = json.load(f)
    with open(WHEEL_TEX, "rb") as f:
        src = "data:image/png;base64," + base64.b64encode(f.read()).decode("ascii")
    for t in d["textures"]:
        if t.get("name") == "kart_ctr_wheel.png":
            t["source"] = src
    with open(BB, "w", encoding="utf-8", newline="\n") as f:
        json.dump(d, f, indent=1)


def main():
    inputs = kra_stamp.inputs_for(__file__)
    for kra, sheet in (("krita/items/kart_ctr.kra", paint_layers()), ("krita/items/kart_ctr_wheel.kra", paint_wheel_layers())):
        kra_layers.write_if_stale(kra, sheet, inputs)
        kra_layers.export_png(kra)                           # the PNGs now, not at the end of the build: the bbmodel embeds them
    if "--repaint-wheel" in sys.argv:                        # put the current wheel picture into the copy the bbmodel carries
        refresh_wheel_copy()
        print("kart_ctr_wheel.png copied into the bbmodel")
        return
    map_bbmodel(BB)


if __name__ == "__main__":
    main()
