"""
The survival side's assets and data (docs/SURVIVAL.md): textures, models, blockstates, language entries, recipes, loot tables, tags and world generation for the Power Palm and its wood set,
the Power Fruit's chain (juice, raw power ore, power ingot, nugget, block), the hanging fruit clusters, the wheel block, the bag of power fruit and the crate blocks. Run from the project
folder (also run by the build script):

  python tools/make_survival_assets.py

Everything it writes is generated and rewritten each run (edit this script, not the files), except the Blockbench models in blockbench/survival/, which are written once and are then yours
(the models are read from them every run):
  power_palm_leaves.bbmodel  the fluffy fronds: a cube and crossed planes that stick out of it (groups base, v1a ... v4b; variant n is base + vna + vnb)
  power_fruit_pods.bbmodel   the hanging fruit: one to four of the race track's Power Fruit (groups p1 ... p4), top at the underside of a frond
  wheel_block.bbmodel        the wheel block: groups w1 ... w4, the kart's wheel flat (1), stacked (2), two stacked and one leaning (3), a two by two (4)
  power_fruit_bag.bbmodel    the bag of power fruit
Textures are original pixel art in the power fruit's colours (red, orange, yellow, lime): the ingot and nugget carry a subtle yellow spark (an animated picture); outlines are the dark of
the colours they enclose, not black. The fluffy fronds are in the manner of the "Better Leaves" resource pack's models (crossed planes round a cube; MIT licence, (c) Motschen).
"""
import copy
import json
import math
import os
import random
import re
import shutil
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "craftteamracing")
DATA = os.path.join(RES, "data")
NS = "craftteamracing"
C = NS + ":"
BB = os.path.join(ROOT, "blockbench", "survival")

RED, ORANGE, YELLOW, LIME = (205, 55, 40), (250, 150, 45), (250, 210, 50), (130, 195, 60)
RAMP = ((0.0, RED), (0.38, ORANGE), (0.68, YELLOW), (1.0, LIME))


def ramp(t):
    t = min(1.0, max(0.0, t))
    for (t0, c0), (t1, c1) in zip(RAMP, RAMP[1:]):
        if t0 <= t <= t1:
            k = (t - t0) / (t1 - t0)
            return tuple(int(c0[i] + (c1[i] - c0[i]) * k) for i in range(3))
    return RAMP[-1][1]


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


def save(img, rel):
    path = os.path.join(ASSETS, "textures", rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def sheet(frames):
    """Stack 16 x 16 frames downwards: a vertical animation strip."""
    out = Image.new("RGBA", (16, 16 * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f, (0, 16 * i))
    return out


def animate(rel, frames, frametime):
    save(sheet(frames), rel)
    write_json(os.path.join(ASSETS, "textures", rel + ".mcmeta"), {"animation": {"frametime": frametime, "frames": list(range(len(frames)))}})


# ======================================================================== item pictures
SPARK = (255, 246, 160, 255)
SPARK_HOT = (255, 255, 225, 255)
WARM_SHADOW = (70, 22, 12)


def derived_outline(im, outline_cells, k_of):
    """Paint the outline cells with the dark of the colours next to them (an average of the neighbouring fill, darkened and warmed a little), so the edge belongs to the colours inside it
    instead of being a hard black line. {outline_cells}: {(x, y): kind}; {k_of(kind)}: how dark that kind is."""
    out = im.copy()
    for (x, y), kind in outline_cells.items():
        near = [im.getpixel((x + dx, y + dy)) for dx in (-1, 0, 1) for dy in (-1, 0, 1)
                if (dx or dy) and 0 <= x + dx < 16 and 0 <= y + dy < 16 and (x + dx, y + dy) not in outline_cells and im.getpixel((x + dx, y + dy))[3]]
        if near:
            c = tuple(sum(p[i] for p in near) // len(near) for i in range(3))
        else:
            c = ramp(0.4)
        k = k_of(kind)
        c = tuple(int(c[i] * k * 0.85 + WARM_SHADOW[i] * 0.15) for i in range(3))
        out.putpixel((x, y), c + (255,))
    return out


# The ingot: the iron ingot's silhouette and shading (a lit top with its glints, the long face in lit tones, the far side in shade; o and p: the outline, lit and dark side).
INGOT_ROWS = [
    "................",
    "................",
    "..........oo....",
    ".......ooolle...",
    "....ooolttttle..",
    ".ooolttttttttle.",
    "ohtttttttthhtte.",
    "olh" + "t" * 6 + "hh" + "ttelp",
    "ol" + "lh" + "t" + "hhh" + "tt" + "deed" + "lp",
    "o" + "lll" + "h" + "tt" + "d" + "eeee" + "lll" + "p",
    "o" + "d" + "ll" + "t" + "d" + "eeee" + "ll" + "d" + "pp" + ".",
    "." + "o" + "d" + "l" + "t" + "d" + "ee" + "dd" + "ppp" + "...",
    ".." + "o" + "d" + "l" + "d" + "e" + "ppp" + "......",
    "..." + "oopp" + "." * 9,
    "................",
    "................",
]
INGOT_K = {"h": 1.22, "t": 1.0, "l": 0.9, "d": 0.76, "e": 0.64}


def ingot_pixel_colour(x, y, cls):
    t = 1.0 - (x * 0.55 + (16 - y) * 0.4 - 2.0) / 12.0
    return shade(ramp(min(1.0, max(0.0, t)) * 0.95), INGOT_K[cls])


def ingot_frame(spark_cells):
    """The ingot at one frame of its animation; {spark_cells}: (x, y, hot) pixels carrying a yellow spark."""
    rows = [r for r in INGOT_ROWS]
    assert all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    outline = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in INGOT_K:
                im.putpixel((x, y), ingot_pixel_colour(x, y, ch) + (255,))
            elif ch in "op":
                outline[(x, y)] = ch
    out = derived_outline(im, outline, lambda kind: 0.5 if kind == "o" else 0.36)
    for (x, y, hot) in spark_cells:
        if (x, y) not in outline and out.getpixel((x, y))[3]:
            out.putpixel((x, y), SPARK_HOT if hot else SPARK)
    return out


INGOT_SPARKS = [
    [(6, 5, True), (11, 8, False)],
    [(7, 5, False), (10, 7, True), (4, 9, False)],
    [(9, 6, False), (9, 9, True)],
    [(5, 7, True), (12, 6, False)],
    [(8, 5, False), (5, 10, True), (11, 7, False)],
    [(10, 6, True), (3, 8, False)],
]


def nugget_frame(spark):
    """The nugget: a little star of a lump, lit from the top left, with the outline derived from its colours."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    mask = Image.new("L", (16, 16), 0)
    ImageDraw.Draw(mask).polygon([(6, 8), (9, 6), (12, 8), (11, 11), (7, 11)], fill=255)
    for y in range(16):
        for x in range(16):
            if mask.getpixel((x, y)):
                t = 1.0 - (x * 0.5 + (16 - y) * 0.5 - 6.0) / 10.0
                k = 1.1 if (x + y) < 17 else 0.8
                im.putpixel((x, y), shade(ramp(t), k) + (255,))
    outline = {}
    for y in range(16):
        for x in range(16):
            if mask.getpixel((x, y)) == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and mask.getpixel((x + dx, y + dy)) for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                outline[(x, y)] = "o" if (x + y) < 17 else "p"
    out = derived_outline(im, outline, lambda kind: 0.5 if kind == "o" else 0.36)
    if spark and out.getpixel(spark[:2])[3] and spark[:2] not in outline:
        out.putpixel(spark[:2], SPARK_HOT if spark[2] else SPARK)
    return out


def raw_power_ore():
    """A lump of ore (raw iron's idea): a brown-red rock flecked with the fruit's colours."""
    rnd = random.Random(8)
    mask = Image.new("L", (16, 16), 0)
    ImageDraw.Draw(mask).polygon([(3, 9), (4, 5), (8, 3), (12, 5), (13, 9), (11, 13), (6, 13)], fill=255)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if mask.getpixel((x, y)):
                base = (150, 96, 72) if (x + y) % 5 else (132, 82, 62)
                if (x + y) < 15:
                    base = shade(base, 1.14)
                if y > 10 or x > 10:
                    base = shade(base, 0.78)
                im.putpixel((x, y), base + (255,))
    for _ in range(11):
        x, y = rnd.randint(4, 12), rnd.randint(5, 12)
        if mask.getpixel((x, y)):
            im.putpixel((x, y), ramp(rnd.random()) + (255,))
            if rnd.random() < 0.4 and mask.getpixel((x + 1, y)):
                im.putpixel((x + 1, y), ramp(rnd.random()) + (255,))
    outline = {}
    for y in range(16):
        for x in range(16):
            if mask.getpixel((x, y)) == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and mask.getpixel((x + dx, y + dy)) for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                outline[(x, y)] = "o"
    return derived_outline(im, outline, lambda kind: 0.42)


def poly_mask(points, size=16):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).polygon(points, fill=255)
    return m


def power_juice():
    """A glass bottle with a cork, half full of the juice: the fruit's colours in layers, lime at the top, red at the bottom."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rim = (92, 112, 128, 255)
    body = [(5, 6), (10, 6), (12, 8), (12, 13), (10, 15), (5, 15), (3, 13), (3, 8)]
    mask = poly_mask(body)
    neck = poly_mask([(6, 3), (9, 3), (9, 6), (6, 6)])
    for y in range(16):
        for x in range(16):
            if mask.getpixel((x, y)) or neck.getpixel((x, y)):
                im.putpixel((x, y), (206, 228, 238, 150))
    for y in range(8, 15):
        t = 1.0 - (y - 8) / 6.0
        for x in range(4, 12):
            if mask.getpixel((x, y)):
                im.putpixel((x, y), shade(ramp(t * 0.9 + 0.05), 1.0 if x < 9 else 0.82) + (255,))
    full = Image.new("L", (16, 16), 0)
    ImageDraw.Draw(full).polygon(body, fill=255)
    ImageDraw.Draw(full).rectangle([6, 3, 9, 6], fill=255)
    out = im.copy()
    for y in range(16):
        for x in range(16):
            if full.getpixel((x, y)) == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and full.getpixel((x + dx, y + dy)) for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                out.putpixel((x, y), rim)
    for x in range(6, 10):
        for y in range(1, 3):
            out.putpixel((x, y), (150, 100, 55, 255) if (x + y) % 2 else (176, 122, 70, 255))
    for x, y in ((4, 9), (4, 10), (5, 8)):
        out.putpixel((x, y), (255, 255, 255, 235))
    return out


def door_item():
    """The door's sprite: a frame of planks, four see-through panes above and two raised panels below, a knob."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([3, 0, 12, 15], fill=(150, 112, 74, 255))
    d.rectangle([4, 1, 11, 14], fill=(206, 166, 116, 255))
    for x0 in (5, 8):
        for y0 in (2, 4):
            d.rectangle([x0, y0, x0 + 1, y0 + 1], fill=(0, 0, 0, 0))
    d.rectangle([4, 7, 11, 7], fill=(150, 112, 74, 255))
    for y0, y1 in ((8, 10), (12, 13)):
        d.rectangle([5, y0, 10, y1], fill=(190, 150, 102, 255), outline=(168, 128, 86, 255))
    im.putpixel((10, 9), (128, 139, 149, 255))
    return im


# ======================================================================== block pictures
def power_block():
    """The block of power: an iron block's flat plate (the faint bands of a plate, a one pixel edge lit top left and in shade bottom right) in the fruit's colours, a diagonal gradient and
    a few glints like gold's; it does not move."""
    rnd = random.Random(31)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    glints = {(rnd.randint(2, 13), rnd.randint(2, 13)) for _ in range(9)}
    for y in range(16):
        for x in range(16):
            t = 1.0 - (x * 0.5 + (15 - y) * 0.5) / 15.0
            c = ramp(0.08 + 0.84 * (1.0 - t))
            k = (1.07, 1.0, 0.93)[y % 3]
            d = min(x, y, 15 - x, 15 - y)
            if d == 0:
                k = 1.16 if (x + y) < 15 else 0.78
            elif (x, y) in glints:
                k = 1.2
            im.putpixel((x, y), shade(c, k) + (255,))
    return im


def log_side(rnd, light=False):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            base = (128, 98, 66)
            if x % 5 in (1, 2):
                base = (106, 80, 54)                                    # fibres
            if y % 4 == 0:
                base = (92, 68, 46)                                     # the ring scars of a palm's trunk
            elif y % 4 == 1:
                base = (150, 118, 82)
            if light:                                                   # stripped: pale, smooth, only the faint rings left
                base = (204, 164, 114) if y % 4 else (186, 146, 98)
                if x % 5 == 1:
                    base = (194, 154, 106)
            base = tuple(max(0, min(255, v + rnd.randint(-6, 6))) for v in base)
            im.putpixel((x, y), base + (255,))
    return im


def log_top(rnd, light=False):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r > 7.2:
                c = (104, 78, 52) if not light else (194, 154, 106)
            elif int(r) % 3 == 0:
                c = (150, 116, 78)
            else:
                c = (196, 156, 108)
            if r < 1.6:
                c = (118, 88, 58)
            c = tuple(max(0, min(255, v + rnd.randint(-5, 5))) for v in c)
            im.putpixel((x, y), c + (255,))
    return im


def planks(rnd):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            row = y // 4
            c = (206, 166, 116) if row % 2 == 0 else (196, 154, 104)
            if y % 4 == 3:
                c = (150, 112, 74)                                      # the seam
            if (x + row * 5) % 16 == 0:
                c = (160, 122, 82)
            c = tuple(max(0, min(255, v + rnd.randint(-5, 5))) for v in c)
            im.putpixel((x, y), c + (255,))
    return im


def trapdoor(rnd):
    im = planks(rnd)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], outline=(150, 112, 74, 255))
    d.rectangle([1, 1, 14, 14], outline=(176, 136, 90, 255))
    for y in (3, 12):
        d.line([(2, y), (13, y)], fill=(120, 88, 58, 255))
    for x, y in ((2, 3), (13, 3), (2, 12), (13, 12)):
        im.putpixel((x, y), (190, 190, 196, 255))
    return im


IRON_LIGHT, IRON_DARK = (128, 139, 149, 255), (107, 111, 122, 255)


def door_half(rnd, top):
    """A door's half, laid out as the oak door's, in the planks' own colours (the planks' picture turned so the boards stand upright; frame, bevels and panels use the planks' tones):
    iron hinges on the left edge, a handle where the halves meet. The top has four panes cut out, each with a bevel (shaded above and to the left, lit below and to the right); the
    bottom has two raised panels with grain."""
    P1, P2, SEAM, EDGE = (206, 166, 116), (196, 154, 104), (150, 112, 74), (160, 122, 82)
    LIT = (222, 184, 134)
    im = planks(rnd).rotate(90)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], outline=SEAM + (255,))
    if top:
        for x0 in (3, 9):
            for y0 in (3, 8):
                for x in range(x0 - 1, x0 + 5):
                    im.putpixel((x, y0 - 1), SEAM + (255,))                              # the shadowed top edge
                    im.putpixel((x, y0 + 3), LIT + (255,))                               # the lit bottom edge
                for y in range(y0, y0 + 3):
                    im.putpixel((x0 - 1, y), EDGE + (255,))                              # shaded left edge
                    im.putpixel((x0 + 4, y), LIT + (255,))                               # lit right edge
                    for x in range(x0, x0 + 4):
                        im.putpixel((x, y), (0, 0, 0, 0))                                    # the pane: cut out
        for x, y in ((11, 14), (12, 14), (13, 14), (11, 15), (13, 15)):
            im.putpixel((x, y), IRON_LIGHT if y == 14 else IRON_DARK)                    # the handle
        for y in (4, 5):
            im.putpixel((0, y), IRON_LIGHT)                                              # hinges
    else:
        for y0, y1 in ((3, 6), (9, 13)):
            d.rectangle([2, y0, 13, y1], fill=SEAM + (255,))
            d.rectangle([3, y0 + 1, 12, y1 - 1], fill=P2 + (255,))
            for x in range(4, 12, 2):
                d.line([(x, y0 + 2), (x, y1 - 2)], fill=P1 + (255,))                    # grain in the raised panel
            d.line([(3, y0 + 1), (12, y0 + 1)], fill=LIT + (255,))                      # lit upper edge
            d.line([(3, y1 - 1), (12, y1 - 1)], fill=EDGE + (255,))                     # shaded lower edge
        for y in (10, 11):
            im.putpixel((0, y), IRON_LIGHT if y == 10 else IRON_DARK)                      # hinge
    return im


