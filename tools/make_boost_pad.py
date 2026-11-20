"""
The boost blocks: a plate, a full block, a slab and stairs, each with a direction (a chevron arrow on top), CTR's turbo pad boost.

  python tools/make_boost_pad.py

Models are Blockbench's: blockbench/boost_pad/*.bbmodel are the SOURCE (each is written once from the shapes below if it is
missing, then it is yours; this script only reads them). Non-obvious structure, read this before editing in Blockbench:
  * Texture slot "top" is the chevron arrow, slot "side" the plain metal. Only the UP faces that carry the arrow use "top".
  * The glow is generated, not in the bbmodels: every "top" face gets a flat overlay (slot "glow", boost_top_glow.png: only the arrow and border pixels) that is lit by
    NeoForge's per face light, and Fusion's metadata of the glow sheet says "emissive": true (Fusion rebuilds the model and drops the per face light, so it needs that).
  * The arrow's uv and face rotation are GENERATED per direction (an up face using "top" is re-mapped here: the arrow points
    north in the file, and is turned 0/90/180/270 for the game). Change an arrow face's size by changing its element; do not
    fiddle with its uv, it is recomputed (a centred slice of the arrow texture at 1:1 pixel scale, so a half-block tread shows
    the middle of the arrow, not a squashed one).
  * The stairs are authored for facing = EAST (high side to the east) like vanilla, in 3 shapes (straight, inner, outer) and 2
    halves. The _top models are the bottom ones turned upside down (x 180) and then edited so that their visible top is one big
    arrow; the boxes are split so every arrow face is exactly the visible tread (some boxes only exist to be hidden under a step).
  * The plate (boost_pad) is 1 px high and has NO collision in the game (a kart drives over it; it is a marker on the floor).

Connecting textures (Fusion, optional): with the Fusion mod installed, boost blocks of the same state next to each other join up
(the yellow border is only drawn where nothing continues it). This is a 5-tile "pieced" sheet made FROM boost_top.png by compose_pieced():
the outer 2 px ring of the base texture is the border, tile 0 is the base as drawn, and the others replace the ring with the
background colour where a neighbour continues it. So repaint boost_top.png (keep a 2 px border ring and keep the arrow inside it) and
the sheet follows on the next build. The sheet lives in src/main/resources/fusion-overrides/ and is only used when Fusion is
present (pack.mcmeta's fusion.overrides_folder); without Fusion the plain boost_top.png is used. `--preview` draws a sample field to
build/boost_connect_preview.png.

Writes models, item models, block states, loot tables, tags and names, and draws placeholder textures once if missing (yours to
repaint). Run from the project folder. The stair rotation table is vanilla's (oak_stairs blockstate), kept here as data.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "craftteamracing")
DATA = os.path.join(RES, "data")
NS = "craftteamracing"
TEX_DIR = os.path.join(ASSETS, "textures", "block")
TEX_TOP = os.path.join(TEX_DIR, "boost_top.png")
TEX_SIDE = os.path.join(TEX_DIR, "boost_side.png")
BB_DIR = os.path.join("blockbench", "boost_pad")

FACINGS = ("north", "east", "south", "west")
ANGLE = {"north": 0, "east": 90, "south": 180, "west": 270}

# vanilla stairs blockstate (facing, half, shape) -> (model kind, y rotation); x 180 for half=top is replaced by our own flipped models
STAIR_TABLE = {
    ('east', 'bottom', 'inner_left'): ('inner', 270),
    ('east', 'bottom', 'inner_right'): ('inner', 0),
    ('east', 'bottom', 'outer_left'): ('outer', 270),
    ('east', 'bottom', 'outer_right'): ('outer', 0),
    ('east', 'bottom', 'straight'): ('straight', 0),
    ('east', 'top', 'inner_left'): ('inner', 0),
    ('east', 'top', 'inner_right'): ('inner', 90),
    ('east', 'top', 'outer_left'): ('outer', 0),
    ('east', 'top', 'outer_right'): ('outer', 90),
    ('east', 'top', 'straight'): ('straight', 0),
    ('north', 'bottom', 'inner_left'): ('inner', 180),
    ('north', 'bottom', 'inner_right'): ('inner', 270),
    ('north', 'bottom', 'outer_left'): ('outer', 180),
    ('north', 'bottom', 'outer_right'): ('outer', 270),
    ('north', 'bottom', 'straight'): ('straight', 270),
    ('north', 'top', 'inner_left'): ('inner', 270),
    ('north', 'top', 'inner_right'): ('inner', 0),
    ('north', 'top', 'outer_left'): ('outer', 270),
    ('north', 'top', 'outer_right'): ('outer', 0),
    ('north', 'top', 'straight'): ('straight', 270),
    ('south', 'bottom', 'inner_left'): ('inner', 0),
    ('south', 'bottom', 'inner_right'): ('inner', 90),
    ('south', 'bottom', 'outer_left'): ('outer', 0),
    ('south', 'bottom', 'outer_right'): ('outer', 90),
    ('south', 'bottom', 'straight'): ('straight', 90),
    ('south', 'top', 'inner_left'): ('inner', 90),
    ('south', 'top', 'inner_right'): ('inner', 180),
    ('south', 'top', 'outer_left'): ('outer', 90),
    ('south', 'top', 'outer_right'): ('outer', 180),
    ('south', 'top', 'straight'): ('straight', 90),
    ('west', 'bottom', 'inner_left'): ('inner', 90),
    ('west', 'bottom', 'inner_right'): ('inner', 180),
    ('west', 'bottom', 'outer_left'): ('outer', 90),
    ('west', 'bottom', 'outer_right'): ('outer', 180),
    ('west', 'bottom', 'straight'): ('straight', 180),
    ('west', 'top', 'inner_left'): ('inner', 180),
    ('west', 'top', 'inner_right'): ('inner', 270),
    ('west', 'top', 'outer_left'): ('outer', 180),
    ('west', 'top', 'outer_right'): ('outer', 270),
    ('west', 'top', 'straight'): ('straight', 180),
}
# the stair's arrow, relative to a climber walking up it (its facing is the high side): world angle offset
STAIR_DIRECTIONS = {"up": 0, "right": 90, "down": 180, "left": 270}


# ---------------------------------------------------------------------------------------------------------------- textures

FRAMES = 8                 # the arrows' animation: the pattern repeats every 8 px along the arrow, one px a frame
PERIOD = 8
DARK = (30, 31, 38, 255)
BG = (46, 48, 58, 255)
# the family's accent colour: the original's pad is green; the super pad (an extension: CTR has the pad type but never draws it differently) is teal
FAMILIES = [
    {"key": "boost", "accent": (70, 220, 80, 255), "name": "Boost", "map_color": "COLOR_GREEN"},
    {"key": "super_boost", "accent": (40, 215, 205, 255), "name": "Super Boost", "map_color": "COLOR_CYAN"},
]


def pattern_frame(accent, k):
    """The arrows' pattern, 16 x 16, with the background: chevrons pointing up the texture (north in the model file), a new one every PERIOD px, so it repeats
    seamlessly across blocks both ways. Frame k has it moved k px up: played in order the arrows run the way they point."""
    im = Image.new("RGBA", (16, 16), BG)
    for y in range(16):
        r = (y + k) % PERIOD
        if r >= 6:
            continue
        for dx in (0, 1):
            im.putpixel((8 - r - dx, y), accent)
            im.putpixel((7 + r + dx, y), accent)
    return im


def draw_frame(accent, k):
    """One frame of a single block: the pattern inside a 2 px ring (the border: the accent colour outside, a dark line inside it)."""
    im = pattern_frame(accent, k)
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                im.putpixel((x, y), accent)
            elif edge == 1:
                im.putpixel((x, y), DARK)
    return im


def draw_top(accent):
    """The animated texture: FRAMES frames of 16 x 16, one under the other."""
    sheet = Image.new("RGBA", (16, 16 * FRAMES))
    for k in range(FRAMES):
        sheet.paste(draw_frame(accent, k), (0, 16 * k))
    return sheet


def draw_side(accent):
    """Plain dark metal with a stripe in the accent colour along the top edge and a few rivets."""
    im = Image.new("RGBA", (16, 16), BG)
    for x in range(16):
        im.putpixel((x, 0), accent)
        im.putpixel((x, 1), accent)
        im.putpixel((x, 2), DARK)
        im.putpixel((x, 15), DARK)
    for x in (3, 12):
        im.putpixel((x, 8), (96, 98, 110, 255))
    return im


# ---------------------------------------------------------------------------------------------------------------- shapes

def element(frm, to, arrow=False, up=True):
    """A box. arrow: its up face carries the arrow (uv/rotation are generated); up=False leaves the up face out (hidden)."""
    faces = {}
    for f in bbmodel.FACES:
        if f == "up":
            if not up:
                continue
            if arrow:
                faces[f] = {"uv": arrow_uv(frm, to, 0), "texture": "#top"}
                continue
        uv = bbmodel.default_uv(f, frm, to)
        faces[f] = {"uv": uv, "texture": "#side"}
    return {"from": frm, "to": to, "faces": faces}


def arrow_uv(frm, to, r):
    """The centred slice of the 16x16 arrow texture for an up face of this size, turned by r (0/90/180/270): at 1:1 pixel scale."""
    ex, ez = to[0] - frm[0], to[2] - frm[2]
    w, h = (ex, ez) if r in (0, 180) else (ez, ex)
    return [(16 - w) / 2, (16 - h) / 2, (16 + w) / 2, (16 + h) / 2]


def stair_boxes(shape, half):
    """Boxes of a stair authored for facing = east. Arrow faces are exactly the visible treads."""
    if half == "bottom":
        if shape == "straight":
            return [([0, 0, 0], [8, 8, 16], True, True), ([8, 0, 0], [16, 8, 16], False, False), ([8, 8, 0], [16, 16, 16], True, True)]
        if shape == "inner":
            return [([0, 0, 0], [8, 8, 8], True, True), ([0, 0, 8], [8, 8, 16], False, False), ([8, 0, 0], [16, 8, 16], False, False),
                    ([8, 8, 0], [16, 16, 16], True, True), ([0, 8, 8], [8, 16, 16], True, True)]
        return [([0, 0, 0], [16, 8, 8], True, True), ([0, 0, 8], [8, 8, 16], True, True), ([8, 0, 8], [16, 8, 16], False, False),
                ([8, 8, 8], [16, 16, 16], True, True)]
    # top half: upside down; the visible top is one big slab
    if shape == "straight":
        return [([0, 8, 0], [16, 16, 16], True, True), ([8, 0, 0], [16, 8, 16], False, False)]
    if shape == "inner":
        return [([0, 8, 0], [16, 16, 16], True, True), ([8, 0, 0], [16, 8, 16], False, False), ([0, 0, 0], [8, 8, 8], False, False)]
    return [([0, 8, 0], [16, 16, 16], True, True), ([8, 0, 0], [16, 8, 8], False, False)]


def display():
    return {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
            "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"scale": [0.75, 0.75, 0.75]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.5, 0.5, 0.5]}}


COMMENT = ("Boost block shape. Texture slots: 'top' (the chevron arrow, points NORTH in this file) and 'side' (plain metal). "
           "Arrow faces get their uv and rotation GENERATED by tools/make_boost_pad.py for each direction, so only change their "
           "size via the element. See the notes at the top of that script.")


def sources():
    """name -> list of (name, [elements]) groups, for every bbmodel source."""
    out = {
        "boost_pad": [("plate", [element([0, 0, 0], [16, 1, 16], arrow=True)])],
        "boost_block": [("block", [element([0, 0, 0], [16, 16, 16], arrow=True)])],
        "boost_slab_bottom": [("slab", [element([0, 0, 0], [16, 8, 16], arrow=True)])],
        "boost_slab_top": [("slab", [element([0, 8, 0], [16, 16, 16], arrow=True)])],
    }
    for half in ("bottom", "top"):
        for shape in ("straight", "inner", "outer"):
            els = [element(f, t, arrow=arrow, up=up) for f, t, arrow, up in stair_boxes(shape, half)]
            out["boost_stairs_%s_%s" % (shape, half)] = [("stairs", els)]
    return out


def glow_map(sheet, accent):
    """The glow picture of a sheet of the arrow texture: only the pixels in the family's accent colour (the arrows and the border line), everything else transparent."""
    src = sheet.convert("RGBA")
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    sp, op = src.load(), out.load()
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = sp[x, y]
            if a and abs(r - accent[0]) + abs(g - accent[1]) + abs(b - accent[2]) < 24:
                op[x, y] = (r, g, b, 255)
    return out


