#!/usr/bin/env python3
"""Draws every recipe of the mod as a picture of a crafting grid (docs/img/recipes/<recipe name>.png), for the README and docs/ITEMS.md.

Icons are drawn from the models themselves, so nothing is a screenshot and nothing has to be kept up to date by hand:
  * a flat item (item/generated, item/handheld) is its texture;
  * a block or any model made of boxes is drawn isometrically, the way an inventory slot draws a block (top, south and east faces);
  * anything else (a mesh) gets a plain labelled tile.
Models and textures come from this mod's resources first; the vanilla ones (iron ingot, black wool...) are read from the Minecraft 1.21.1 client jar
that Gradle has already downloaded, so they stay on the machine and only the finished pictures are written to the repository.

Usage:  python tools/make_recipe_grids.py            (all recipes)
        python tools/make_recipe_grids.py wheel kart  (some, by recipe file name)
Needs Pillow.
"""
import io
import json
import math
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ASSET_ROOTS = [ROOT / "src/main/resources/assets", ROOT / "src/generated/resources/assets"]
DATA_ROOTS = [ROOT / "src/main/resources/data", ROOT / "src/generated/resources/data"]
RECIPES = ROOT / "src/main/resources/data/craftteamracing/recipe"
OUT = ROOT / "docs/img/recipes"
JAR = Path.home() / ".gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"

S = 2            # GUI pixel scale (a recipe picture is 292 px wide; every picture has the same size)
ICON = 16 * S    # an icon inside an 18 unit slot
VANILLA_TAGS = {"minecraft:planks": "minecraft:oak_planks", "minecraft:logs": "minecraft:oak_log"}
FLAT_PARENTS = ("item/generated", "item/handheld", "item/handheld_rod", "builtin/generated")

_jar = zipfile.ZipFile(JAR) if JAR.exists() else None


def split(ref, default="minecraft"):
    ns, _, path = ref.rpartition(":")
    return (ns or default), path


def read_asset(ns, kind, name):
    for root in ASSET_ROOTS:
        f = root / ns / kind / name
        if f.exists():
            return f.read_bytes()
    if _jar is not None and ns == "minecraft":
        try:
            return _jar.read(f"assets/{ns}/{kind}/{name}")
        except KeyError:
            return None
    return None


def load_model(ref):
    ns, path = split(ref)
    if path in FLAT_PARENTS:
        return {"flat": True, "textures": {}, "elements": None, "display": {}}
    raw = read_asset(ns, "models", path + ".json")
    if raw is None:
        return {"flat": False, "textures": {}, "elements": None, "display": {}}
    d = json.loads(raw)
    base = load_model(d["parent"]) if "parent" in d else {"flat": False, "textures": {}, "elements": None, "display": {}}
    return {"flat": base["flat"], "textures": {**base["textures"], **d.get("textures", {})},
            "elements": d.get("elements", base["elements"]), "display": {**base["display"], **d.get("display", {})}}


def resolve(textures, name):
    for _ in range(10):
        if not name.startswith("#"):
            return name
        name = textures.get(name[1:], "")
    return None


def load_texture(ref):
    ns, path = split(ref)
    raw = read_asset(ns, "textures", path + ".png")
    if raw is None:
        return None
    img = Image.open(io.BytesIO(raw)).convert("RGBA")
    if img.height > img.width:              # animated strip: first frame
        img = img.crop((0, 0, img.width, img.width))
    return img


def rot_xyz(v, rx, ry, rz):
    """JOML rotationXYZ, which is what Minecraft's item transforms use: a vector is turned about z, then y, then x."""
    x, y, z = v
    c, s_ = math.cos(rz), math.sin(rz)
    x, y = x * c - y * s_, x * s_ + y * c
    c, s_ = math.cos(ry), math.sin(ry)
    x, z = x * c + z * s_, -x * s_ + z * c
    c, s_ = math.cos(rx), math.sin(rx)
    y, z = y * c - z * s_, y * s_ + z * c
    return (x, y, z)