LEAF_GREENS = [(38, 104, 44), (52, 132, 52), (72, 160, 60), (100, 190, 76)]


def palm_leaves(rnd):
    """Fronds on a 32 x 32 picture, in the manner of the Better Leaves pack: a rounded, ragged tuft (the crossed planes of the fluffy model show the whole of it) with the leaflets in
    four greens along diagonal ribs, and about a third of the pixels see-through, in clumps (the cube in the middle of the block shows only the picture's middle, uv 4..12)."""
    n = 32
    coarse = [[rnd.random() for _ in range(9)] for _ in range(9)]

    def noise(x, y):
        fx, fy = x / 4.0, y / 4.0
        x0, y0 = int(fx), int(fy)
        tx, ty = fx - x0, fy - y0
        a = coarse[y0][x0] * (1 - tx) + coarse[y0][x0 + 1] * tx
        b = coarse[y0 + 1][x0] * (1 - tx) + coarse[y0 + 1][x0 + 1] * tx
        return a * (1 - ty) + b * ty
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    for y in range(n):
        for x in range(n):
            r = math.hypot(x - 15.5, y - 15.5)
            edge = 14.8 + rnd.uniform(-2.0, 1.0)
            if r > edge:
                continue
            u = (x - y) % 8
            v = (x + y) % 8
            rib = u in (0, 1)
            if not rib and noise(x, y) * 0.7 + rnd.random() * 0.3 < 0.50:
                continue
            idx = 0 if rib else 2 if v in (3, 4) else 1
            if rnd.random() < 0.3:
                idx = min(3, max(0, idx + rnd.choice((-1, 1))))
            if y < 14 and idx < 3 and rnd.random() < 0.25:
                idx += 1
            c = LEAF_GREENS[idx]
            if rnd.random() < 0.05:
                c = (150, 214, 90)
            im.putpixel((x, y), c + (255,))
    return im


def palm_leaves_item(rnd):
    """The leaf block as an item (and its particles): a dense 16 x 16 leaf picture like the vanilla leaves', a few holes."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            u, v = (x - y) % 8, (x + y) % 8
            if rnd.random() < 0.30 and u not in (0, 1):
                continue                                                             # about the jungle leaves' 30 % air
            idx = 0 if u in (0, 1) else 2 if v in (3, 4) else 1
            if rnd.random() < 0.3:
                idx = min(3, max(0, idx + rnd.choice((-1, 1))))
            im.putpixel((x, y), LEAF_GREENS[idx] + (255,))
    return im


SAPLING_ROWS = [
    "................",
    ".......H........",
    "......HLM.......",
    "..HL...LMD..HL..",
    ".HLMM..MMD.LMMH.",
    "HLMMDLLMMDLMMDM.",
    "MMDD.MMLMDMMD.D.",
    ".DD..DMMLMMDD...",
    ".D...DDMLMD..D..",
    ".....DDTtD......",
    ".......Tt.......",
    ".......Tt.......",
    "......tTt.......",
    ".......Tt.......",
    ".......Tt.......",
    "......TTtt......",
]
SAPLING_COLOURS = {"H": (136, 214, 96), "L": (96, 182, 70), "M": (60, 144, 52), "D": (34, 96, 42), "T": (96, 72, 48), "t": (140, 108, 72)}


def palm_sapling():
    """A young palm: a short ringed trunk and a rosette of fronds that arch and droop, four greens (a lit highlight, light, mid, shade), as full as the vanilla saplings."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(SAPLING_ROWS):
        assert len(row) == 16, (y, len(row))
        for x, ch in enumerate(row):
            if ch in SAPLING_COLOURS:
                im.putpixel((x, y), SAPLING_COLOURS[ch] + (255,))
    return im