def write_glow(path_png, accent, emissive=False):
    """Writes {name}_glow.png beside path_png (made from it) with a copy of its metadata; emissive adds Fusion's "emissive": true to the metadata's fusion section."""
    base = path_png[:-4]
    glow_map(Image.open(path_png), accent).save(base + "_glow.png")
    meta = json.load(open(path_png + ".mcmeta", encoding="utf-8"))
    if emissive and "fusion" in meta:
        meta["fusion"]["emissive"] = True
    dump(base + "_glow.png.mcmeta", meta)


def ensure_sources():
    os.makedirs(TEX_DIR, exist_ok=True)
    for fam in FAMILIES:
        # placeholder art, regenerated every run (the arrows' animation and the pieced sheet follow from it); repaint by changing draw_frame
        draw_top(fam["accent"]).save(os.path.join(TEX_DIR, fam["key"] + "_top.png"))
        draw_side(fam["accent"]).save(os.path.join(TEX_DIR, fam["key"] + "_side.png"))
        dump(os.path.join(TEX_DIR, fam["key"] + "_top.png.mcmeta"), {"animation": {"frametime": 1}})
        write_glow(os.path.join(TEX_DIR, fam["key"] + "_top.png"), fam["accent"])
    for name, groups in sources().items():
        path = os.path.join(BB_DIR, name + ".bbmodel")
        if not os.path.exists(path):
            bbmodel.write(path, name, {"top": TEX_TOP, "side": TEX_SIDE}, groups, display=display(), gui_light_front=False,
                          comment=COMMENT)


