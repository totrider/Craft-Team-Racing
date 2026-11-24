"""
The survival side's pictures (the Power Palm and its wood set, the Power Fruit's chain, the bag), drawn as LAYERED sheets for tools/make_survival_assets.py.

The pipeline is  art script -> layered .kra (krita/) -> PNG (tools/build_art.py), see krita/README.md. Every function here returns a kra_layers.Sheet whose layers
are the picture's parts, bottom to top (roles such as the ring scars of a trunk, the seams of the planks, the outline of an item). The parts never change the picture:
flattened, a sheet is exactly what the script used to draw straight to a PNG. Textures are original pixel art in the power fruit's colours (red, orange, yellow, lime);
outlines are the dark of the colours they enclose, not black.

build_all() draws every picture in the order the old script did: one random generator (seed 14) is shared by the wood and leaf pictures, so the order matters.
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402

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


def clamp(c, d):
    return tuple(max(0, min(255, v + d)) for v in c)


def hole(layers, cells):
    """Cuts see-through holes: clears the cells in the given pixel accesses (a hole must not show the layers below it)."""
    for px in layers:
        for xy in cells:
            px[xy] = (0, 0, 0, 0)


def only(img, cells):
    """A transparent copy of img that keeps just the given cells."""
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for xy in cells:
        out.putpixel(xy, img.getpixel(xy))
    return out


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
    """The ingot at one frame of its animation as (body, outline, sparks) pictures; {spark_cells}: (x, y, hot) pixels carrying a yellow spark."""
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
    sparks = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y, hot) in spark_cells:
        if (x, y) not in outline and out.getpixel((x, y))[3]:
            sparks.putpixel((x, y), SPARK_HOT if hot else SPARK)
    return im, only(out, outline), sparks


INGOT_SPARKS = [
    [(6, 5, True), (11, 8, False)],
    [(7, 5, False), (10, 7, True), (4, 9, False)],
    [(9, 6, False), (9, 9, True)],
    [(5, 7, True), (12, 6, False)],
    [(8, 5, False), (5, 10, True), (11, 7, False)],
    [(10, 6, True), (3, 8, False)],
]
NUGGET_SPARKS = ((8, 8, True), (10, 9, False), (7, 10, True), (9, 7, False))


def nugget_frame(spark):
    """The nugget: a little star of a lump, lit from the top left, with the outline derived from its colours. Returns (body, outline, spark) pictures."""
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
    sparks = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    if spark and out.getpixel(spark[:2])[3] and spark[:2] not in outline:
        sparks.putpixel(spark[:2], SPARK_HOT if spark[2] else SPARK)
    return im, only(out, outline), sparks


def strip(frames_parts, part_names):
    """A vertical animation strip (frames stacked downwards, 16 x 16 each) as layers: for every frame one layer per part, named 'frame <n> <part>'."""
    n = len(frames_parts)
    sheet = kra_layers.Sheet(16, 16 * n)
    for i, parts in enumerate(frames_parts):
        for name, img in zip(part_names, parts):
            layer = Image.new("RGBA", (16, 16 * n), (0, 0, 0, 0))
            layer.paste(img, (0, 16 * i))
            sheet.add("frame %d %s" % (i + 1, name), layer)
    return sheet


def power_ingot():
    return strip([ingot_frame(cells) for cells in INGOT_SPARKS], ("body", "outline", "sparks"))


def power_nugget():
    return strip([nugget_frame(s) for s in NUGGET_SPARKS], ("body", "outline", "spark"))


def raw_power_ore():
    """A lump of ore (raw iron's idea): a brown-red rock flecked with the fruit's colours."""
    rnd = random.Random(8)
    mask = Image.new("L", (16, 16), 0)
    ImageDraw.Draw(mask).polygon([(3, 9), (4, 5), (8, 3), (12, 5), (13, 9), (11, 13), (6, 13)], fill=255)
    body = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if mask.getpixel((x, y)):
                base = (150, 96, 72) if (x + y) % 5 else (132, 82, 62)
                if (x + y) < 15:
                    base = shade(base, 1.14)
                if y > 10 or x > 10:
                    base = shade(base, 0.78)
                body.putpixel((x, y), base + (255,))
    flecks = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for _ in range(11):
        x, y = rnd.randint(4, 12), rnd.randint(5, 12)
        if mask.getpixel((x, y)):
            flecks.putpixel((x, y), ramp(rnd.random()) + (255,))
            if rnd.random() < 0.4 and mask.getpixel((x + 1, y)):
                flecks.putpixel((x + 1, y), ramp(rnd.random()) + (255,))
    im = body.copy()
    im.alpha_composite(flecks)
    outline = {}
    for y in range(16):
        for x in range(16):
            if mask.getpixel((x, y)) == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and mask.getpixel((x + dx, y + dy)) for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                outline[(x, y)] = "o"
    out = derived_outline(im, outline, lambda kind: 0.42)
    sheet = kra_layers.Sheet(16, 16)
    sheet.add("rock", body)
    sheet.add("flecks", flecks)
    sheet.add("outline", only(out, outline))
    return sheet


def poly_mask(points, size=16):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).polygon(points, fill=255)
    return m


def power_juice():
    """A glass bottle with a cork, half full of the juice: the fruit's colours in layers, lime at the top, red at the bottom."""
    sheet = kra_layers.Sheet(16, 16)
    glass, liquid, rim_px, cork, glints = (sheet.layer(n) for n in ("glass", "juice", "rim", "cork", "highlights"))
    rim = (92, 112, 128, 255)
    body = [(5, 6), (10, 6), (12, 8), (12, 13), (10, 15), (5, 15), (3, 13), (3, 8)]
    mask = poly_mask(body)
    neck = poly_mask([(6, 3), (9, 3), (9, 6), (6, 6)])
    for y in range(16):
        for x in range(16):
            if mask.getpixel((x, y)) or neck.getpixel((x, y)):
                glass[x, y] = (206, 228, 238, 150)
    for y in range(8, 15):
        t = 1.0 - (y - 8) / 6.0
        for x in range(4, 12):
            if mask.getpixel((x, y)):
                liquid[x, y] = shade(ramp(t * 0.9 + 0.05), 1.0 if x < 9 else 0.82) + (255,)
    full = Image.new("L", (16, 16), 0)
    ImageDraw.Draw(full).polygon(body, fill=255)
    ImageDraw.Draw(full).rectangle([6, 3, 9, 6], fill=255)
    for y in range(16):
        for x in range(16):
            if full.getpixel((x, y)) == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and full.getpixel((x + dx, y + dy)) for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                rim_px[x, y] = rim
    for x in range(6, 10):
        for y in range(1, 3):
            cork[x, y] = (150, 100, 55, 255) if (x + y) % 2 else (176, 122, 70, 255)
    cells = ((4, 9), (4, 10), (5, 8))
    hole((glass, liquid), cells)                                   # the highlights are semi-transparent: nothing may show through under them
    for xy in cells:
        glints[xy] = (255, 255, 255, 235)
    return sheet


def door_item():
    """The door's sprite: a frame of planks, four see-through panes above and two raised panels below, a knob."""
    sheet = kra_layers.Sheet(16, 16)
    frame_img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    face_img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rail_img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    fill_img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    line_img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    ImageDraw.Draw(frame_img).rectangle([3, 0, 12, 15], fill=(150, 112, 74, 255))
    ImageDraw.Draw(face_img).rectangle([4, 1, 11, 14], fill=(206, 166, 116, 255))
    cells = [(x, y) for x0 in (5, 8) for y0 in (2, 4) for x in (x0, x0 + 1) for y in (y0, y0 + 1)]
    hole((frame_img.load(), face_img.load()), cells)               # the four panes are see-through
    ImageDraw.Draw(rail_img).rectangle([4, 7, 11, 7], fill=(150, 112, 74, 255))
    for y0, y1 in ((8, 10), (12, 13)):
        ImageDraw.Draw(fill_img).rectangle([5, y0, 10, y1], fill=(190, 150, 102, 255))
        ImageDraw.Draw(line_img).rectangle([5, y0, 10, y1], outline=(168, 128, 86, 255))
    for name, img in (("frame", frame_img), ("door face", face_img), ("rail", rail_img), ("raised panels", fill_img), ("panel outlines", line_img)):
        sheet.add(name, img)
    sheet.layer("knob")[10, 9] = (128, 139, 149, 255)
    return sheet


# ======================================================================== block pictures
def power_block():
    """The block of power: an iron block's flat plate (the faint bands of a plate, a one pixel edge lit top left and in shade bottom right) in the fruit's colours, a diagonal gradient and
    a few glints like gold's; it does not move."""
    rnd = random.Random(31)
    sheet = kra_layers.Sheet(16, 16)
    plate, edge, glint = sheet.layer("plate"), sheet.layer("edge"), sheet.layer("glints")
    glints = {(rnd.randint(2, 13), rnd.randint(2, 13)) for _ in range(9)}
    for y in range(16):
        for x in range(16):
            t = 1.0 - (x * 0.5 + (15 - y) * 0.5) / 15.0
            c = ramp(0.08 + 0.84 * (1.0 - t))
            k = (1.07, 1.0, 0.93)[y % 3]
            d = min(x, y, 15 - x, 15 - y)
            target = plate
            if d == 0:
                k = 1.16 if (x + y) < 15 else 0.78
                target = edge
            elif (x, y) in glints:
                k = 1.2
                target = glint
            target[x, y] = shade(c, k) + (255,)
    return sheet


def log_side(rnd, light=False):
    sheet = kra_layers.Sheet(16, 16)
    names = ("wood", "grain", "rings") if light else ("bark", "fibres", "ring highlights", "ring scars")
    L = {n: sheet.layer(n) for n in names}
    for y in range(16):
        for x in range(16):
            role = "bark"
            base = (128, 98, 66)
            if x % 5 in (1, 2):
                base = (106, 80, 54)                                    # fibres
                role = "fibres"
            if y % 4 == 0:
                base = (92, 68, 46)                                     # the ring scars of a palm's trunk
                role = "ring scars"
            elif y % 4 == 1:
                base = (150, 118, 82)
                role = "ring highlights"
            if light:                                                   # stripped: pale, smooth, only the faint rings left
                base = (204, 164, 114) if y % 4 else (186, 146, 98)
                role = "wood" if y % 4 else "rings"
                if x % 5 == 1:
                    base = (194, 154, 106)
                    role = "grain"
            base = tuple(max(0, min(255, v + rnd.randint(-6, 6))) for v in base)
            L[role][x, y] = base + (255,)
    return sheet


def log_top(rnd, light=False):
    sheet = kra_layers.Sheet(16, 16)
    L = {n: sheet.layer(n) for n in ("bark ring", "wood", "growth rings", "pith")}
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r > 7.2:
                c, role = ((104, 78, 52) if not light else (194, 154, 106)), "bark ring"
            elif int(r) % 3 == 0:
                c, role = (150, 116, 78), "growth rings"
            else:
                c, role = (196, 156, 108), "wood"
            if r < 1.6:
                c, role = (118, 88, 58), "pith"
            c = tuple(max(0, min(255, v + rnd.randint(-5, 5))) for v in c)
            L[role][x, y] = c + (255,)
    return sheet


def planks_into(sheet, rnd):
    """Adds the planks' layers (boards, seams, end joints) to a sheet."""
    L = {n: sheet.layer(n) for n in ("boards", "boards (darker rows)", "seams", "end joints")}
    for y in range(16):
        for x in range(16):
            row = y // 4
            c, role = ((206, 166, 116) if row % 2 == 0 else (196, 154, 104)), ("boards" if row % 2 == 0 else "boards (darker rows)")
            if y % 4 == 3:
                c, role = (150, 112, 74), "seams"                       # the seam
            if (x + row * 5) % 16 == 0:
                c, role = (160, 122, 82), "end joints"
            c = tuple(max(0, min(255, v + rnd.randint(-5, 5))) for v in c)
            L[role][x, y] = c + (255,)


def planks(rnd):
    sheet = kra_layers.Sheet(16, 16)
    planks_into(sheet, rnd)
    return sheet


def trapdoor(rnd):
    sheet = kra_layers.Sheet(16, 16)
    planks_into(sheet, rnd)
    outer, inner, bars, bolts = (Image.new("RGBA", (16, 16), (0, 0, 0, 0)) for _ in range(4))
    ImageDraw.Draw(outer).rectangle([0, 0, 15, 15], outline=(150, 112, 74, 255))
    ImageDraw.Draw(inner).rectangle([1, 1, 14, 14], outline=(176, 136, 90, 255))
    for y in (3, 12):
        ImageDraw.Draw(bars).line([(2, y), (13, y)], fill=(120, 88, 58, 255))
    for x, y in ((2, 3), (13, 3), (2, 12), (13, 12)):
        bolts.putpixel((x, y), (190, 190, 196, 255))
    for name, img in (("frame", outer), ("inner frame", inner), ("cross bars", bars), ("bolts", bolts)):
        sheet.add(name, img)
    return sheet


IRON_LIGHT, IRON_DARK = (128, 139, 149, 255), (107, 111, 122, 255)


def door_half(rnd, top):
    """A door's half, laid out as the oak door's, in the planks' own colours (the planks' picture turned so the boards stand upright; frame, bevels and panels use the planks' tones):
    iron hinges on the left edge, a handle where the halves meet. The top has four panes cut out, each with a bevel (shaded above and to the left, lit below and to the right); the
    bottom has two raised panels with grain."""
    P1, P2, SEAM, EDGE = (206, 166, 116), (196, 154, 104), (150, 112, 74), (160, 122, 82)
    LIT = (222, 184, 134)
    flat = kra_layers.Sheet(16, 16)
    planks_into(flat, rnd)
    sheet = kra_layers.Sheet(16, 16)
    for name, img in flat.layers:                                # the planks' picture turned so the boards stand upright
        sheet.layers.append((name, img.rotate(90)))
    wood_px = [img.load() for _, img in sheet.layers]

    def blank():
        return Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    frame = blank()
    ImageDraw.Draw(frame).rectangle([0, 0, 15, 15], outline=SEAM + (255,))
    sheet.add("frame", frame)
    if top:
        bevels = blank()
        cells = []
        for x0 in (3, 9):
            for y0 in (3, 8):
                for x in range(x0 - 1, x0 + 5):
                    bevels.putpixel((x, y0 - 1), SEAM + (255,))                              # the shadowed top edge
                    bevels.putpixel((x, y0 + 3), LIT + (255,))                               # the lit bottom edge
                for y in range(y0, y0 + 3):
                    bevels.putpixel((x0 - 1, y), EDGE + (255,))                              # shaded left edge
                    bevels.putpixel((x0 + 4, y), LIT + (255,))                               # lit right edge
                    for x in range(x0, x0 + 4):
                        cells.append((x, y))                                                 # the pane: cut out
        hole(wood_px, cells)
        sheet.add("pane bevels", bevels)
        handle, hinges = blank(), blank()
        for x, y in ((11, 14), (12, 14), (13, 14), (11, 15), (13, 15)):
            handle.putpixel((x, y), IRON_LIGHT if y == 14 else IRON_DARK)                    # the handle
        for y in (4, 5):
            hinges.putpixel((0, y), IRON_LIGHT)                                              # hinges
        sheet.add("handle", handle)
        sheet.add("hinges", hinges)
    else:
        seam, face, grain, lit, shaded, hinge = (blank() for _ in range(6))
        for y0, y1 in ((3, 6), (9, 13)):
            ImageDraw.Draw(seam).rectangle([2, y0, 13, y1], fill=SEAM + (255,))
            ImageDraw.Draw(face).rectangle([3, y0 + 1, 12, y1 - 1], fill=P2 + (255,))
            for x in range(4, 12, 2):
                ImageDraw.Draw(grain).line([(x, y0 + 2), (x, y1 - 2)], fill=P1 + (255,))   # grain in the raised panel
            ImageDraw.Draw(lit).line([(3, y0 + 1), (12, y0 + 1)], fill=LIT + (255,))         # lit upper edge
            ImageDraw.Draw(shaded).line([(3, y1 - 1), (12, y1 - 1)], fill=EDGE + (255,))     # shaded lower edge
        for y in (10, 11):
            hinge.putpixel((0, y), IRON_LIGHT if y == 10 else IRON_DARK)                      # hinge
        for name, img in (("raised panel frames", seam), ("raised panels", face), ("panel grain", grain), ("panel lit edges", lit), ("panel shaded edges", shaded), ("hinges", hinge)):
            sheet.add(name, img)
    return sheet


LEAF_GREENS = [(38, 104, 44), (52, 132, 52), (72, 160, 60), (100, 190, 76)]
LEAF_HIGHLIGHT = (150, 214, 90)
LEAF_LAYERS = {LEAF_GREENS[0]: "ribs and shade", LEAF_GREENS[1]: "leaflets", LEAF_GREENS[2]: "light leaflets", LEAF_GREENS[3]: "bright leaflets", LEAF_HIGHLIGHT: "highlights"}


def leaf_sheet(n):
    sheet = kra_layers.Sheet(n, n)
    return sheet, {c: sheet.layer(name) for c, name in LEAF_LAYERS.items()}


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
    sheet, L = leaf_sheet(n)
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
                c = LEAF_HIGHLIGHT
            L[c][x, y] = c + (255,)
    return sheet


def palm_leaves_item(rnd):
    """The leaf block as an item (and its particles): a dense 16 x 16 leaf picture like the vanilla leaves', a few holes."""
    sheet, L = leaf_sheet(16)
    for y in range(16):
        for x in range(16):
            u, v = (x - y) % 8, (x + y) % 8
            if rnd.random() < 0.30 and u not in (0, 1):
                continue                                                             # about the jungle leaves' 30 % air
            idx = 0 if u in (0, 1) else 2 if v in (3, 4) else 1
            if rnd.random() < 0.3:
                idx = min(3, max(0, idx + rnd.choice((-1, 1))))
            L[LEAF_GREENS[idx]][x, y] = LEAF_GREENS[idx] + (255,)
    return sheet


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
SAPLING_LAYERS = {"D": "frond shade", "M": "fronds", "L": "light fronds", "H": "frond highlights", "T": "trunk", "t": "trunk light"}


def palm_sapling():
    """A young palm: a short ringed trunk and a rosette of fronds that arch and droop, four greens (a lit highlight, light, mid, shade), as full as the vanilla saplings."""
    sheet = kra_layers.Sheet(16, 16)
    L = {ch: sheet.layer(name) for ch, name in SAPLING_LAYERS.items()}
    for y, row in enumerate(SAPLING_ROWS):
        assert len(row) == 16, (y, len(row))
        for x, ch in enumerate(row):
            if ch in SAPLING_COLOURS:
                L[ch][x, y] = SAPLING_COLOURS[ch] + (255,)
    return sheet


def bag_textures():
    """The bag of power fruit's own pictures (used when Farm & Charm is not there): burlap sides with a band of the fruit's colours, a top with a two pixel edge of burlap round a heap
    of fruit, and a plain bottom. The burlap's colours are a sack's; the top is also used with Farm & Charm's sack (the same two pixel edge). Returns three sheets: side, top, bottom."""
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
    side_sack = sack(16, 16)
    side = kra_layers.Sheet(16, 16)
    side.add("burlap", side_sack)
    band = side.layer("stitched band")
    three = (RED, ORANGE, YELLOW)
    last = -1
    for x in range(16):                                           # the band: a stitched line of the fruit's three colours, spread about
        k = rnd.choice([i for i in range(3) if i != last])
        last = k
        band[x, 4] = three[k] + (255,)
        band[x, 5] = shade(three[k], 0.78) + (255,)
    lower = side.layer("lower shade")
    for y in range(11, 16):
        for x in range(16):
            c = side_sack.getpixel((x, y))
            lower[x, y] = shade(c[:3], 0.9) + (255,)
    bottom = kra_layers.Sheet(16, 16)
    bottom.add("burlap", sack(16, 16, True))
    top = kra_layers.Sheet(16, 16)
    top.add("burlap", sack(16, 16))
    seam = top.layer("rolled rim")
    for x in range(16):
        for y in range(16):
            if min(x, y, 15 - x, 15 - y) == 1:
                seam[x, y] = burlap[3] + (255,)                   # the second pixel of the edge: the seam where the rim is rolled
    inner = Image.new("RGBA", (12, 12), (60, 30, 20, 255))
    di = ImageDraw.Draw(inner)
    spots = [(2, 2), (6, 1), (10, 2), (1, 6), (5, 5), (9, 6), (3, 10), (7, 9), (11, 10)]
    picks = [0, 1, 0, 2, 0, 2, 1, 2, 1]                           # the three colours on the three by three, none the same as the one beside or below it
    for i, (cx, cy) in enumerate(spots):
        base = (RED, ORANGE, YELLOW)[picks[i]]
        di.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=shade(base, 0.8) + (255,))
        di.ellipse([cx - 2, cy - 2, cx + 1, cy + 1], fill=base + (255,))
        inner.putpixel((max(0, min(11, cx - 1)), max(0, min(11, cy - 1))), tuple(min(255, v + 70) for v in base) + (255,))
    heap = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    heap.paste(inner, (2, 2))
    top.add("fruit heap", heap)
    return side, top, bottom