def bag_textures():
    """The bag of power fruit's own pictures (used when Farm & Charm is not there): burlap sides with a band of the fruit's colours, a top with a two pixel edge of burlap round a heap
    of fruit, and a plain bottom. The burlap's colours are a sack's; the top is also used with Farm & Charm's sack (the same two pixel edge)."""
    rnd = random.Random(5)
    burlap = [(212, 185, 137), (202, 170, 122), (181, 147, 106), (160, 125, 87)]

    def sack(w, h, dark=False):
        im = Image.new("RGBA", (w, h), (0, 0, 0, 255))
        for y in range(h):
            for x in range(w):
                c = burlap[rnd.choice((0, 0, 0, 1, 1, 2))]
                if (x + y) % 5 == 0:
                    c = burlap[min(3, 1 + rnd.randint(0, 2))]
                if dark:
                    c = shade(c, 0.82)
                im.putpixel((x, y), c + (255,))
        return im
    side = sack(16, 16)
    three = (RED, ORANGE, YELLOW)
    last = -1
    for x in range(16):                                           # the band: a stitched line of the fruit's three colours, spread about
        k = rnd.choice([i for i in range(3) if i != last])
        last = k
        side.putpixel((x, 4), three[k] + (255,))
        side.putpixel((x, 5), shade(three[k], 0.78) + (255,))
    for y in range(11, 16):
        for x in range(16):
            c = side.getpixel((x, y))
            side.putpixel((x, y), shade(c[:3], 0.9) + (255,))
    bottom = sack(16, 16, True)
    top = sack(16, 16)
    d = ImageDraw.Draw(top)
    for x in range(16):
        for y in range(16):
            if min(x, y, 15 - x, 15 - y) == 1:
                top.putpixel((x, y), burlap[3] + (255,))          # the second pixel of the edge: the seam where the rim is rolled
    inner = Image.new("RGBA", (12, 12), (60, 30, 20, 255))
    di = ImageDraw.Draw(inner)
    spots = [(2, 2), (6, 1), (10, 2), (1, 6), (5, 5), (9, 6), (3, 10), (7, 9), (11, 10)]
    picks = [0, 1, 0, 2, 0, 2, 1, 2, 1]                           # the three colours on the three by three, none the same as the one beside or below it
    for i, (cx, cy) in enumerate(spots):
        base = (RED, ORANGE, YELLOW)[picks[i]]
        di.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=shade(base, 0.8) + (255,))
        di.ellipse([cx - 2, cy - 2, cx + 1, cy + 1], fill=base + (255,))
        inner.putpixel((max(0, min(11, cx - 1)), max(0, min(11, cy - 1))), tuple(min(255, v + 70) for v in base) + (255,))
    top.paste(inner, (2, 2))
    return side, top, bottom


def crate_textures():
    """The planked crate's pictures, from the pickups' own art: the side (a frame round the panel), and the top and bottom (the frame round plain oak)."""
    import make_pickup_models as pm
    out = {}
    for kind in ("item", "fruit"):
        side_panel = pm.blank_panel if kind == "item" else pm.plank_panel
        marked = pm.question_panel if kind == "item" else pm.plank_panel
        main = side_panel

        def top_panel(n):
            im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
            d = ImageDraw.Draw(im)
            pm.wood(d, [0, 0, n - 1, n - 1], pm.rgba(pm.OAK["base"]), pm.rgba(pm.OAK["dark"]), pm.rgba(pm.OAK["light"]), 5, vertical=False)
            d.rectangle([0, 0, n - 1, n - 1], outline=pm.rgba(pm.OAK["edge"]))
            return im
        out[kind] = (pm.crate_face(16, side_panel), pm.crate_face(16, top_panel), pm.crate_face(16, marked), pm.crate_face(16, main))
    return out


# ======================================================================== blockbench sources (written once, then yours)
def fruit_elements():
    model = bbmodel.read(os.path.join(ROOT, "blockbench", "pickups", "power_fruit.bbmodel"))
    return bbmodel.elements(model, "body", "leaves"), model["display"]


def moved(el, dx, dy, dz):
    e = copy.deepcopy(el)
    e["from"] = [round(e["from"][0] + dx, 4), round(e["from"][1] + dy, 4), round(e["from"][2] + dz, 4)]
    e["to"] = [round(e["to"][0] + dx, 4), round(e["to"][1] + dy, 4), round(e["to"][2] + dz, 4)]
    if "rotation" in e:
        e["rotation"]["origin"] = [round(e["rotation"]["origin"][0] + dx, 4), round(e["rotation"]["origin"][1] + dy, 4), round(e["rotation"]["origin"][2] + dz, 4)]
    return e


POD_LAYOUT = {
    1: [(0.0, 9.5, 0.0)],
    2: [(-2.6, 9.5, -1.5), (2.6, 8.5, 1.5)],
    3: [(-3.0, 9.5, -2.5), (3.0, 8.5, -1.0), (0.0, 9.0, 3.0)],
    4: [(-3.0, 9.5, -3.0), (3.0, 8.5, -3.0), (-3.0, 8.5, 3.0), (3.0, 9.5, 3.0)],
}


def ensure_pods_bbmodel():
    path = os.path.join(BB, "power_fruit_pods.bbmodel")
    if not os.path.exists(path):
        els, _ = fruit_elements()
        groups = [("p%d" % n, [moved(el, dx, dy, dz) for (dx, dy, dz) in pos for el in els]) for n, pos in POD_LAYOUT.items()]
        tex = os.path.join(ROOT, "src", "main", "resources", "assets", "craftteamracing", "textures", "item", "pickups.png")
        bbmodel.write(path, "power_fruit_pods", {"pickups": tex}, groups, gui_light_front=False,
                      comment="Power Fruit hanging under palm fronds: groups p1 to p4 are the cluster of one to four fruit (the race track's Power Fruit, copied and spread). The top of "
                              "each fruit is at the underside of the frond above (y 16). Texture slot 'pickups' is the pickups sheet.")
    return bbmodel.read(path)


def leaf_plane(z, angle, along="z", span=32, lift=-6):
    """A plane of the fluffy fronds, both sides, tilted by {angle} about the vertical through its middle; along "z" it faces north/south at depth z, along "x" east/west at depth z."""
    face = {"uv": [0, 0, 16, 16], "texture": "#all"}
    if along == "z":
        el = {"from": [-8, lift, z], "to": [-8 + span, lift + 28, z], "faces": {"north": dict(face), "south": dict(face)}, "rotation": {"origin": [8, 8, z], "axis": "y", "angle": angle}}
    else:
        el = {"from": [z, lift, -8], "to": [z, lift + 28, -8 + span], "faces": {"east": dict(face), "west": dict(face)}, "rotation": {"origin": [z, 8, 8], "axis": "y", "angle": angle}}
    return el


def ensure_leaves_bbmodel():
    path = os.path.join(BB, "power_palm_leaves.bbmodel")
    if not os.path.exists(path):
        base_face = {"uv": [4, 4, 12, 12], "texture": "#all"}                      # the middle of the 32 x 32 picture
        base = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {f: dict(base_face, cullface=f) for f in bbmodel.FACES}}
        groups = [("base", [base]),
                  ("v1a", [leaf_plane(8, 22.5)]), ("v1b", [leaf_plane(4, -45)]),
                  ("v2a", [leaf_plane(12, 45)]), ("v2b", [leaf_plane(8, -22.5)]),
                  ("v3a", [leaf_plane(9, 22.5)]), ("v3b", [leaf_plane(9, 22.5, along="x")]),
                  ("v4a", [leaf_plane(8, -22.5, lift=-5)]), ("v4b", [leaf_plane(7, -22.5, along="x", lift=-5)])]
        tex = os.path.join(RES, "resourcepacks", "fluffy_fronds", "assets", NS, "textures", "block", "power_palm_leaves_fluffy.png")
        bbmodel.write(path, "power_palm_leaves", {"all": tex}, groups, gui_light_front=False,
                      comment="The fluffy Power Palm fronds, in the manner of the Better Leaves resource pack: a plain cube (group base) and crossed planes that stick out of it (two per "
                              "variant: v1a + v1b, v2a + v2b ...). The block state picks one of the four variants and a turn at random. Texture slot 'all' is the fronds' picture.")
    return bbmodel.read(path)