# ---------------------------------------------------------------------------------------------------------------- models

def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


def turned(elements, r):
    """The elements with every arrow face (an up face using #top) turned to point r degrees clockwise from north."""
    out = []
    for el in elements:
        el = json.loads(json.dumps(el))
        up = el["faces"].get("up")
        if up and up.get("texture") == "#top":
            up["uv"] = arrow_uv(el["from"], el["to"], r)
            if r:
                up["rotation"] = r
            else:
                up.pop("rotation", None)
        out.append(el)
    return out


LIFT = 0.01                                 # the glowing overlay floats this many sixteenths above the arrow face (no z-fighting)
LIT = {"block_light": 15, "sky_light": 15}


def glow(elements):
    """The arrows glow: next to every face that shows the top picture there is a flat overlay with the glow picture (only the arrow pixels, the rest transparent). Fusion
    (which rebuilds the models of blocks with its textures and drops NeoForge's per face light) is told that the glow texture is emissive in its metadata; without Fusion the
    overlay's own per face light (block_light / sky_light 15) does it. The base face keeps the plain picture, so the dark metal does not glow."""
    out = []
    for el in elements:
        out.append(el)
        face = el["faces"].get("up")
        if face and face.get("texture") == "#top":
            top = el["to"][1] + LIFT
            overlay = {"from": [el["from"][0], top, el["from"][2]], "to": [el["to"][0], top, el["to"][2]],
                       "faces": {"up": {k: v for k, v in face.items() if k not in ("neoforge_data", "cullface")}}}
            overlay["faces"]["up"]["texture"] = "#glow"
            overlay["faces"]["up"]["neoforge_data"] = LIT
            out.append(overlay)
    return out