def rotate_axis(p, rot):
    """An element's own rotation: about its origin, on one axis."""
    o, ax, ang = rot["origin"], rot["axis"], math.radians(rot["angle"])
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s_ = math.cos(ang), math.sin(ang)
    if ax == "x":
        y, z = y * c - z * s_, y * s_ + z * c
    elif ax == "y":
        x, z = x * c + z * s_, -x * s_ + z * c
    else:
        x, y = x * c - y * s_, x * s_ + y * c
    return (x + o[0], y + o[1], z + o[2])


def draw_face(canvas, tex, uv, o, u, v, shade):
    """o, u, v: the screen points of the picture's top left, top right and bottom left corners."""
    w, h = tex.size
    u1, v1, u2, v2 = uv
    box = (min(u1, u2) / 16 * w, min(v1, v2) / 16 * h, max(u1, u2) / 16 * w, max(v1, v2) / 16 * h)
    crop = tex.crop(tuple(int(round(b)) for b in box))
    if crop.width == 0 or crop.height == 0:
        return
    if u2 < u1:
        crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
    if v2 < v1:
        crop = crop.transpose(Image.FLIP_TOP_BOTTOM)
    cw, ch = crop.size
    a, c = (u[0] - o[0]) / cw, (u[1] - o[1]) / cw
    b, d = (v[0] - o[0]) / ch, (v[1] - o[1]) / ch
    det = a * d - b * c
    if abs(det) < 1e-9:
        return
    ia, ib, ic, id_ = d / det, -b / det, -c / det, a / det
    data = (ia, ib, -(ia * o[0] + ib * o[1]), ic, id_, -(ic * o[0] + id_ * o[1]))
    face = crop.transform(canvas.size, Image.AFFINE, data, resample=Image.NEAREST)
    if shade < 1:
        r, g, bl, al = face.split()
        face = Image.merge("RGBA", [r.point(lambda p: int(p * shade)), g.point(lambda p: int(p * shade)),
                                    bl.point(lambda p: int(p * shade)), al])
    canvas.alpha_composite(face)


def raster_face(color, zbuf, tex, uv, o, u, v, shade):
    """Draws one textured face into an RGBA float buffer with a depth test. o, u, v: the face's top left, top right and bottom left corners as (screen x, screen y, depth)."""
    h_, w_ = zbuf.shape
    w, h = tex.size
    u1, v1, u2, v2 = uv
    box = (min(u1, u2) / 16 * w, min(v1, v2) / 16 * h, max(u1, u2) / 16 * w, max(v1, v2) / 16 * h)
    crop = tex.crop(tuple(int(round(b)) for b in box))
    if crop.width == 0 or crop.height == 0:
        return
    if u2 < u1:
        crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
    if v2 < v1:
        crop = crop.transpose(Image.FLIP_TOP_BOTTOM)
    cw, ch = crop.size
    a, c = (u[0] - o[0]) / cw, (u[1] - o[1]) / cw
    b, d = (v[0] - o[0]) / ch, (v[1] - o[1]) / ch
    det = a * d - b * c
    if abs(det) < 1e-9:
        return
    xs = (o[0], u[0], v[0], u[0] + v[0] - o[0])
    ys = (o[1], u[1], v[1], u[1] + v[1] - o[1])
    x0, x1 = max(0, int(math.floor(min(xs)))), min(w_, int(math.ceil(max(xs))) + 1)
    y0, y1 = max(0, int(math.floor(min(ys)))), min(h_, int(math.ceil(max(ys))) + 1)
    if x1 <= x0 or y1 <= y0:
        return
    X, Y = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
    dx, dy = X - o[0], Y - o[1]
    tu = (d * dx - b * dy) / det
    tv = (-c * dx + a * dy) / det
    inside = (tu >= 0) & (tu < cw) & (tv >= 0) & (tv < ch)
    ti = np.clip(tu.astype(np.int32), 0, cw - 1)
    tj = np.clip(tv.astype(np.int32), 0, ch - 1)
    px = np.asarray(crop, dtype=np.float32)[tj, ti]
    depth = o[2] + (u[2] - o[2]) * tu / cw + (v[2] - o[2]) * tv / ch
    zreg = zbuf[y0:y1, x0:x1]
    alpha = px[..., 3] / 255.0
    # a later face wins a tie (the elements are in the model's own order); a see-through texel does not hide what is behind it
    win = inside & (alpha > 0) & (depth >= zreg - 1e-5)
    creg = color[y0:y1, x0:x1]
    rgb = px[..., :3] * shade
    old_a = creg[..., 3] / 255.0
    out_a = alpha + old_a * (1 - alpha)
    for i in range(3):
        blended = np.where(out_a > 0, (rgb[..., i] * alpha + creg[..., i] * old_a * (1 - alpha)) / np.maximum(out_a, 1e-6), 0)
        creg[..., i] = np.where(win, blended, creg[..., i])
    creg[..., 3] = np.where(win, out_a * 255.0, creg[..., 3])
    zreg[...] = np.where(win & (alpha >= 0.999), depth, zreg)