def ensure_bag_bbmodel():
    path = os.path.join(BB, "power_fruit_bag.bbmodel")
    if not os.path.exists(path):
        def face(uv, tex):
            return {"uv": uv, "texture": "#" + tex}
        lower = {"from": [2, 0, 2], "to": [14, 10, 14], "faces": {f: face([2, 6, 14, 16], "side") for f in ("north", "east", "south", "west")}}
        lower["faces"]["down"] = face([2, 2, 14, 14], "bottom")
        rim = {"from": [1, 10, 1], "to": [15, 14, 15], "faces": {f: face([0, 3, 16, 7], "side") for f in ("north", "east", "south", "west")}}
        rim["faces"]["up"] = face([0, 0, 16, 16], "top")
        rim["faces"]["down"] = face([0, 0, 16, 16], "bottom")
        tex = lambda n: os.path.join(ASSETS, "textures", "block", n + ".png")
        bbmodel.write(path, "power_fruit_bag", {"side": tex("power_fruit_bag_side"), "top": tex("power_fruit_bag_top"), "bottom": tex("power_fruit_bag_bottom")},
                      [("body", [lower, rim])], gui_light_front=False,
                      comment="The bag of power fruit (own sack pictures; with Farm & Charm installed a built-in resource pack puts its sack model and side/bottom pictures under our top picture). "
                              "Slots: side, top (the heap of fruit with a two pixel burlap edge), bottom.")
    return bbmodel.read(path)


# ---- the wheel block: the kart's wheel (blockbench/kart/kart_wheel.bbmodel), moved and turned
def rot_x(v):                                  # the wheel's axle (x) becomes the upright (y): flat wheels
    return (-v[1], v[0], v[2])


def rot_z(v):                                  # the axle becomes z: standing wheels
    return (v[2], v[1], -v[0])


NORMALS = {"east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0), "south": (0, 0, 1), "north": (0, 0, -1)}
AXIS_NAME = {(1, 0, 0): "x", (0, 1, 0): "y", (0, 0, 1): "z"}


def turn(el, M, centre, tilt=None):
    """The wheel element {el} (centred on 8, 8, 8) with its axes sent through {M} (a signed axis permutation) and moved to {centre}; {tilt}: degrees about x for the whole wheel."""
    f = [el["from"][i] - 8 for i in range(3)]
    t = [el["to"][i] - 8 for i in range(3)]
    a, b = M(f), M(t)
    out = {"from": [round(min(a[i], b[i]) + centre[i], 4) for i in range(3)], "to": [round(max(a[i], b[i]) + centre[i], 4) for i in range(3)], "faces": {}}
    for name, face in el["faces"].items():
        n = M(NORMALS[name])
        new = [k for k, v in NORMALS.items() if v == tuple(int(round(c)) for c in n)][0]
        out["faces"][new] = copy.deepcopy(face)
    if "rotation" in el:
        r = el["rotation"]
        axis_vec = tuple((1, 0, 0) if r["axis"] == "x" else (0, 1, 0) if r["axis"] == "y" else (0, 0, 1))
        m = M(axis_vec)
        sign = 1 if sum(m) > 0 else -1
        o = M([r["origin"][i] - 8 for i in range(3)])
        out["rotation"] = {"origin": [round(o[i] + centre[i], 4) for i in range(3)], "axis": AXIS_NAME[tuple(abs(int(round(c))) for c in m)], "angle": r["angle"] * sign}
    elif tilt:
        out["rotation"] = {"origin": [round(c, 4) for c in centre], "axis": "x", "angle": tilt}
    return out


def ensure_wheel_bbmodel():
    path = os.path.join(BB, "wheel_block.bbmodel")
    if not os.path.exists(path):
        src = bbmodel.read(os.path.join(ROOT, "blockbench", "kart", "kart_wheel.bbmodel"))
        tyre, faces = src["groups"]["tyre"], src["groups"]["faces"]
        wheel = tyre + faces

        def flat(x, y, z):
            return [turn(e, rot_x, (x, y, z)) for e in wheel]
        w3 = flat(4.2, 1.5, 8.0) + flat(11.8, 1.5, 8.0) + flat(8.0, 4.5, 8.0)                    # a pyramid: two side by side, one on top of both
        groups = [("w1", flat(8, 1.5, 8)), ("w2", flat(8, 1.5, 8) + flat(8, 4.5, 8)), ("w3", w3),
                  ("w4", flat(4.0, 1.5, 4.0) + flat(12.0, 1.5, 4.0) + flat(4.0, 1.5, 12.0) + flat(12.0, 1.5, 12.0))]
        tex = os.path.join(ASSETS, "textures", "item", "kart_wheel.png")
        bbmodel.write(path, "wheel_block", {"wheel": tex}, groups, gui_light_front=False,
                      comment="The wheel block, made from the kart's wheel (blockbench/kart/kart_wheel.bbmodel): w1 one wheel lying in the middle, w2 two stacked, w3 a pyramid (two side by side, one on top), "
                              "w4 a two by two. Texture slot 'wheel' is the kart wheel's picture.")
    return bbmodel.read(path)


# ======================================================================== recipes
def shaped(name, pattern, key, result, count=1):
    write_json(os.path.join(DATA, NS, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
        "pattern": pattern, "result": {"id": result, "count": count}})


def shapeless(name, ingredients, result, count=1):
    write_json(os.path.join(DATA, NS, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [({"tag": v[1:]} if v.startswith("#") else {"item": v}) for v in ingredients], "result": {"id": result, "count": count}})


def cooking(kind, name, ingredient, result, xp, ticks):
    write_json(os.path.join(DATA, NS, "recipe", name + ".json"), {
        "type": "minecraft:" + kind, "category": "misc", "ingredient": {"item": ingredient}, "result": {"id": result}, "experience": xp, "cookingtime": ticks})


def recipes():
    # the chain
    cooking("smelting", "power_juice_from_fruit", C + "power_fruit", C + "power_juice", 0.35, 200)             # as raw beef: furnace, smoker (faster) or campfire (slow)
    cooking("smoking", "power_juice_from_fruit_smoking", C + "power_fruit", C + "power_juice", 0.35, 100)
    cooking("campfire_cooking", "power_juice_from_fruit_campfire", C + "power_fruit", C + "power_juice", 0.35, 600)
    shapeless("raw_power_ore_from_juice", [C + "power_juice", "minecraft:raw_iron"], C + "raw_power_ore", 2)
    cooking("smelting", "power_ingot_from_raw", C + "raw_power_ore", C + "power_ingot", 1.0, 200)
    cooking("blasting", "power_ingot_from_raw_blasting", C + "raw_power_ore", C + "power_ingot", 1.0, 100)
    shapeless("power_nugget_from_ingot", [C + "power_ingot"], C + "power_nugget", 9)
    shaped("power_ingot_from_nuggets", ["NNN", "NNN", "NNN"], {"N": C + "power_nugget"}, C + "power_ingot")
    shaped("power_block", ["III", "III", "III"], {"I": C + "power_ingot"}, C + "power_block")
    shapeless("power_ingot_from_block", [C + "power_block"], C + "power_ingot", 9)
    # the bag
    shaped("power_fruit_bag", ["FFF", "FFF", "FFF"], {"F": C + "power_fruit"}, C + "power_fruit_bag")
    shapeless("power_fruit_from_bag", [C + "power_fruit_bag"], C + "power_fruit", 9)
    # the palm's wood
    shapeless("power_palm_planks", ["#" + NS + ":power_palm_logs"], C + "power_palm_planks", 4)
    shaped("power_palm_wood", ["LL", "LL"], {"L": C + "power_palm_log"}, C + "power_palm_wood", 3)
    shaped("stripped_power_palm_wood", ["LL", "LL"], {"L": C + "stripped_power_palm_log"}, C + "stripped_power_palm_wood", 3)
    p = {"P": C + "power_palm_planks"}
    shaped("power_palm_stairs", ["P  ", "PP ", "PPP"], p, C + "power_palm_stairs", 4)
    shaped("power_palm_slab", ["PPP"], p, C + "power_palm_slab", 6)
    shaped("power_palm_fence", ["PSP", "PSP"], dict(p, S="minecraft:stick"), C + "power_palm_fence", 3)
    shaped("power_palm_fence_gate", ["SPS", "SPS"], dict(p, S="minecraft:stick"), C + "power_palm_fence_gate")
    shaped("power_palm_door", ["PP", "PP", "PP"], p, C + "power_palm_door", 3)
    shaped("power_palm_trapdoor", ["PPP", "PPP"], p, C + "power_palm_trapdoor", 2)
    shapeless("power_palm_button", [C + "power_palm_planks"], C + "power_palm_button")
    shaped("power_palm_pressure_plate", ["PP"], p, C + "power_palm_pressure_plate")
    # the crates: an oak frame round a spruce (item) or dark oak (fruit) panel, with a power nugget or a fruit in the middle; the planked kind and the marked kind convert both ways
    shaped("item_crate", ["OSO", "SNS", "OSO"], {"O": "minecraft:oak_planks", "S": "minecraft:spruce_planks", "N": C + "power_nugget"}, C + "item_crate", 2)
    shaped("fruit_crate", ["ODO", "DFD", "ODO"], {"O": "minecraft:oak_planks", "D": "minecraft:dark_oak_planks", "F": C + "power_fruit"}, C + "fruit_crate", 2)
    # the crate blocks look-alikes are cut from one another at the stonecutter (any of a kind into any other of it)
    for group in (("item_crate", "item_crate_planked", "item_crate_random"), ("fruit_crate", "fruit_crate_planked")):
        for src in group:
            for dst in group:
                if src != dst:
                    write_json(os.path.join(DATA, NS, "recipe", "stonecutting_%s_to_%s.json" % (src, dst)), {
                        "type": "minecraft:stonecutting", "ingredient": {"item": C + src}, "result": {"id": C + dst, "count": 1}})
    # the wheel (four wool and an iron ingot make four wheels)
    shaped("wheel", [" B ", "BIB", " B "], {"B": "minecraft:black_wool", "I": "minecraft:iron_ingot"}, C + "wheel", 4)
    # the boost blocks
    shaped("boost_pad", ["NNN", "III"], {"N": C + "power_nugget", "I": "minecraft:iron_ingot"}, C + "boost_pad", 4)
    shaped("boost_block", ["SSS", "SPS", "SSS"], {"S": "minecraft:cobblestone", "P": C + "power_ingot"}, C + "boost_block", 8)
    shaped("boost_slab", ["BBB"], {"B": C + "boost_block"}, C + "boost_slab", 6)
    shaped("boost_stairs", ["B  ", "BB ", "BBB"], {"B": C + "boost_block"}, C + "boost_stairs", 4)
    for kind in ("pad", "block", "slab", "stairs"):
        shapeless("super_boost_" + kind, [C + "boost_" + kind, "minecraft:lapis_lazuli"], C + "super_boost_" + kind)
    # the kart and its tools
    shaped("kart", ["WLW", "PMP", "WRW"], {"W": C + "wheel", "L": "minecraft:leather", "P": C + "power_ingot", "M": "minecraft:minecart", "R": "minecraft:redstone"}, C + "kart")
    shaped("offroad_tires", ["WSW", "SIS", "WSW"], {"W": C + "wheel", "S": "minecraft:slime_ball", "I": "minecraft:iron_ingot"}, C + "offroad_tires")
    shaped("paddle", ["IPI", "PWP", "IPI"], {"I": "minecraft:iron_ingot", "P": "#minecraft:planks", "W": C + "power_ingot"}, C + "paddle")
    shaped("wrench", ["IPI", " I ", " I "], {"I": "minecraft:iron_ingot", "P": C + "power_ingot"}, C + "wrench")
    shaped("track_tool", ["  P", " S ", "S  "], {"P": C + "power_ingot", "S": "minecraft:stick"}, C + "track_tool")
    shapeless("rulebook", ["minecraft:book", C + "power_nugget", "minecraft:redstone"], C + "rulebook")
    shapeless("drop_table", ["minecraft:book", C + "power_nugget", "#" + NS + ":item_crates"], C + "drop_table")
    shapeless("track_music_sheet", ["minecraft:paper", "minecraft:note_block", C + "power_nugget"], C + "track_music_sheet")
    # the track's blocks
    shaped("checkpoint_node", ["N", "R"], {"N": C + "power_nugget", "R": "minecraft:redstone"}, C + "checkpoint_node", 2)
    shapeless("start_node", [C + "checkpoint_node", C + "power_ingot"], C + "start_node")
    for place, ingot in ((1, "minecraft:gold_ingot"), (2, "minecraft:iron_ingot"), (3, "minecraft:copper_ingot")):
        shapeless("podium_%d_node" % place, [C + "checkpoint_node", ingot], C + "podium_%d_node" % place)
    shapeless("fruit_spot", [C + "power_fruit", C + "checkpoint_node"], C + "fruit_spot")
    shapeless("fruit_crate_spot", ["#" + NS + ":fruit_crates", C + "checkpoint_node"], C + "fruit_crate_spot")
    shapeless("powerup_crate_spot", ["#" + NS + ":item_crates", C + "checkpoint_node"], C + "powerup_crate_spot")
    shapeless("trigger_crate_spot", ["#" + NS + ":item_crates", C + "checkpoint_node", "minecraft:redstone_torch"], C + "trigger_crate_spot")
    shapeless("redstone_link_node", [C + "checkpoint_node", "minecraft:redstone", "minecraft:quartz"], C + "redstone_link_node")


# ======================================================================== loot tables, tags, world generation
def loot(name, entries, random_sequence=True):
    body = {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}], "entries": entries}]}
    if random_sequence:
        body["random_sequence"] = "%s:blocks/%s" % (NS, name)
    write_json(os.path.join(DATA, NS, "loot_table", "blocks", name + ".json"), body)