def write_model(name, elements, fam):
    key = fam["key"]
    elements = glow(elements)
    dump(os.path.join(ASSETS, "models", "block", name + ".json"), {
        "render_type": "minecraft:cutout",
        "textures": {"top": f"{NS}:block/{key}_top", "glow": f"{NS}:block/{key}_top_glow", "side": f"{NS}:block/{key}_side", "particle": f"{NS}:block/{key}_side"},
        "elements": elements})


def write_item(name, parent, disp):
    dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": f"{NS}:block/{parent}", "display": disp})


def read(name):
    return bbmodel.read(os.path.join(BB_DIR, name + ".bbmodel"))


# ---------------------------------------------------------------------------------------------------------------- Fusion

RING = 2                                    # px of border ring in the top texture
OVERRIDES = os.path.join(RES, "fusion-overrides", "assets", NS, "textures", "block")
# Fusion's pieced layout (PiecedLayoutHandler): 5 tiles in a row, chosen per quarter of the face by (side, side, diagonal) bits
PIECED_CORNER_TILE = (0, 3, 2, 4, 0, 3, 2, 1)


def compose_pieced(base, pattern):
    """The 5-tile pieced sheet (80 x 16) of one frame: tile 0 = isolated, 1 = all eight neighbours, 2 = up and down, 3 = left and right, 4 = the four sides but no
    diagonals (inner corners). {base} is a single block's frame (the pattern inside the ring), {pattern} the same frame's pattern without the ring: where a neighbour
    continues the border the ring is the pattern, so the arrows run on across the blocks."""
    base = base.convert("RGBA")
    pattern = pattern.convert("RGBA")
    assert base.size == (16, 16), "a frame must be 16x16"
    specs = [dict(t=0, r=0, b=0, l=0, tl=0, tr=0, bl=0, br=0),
             dict(t=1, r=1, b=1, l=1, tl=1, tr=1, bl=1, br=1),
             dict(t=1, r=0, b=1, l=0, tl=0, tr=0, bl=0, br=0),
             dict(t=0, r=1, b=0, l=1, tl=0, tr=0, bl=0, br=0),
             dict(t=1, r=1, b=1, l=1, tl=0, tr=0, bl=0, br=0)]
    sheet = Image.new("RGBA", (16 * len(specs), 16))
    for i, c in enumerate(specs):
        tile = base.copy()
        px = tile.load()
        for y in range(16):
            for x in range(16):
                top, bot, left, right = y < RING, y >= 16 - RING, x < RING, x >= 16 - RING
                if not (top or bot or left or right):
                    continue
                h, v = top or bot, left or right
                if h and v:
                    # a corner: its horizontal edge (top/bottom) has a border unless the vertical neighbour continues it, and
                    # its vertical edge (left/right) unless the horizontal neighbour does
                    vert = c["t"] if top else c["b"]
                    horz = c["l"] if left else c["r"]
                    diag = c[("t" if top else "b") + ("l" if left else "r")]
                    edge_h, edge_v = not vert, not horz
                    if edge_h and edge_v:
                        continue                                          # an outer corner: as drawn
                    if edge_h:                                            # only the top/bottom border continues across: extend it
                        px[x, y] = base.getpixel((min(max(x, RING), 15 - RING), y))
                    elif edge_v:                                          # only the left/right border continues across
                        px[x, y] = base.getpixel((x, min(max(y, RING), 15 - RING)))
                    elif diag:
                        px[x, y] = pattern.getpixel((x, y))               # surrounded: no border
                    continue                                              # surrounded but the diagonal is missing: an inner corner, as drawn
                border = not (c["t"] if top else c["b"]) if h else not (c["l"] if left else c["r"])
                if not border:
                    px[x, y] = pattern.getpixel((x, y))
        sheet.paste(tile, (16 * i, 0))
    return sheet