def build_all():
    """Every picture of this family as {kra name: (sheet, 'block' | 'item', (png name))} in the old script's drawing order (one random generator is shared)."""
    rnd = random.Random(14)
    out = {}
    out["power_juice"] = ("item", power_juice())
    out["raw_power_ore"] = ("item", raw_power_ore())
    out["power_ingot"] = ("item", power_ingot())
    out["power_nugget"] = ("item", power_nugget())
    out["power_palm_door"] = ("item", door_item())
    out["power_block"] = ("block", power_block())
    out["power_palm_log"] = ("block", log_side(rnd))
    out["power_palm_log_top"] = ("block", log_top(rnd))
    out["stripped_power_palm_log"] = ("block", log_side(rnd, light=True))
    out["stripped_power_palm_log_top"] = ("block", log_top(rnd, light=True))
    out["power_palm_planks"] = ("block", planks(rnd))
    out["power_palm_trapdoor"] = ("block", trapdoor(rnd))
    out["power_palm_door_top"] = ("block", door_half(rnd, True))
    out["power_palm_door_bottom"] = ("block", door_half(rnd, False))
    out["power_palm_leaves_fluffy"] = ("fluffy", palm_leaves(rnd))
    out["power_palm_leaves"] = ("block", palm_leaves_item(rnd))
    out["power_palm_sapling"] = ("block", palm_sapling())
    side, top, bottom = bag_textures()
    out["power_fruit_bag_side"] = ("block", side)
    out["power_fruit_bag_top"] = ("block", top)
    out["power_fruit_bag_bottom"] = ("block", bottom)
    return out