def loot_self(name):
    loot(name, [{"type": "minecraft:item", "name": C + name}])


def counted(item, block, prop, values):
    """Entries that give {item} as many as the block's {prop} says."""
    return [{"type": "minecraft:item", "name": item, "conditions": [{"condition": "minecraft:block_state_property", "block": block, "properties": {prop: str(n)}}],
             "functions": [{"function": "minecraft:set_count", "count": float(n), "add": False}]} for n in values]


SHEARS_OR_SILK = {"condition": "minecraft:any_of", "terms": [
    {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
    {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}

SELF_DROPS = ("power_palm_log", "power_palm_wood", "stripped_power_palm_log", "stripped_power_palm_wood", "power_palm_planks", "power_palm_stairs", "power_palm_fence",
              "power_palm_fence_gate", "power_palm_trapdoor", "power_palm_button", "power_palm_pressure_plate", "power_palm_sapling", "power_block", "item_crate", "fruit_crate",
              "item_crate_planked", "fruit_crate_planked", "item_crate_random", "power_fruit_bag")


def loot_tables():
    for name in SELF_DROPS:
        loot_self(name)
    loot("power_palm_slab", [{"type": "minecraft:item", "name": C + "power_palm_slab", "functions": [
        {"function": "minecraft:set_count", "count": 2.0, "add": False, "conditions": [{"condition": "minecraft:block_state_property", "block": C + "power_palm_slab", "properties": {"type": "double"}}]}]}])
    write_json(os.path.join(DATA, NS, "loot_table", "blocks", "power_palm_door.json"), {
        "type": "minecraft:block", "random_sequence": NS + ":blocks/power_palm_door",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}], "entries": [
            {"type": "minecraft:item", "name": C + "power_palm_door", "conditions": [{"condition": "minecraft:block_state_property", "block": C + "power_palm_door", "properties": {"half": "lower"}}]}]}]})
    loot("power_fruit_cluster", counted(C + "power_fruit", C + "power_fruit_cluster", "fruits", (1, 2, 3, 4)))
    loot("wheel", counted(C + "wheel", C + "wheel", "wheels", (1, 2, 3, 4)))
    not_shears = {"condition": "minecraft:inverted", "term": SHEARS_OR_SILK}
    write_json(os.path.join(DATA, NS, "loot_table", "blocks", "power_palm_leaves.json"), {
        "type": "minecraft:block", "random_sequence": NS + ":blocks/power_palm_leaves",
        "pools": [
            {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [SHEARS_OR_SILK], "entries": [{"type": "minecraft:item", "name": NS + ":power_palm_leaves"}]},
            {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [not_shears, {"condition": "minecraft:survives_explosion"}], "entries": [
                {"type": "minecraft:item", "name": NS + ":power_palm_sapling",
                 "conditions": [{"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": [0.06, 0.07, 0.09, 0.12, 0.2]}]}]},
            {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [not_shears, {"condition": "minecraft:survives_explosion"}], "entries": [
                {"type": "minecraft:item", "name": NS + ":power_fruit",
                 "conditions": [{"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": [0.1, 0.13, 0.17, 0.25, 0.4]}]}]},
            {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [not_shears], "entries": [
                {"type": "minecraft:item", "name": "minecraft:stick",
                 "conditions": [{"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]}],
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 2.0}, "add": False}]}]}]})


def tag(path, values):
    write_json(os.path.join(DATA, path + ".json"), {"values": values})


def tags():
    n = lambda s: NS + ":" + s
    logs = [n("power_palm_log"), n("power_palm_wood"), n("stripped_power_palm_log"), n("stripped_power_palm_wood")]
    for kind in ("block", "item"):
        tag("minecraft/tags/%s/logs" % kind, logs)
        tag("minecraft/tags/%s/logs_that_burn" % kind, logs)
        tag("minecraft/tags/%s/planks" % kind, [n("power_palm_planks")])
        tag("minecraft/tags/%s/leaves" % kind, [n("power_palm_leaves")])
        tag("minecraft/tags/%s/saplings" % kind, [n("power_palm_sapling")])
        tag("minecraft/tags/%s/stairs" % kind, [n("power_palm_stairs")])
        tag("minecraft/tags/%s/wooden_stairs" % kind, [n("power_palm_stairs")])
        tag("minecraft/tags/%s/slabs" % kind, [n("power_palm_slab")])
        tag("minecraft/tags/%s/wooden_slabs" % kind, [n("power_palm_slab")])
        tag("minecraft/tags/%s/fences" % kind, [n("power_palm_fence")])
        tag("minecraft/tags/%s/wooden_fences" % kind, [n("power_palm_fence")])
        tag("minecraft/tags/%s/fence_gates" % kind, [n("power_palm_fence_gate")])
        tag("minecraft/tags/%s/doors" % kind, [n("power_palm_door")])
        tag("minecraft/tags/%s/wooden_doors" % kind, [n("power_palm_door")])
        tag("minecraft/tags/%s/trapdoors" % kind, [n("power_palm_trapdoor")])
        tag("minecraft/tags/%s/wooden_trapdoors" % kind, [n("power_palm_trapdoor")])
        tag("minecraft/tags/%s/buttons" % kind, [n("power_palm_button")])
        tag("minecraft/tags/%s/wooden_buttons" % kind, [n("power_palm_button")])
        tag("minecraft/tags/%s/pressure_plates" % kind, [n("power_palm_pressure_plate")])
        tag("minecraft/tags/%s/wooden_pressure_plates" % kind, [n("power_palm_pressure_plate")])
        tag("c/tags/%s/fences" % kind, [n("power_palm_fence")])
        tag("c/tags/%s/fence_gates" % kind, [n("power_palm_fence_gate")])
        tag(NS + "/tags/%s/power_palm_logs" % kind, logs)
    tag("minecraft/tags/block/mineable/axe", logs + [n(s) for s in ("power_palm_planks", "power_palm_stairs", "power_palm_slab", "power_palm_fence", "power_palm_fence_gate", "power_palm_door",
                                                                    "power_palm_trapdoor", "power_palm_button", "power_palm_pressure_plate", "item_crate", "fruit_crate", "item_crate_random",
                                                                    "item_crate_planked", "fruit_crate_planked", "power_fruit_bag")])
    tag("minecraft/tags/block/mineable/hoe", [n("power_palm_leaves"), n("power_fruit_cluster")])
    tag("minecraft/tags/block/mineable/pickaxe", [n("power_block")])
    tag("minecraft/tags/block/needs_stone_tool", [n("power_block")])
    tag(NS + "/tags/item/item_crates", [n("item_crate"), n("item_crate_planked"), n("item_crate_random")])
    tag(NS + "/tags/item/fruit_crates", [n("fruit_crate"), n("fruit_crate_planked")])
    tag("c/tags/item/ingots/power", [n("power_ingot")])
    tag("c/tags/item/nuggets/power", [n("power_nugget")])
    tag("c/tags/item/storage_blocks/power", [n("power_block")])
    tag("c/tags/block/storage_blocks/power", [n("power_block")])
    tag("c/tags/item/ingots", [n("power_ingot")])
    tag("c/tags/item/nuggets", [n("power_nugget")])
    tag("c/tags/item/raw_materials", [n("raw_power_ore")])
    tag("c/tags/item/foods/fruit", [n("power_fruit")])
    tag("minecraft/tags/item/saplings", [n("power_palm_sapling")])