def write_fusion(preview=False):
    os.makedirs(OVERRIDES, exist_ok=True)
    for fam in FAMILIES:
        accent = fam["accent"]
        sheet = Image.new("RGBA", (80, 16 * FRAMES))
        for k in range(FRAMES):
            sheet.paste(compose_pieced(draw_frame(accent, k), pattern_frame(accent, k)), (0, 16 * k))
        sheet.save(os.path.join(OVERRIDES, fam["key"] + "_top.png"))
        # the frames are 80 x 16 (the default frame is square)
        dump(os.path.join(OVERRIDES, fam["key"] + "_top.png.mcmeta"), {
            "animation": {"frametime": 1, "width": 80, "height": 16},
            "fusion": {"type": "connecting", "layout": "pieced", "connections": {"type": "is_same_state"}}})
        write_glow(os.path.join(OVERRIDES, fam["key"] + "_top.png"), accent, emissive=True)
        if preview and fam["key"] == "boost":
            draw_preview(sheet.crop((0, 0, 80, 16)))
    # the mod's own pack.mcmeta: tells Fusion where the optional files are (it reads it for every mod). The formats cover resources
    # (34) and data (48) of 1.21.1 so the mod's pack is never flagged
    dump(os.path.join(RES, "pack.mcmeta"), {
        "pack": {"description": "Craft Team Racing", "pack_format": 34,
                 "supported_formats": {"min_inclusive": 34, "max_inclusive": 48}},
        "fusion": {"overrides_folder": "fusion-overrides"}})


