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

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ASSET_ROOTS = [ROOT / "src/main/resources/assets", ROOT / "src/generated/resources/assets"]
DATA_ROOTS = [ROOT / "src/main/resources/data", ROOT / "src/generated/resources/data"]
RECIPES = ROOT / "src/main/resources/data/craftteamracing/recipe"
OUT = ROOT / "docs/img/recipes"
JAR = Path.home() / ".gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"

S = 3            # GUI pixel scale
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
        return {"flat": True, "textures": {}, "elements": None}
    raw = read_asset(ns, "models", path + ".json")
    if raw is None:
        return {"flat": False, "textures": {}, "elements": None}
    d = json.loads(raw)
    base = load_model(d["parent"]) if "parent" in d else {"flat": False, "textures": {}, "elements": None}
    return {"flat": base["flat"], "textures": {**base["textures"], **d.get("textures", {})},
            "elements": d.get("elements", base["elements"])}


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


def project(x, y, z, k):
    return (16 * k + (x - z) * 0.866 * k, 16 * k + ((x + z) * 0.5 - y) * k)


def draw_face(canvas, tex, uv, corner, k, shade):
    """corner(su, sv) -> world point of the face at fraction (su, sv) of its u and v extent."""
    w, h = tex.size
    u1, v1, u2, v2 = uv
    flip_u, flip_v = u2 < u1, v2 < v1
    box = (min(u1, u2) / 16 * w, min(v1, v2) / 16 * h, max(u1, u2) / 16 * w, max(v1, v2) / 16 * h)
    crop = tex.crop(tuple(int(round(b)) for b in box))
    if crop.width == 0 or crop.height == 0:
        return
    o = project(*corner(1 if flip_u else 0, 1 if flip_v else 0), k)
    u = project(*corner(0 if flip_u else 1, 1 if flip_v else 0), k)
    v = project(*corner(1 if flip_u else 0, 0 if flip_v else 1), k)
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


def iso(model):
    k = 8
    canvas = Image.new("RGBA", (32 * k, 32 * k), (0, 0, 0, 0))
    elements = sorted(model["elements"], key=lambda e: e["to"][0] + e["to"][1] + e["to"][2])
    for e in elements:
        (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
        faces = e.get("faces", {})
        specs = [  # name, default uv, corner(su, sv), shade
            ("up", (x1, z1, x2, z2), lambda su, sv: (x1 + (x2 - x1) * su, y2, z1 + (z2 - z1) * sv), 1.0),
            ("south", (x1, 16 - y2, x2, 16 - y1), lambda su, sv: (x1 + (x2 - x1) * su, y2 - (y2 - y1) * sv, z2), 0.8),
            ("east", (16 - z2, 16 - y2, 16 - z1, 16 - y1), lambda su, sv: (x2, y2 - (y2 - y1) * sv, z2 - (z2 - z1) * su), 0.6),
        ]
        for name, default_uv, corner, shade in specs:
            f = faces.get(name)
            if not f:
                continue
            ref = resolve(model["textures"], f.get("texture", ""))
            tex = load_texture(ref) if ref else None
            if tex is None:
                continue
            draw_face(canvas, tex, f.get("uv", default_uv), corner, k, shade)
    box = canvas.getbbox()
    if box and max(box[2] - box[0], box[3] - box[1]) < 0.55 * canvas.width:      # a small model (a wheel): enlarge it to read
        part = canvas.crop(box)
        f = 0.75 * canvas.width / max(part.size)
        part = part.resize((max(1, int(part.width * f)), max(1, int(part.height * f))), Image.LANCZOS)
        canvas = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        canvas.alpha_composite(part, ((canvas.width - part.width) // 2, (canvas.height - part.height) // 2))
    return canvas.resize((ICON, ICON), Image.LANCZOS)


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
    rows, cols = len(grid), len(grid[0])
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
        render([[ingredient(d["ingredient"])]], d["result"]["id"], d["result"].get("count", 1), name)
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