def worldgen():
    write_json(os.path.join(DATA, NS, "worldgen", "configured_feature", "power_palm.json"), {"type": NS + ":power_palm", "config": {}})
    write_json(os.path.join(DATA, NS, "worldgen", "configured_feature", "power_palm_wild.json"), {"type": NS + ":power_palm_wild", "config": {}})
    write_json(os.path.join(DATA, NS, "worldgen", "placed_feature", "power_palm_beach.json"), {
        "feature": NS + ":power_palm_wild",
        "placement": [
            {"type": "minecraft:count", "count": 8},                                  # tries a chunk: the server's config says how many stand (PowerPalmWildFeature)
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
            {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_block_tag", "offset": [0, -1, 0], "tag": "minecraft:sand"}},
            {"type": "minecraft:biome"}]})
    write_json(os.path.join(DATA, NS, "neoforge", "biome_modifier", "add_power_palms.json"), {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_beach", "features": NS + ":power_palm_beach", "step": "vegetal_decoration"})


# ======================================================================== models, blockstates, language
def block_model(name, obj):
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"), obj)


def item_model(name, parent_block=None, layer=None, display=None):
    if parent_block:
        obj = {"parent": "%s:block/%s" % (NS, parent_block)}
    else:
        obj = {"parent": "minecraft:item/generated", "textures": {"layer0": layer or "%s:item/%s" % (NS, name)}}
    if display:
        obj["display"] = display
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), obj)


def state(name, obj):
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), obj)


def tex(name):
    return NS + ":block/" + name


def stairs_state(name):
    variants = {}
    tops = {"straight": ("", ), }
    for facing, ys in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = name + ("" if shape == "straight" else "_" + shape.split("_")[0])
                y = ys
                if shape.endswith("left"):
                    y = (ys + 270) % 360
                if half == "top":
                    if shape in ("straight",):
                        pass
                    elif shape.endswith("right"):
                        pass
                v = {"model": "%s:block/%s" % (NS, model)}
                x = 0
                if half == "top":
                    x = 180
                    # a flipped stair turns the other way round: the "right" shapes are what the "left" ones are from below
                    if shape != "straight":
                        y = (ys + 90) % 360 if shape.endswith("right") else ys
                    else:
                        y = ys
                if x:
                    v["x"] = x
                if y:
                    v["y"] = y
                v["uvlock"] = True
                variants["facing=%s,half=%s,shape=%s" % (facing, half, shape)] = v
    state(name, {"variants": variants})