STATIC = os.path.join(RES, "static-pads")


def write_static_pack():
    """The built-in pack the client turns on when the player switches the arrow animation off (the client config's animatedBoostPads, see client/BoostPadAnimation):
    the same pictures with a metadata that shows frame 0 only. A pack has to carry the pictures as well as the .mcmeta (the metadata is read from the pack that holds the
    picture), and Fusion's sheets come from the pack's own fusion-overrides folder, as the mod's do. Not part of the mod's own assets: it sits outside assets/."""
    tex = os.path.join(STATIC, "assets", NS, "textures", "block")
    fus = os.path.join(STATIC, "fusion-overrides", "assets", NS, "textures", "block")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(fus, exist_ok=True)
    dump(os.path.join(STATIC, "pack.mcmeta"), {
        "pack": {"description": "CTR: Still Boost Pad Arrows", "pack_format": 34, "supported_formats": {"min_inclusive": 34, "max_inclusive": 48}},
        "fusion": {"overrides_folder": "fusion-overrides"}})
    for fam in FAMILIES:
        key = fam["key"]
        Image.open(os.path.join(TEX_DIR, key + "_top.png")).save(os.path.join(tex, key + "_top.png"))
        dump(os.path.join(tex, key + "_top.png.mcmeta"), {"animation": {"frames": [0]}})
        Image.open(os.path.join(OVERRIDES, key + "_top.png")).save(os.path.join(fus, key + "_top.png"))
        dump(os.path.join(fus, key + "_top.png.mcmeta"), {
            "animation": {"frames": [0], "width": 80, "height": 16},
            "fusion": {"type": "connecting", "layout": "pieced", "connections": {"type": "is_same_state"}}})
        write_glow(os.path.join(tex, key + "_top.png"), fam["accent"])
        write_glow(os.path.join(fus, key + "_top.png"), fam["accent"], emissive=True)