def iso(model, k=16, tints=None, size=None, fit=15.0, info=None):
    """The item drawn as the inventory draws it: the model's own display.gui (rotation, translation, scale), orthographic."""
    gui = model["display"].get("gui", {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]})
    rx, ry, rz = (math.radians(a) for a in gui.get("rotation", [0, 0, 0]))
    tr = gui.get("translation", [0, 0, 0])
    sc = gui.get("scale", [1, 1, 1])
    canvas = Image.new("RGBA", (16 * k, 16 * k), (0, 0, 0, 0))

    def view(p):
        q = rot_xyz(((p[0] - 8) / 16 * sc[0], (p[1] - 8) / 16 * sc[1], (p[2] - 8) / 16 * sc[2]), rx, ry, rz)
        q = (q[0] + tr[0] / 16, q[1] + tr[1] / 16, q[2] + tr[2] / 16)
        return (8 * k + q[0] * 16 * k, 8 * k - q[1] * 16 * k, q[2])        # screen x, screen y (down), depth (towards the viewer)

    faces = []
    for e in model["elements"]:
        (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
        # name: default uv, then the picture's top left, top right and bottom left corners (u runs along the face's "right", v down it), outward shade
        geo = {
            "up": ((x1, z1, x2, z2), (x1, y2, z1), (x2, y2, z1), (x1, y2, z2), 1.0),
            "down": ((x1, 16 - z2, x2, 16 - z1), (x1, y1, z2), (x2, y1, z2), (x1, y1, z1), 0.5),
            "north": ((16 - x2, 16 - y2, 16 - x1, 16 - y1), (x2, y2, z1), (x1, y2, z1), (x2, y1, z1), 0.8),
            "south": ((x1, 16 - y2, x2, 16 - y1), (x1, y2, z2), (x2, y2, z2), (x1, y1, z2), 0.8),
            "west": ((z1, 16 - y2, z2, 16 - y1), (x1, y2, z1), (x1, y2, z2), (x1, y1, z1), 0.6),
            "east": ((16 - z2, 16 - y2, 16 - z1, 16 - y1), (x2, y2, z2), (x2, y2, z1), (x2, y1, z2), 0.6),
        }
        for name, f in e.get("faces", {}).items():
            if name not in geo:
                continue
            default_uv, po, pu, pv, shade = geo[name]
            pts = [rotate_axis(p, e["rotation"]) if e.get("rotation") else p for p in (po, pu, pv)]
            o, u, v = (view(p) for p in pts)
            ux, uy, uz = u[0] - o[0], u[1] - o[1], u[2] - o[2]
            vx, vy, vz = v[0] - o[0], v[1] - o[1], v[2] - o[2]
            # screen y points down, which mirrors the cross product's z: the face looks at the viewer when this is positive
            if ux * vy - uy * vx <= 0:
                continue
            ref = resolve(model["textures"], f.get("texture", ""))
            tex = load_texture(ref) if ref else None
            if tex is None:
                continue
            tint = (tints or {}).get(f.get("tintindex"))
            if tint:                                    # a tinted face (the kart's frame and seat): the colour multiplies the picture
                r, g, b, a = tex.split()
                tex = Image.merge("RGBA", [r.point(lambda p: p * tint[0] // 255), g.point(lambda p: p * tint[1] // 255), b.point(lambda p: p * tint[2] // 255), a])
            faces.append((tex, f.get("uv", default_uv), o, u, v, shade))
    # a depth buffer, not an order of faces: the kart's parts overlap in ways no single order of drawing gets right
    color = np.zeros((canvas.height, canvas.width, 4), dtype=np.float32)
    zbuf = np.full((canvas.height, canvas.width), -1e9, dtype=np.float32)
    for tex, uv, o, u, v, shade in faces:
        raster_face(color, zbuf, tex, uv, o, u, v, shade)
    canvas = Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGBA")
    if info is not None:
        info.setdefault("view", view)
    box = canvas.getbbox()                      # fit the picture to the slot so a small model (the kart) can be read; the angle is the game's
    if box and fit is not None:                 # fit=None leaves the picture at the game's own scale (two passes laid over each other)
        part = canvas.crop(box)
        f = fit * k / max(part.size)
        part = part.resize((max(1, round(part.width * f)), max(1, round(part.height * f))), Image.LANCZOS)
        canvas = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        canvas.alpha_composite(part, ((canvas.width - part.width) // 2, (canvas.height - part.height) // 2))
        if info is not None:                    # where a model point ends up in the picture (tools/make_mod_icon.py lays a floor under the kart)
            info.update(view=view, box=box, f=f, paste=((canvas.width - part.width) // 2, (canvas.height - part.height) // 2), side=canvas.width)
    return canvas.resize((size or ICON, size or ICON), Image.LANCZOS)


def flat(model):
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(4):
        name = model["textures"].get(f"layer{i}")
        tex = load_texture(name) if name else None
        if tex is not None:
            out.alpha_composite(tex.resize((16, 16), Image.NEAREST) if tex.size != (16, 16) else tex)
    return out.resize((ICON, ICON), Image.NEAREST)


def tile(label):
    img = Image.new("RGBA", (ICON, ICON), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((4, 4, ICON - 5, ICON - 5), 6, fill=(150, 120, 190, 255), outline=(60, 40, 90, 255), width=3)
    d.text((ICON // 2, ICON // 2), label[:2].upper(), fill=(255, 255, 255, 255), font=font(18), anchor="mm")
    return img


def font(size):
    for name in ("arialbd.ttf", "arial.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


_icons = {}


def item_tag_first(tag):
    ns, path = split(tag)
    if tag in VANILLA_TAGS:
        return VANILLA_TAGS[tag]
    for root in DATA_ROOTS:
        f = root / ns / "tags/item" / (path + ".json")
        if f.exists():
            for v in json.loads(f.read_text(encoding="utf-8"))["values"]:
                v = v if isinstance(v, str) else v["id"]
                return item_tag_first(v[1:]) if v.startswith("#") else v
    return None


def icon(item):
    if item in _icons:
        return _icons[item]
    ns, name = split(item)
    model = None
    for kind in ("item", "block"):
        m = load_model(f"{ns}:{kind}/{name}")
        if m["flat"] and m["textures"]:
            model = m
            break
        if m["elements"]:
            model = m
            break
    if model is None:
        img = tile(name)
    elif model["flat"]:
        img = flat(model)
    else:
        img = iso(model)
    if name == "lily_pad":                      # vanilla tints it green at run time
        r, g, b, a = img.split()
        img = Image.merge("RGBA", [r.point(lambda p: p * 113 // 255), g.point(lambda p: p * 188 // 255), b.point(lambda p: p * 69 // 255), a])
    _icons[item] = img
    return img


def slot(draw, x, y, size):
    draw.rectangle((x, y, x + size - 1, y + size - 1), fill=(139, 139, 139, 255))
    draw.rectangle((x, y, x + size - 1, y + S - 1), fill=(55, 55, 55, 255))
    draw.rectangle((x, y, x + S - 1, y + size - 1), fill=(55, 55, 55, 255))
    draw.rectangle((x, y + size - S, x + size - 1, y + size - 1), fill=(255, 255, 255, 255))
    draw.rectangle((x + size - S, y, x + size - 1, y + size - 1), fill=(255, 255, 255, 255))


def arrow(draw, x, y):
    c = (139, 139, 139, 255)
    draw.rectangle((x, y + 7 * S, x + 14 * S, y + 11 * S), fill=c)
    draw.polygon([(x + 12 * S, y + 2 * S), (x + 24 * S, y + 9 * S), (x + 12 * S, y + 16 * S)], fill=c)


def put(img, item, x, y, size):
    ic = icon(item)
    img.alpha_composite(ic, (x + (size - ic.width) // 2, y + (size - ic.height) // 2))


def render(grid, result, count, name):
    """grid: 3x3 list of item ids or None; None for a single input recipe uses grid = [[item]]."""
    rows, cols = 3, 3
    pad = 7 * S
    gw, gh = cols * 18 * S, rows * 18 * S
    height = max(gh, 26 * S) + 2 * pad
    width = pad + gw + 14 * S + 24 * S + 14 * S + 26 * S + pad
    img = Image.new("RGBA", (width, height), (198, 198, 198, 255))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, width - 1, S - 1), fill=(255, 255, 255, 255))
    d.rectangle((0, 0, S - 1, height - 1), fill=(255, 255, 255, 255))
    d.rectangle((0, height - S, width - 1, height - 1), fill=(85, 85, 85, 255))
    d.rectangle((width - S, 0, width - 1, height - 1), fill=(85, 85, 85, 255))
    gy = (height - gh) // 2
    for r in range(rows):
        for c in range(cols):
            x, y = pad + c * 18 * S, gy + r * 18 * S
            slot(d, x, y, 18 * S)
            if grid[r][c]:
                put(img, grid[r][c], x + S, y + S, 16 * S)
    ax = pad + gw + 14 * S
    arrow(d, ax, (height - 17 * S) // 2)
    rx, ry = ax + 24 * S + 14 * S, (height - 26 * S) // 2
    slot(d, rx, ry, 26 * S)
    put(img, result, rx + 5 * S, ry + 5 * S, 16 * S)
    if count > 1:
        f = font(7 * S)
        tx, ty = rx + 24 * S, ry + 24 * S
        d.text((tx + S, ty + S), str(count), fill=(63, 63, 63, 255), font=f, anchor="rd")
        d.text((tx, ty), str(count), fill=(255, 255, 255, 255), font=f, anchor="rd")
    img.convert("RGB").save(OUT / f"{name}.png")


def ingredient(x):
    if "item" in x:
        return x["item"]
    return item_tag_first(x["tag"]) if "tag" in x else None


def make(recipe_file):
    d = json.loads(recipe_file.read_text(encoding="utf-8"))
    kind = d["type"].split(":")[-1]
    name = recipe_file.stem
    if kind == "crafting_shaped":
        key = {k: ingredient(v) for k, v in d["key"].items()}
        pattern = d["pattern"]
        grid = [[key.get(ch) if ch != " " else None for ch in row.ljust(3)] for row in pattern]
        while len(grid) < 3:                    # always a full 3 x 3 grid, the pattern in its top left corner
            grid.append([None] * 3)
        render(grid, d["result"]["id"], d["result"].get("count", 1), name)
    elif kind == "crafting_shapeless":
        items = [ingredient(i) for i in d["ingredients"]]
        grid = [[None] * 3 for _ in range(3)]
        for i, it in enumerate(items):
            grid[i // 3][i % 3] = it
        render(grid, d["result"]["id"], d["result"].get("count", 1), name)
    elif kind in ("smelting", "blasting", "smoking", "campfire_cooking", "stonecutting"):
        render([[None] * 3, [None, ingredient(d["ingredient"]), None], [None] * 3], d["result"]["id"], d["result"].get("count", 1), name)
    else:
        return False
    return True


def main():
    if _jar is None:
        print(f"Minecraft 1.21.1 client jar not found at {JAR}; vanilla ingredients will be plain tiles.")
    OUT.mkdir(parents=True, exist_ok=True)
    wanted = set(sys.argv[1:])
    done = 0
    for f in sorted(RECIPES.glob("*.json")):
        if wanted and f.stem not in wanted:
            continue
        if make(f):
            done += 1
    print(f"{done} recipe pictures in {OUT}")


if __name__ == "__main__":
    main()