def models():
    # ---- the fronds: plain cubes in the mod itself; the fluffy look (crossed planes round a cube, four variants) is an optional built-in pack ("fluffy_fronds") that turns it on
    leaves = ensure_leaves_bbmodel()
    block_model("power_palm_leaves", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped", "textures": {"all": tex("power_palm_leaves")}})
    state("power_palm_leaves", {"variants": {"": {"model": NS + ":block/power_palm_leaves"}}})
    item_model("power_palm_leaves", "power_palm_leaves")
    fluffy = os.path.join(RES, "resourcepacks", "fluffy_fronds")
    write_json(os.path.join(fluffy, "pack.mcmeta"), {"pack": {"pack_format": 34, "description": "CTR: Fluffy Palm Fronds (crossed planes round each leaf block)"}})
    variants = []
    for n in range(1, 5):
        write_json(os.path.join(fluffy, "assets", NS, "models", "block", "power_palm_leaves_%d.json" % n), {
            "parent": "minecraft:block/block", "render_type": "minecraft:cutout_mipped",
            "textures": {"all": tex("power_palm_leaves_fluffy"), "particle": tex("power_palm_leaves")},
            "elements": bbmodel.elements(leaves, "base", "v%da" % n, "v%db" % n)})
        for y in (0, 90, 180, 270):
            v = {"model": "%s:block/power_palm_leaves_%d" % (NS, n)}
            if y:
                v["y"] = y
            variants.append(v)
    write_json(os.path.join(fluffy, "assets", NS, "blockstates", "power_palm_leaves.json"), {"variants": {"": variants}})
    # ---- logs and wood
    for name, side, end in (("power_palm_log", "power_palm_log", "power_palm_log_top"), ("power_palm_wood", "power_palm_log", "power_palm_log"),
                            ("stripped_power_palm_log", "stripped_power_palm_log", "stripped_power_palm_log_top"),
                            ("stripped_power_palm_wood", "stripped_power_palm_log", "stripped_power_palm_log")):
        block_model(name, {"parent": "minecraft:block/cube_column", "textures": {"end": tex(end), "side": tex(side)}})
        state(name, {"variants": {"axis=y": {"model": "%s:block/%s" % (NS, name)}, "axis=z": {"model": "%s:block/%s" % (NS, name), "x": 90},
                                  "axis=x": {"model": "%s:block/%s" % (NS, name), "x": 90, "y": 90}}})
        item_model(name, name)
    # ---- planks and the rest of the wood set
    P = tex("power_palm_planks")
    block_model("power_palm_planks", {"parent": "minecraft:block/cube_all", "textures": {"all": P}})
    state("power_palm_planks", {"variants": {"": {"model": NS + ":block/power_palm_planks"}}})
    item_model("power_palm_planks", "power_palm_planks")
    st = {"bottom": P, "top": P, "side": P}
    block_model("power_palm_stairs", {"parent": "minecraft:block/stairs", "textures": st})
    block_model("power_palm_stairs_inner", {"parent": "minecraft:block/inner_stairs", "textures": st})
    block_model("power_palm_stairs_outer", {"parent": "minecraft:block/outer_stairs", "textures": st})
    stairs_state("power_palm_stairs")
    item_model("power_palm_stairs", "power_palm_stairs")
    block_model("power_palm_slab", {"parent": "minecraft:block/slab", "textures": st})
    block_model("power_palm_slab_top", {"parent": "minecraft:block/slab_top", "textures": st})
    state("power_palm_slab", {"variants": {"type=bottom": {"model": NS + ":block/power_palm_slab"}, "type=top": {"model": NS + ":block/power_palm_slab_top"},
                                           "type=double": {"model": NS + ":block/power_palm_planks"}}})
    item_model("power_palm_slab", "power_palm_slab")
    t = {"texture": P}
    block_model("power_palm_fence_post", {"parent": "minecraft:block/fence_post", "textures": t})
    block_model("power_palm_fence_side", {"parent": "minecraft:block/fence_side", "textures": t})
    block_model("power_palm_fence_inventory", {"parent": "minecraft:block/fence_inventory", "textures": t})
    state("power_palm_fence", {"multipart": [{"apply": {"model": NS + ":block/power_palm_fence_post"}}] + [
        {"when": {d: "true"}, "apply": {"model": NS + ":block/power_palm_fence_side", "uvlock": True, **({"y": y} if y else {})}} for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))]})
    item_model("power_palm_fence", "power_palm_fence_inventory")
    for suffix, parent in (("", "template_fence_gate"), ("_open", "template_fence_gate_open"), ("_wall", "template_fence_gate_wall"), ("_wall_open", "template_fence_gate_wall_open")):
        block_model("power_palm_fence_gate" + suffix, {"parent": "minecraft:block/" + parent, "textures": t})
    gate = {}
    for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        for in_wall in ("false", "true"):
            for opened in ("false", "true"):
                model = "power_palm_fence_gate" + ("_wall" if in_wall == "true" else "") + ("_open" if opened == "true" else "")
                v = {"model": "%s:block/%s" % (NS, model), "uvlock": True}
                if y:
                    v["y"] = y
                gate["facing=%s,in_wall=%s,open=%s" % (facing, in_wall, opened)] = v
    state("power_palm_fence_gate", {"variants": gate})
    item_model("power_palm_fence_gate", "power_palm_fence_gate")
    td = {"texture": tex("power_palm_trapdoor")}
    for suffix in ("bottom", "top", "open"):
        block_model("power_palm_trapdoor_" + suffix, {"parent": "minecraft:block/template_trapdoor_" + suffix, "render_type": "minecraft:cutout", "textures": td})
    trap = {}
    for facing, y in (("north", 0), ("south", 180), ("east", 90), ("west", 270)):
        for half in ("bottom", "top"):
            for opened in ("false", "true"):
                if opened == "false":
                    v = {"model": "%s:block/power_palm_trapdoor_%s" % (NS, half)}
                    if y:
                        v["y"] = y
                elif half == "bottom":
                    v = {"model": NS + ":block/power_palm_trapdoor_open"}
                    if y:
                        v["y"] = y
                else:
                    v = {"model": NS + ":block/power_palm_trapdoor_open", "x": 180, "y": (y + 180) % 360}
                    if v["y"] == 0:
                        del v["y"]
                trap["facing=%s,half=%s,open=%s" % (facing, half, opened)] = v
    state("power_palm_trapdoor", {"variants": trap})
    item_model("power_palm_trapdoor", "power_palm_trapdoor_bottom")
    block_model("power_palm_button", {"parent": "minecraft:block/button", "textures": t})
    block_model("power_palm_button_pressed", {"parent": "minecraft:block/button_pressed", "textures": t})
    block_model("power_palm_button_inventory", {"parent": "minecraft:block/button_inventory", "textures": t})
    btn = {}
    for face, x, ys in (("floor", 0, {"east": 90, "west": 270, "south": 180, "north": 0}), ("wall", 90, {"east": 90, "west": 270, "south": 180, "north": 0}),
                        ("ceiling", 180, {"east": 270, "west": 90, "south": 0, "north": 180})):
        for facing, y in ys.items():
            for powered in ("false", "true"):
                v = {"model": NS + ":block/power_palm_button" + ("_pressed" if powered == "true" else "")}
                if x:
                    v["x"] = x
                if y:
                    v["y"] = y
                if face != "wall":
                    v["uvlock"] = True
                btn["face=%s,facing=%s,powered=%s" % (face, facing, powered)] = v
    state("power_palm_button", {"variants": btn})
    item_model("power_palm_button", "power_palm_button_inventory")
    block_model("power_palm_pressure_plate", {"parent": "minecraft:block/pressure_plate_up", "textures": t})
    block_model("power_palm_pressure_plate_down", {"parent": "minecraft:block/pressure_plate_down", "textures": t})
    state("power_palm_pressure_plate", {"variants": {"powered=false": {"model": NS + ":block/power_palm_pressure_plate"}, "powered=true": {"model": NS + ":block/power_palm_pressure_plate_down"}}})
    item_model("power_palm_pressure_plate", "power_palm_pressure_plate")
    dt = {"top": tex("power_palm_door_top"), "bottom": tex("power_palm_door_bottom")}
    for half in ("bottom", "top"):
        for hinge in ("left", "right"):
            for suffix in ("", "_open"):
                block_model("power_palm_door_%s_%s%s" % (half, hinge, suffix), {"parent": "minecraft:block/door_%s_%s%s" % (half, hinge, suffix), "render_type": "minecraft:cutout", "textures": dt})
    door = {}
    closed_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    open_left = {"east": 90, "south": 180, "west": 270, "north": 0}
    open_right = {"east": 270, "south": 0, "west": 90, "north": 180}
    for facing in closed_y:
        for half, half_name in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for opened in ("false", "true"):
                    if opened == "false":
                        y, suffix = closed_y[facing], ""
                    else:
                        y, suffix = (open_left if hinge == "left" else open_right)[facing], "_open"
                    v = {"model": "%s:block/power_palm_door_%s_%s%s" % (NS, half_name, hinge, suffix)}
                    if y:
                        v["y"] = y
                    door["facing=%s,half=%s,hinge=%s,open=%s" % (facing, half, hinge, opened)] = v
    state("power_palm_door", {"variants": door})
    item_model("power_palm_door")
    # ---- the log's, the planked crates' and the metal's blocks
    block_model("power_block", {"parent": "minecraft:block/cube_all", "textures": {"all": tex("power_block")}})
    state("power_block", {"variants": {"": {"model": NS + ":block/power_block"}}})
    item_model("power_block", "power_block")
    block_model("item_crate_random", {"parent": "minecraft:block/cube_all", "textures": {"all": tex("item_crate_random")}})
    state("item_crate_random", {"variants": {"": {"model": NS + ":block/item_crate_random"}}})
    item_model("item_crate_random", "item_crate_random")
    for kind in ("item_crate", "fruit_crate"):
        block_model(kind, {"parent": "minecraft:block/cube_all", "textures": {"all": tex(kind)}})
        state(kind, {"variants": {"": {"model": NS + ":block/" + kind}}})
        item_model(kind, kind)
        planked = kind + "_planked"
        block_model(planked, {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": tex(planked + "_top"), "bottom": tex(planked + "_top"), "side": tex(planked + "_side")}})
        state(planked, {"variants": {"": {"model": NS + ":block/" + planked}}})
        item_model(planked, planked)
    # ---- the sapling
    block_model("power_palm_sapling", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": tex("power_palm_sapling")}})
    state("power_palm_sapling", {"variants": {"stage=0": {"model": NS + ":block/power_palm_sapling"}, "stage=1": {"model": NS + ":block/power_palm_sapling"}}})
    item_model("power_palm_sapling", layer=tex("power_palm_sapling"))
    # ---- the hanging fruit: one to four of the track's Power Fruit
    pods = ensure_pods_bbmodel()
    for n in range(1, 5):
        block_model("power_fruit_cluster_%d" % n, {"parent": "minecraft:block/block", "textures": {"pickups": NS + ":item/pickups", "particle": NS + ":item/pickups"},
                                                    "elements": bbmodel.elements(pods, "p%d" % n), "display": pods["display"] or {}})
    state("power_fruit_cluster", {"variants": {"fruits=%d,pruned=%s" % (n, pr): {"model": "%s:block/power_fruit_cluster_%d" % (NS, n)} for n in range(1, 5) for pr in ("false", "true")}})
    # ---- the wheel
    wheel = ensure_wheel_bbmodel()
    gui = {"gui": {"rotation": [30, 45, 0], "translation": [0, 11.5, 0], "scale": [2.0, 2.0, 2.0]}, "ground": {"translation": [0, 3, 0], "scale": [0.8, 0.8, 0.8]},
           "fixed": {"translation": [0, 5, 0], "scale": [1.5, 1.5, 1.5]}, "thirdperson_righthand": {"translation": [0, 3, 1], "scale": [0.8, 0.8, 0.8]}}
    for n in range(1, 5):
        block_model("wheel_%d" % n, {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": {"wheel": NS + ":item/kart_wheel", "particle": NS + ":item/kart_wheel"}, "elements": bbmodel.elements(wheel, "w%d" % n), "display": gui})
    wheel_state = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for n in range(1, 5):
            v = {"model": "%s:block/wheel_%d" % (NS, n)}
            if y:
                v["y"] = y
            wheel_state["facing=%s,wheels=%d" % (facing, n)] = v
    state("wheel", {"variants": wheel_state})
    item_model("wheel", "wheel_1")
    # ---- the bag
    bag = ensure_bag_bbmodel()
    block_model("power_fruit_bag", {"parent": "minecraft:block/block", "textures": {"side": tex("power_fruit_bag_side"), "top": tex("power_fruit_bag_top"), "bottom": tex("power_fruit_bag_bottom"),
                                                  "particle": tex("power_fruit_bag_side")}, "elements": bbmodel.elements(bag), "display": bag["display"] or {}})
    state("power_fruit_bag", {"variants": {"facing=%s" % f: ({"model": NS + ":block/power_fruit_bag", "y": y} if y else {"model": NS + ":block/power_fruit_bag"})
                                           for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    item_model("power_fruit_bag", "power_fruit_bag")
    # with Farm & Charm: its sack model and side/bottom/knot pictures under our own top picture (a built-in pack, only active when that mod is loaded)
    compat = os.path.join(RES, "resourcepacks", "farm_and_charm_compat")
    write_json(os.path.join(compat, "pack.mcmeta"), {"pack": {"pack_format": 34, "description": "CTR: Farm & Charm Sacks (the bag of power fruit in their sack)"}})
    write_json(os.path.join(compat, "assets", NS, "models", "block", "power_fruit_bag.json"), {
        "parent": "farm_and_charm:block/template_bag",
        "textures": {"3": "farm_and_charm:block/bag_side_knot", "4": "farm_and_charm:block/bag_bottom", "7": "farm_and_charm:block/bag_side", "8": tex("power_fruit_bag_top"),
                     "particle": tex("power_fruit_bag_top")}})
    # ---- the items of the chain
    for name in ("power_fruit", "power_juice", "raw_power_ore", "power_ingot", "power_nugget"):
        item_model(name)


LANG = {
    "block.craftteamracing.power_palm_log": "Power Palm Log",
    "block.craftteamracing.power_palm_wood": "Power Palm Wood",
    "block.craftteamracing.stripped_power_palm_log": "Stripped Power Palm Log",
    "block.craftteamracing.stripped_power_palm_wood": "Stripped Power Palm Wood",
    "block.craftteamracing.power_palm_planks": "Power Palm Planks",
    "block.craftteamracing.power_palm_stairs": "Power Palm Stairs",
    "block.craftteamracing.power_palm_slab": "Power Palm Slab",
    "block.craftteamracing.power_palm_fence": "Power Palm Fence",
    "block.craftteamracing.power_palm_fence_gate": "Power Palm Fence Gate",
    "block.craftteamracing.power_palm_door": "Power Palm Door",
    "block.craftteamracing.power_palm_trapdoor": "Power Palm Trapdoor",
    "block.craftteamracing.power_palm_button": "Power Palm Button",
    "block.craftteamracing.power_palm_pressure_plate": "Power Palm Pressure Plate",
    "block.craftteamracing.power_palm_leaves": "Power Palm Fronds",
    "block.craftteamracing.power_palm_sapling": "Power Palm Sapling",
    "block.craftteamracing.power_fruit_cluster": "Power Fruit Cluster",
    "block.craftteamracing.power_block": "Block of Power",
    "block.craftteamracing.item_crate": "Item Crate",
    "block.craftteamracing.fruit_crate": "Fruit Crate",
    "block.craftteamracing.item_crate_random": "Item Crate Random",
    "block.craftteamracing.trigger_crate_spot": "Trigger Crate Spot",
    "block.craftteamracing.redstone_link_node": "Redstone Linking Node",
    "screen.craftteamracing.trigger.title": "Trigger Crate",
    "screen.craftteamracing.trigger.respawn": "Respawn: %s",
    "screen.craftteamracing.trigger.duration": "Redstone signal: %s",
    "screen.craftteamracing.trigger.uses": "Uses: %s",
    "screen.craftteamracing.trigger.default": "as other crates",
    "screen.craftteamracing.trigger.always": "always",
    "screen.craftteamracing.trigger.infinite": "unlimited",
    "screen.craftteamracing.trigger.ticks": "%s ticks",
    "screen.craftteamracing.trigger.seconds": "%s s",
    "screen.craftteamracing.trigger.minutes": "%s min",
    "message.craftteamracing.link.picked": "Linking node picked: use the Wrench on a Trigger Crate Spot",
    "message.craftteamracing.link.linked": "Linked to the Trigger Crate Spot",
    "message.craftteamracing.link.unlinked": "Link removed",
    "message.craftteamracing.link.not_linked": "That spot is not linked to the picked node",
    "message.craftteamracing.link.already": "Already linked (crouch and use the Wrench to unlink)",
    "message.craftteamracing.link.dropped": "Linking node let go",
    "screen.craftteamracing.tuning.debug": "Debug (operators)",
    "screen.craftteamracing.tuning.debug_hint": "Drag the handles; Use puts these numbers on the kart now.",
    "screen.craftteamracing.tuning.export": "Export",
    "message.craftteamracing.tuning.exported": "Tuning exported to %s",
    "message.craftteamracing.tuning.export_failed": "Could not export the tuning: %s",
    "screen.craftteamracing.kart.tuning": "Tuning: %s...",
    "screen.craftteamracing.spot.hint": "Click to select several: the crate gives one of them at random. The mark on a selected one sets how many.",
    "screen.craftteamracing.spot.quantity_of": "%s: how many",
    "screen.craftteamracing.spot.quantity": "Quantity: %s",
    "screen.craftteamracing.spot.min": "Minimum: %s",
    "screen.craftteamracing.spot.max": "Maximum: %s",
    "screen.craftteamracing.spot.range_off": "Random range: off",
    "screen.craftteamracing.spot.range_on": "Range: on",
    "screen.craftteamracing.spot.range_hint": "On: the crate gives a random number from the minimum to the maximum.",
    "screen.craftteamracing.kart.mask_light_plain": "light",
    "screen.craftteamracing.kart.mask_dark_plain": "dark",
    "screen.craftteamracing.tuning.title": "Kart tuning",
    "screen.craftteamracing.tuning.use": "Use this tuning",
    "screen.craftteamracing.tuning.back": "Back",
    "screen.craftteamracing.tuning.official": "Classic class",
    "screen.craftteamracing.tuning.custom": "Custom (data pack)",
    "screen.craftteamracing.tuning.accel": "Acceleration",
    "screen.craftteamracing.tuning.speed": "Top speed",
    "screen.craftteamracing.tuning.turn": "Turning",
    "screen.craftteamracing.tuning.turn_decrease": "Speed lost steering",
    "screen.craftteamracing.tuning.response": "Steering response",
    "screen.craftteamracing.tuning.drift": "Drift turn",
    "screen.craftteamracing.tuning.weight": "Bump weight",
    "screen.craftteamracing.tuning.tick": "The white tick is what the kart has now.",
    "message.craftteamracing.tuning.set": "Tuning: %s",
    "tooltip.craftteamracing.kart.tuning": "Tuning: %s",
    "block.craftteamracing.item_crate_planked": "Planked Item Crate",
    "block.craftteamracing.fruit_crate_planked": "Planked Fruit Crate",
    "block.craftteamracing.wheel": "Wheel",
    "block.craftteamracing.power_fruit_bag": "Bag of Power Fruit",
    "item.craftteamracing.power_fruit": "Power Fruit",
    "item.craftteamracing.power_juice": "Power Juice",
    "item.craftteamracing.raw_power_ore": "Raw Power Ore",
    "item.craftteamracing.power_ingot": "Power Ingot",
    "item.craftteamracing.power_nugget": "Power Nugget",
}


def humanise(name):
    words = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", name)
    return words[0].upper() + words[1:]


def config_lang():
    """Labels for NeoForge's config screen (craftteamracing.configuration.<name>): every option and section named in the two config classes, written out from its camelCase name."""
    out = {"craftteamracing.configuration.title": "Craft Team Racing"}
    for rel in ("CtrConfig.java", "CtrClientConfig.java"):
        with open(os.path.join(ROOT, "src", "main", "java", "com", "craftteamracing", rel), encoding="utf-8") as f:
            src = f.read()
        for name in re.findall(r'\.(?:define\w*)\("([A-Za-z]+)"', src) + re.findall(r'\.push\("([A-Za-z]+)"\)', src):
            out["craftteamracing.configuration." + name] = humanise(name)
    return out


def lang():
    path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    data.update(config_lang())
    for k in [k for k in data if k.endswith("raw_power_iron") or k.endswith("power_fruit_cluster_0")]:
        del data[k]
    data.update(LANG)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


STALE = [
    "assets/craftteamracing/textures/block/power_palm_leaves_item.png", "assets/craftteamracing/models/block/power_palm_leaves_1.json", "assets/craftteamracing/models/block/power_palm_leaves_2.json",
    "assets/craftteamracing/models/block/power_palm_leaves_3.json", "assets/craftteamracing/models/block/power_palm_leaves_4.json",
    "assets/craftteamracing/textures/item/raw_power_iron.png", "assets/craftteamracing/models/item/raw_power_iron.json",
    "assets/craftteamracing/textures/block/item_crate_side.png", "assets/craftteamracing/textures/block/item_crate_top.png",
    "assets/craftteamracing/textures/block/fruit_crate_side.png", "assets/craftteamracing/textures/block/fruit_crate_top.png",
    "assets/craftteamracing/textures/block/power_block.png.mcmeta",
    "assets/craftteamracing/textures/block/power_fruit_cluster_0.png", "assets/craftteamracing/textures/block/power_fruit_cluster_1.png", "assets/craftteamracing/textures/block/power_fruit_cluster_2.png",
    "assets/craftteamracing/models/block/power_fruit_cluster_0.json", "assets/craftteamracing/models/block/power_fruit_cluster_1.json", "assets/craftteamracing/models/block/power_fruit_cluster_2.json",
    "data/craftteamracing/recipe/raw_power_iron_from_juice.json", "data/craftteamracing/loot_table/blocks/raw_power_iron.json",
    "data/craftteamracing/recipe/item_crate_planked.json", "data/craftteamracing/recipe/item_crate_from_planked.json", "data/craftteamracing/recipe/item_crate_random.json", "data/craftteamracing/recipe/item_crate_from_random.json",
    "data/craftteamracing/recipe/fruit_crate_planked.json", "data/craftteamracing/recipe/fruit_crate_from_planked.json",
]


# ======================================================================== main
def main():
    shutil.rmtree(os.path.join(RES, "resourcepacks", "plain_fronds"), ignore_errors=True)
    for rel in STALE:
        p = os.path.join(RES, rel)
        if os.path.exists(p):
            os.remove(p)
    old_pods = os.path.join(BB, "power_fruit_cluster.bbmodel")
    if os.path.exists(old_pods):
        os.remove(old_pods)
    rnd = random.Random(14)
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import make_power_sprites as sprites
    # item pictures
    save(sprites.power_fruit(), "item/power_fruit.png")
    save(power_juice(), "item/power_juice.png")
    save(raw_power_ore(), "item/raw_power_ore.png")
    animate("item/power_ingot.png", [ingot_frame(cells) for cells in INGOT_SPARKS], 4)
    animate("item/power_nugget.png", [nugget_frame(s) for s in ((8, 8, True), (10, 9, False), (7, 10, True), (9, 7, False))], 5)
    save(door_item(), "item/power_palm_door.png")
    # block pictures
    save(power_block(), "block/power_block.png")
    save(log_side(rnd), "block/power_palm_log.png")
    save(log_top(rnd), "block/power_palm_log_top.png")
    save(log_side(rnd, light=True), "block/stripped_power_palm_log.png")
    save(log_top(rnd, light=True), "block/stripped_power_palm_log_top.png")
    save(planks(rnd), "block/power_palm_planks.png")
    save(trapdoor(rnd), "block/power_palm_trapdoor.png")
    save(door_half(rnd, True), "block/power_palm_door_top.png")
    save(door_half(rnd, False), "block/power_palm_door_bottom.png")
    fluffy_tex = os.path.join(RES, "resourcepacks", "fluffy_fronds", "assets", NS, "textures", "block", "power_palm_leaves_fluffy.png")
    os.makedirs(os.path.dirname(fluffy_tex), exist_ok=True)
    palm_leaves(rnd).save(fluffy_tex)
    save(palm_leaves_item(rnd), "block/power_palm_leaves.png")
    save(palm_sapling(), "block/power_palm_sapling.png")
    side, top, bottom = bag_textures()
    save(side, "block/power_fruit_bag_side.png")
    save(top, "block/power_fruit_bag_top.png")
    save(bottom, "block/power_fruit_bag_bottom.png")
    for kind, (cside, ctop, cmarked, cmain) in crate_textures().items():
        save(cmain, "block/%s_crate.png" % kind)                   # the crate as the race's pickups are: its face on all six sides (the item crate blank)
        if kind == "item":
            save(cmarked, "block/item_crate_random.png")           # the "?" crate
        save(cside, "block/%s_crate_planked_side.png" % kind)
        save(ctop, "block/%s_crate_planked_top.png" % kind)
    models()
    lang()
    recipes()
    loot_tables()
    tags()
    worldgen()
    print("survival assets ok")


if __name__ == "__main__":
    main()