def draw_preview(sheet):
    """A sample field of same-state pads put together the way Fusion would (quarters from the pieced tiles), for a look."""
    cells = {(x, z) for x in range(1, 5) for z in range(1, 4)} | {(5, 2), (6, 2), (6, 3), (1, 5)}
    scale, size = 6, 16
    img = Image.new("RGBA", (8 * size, 7 * size), (110, 150, 90, 255))
    for (x, z) in cells:
        has = lambda dx, dz: (x + dx, z + dz) in cells
        for qx in (0, 1):
            for qz in (0, 1):
                dx, dz = (-1 if qx == 0 else 1), (-1 if qz == 0 else 1)
                idx = (1 if has(dx, 0) else 0) | ((1 if has(0, dz) else 0) << 1) | ((1 if has(dx, dz) else 0) << 2)
                tile = PIECED_CORNER_TILE[idx]
                piece = sheet.crop((tile * 16 + qx * 8, qz * 8, tile * 16 + qx * 8 + 8, qz * 8 + 8))
                img.paste(piece, (x * size + qx * 8, z * size + qz * 8))
    os.makedirs("build", exist_ok=True)
    img.resize((img.width * scale, img.height * scale), Image.NEAREST).save(os.path.join("build", "boost_connect_preview.png"))
    print("preview: build/boost_connect_preview.png")


def main():
    ensure_sources()
    models = []                                           # every model file written, to check the block states against
    all_blocks = {}                                       # family key -> its four block names
    lp = os.path.join(ASSETS, "lang", "en_us.json")
    lang = json.load(open(lp, encoding="utf-8")) if os.path.exists(lp) else {}
    disp = read("boost_block")["display"] or display()

    def ys(facing):
        return {} if ANGLE[facing] == 0 else {"y": ANGLE[facing]}

    for fam in FAMILIES:
        key = fam["key"]
        out = lambda name, key=key: key + name[len("boost"):]            # boost_pad -> super_boost_pad

        # ---- plate, block, slab: one model each, the block state turns it
        for name in ("boost_pad", "boost_block", "boost_slab_bottom", "boost_slab_top"):
            m = read(name)
            write_model(out(name), turned(bbmodel.elements(m), 0), fam)
            models.append(out(name))
        write_item(out("boost_pad"), out("boost_pad"), disp)
        write_item(out("boost_block"), out("boost_block"), disp)
        write_item(out("boost_slab"), out("boost_slab_bottom"), disp)

        dump(os.path.join(ASSETS, "blockstates", out("boost_pad") + ".json"),
             {"variants": {"facing=%s" % f: dict({"model": f"{NS}:block/{out('boost_pad')}"}, **ys(f)) for f in FACINGS}})
        dump(os.path.join(ASSETS, "blockstates", out("boost_block") + ".json"),
             {"variants": {"facing=%s" % f: dict({"model": f"{NS}:block/{out('boost_block')}"}, **ys(f)) for f in FACINGS}})
        slab = {}
        for f in FACINGS:
            slab["type=bottom,facing=%s" % f] = dict({"model": f"{NS}:block/{out('boost_slab_bottom')}"}, **ys(f))
            slab["type=top,facing=%s" % f] = dict({"model": f"{NS}:block/{out('boost_slab_top')}"}, **ys(f))
            slab["type=double,facing=%s" % f] = dict({"model": f"{NS}:block/{out('boost_block')}"}, **ys(f))
        dump(os.path.join(ASSETS, "blockstates", out("boost_slab") + ".json"), {"variants": slab})

        # ---- stairs: a model per shape, half and arrow direction in the model's own frame (0/90/180/270)
        for shape in ("straight", "inner", "outer"):
            for half in ("bottom", "top"):
                els = bbmodel.elements(read("boost_stairs_%s_%s" % (shape, half)))
                for r in (0, 90, 180, 270):
                    name = out("boost_stairs_%s_%s_%d" % (shape, half, r))
                    write_model(name, turned(els, r), fam)
                    models.append(name)
        write_item(out("boost_stairs"), out("boost_stairs_straight_bottom_90"), disp)
        stairs = {}
        for (facing, half, shape), (kind, y) in STAIR_TABLE.items():
            for direction, offset in STAIR_DIRECTIONS.items():
                world = (ANGLE[facing] + offset) % 360          # where the arrow points in the world
                r = (world - y) % 360                           # and in the model's own frame (the block state turns it by y)
                v = {"model": f"{NS}:block/{out('boost_stairs_%s_%s_%d' % (kind, half, r))}"}
                if y:
                    v["y"] = y
                stairs["direction=%s,facing=%s,half=%s,shape=%s" % (direction, facing, half, shape)] = v
        dump(os.path.join(ASSETS, "blockstates", out("boost_stairs") + ".json"), {"variants": stairs})
        assert len(stairs) == 160

        # ---- loot tables: each drops itself (a double slab two)
        def loot(block, extra=None):
            entry = {"type": "minecraft:item", "name": f"{NS}:{block}"}
            if extra:
                entry["functions"] = extra
            dump(os.path.join(DATA, NS, "loot_table", "blocks", block + ".json"), {
                "type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry],
                                                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        for b in ("boost_pad", "boost_block", "boost_stairs"):
            loot(out(b))
        loot(out("boost_slab"), [{"function": "minecraft:set_count", "count": 2.0, "add": False, "conditions": [{
            "condition": "minecraft:block_state_property", "block": f"{NS}:{out('boost_slab')}", "properties": {"type": "double"}}]}])

        all_blocks[key] = [out(b) for b in ("boost_pad", "boost_block", "boost_slab", "boost_stairs")]
        for b in ("pad", "block", "slab", "stairs"):
            lang[f"block.{NS}.{out('boost_' + b)}"] = f"{fam['name']} {b.capitalize()}"

    # ---- tags: the pad tags are per family (a normal pad, a super pad), the rest cover all of them
    def tag(kind, ns, path, values):
        dump(os.path.join(DATA, ns, "tags", kind, path + ".json"), {"replace": False, "values": values})
    every = [f"{NS}:{b}" for fam in FAMILIES for b in all_blocks[fam["key"]]]
    tag("block", NS, "turbo_pad", [f"{NS}:{b}" for b in all_blocks["boost"]])
    tag("block", NS, "super_turbo_pad", [f"{NS}:{b}" for b in all_blocks["super_boost"]])
    tag("block", "minecraft", "slabs", [f"{NS}:{b[2]}" for b in all_blocks.values()])
    tag("block", "minecraft", "stairs", [f"{NS}:{b[3]}" for b in all_blocks.values()])
    tag("block", "minecraft", "mineable/pickaxe", every)
    tag("item", "minecraft", "slabs", [f"{NS}:{b[2]}" for b in all_blocks.values()])
    tag("item", "minecraft", "stairs", [f"{NS}:{b[3]}" for b in all_blocks.values()])
    # Create's copycats accept these as materials (a copycat of a boost block boosts, see McBlockSource)
    tag("block", "create", "copycat_allow", [o for o in every if not o.endswith("boost_pad")])
    dump(lp, lang)

    write_fusion(preview="--preview" in sys.argv)
    write_static_pack()

    # ---- check every model a block state names was written
    for fam in FAMILIES:
        for fn in all_blocks[fam["key"]]:
            states = json.load(open(os.path.join(ASSETS, "blockstates", fn + ".json"), encoding="utf-8"))["variants"]
            for k, v in states.items():
                name = v["model"].split("/")[-1]
                assert os.path.exists(os.path.join(ASSETS, "models", "block", name + ".json")), (fn, k, name)
    print("boost blocks written: %d models" % len(models))


if __name__ == "__main__":
    main()
