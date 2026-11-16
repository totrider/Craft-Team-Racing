"""
Extract CTR's original textures for the mod's crates, power-up icons, Wumpa fruit and start lamps from YOUR OWN copy of the game into LOCAL resource packs.

  python tools/extract_ctr_textures.py                      (finds the disc image in the project folder)
  python tools/extract_ctr_textures.py --disc "D:/CTR.bin" --install "F:/ATLauncher/instances/CTRCraft/resourcepacks"

It always writes both packs (into local-assets/ctr-textures/, git-ignored), as which one suits is decided in the game:
  CTRCraft-OriginalTextures             the art scaled DOWN to Minecraft sizes: 32 x 32 crate faces (a crate is one block wide), half-size icons (16 x 24).
  CTRCraft-OriginalTextures-OriginalScale   the art at its original size: 64 x 64 crate faces, 32 x 48 icons, 48 x 32 lamps. Closest to the game;
                                        it looks out of place next to vanilla textures.
Enable either one (not both) in Options > Resource Packs, above the mod, like the sound pack. Nothing from the disc is sent anywhere, nothing is
committed or put in the mod jar, and the packs must not be shared (the art is Sony / Naughty Dog's).

Where things are (BIGFILE.BIG, NTSC-U; formats: see ctr_disc_textures.py): the power-up icons, the Wumpa fruit sprite and the lamps are icons of
packs\\shared.mpk drawn from packs\\shared.vrm; the "?" crate and the fruit crate are models of Crash Cove's level file (levels\\tracks\\coco1\\1P),
their face textures are 64 x 64, 4 bits per texel with a palette. The crate models in the packs are plain cubes with those faces (the original has
edge strips too, which the mod's stand-in model keeps as frames and the packs leave out). The Wumpa fruit model is untextured in the game (vertex
colours), so it keeps the mod's colours. Spring, Invisibility and Super Engine have no HUD icon in the game's shared pack: they keep the stand-ins.

Needs numpy and Pillow.
"""
import argparse
import glob
import json
import os
import shutil
import struct
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ctr_disc_textures as ct  # noqa: E402
import ctr_bomb  # noqa: E402  (the bomb as a 3D model)
import sheen_frames
import ctr_mesh_pack  # noqa: E402  (a model as an OBJ for a local pack)
import render_ctr_model as rm  # noqa: E402
import extract_ctr_sounds as sounds  # noqa: E402  (the disc reader)
import render_ctr_sprite as sprite  # noqa: E402  (the Wumpa fruit's spin, rendered from the model)
import make_pickup_models as stand_in  # noqa: E402  (the pickup sheet's layout and palette)
import crate_blank  # noqa: E402  (the power-up crate's face without its "?")

PACK_FORMAT = 34

# the mod's power-up id -> the game's icon name in shared.mpk (what the icon shows, from looking at them)
POWER_ICONS = {
    "turbo": "turbonew", "bomb": "bomb", "bomb_3x": "bomb3", "missile": "bomb-tracker", "missile_3x": "bomb-tracker3",
    "tnt": "nitro", "potion": "poison", "shield": "shield", "mask": "doctor", "mask_dark": "ukauka", "clock": "clock", "warpball": "missile",
}
JUICED_ICONS = {"tnt_charged": "nitroj", "potion_charged": "poisonj", "shield_charged": "shieldj"}      # the mod calls the original's "juiced" state "charged" (the pack's lang file translates the words back)
LAMPS = {"red_on": "lightredon", "red_off": "lightredoff", "green_on": "lightgreenon", "green_off": "lightgreenoff"}
WUMPA_ICON = "wumpaposter"
# The mod's own words are "Power Fruit" and "charged"; the local pack translates them back into the original game's ("Wumpa", "juiced") with a lang file (a pack's lang keys override the jar's).
TRANSLATION = {"lang/en_us.json": {
    "block.ctrcraft.fruit_spot": "Wumpa Fruit Spot",
    "block.ctrcraft.fruit_crate_spot": "Wumpa Fruit Crate Spot",
    "ctrcraft.term.power_fruit": "Wumpa",
    "ctrcraft.term.charged": "juiced",
}}


def to_image(arr):
    return Image.fromarray(arr, "RGBA")


def clear_background(img):
    """HUD icons sit on a dark backdrop of opaque near-black texels (such as (0, 0, 8); the transparent 0x0000 only surrounds that): make the backdrop transparent. The
    backdrop is every near-black texel (all channels 16 or less) that touches the picture's border or a transparent texel, directly or through other such texels;
    near-black texels enclosed by the picture (its own dark shading) stay."""
    a = np.array(img)
    h, w = a.shape[:2]
    dark = (a[:, :, :3].max(axis=2) <= 16) & (a[:, :, 3] == 255)
    seen = np.zeros((h, w), dtype=bool)
    stack = [(y, x) for y in range(h) for x in range(w) if a[y, x, 3] == 0 or y in (0, h - 1) or x in (0, w - 1)]
    while stack:
        y, x = stack.pop()
        if 0 <= y < h and 0 <= x < w and not seen[y, x] and (dark[y, x] or a[y, x, 3] == 0):
            seen[y, x] = True
            stack += [(y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)]
    a[seen] = (0, 0, 0, 0)
    return Image.fromarray(a, "RGBA")


def power_icon(vram, layout):
    """A power-up icon with its backing taken off. The game draws each icon (DecalHUD_DrawWeapon, TRANS_50_DECAL) inside a rectangle: a fill of pure black texels with the
    semi-transparency bit (bit 15), drawn half and half with the screen, and a one texel frame of opaque near-black ((0, 0, 8)) round it. Every flagged texel is that fill (in
    all the icons they are pure black, in the rectangle where the art does not cover it) and the frame is at the same place in all of them (rows 2 and h - 4, columns 3 and
    w - 3 as stored). Both are removed here, because the mod draws its own box. The art's own near-black, its outlines and shading, has no flag and stays, as does everything
    else; texel 0x0000 is not drawn, as in the game."""
    img, stp = vram.texture_stp(layout)
    a = img.copy()
    h, w = a.shape[:2]
    dark = (a[:, :, :3].max(axis=2) <= 16) & (a[:, :, 3] == 255)
    backdrop = dark & ~stp
    # The frame is one or two texels thick (the TNT and Nitro crates' two) at rows 2 and h - 4 and columns 3 and w - 3 as stored: in that band, every straight run of four or more
    # unflagged near-black texels is the frame, the art's own outlines never form such a run there.
    frame = np.zeros((h, w), dtype=bool)

    def runs(hits, mark):
        n = len(hits)
        k = 0
        while k < n:
            if hits[k]:
                e = k
                while e < n and hits[e]:
                    e += 1
                if e - k >= 4:
                    mark(k, e)
                k = e
            else:
                k += 1

    for r in (2, 3, h - 5, h - 4):
        runs(backdrop[r], lambda k, e, r=r: frame.__setitem__((r, slice(k, e)), True))
    for c in (2, 3, w - 4, w - 3):
        runs(backdrop[:, c], lambda k, e, c=c: frame.__setitem__((slice(k, e), c), True))
    a[stp] = (0, 0, 0, 0)
    a[backdrop & frame] = (0, 0, 0, 0)
    return Image.fromarray(a, "RGBA")


def scaled(img, size):
    """Resize with the colours weighted by alpha (so transparent texels do not darken the edges)."""
    return img.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")


def fit(img, box):
    """Scale to fit inside a box x box square, keeping the shape, centred on a transparent square."""
    w, h = img.size
    k = min(box / w, box / h)
    nw, nh = max(1, round(w * k)), max(1, round(h * k))
    out = Image.new("RGBA", (box, box), (0, 0, 0, 0))
    out.paste(scaled(img, (nw, nh)), ((box - nw) // 2, (box - nh) // 2))
    return out


QUESTION_MODE = None        # None: the pictures as the disc has them; "blank": the power-up crate's panel without its "?"; "mark": only the "?" (everything else transparent)
_texture = ct.Vram.texture


def _patched_texture(self, layout):
    img = _texture(self, layout)
    if QUESTION_MODE is None or img is None:
        return img
    panel = img.shape[:2] == (64, 64) and crate_blank.question_mask(np.asarray(img)) is not None
    if QUESTION_MODE == "blank":
        return crate_blank.blank(np.array(img)) if panel else img
    if not panel:
        return np.zeros_like(img)                                   # the frame strip and the rest: nothing
    out = np.zeros_like(img)
    m = crate_blank.question_mask(np.asarray(img))
    out[m] = img[m]
    return out


ct.Vram.texture = _patched_texture


def bake_face(vram, model, size):
    """The original's crate face as the game draws it: the model's front face (the triangles in the plane of the lowest z), rasterised at size x size with its own
    UVs and palette, so the mitred frame, the rivets and the panel are exactly the original's."""
    verts = ct.mesh_vertices(model)
    tris = ct.mesh_triangles(model)
    zmin = min(v[2] for v in verts)
    xs = [v[0] for v in verts if abs(v[2] - zmin) < 0.5]
    ys = [v[1] for v in verts if abs(v[2] - zmin) < 0.5]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    out = np.zeros((size, size, 4), dtype=np.uint8)
    cache = {}
    for corner, tex, _, _ in tris:
        pts = [verts[i] for i in corner]
        if not all(abs(p[2] - zmin) < 0.5 for p in pts):
            continue
        u0, v0, clut, u1, v1, tpage, u2, v2, u3, v3 = struct.unpack("<BBHBBHBBBB", ct.layout_bytes(model, tex))
        uv = [(u0, v0), (u1, v1), (u2, v2)]
        # the face as the texture is authored (checked against the panel's own texture: the other direction comes out mirrored): x runs to the right
        P = [((p[0] - x0) / (x1 - x0) * size, (y1 - p[1]) / (y1 - y0) * size) for p in pts]
        (ax, ay), (bx, by), (cx, cy) = P
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            continue
        for y in range(max(0, int(min(p[1] for p in P))), min(size, int(max(p[1] for p in P)) + 1)):
            for x in range(max(0, int(min(p[0] for p in P))), min(size, int(max(p[0] for p in P)) + 1)):
                a = ((by - cy) * (x + .5 - cx) + (cx - bx) * (y + .5 - cy)) / den
                b = ((cy - ay) * (x + .5 - cx) + (ax - cx) * (y + .5 - cy)) / den
                c = 1 - a - b
                if a < -1e-6 or b < -1e-6 or c < -1e-6:
                    continue
                u = int(round(a * uv[0][0] + b * uv[1][0] + c * uv[2][0]))
                v = int(round(a * uv[0][1] + b * uv[1][1] + c * uv[2][1]))
                u, v = min(255, max(0, u)), min(255, max(0, v))
                key = (u0, v0, clut, u1, v1, tpage, u2, v2, u3, v3)
                if key not in cache:
                    lay = ct.layout_bytes(model, tex)
                    cache[key] = (vram.texture(lay), min(u0, u1, u2, u3), min(v0, v1, v2, v3))
                img, bu, bv = cache[key]
                if img is None:
                    continue
                texel = img[min(img.shape[0] - 1, max(0, v - bv)), min(img.shape[1] - 1, max(0, u - bu))]
                if texel[3]:
                    out[y, x] = tuple(int(c) for c in texel[:3]) + (255,)
    return Image.fromarray(out, "RGBA")


def vram_texel(vram, tpage, clut, u, v):
    px, py = (tpage & 15) * 64, ((tpage >> 4) & 1) * 256
    depth = (tpage >> 7) & 3
    cx, cy = (clut & 63) * 16, clut >> 6
    if depth == 0:
        w = int(vram.mem[py + v, px + u // 4])
        c = int(vram.mem[cy, cx + ((w >> (4 * (u % 4))) & 15)])
    elif depth == 1:
        w = int(vram.mem[py + v, px + u // 2])
        c = int(vram.mem[cy, cx + ((w >> (8 * (u % 2))) & 255)])
    else:
        c = int(vram.mem[py + v, px + u])
    if c == 0:
        return None
    return ((c & 31) * 255 // 31, ((c >> 5) & 31) * 255 // 31, ((c >> 10) & 31) * 255 // 31)


def sheet(question, planks, strip, fruit, q):
    """The pickup sheet (see tools/make_pickup_models.py): top left the palette (4 x 4 cells; the fruit's colours are the original sprite's), top right the
    '?' face, bottom left the fruit crate side, bottom right (top left corner) the crate's edge strip, at a quarter of a quadrant."""
    im = Image.new("RGBA", (2 * q, 2 * q), (0, 0, 0, 0))
    cell = q // 4
    palette = dict((n, c[2]) for n, c in stand_in.PALETTE.items())
    palette.update(fruit)
    for name, (cx, cy, _) in stand_in.PALETTE.items():
        im.paste(Image.new("RGBA", (cell, cell), palette[name] + (255,)), (cx * cell, cy * cell))
    im.alpha_composite(stand_in.fruit_cells(palette, q), (q, q))
    im.paste(question if question.size == (q, q) else scaled(question, (q, q)), (q, 0))
    im.paste(planks if planks.size == (q, q) else scaled(planks, (q, q)), (0, q))
    return im


def full_burst(quarter):
    """The shine behind the fruit and the held power-up (UI_WeaponBG_DrawShine) is one quarter of a burst, drawn four times mirrored round a centre: the whole burst from the
    shared pack's `poweredup` picture (its centre is the bottom right corner)."""
    # the particle's burst: white, with the brightness as alpha (the game draws it additively, so black is nothing), the empty rim trimmed off so the mirrored quarters meet
    # without a seam
    a = np.array(quarter)
    lum = a[:, :, :3].max(axis=2).astype(np.uint16)
    a[:, :, 3] = np.where(a[:, :, 3] > 0, np.minimum(255, lum * 255 // 200), 0).astype(np.uint8)
    a[:, :, :3] = 255
    quarter = Image.fromarray(a, "RGBA")
    quarter = quarter.crop(quarter.getbbox())
    w, h = quarter.size
    full = Image.new("RGBA", (2 * w, 2 * h), (0, 0, 0, 0))
    full.alpha_composite(quarter, (0, 0))
    full.alpha_composite(quarter.transpose(Image.FLIP_LEFT_RIGHT), (w, 0))
    full.alpha_composite(quarter.transpose(Image.FLIP_TOP_BOTTOM), (0, h))
    full.alpha_composite(quarter.transpose(Image.ROTATE_180), (w, h))
    return full


def dust_puff(img):
    """An original dust puff picture as a particle mask: the game draws its puffs with the picture's brightness added to (or taken from) the screen, so the brightness IS the amount: the
    mask is white with the brightness as its alpha, smoothed up to 64 x 64 (the particle sheet is point filtered), so the game can colour it (dust, dark exhaust smoke, bright boost smoke)
    and alpha blend it, instead of showing the grey picture itself with its dark edges."""
    a = np.array(img.convert("RGBA")).astype(np.float64)
    lum = a[:, :, :3].max(axis=2) / 255.0
    lum = np.where(a[:, :, 3] > 0, lum, 0.0)
    mask = Image.fromarray((np.clip(lum, 0, 1) * 255).astype(np.uint8), "L").resize((64, 64), Image.BICUBIC)
    alpha = np.clip(np.array(mask).astype(np.float64) * 1.25, 0, 255).astype(np.uint8)
    out = np.zeros((64, 64, 4), dtype=np.uint8)
    out[:, :, :3] = 255
    out[:, :, 3] = alpha
    return Image.fromarray(out, "RGBA")


def tire_mark(img):
    """The original's tyre mark (the icon "tread", global icon 0x2f: 4 texels across, 16 along, stripes of grey from 74 to 205): the game draws it subtractively (texture page blend
    mode 2), so the grey is how much light a stripe takes away; as a particle sprite it is black with the grey as its alpha, scaled up with hard edges (16 x 64) so the stripes stay."""
    a = np.array(img.convert("RGBA")).astype(np.float64)
    grey = a[:, :, :3].max(axis=2) / 255.0
    out = np.zeros(a.shape, dtype=np.uint8)
    out[:, :, 3] = np.clip(grey * 1.2, 0, 1) * 255
    return Image.fromarray(out, "RGBA").resize((img.width * 4, img.height * 4), Image.NEAREST)


def kart_shadow(top, bottom):
    """The kart's shadow from its two half pictures (the game draws four quads: the pictures stacked and mirrored for the other side): a dark, half-transparent
    version, black with the picture's shape."""
    w, h = top.size
    full = Image.new("RGBA", (2 * w, 2 * h), (0, 0, 0, 0))
    for im, y in ((top, 0), (bottom, h)):
        full.alpha_composite(im, (0, y))
        full.alpha_composite(im.transpose(Image.FLIP_LEFT_RIGHT), (w, y))
    a = np.array(full)
    lum = a[:, :, :3].mean(axis=2)
    out = np.zeros_like(a)
    solid = a[:, :, 3] > 0
    out[solid, 3] = np.clip(110 + (255 - lum[solid]) * 0.35, 0, 200).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def fruit_palette(model):
    """The Wumpa fruit's colours from the model's own colour table (the fruit is untextured: the game shades it per vertex). By height, bottom to top, the table
    runs dark olive green, yellow-green, yellow-orange, orange, red; the leaves are green."""
    table = ct.colour_table(model)
    pick = lambda i: tuple(int(c) for c in table[i])
    return {"green_dark": pick(12), "yellow_dark": pick(18), "orange_light": pick(10), "orange": pick(26), "red": pick(3), "green": pick(23)}


def sprite_files(model, size):
    """The Wumpa fruit as a sprite: the model's spin rendered as an animated texture (a vertical strip of {size} px frames, one tick each) on a flat one-block quad. The
    pickup renderer draws a flat single-quad model as a billboard that always faces the camera."""
    strip, count = sprite.render_strip(model, size)
    quad = {"textures": {"sprite": "ctrcraft:item/power_fruit_sprite", "particle": "ctrcraft:item/power_fruit_sprite"},
            "elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#sprite"}}}],
            "display": stand_in.display()}
    return {"textures/item/power_fruit_sprite.png": strip,
            "textures/item/power_fruit_sprite.png.mcmeta": {"animation": {"frametime": 1, "interpolate": False}},
            "models/item/pickup_power_fruit.json": quad}


def cube(quadrant_uv):
    """The original's crate: the mod's own crate shape, a plain cube of one block whose every face is the whole face picture (the original's faces are flat: a
    frame of four mitred strips with the rivets in the corners, round the panel). The sheet layout is the stand-in's, so the pack changes only the sheet."""
    groups = stand_in.crate(quadrant_uv)
    return {"textures": {"pickups": "ctrcraft:item/pickups", "particle": "ctrcraft:item/pickups"},
            "elements": [el for _, els in groups for el in els],
            "display": stand_in.display()}


CRATE_LEVEL_LEV = 145       # a level with TNT and Nitro crates (BIGFILE index of its .lev; the textures are the entry before)
CRATE_LEVEL_VRM = 144


def crate_cube_files(crates, scale):
    """The TNT and Nitro crates as real 3D models: a cube of one block with the original's own pictures of its sides, top and bottom (the mod draws the model solid)."""
    files = {}
    for key, faces in crates.items():
        tex = {}
        for part, img in faces.items():
            pic = img if scale == 1 else scaled(img, (img.width // scale, img.height // scale))
            files["textures/item/%s_crate_%s.png" % (key, part)] = pic
            tex[part] = "ctrcraft:item/%s_crate_%s" % (key, part)
        side = {"uv": [0, 0, 16, 16], "texture": "#side"}
        e = 0.0
        files["models/item/pickup_%s.json" % {"question": "powerup_crate", "question_blank": "powerup_crate_blank", "fruit": "fruit_crate"}.get(key, key)] = {
            "textures": {"side": tex["side"], "top": tex["top"], "bottom": tex["bottom"], "particle": tex["side"]},
            "elements": [{"from": [-e, -e, -e], "to": [16 + e, 16 + e, 16 + e], "faces": {
                "north": side, "south": side, "east": side, "west": side,
                "up": {"uv": [0, 0, 16, 16], "texture": "#top"}, "down": {"uv": [0, 0, 16, 16], "texture": "#bottom"}}}]}
    return files


def write_pack(root, name, description, files):
    pack = os.path.join(root, name)
    if os.path.isdir(pack):
        shutil.rmtree(pack)
    for rel, payload in files.items():
        path = os.path.join(pack, "assets", "ctrcraft", rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if isinstance(payload, Image.Image):
            payload.save(path)
        elif isinstance(payload, str):
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(payload)
        else:
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                json.dump(payload, f, indent=2)
    with open(os.path.join(pack, "pack.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"pack": {"pack_format": PACK_FORMAT, "description": description}}, f, indent=2)
    with open(os.path.join(pack, "README.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("Made by tools/extract_ctr_textures.py from the owner's own copy of Crash Team Racing.\n"
                "The artwork belongs to Sony / Naughty Dog: keep this pack on this machine, do not share or upload it.\n"
                "Enable it in Options > Resource Packs, above the Craft Team Racing mod. Use only one of the two texture packs.\n")
    return pack


def main():
    project = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--disc", help="the disc image (.bin raw 2352 or .iso); default: found in the project folder")
    ap.add_argument("--out", default=os.path.join(project, "local-assets", "ctr-textures"), help="folder to write the packs into")
    ap.add_argument("--install", action="append", help="also copy the packs into this resourcepacks folder (an ATLauncher instance's); may be given more than once")
    args = ap.parse_args()

    disc_path = args.disc or sounds.find_disc(project)
    if not disc_path or not os.path.exists(disc_path):
        sys.exit("No disc image found. Pass --disc \"path\\to\\CTR.bin\".")
    disc = sounds.Disc(disc_path)
    print("Disc:", disc_path, "| boot file:", disc.game_id())
    big = ct.BigFile(disc)

    vram = ct.Vram()
    vram.load(big.read(ct.SHARED_VRM))
    icon_table = dict(ct.icons(big.read(ct.SHARED_MPK)))

    def icon(name):
        if name not in icon_table:
            sys.exit("icon %r is not in shared.mpk (is this the NTSC-U disc?)" % name)
        return clear_background(to_image(vram.texture(icon_table[name])))

    # the power-up icons are stored on their side in the game's texture page (the picture is 32 wide and 48 high but upright it is 48 by 32): turn them a quarter
    # turn counter-clockwise
    def power(name):
        if name not in icon_table:
            sys.exit("icon %r is not in shared.mpk (is this the NTSC-U disc?)" % name)
        return power_icon(vram, icon_table[name])

    icons = {pid: power(n).transpose(Image.ROTATE_90) for pid, n in POWER_ICONS.items()}
    # the picture of a power-up with ten wumpa ("juiced up"): the game has them for three of them
    for pid, name in JUICED_ICONS.items():
        icons[pid] = power(name).transpose(Image.ROTATE_90)
    # the TNT and Nitro crates are in the shared model pack (BIGFILE entry 259): the real models, seen from the side, the top and the bottom with render_ctr_model, a face picture
    # each (the four sides of a crate are the same)
    pack = ct.pack_models(big.read(ct.SHARED_MPK))
    crates = {}
    for key in ("tnt", "nitro"):
        pm = rm.Prepared(vram, pack["crate_" + key], up=1)          # the pack's models have their height in the second coordinate
        # the lettering runs up the model's sides (the same picture is on all six faces): the side is turned a quarter so it reads along the face of the cube
        crates[key] = {"side": rm.render_view(pm, 0, 0, 96, fit_bbox=True).transpose(Image.ROTATE_270), "top": rm.render_view(pm, 0, 90, 96, fit_bbox=True),
                       "bottom": rm.render_view(pm, 0, -90, 96, fit_bbox=True)}
    # the bomb as a 3D model (the ball and the fuse), its spark as a picture and a place at the fuse's tip (tools/ctr_bomb.py)
    bomb_3d = ctr_bomb.bomb_files(vram, pack["bomb"], rm)
    wumpa = icon(WUMPA_ICON)
    # the HUD shine: the quarter burst exactly as the game has it (it draws it four times mirrored, with gouraud colours and the PS1's colour / 128 modulation: ItemHud does
    # that). The texel grey runs 0 to 189, and the last texel column is empty: trimmed, so the mirrored quarters meet without a seam
    shine = to_image(vram.texture(icon_table["poweredup"]))
    shine = shine.crop((0, 0, shine.getbbox()[2], shine.height))
    burst = full_burst(to_image(vram.texture(icon_table["poweredup"])))
    burst_square = Image.new("RGBA", (burst.width, burst.width), (0, 0, 0, 0))
    burst_square.alpha_composite(burst, (0, (burst.width - burst.height) // 2))
    lamps = {k: icon(n) for k, n in LAMPS.items()}
    puffs = [dust_puff(to_image(vram.texture(icon_table["dustpuff0%d" % i]))) for i in range(5)]
    skid = tire_mark(to_image(vram.texture(icon_table["tread"])))
    # the arrangement whose seam matches (found by comparing the edges of the pictures): shadow2 upside down above shadow1
    clod = dust_puff(to_image(vram.texture(icon_table["clod"])))
    ring = dust_puff(to_image(vram.texture(icon_table["smokering"])))
    shadow = kart_shadow(to_image(vram.texture(icon_table["shadow2"])).transpose(Image.FLIP_TOP_BOTTOM), to_image(vram.texture(icon_table["shadow1"])))

    vram.load(big.read(ct.CRASH_COVE_1P_VRM))       # the level's own textures on top of the shared ones
    models = ct.level_models(big.read(ct.CRASH_COVE_1P_LEV))
    # the whole faces, baked from the meshes: at the panel's own size for the original-scale pack (a 64 texel panel fills two thirds of the face), at half that for
    # the Minecraft-size one
    global QUESTION_MODE
    QUESTION_MODE = None
    question_96 = bake_face(vram, models["crate_question"], 96)
    planks_96 = bake_face(vram, models["crate_fruit"], 96)
    question = question_96
    planks = planks_96
    strip = to_image(vram.texture(ct.layout_bytes(models["crate_question"], 9)))      # the orange bevel strip of the crate's edges
    fruit = fruit_palette(models["fruit"])
    # the weapon crate and the fruit crate, like the TNT and Nitro ones: the real models (Crash Cove's level file) seen from the side, the top and the bottom with the
    # game's vertex shading, a face picture each
    # the weapon crate as the disc has it, and a second one without its "?" for a crate locked to one power-up (which shows that power-up's icon)
    for key, mname, mode in (("question", "crate_question", None), ("question_blank", "crate_question", "blank"), ("fruit", "crate_fruit", None)):
        QUESTION_MODE = mode
        pm = rm.Prepared(vram, models[mname])
        crates[key] = {"side": rm.render_view(pm, 0, 0, 96, fit_bbox=True), "top": rm.render_view(pm, 0, 90, 96, fit_bbox=True),
                       "bottom": rm.render_view(pm, 0, -90, 96, fit_bbox=True)}
    QUESTION_MODE = None
    # a look at the result: the blank faces (side, top, bottom) above, the standard faces below
    prev = Image.new("RGBA", (3 * 96 * 2, 2 * 96 * 2), (40, 40, 40, 255))
    for i, part in enumerate(("side", "top", "bottom")):
        prev.alpha_composite(crates["question_blank"][part].resize((192, 192), Image.NEAREST), (i * 192, 0))
        prev.alpha_composite(crates["question"][part].resize((192, 192), Image.NEAREST), (i * 192, 192))
    os.makedirs(args.out, exist_ok=True)
    prev.save(os.path.join(args.out, "crate_blank_preview.png"))
    print("crate faces: %dx%d and %dx%d" % (question.width, question.height, planks.width, planks.height))

    # the 3D bomb with its spark, and the burst effects: the same for both packs (their sizes are not the crates' or icons' scale)
    bomb_files = {"textures/gui/glow.png": shine,
                  "textures/gui/glow.png.mcmeta": {"texture": {"blur": True, "clamp": True}},        # smooth, and no wrapping round the edge (that draws lines)
                  "textures/particle/burst.png": burst_square}
    bomb_files.update(bomb_3d)
    # N. Brio's Beakers, the original meshes (frame 0 of the bubble animation) as 3D models: an OBJ and an atlas each, loaded by NeoForge's OBJ loader. The level is 108.8 units
    # to the block, as the crates are (one block); the beaker comes out 0.91 blocks tall
    beaker_files = {}
    for key in ("green", "red"):
        beaker_files.update(ctr_mesh_pack.mesh_pack_files(vram, pack["beaker" + key], "beaker_" + key, 1.0 / 108.8))
    # the Wumpa fruit placed in the world as a 3D model, too (Crash Cove's level file; untextured, Gouraud shaded: baked into the atlas): the leaves are double sided. It replaces the
    # animated sprite in the pickup model; the sprite strip stays for the HUD
    wumpa_files = ctr_mesh_pack.mesh_pack_files(vram, models["fruit"], "power_fruit", 1.0 / 108.8, up=2, animated=False, honour_cull=True)
    # the Tracking Missile (DYNAMIC_ROCKET, the pack's `rocket`): a thin body (a pointed nose, white with a red band and green fins) and the flame fans behind it, which wobble over ten
    # frames; the steady triangles are the model (animated=True leaves the moving sparks out), turned so its nose points to -z, its middle at the block's middle, crate scale
    # the effects' looks (assets/ctrcraft/fx, read by the mod's client/Fx): the jar's defaults are vanilla particles; this pack shows none of them, only the mod's own particles or nothing
    def _fx(*emitters):
        return {"emitters": list(emitters)}

    def _dust(count, spread, y):
        return {"particle": "ctrcraft:dust_puff", "count": count, "pos": [0, y, 0], "pos_spread": [spread, 0.0, spread], "velocity": [0, 0, 0], "velocity_spread": [0.02, 0.02, 0.02]}

    fx_files = {
        "fx/bomb_fuse.json": _fx(),                                                  # the spark at the fuse's tip is the effect
        "fx/vanish_bomb.json": _fx(_dust(4, 0.3, 0.3)),
        "fx/vanish_tnt.json": _fx(_dust(4, 0.3, 0.4)),
        "fx/vanish_potion.json": _fx(_dust(3, 0.2, 0.3)),
        "fx/crate_break.json": _fx(),                                                # the crate's own break-up pieces are the effect
        "fx/potion_shatter_green.json": _fx(),
        "fx/potion_shatter_red.json": _fx(),
        "fx/bomb_burst.json": _fx({"model": "burst", "frames": 30, "pos": [0, 0.3, 0]}, {"model": "burst", "frames": 30, "roll": 90, "pos": [0, 0.3, 0]},
                                  {"model": "shockwave", "frames": 17, "billboard": False, "pos": [0, 0.05, 0]}),
        "fx/tnt_explode.json": _fx({"model": "burst", "frames": 30, "pos": [0, 0.3, 0]}, {"model": "burst", "frames": 30, "roll": 90, "pos": [0, 0.3, 0]},
                                   {"model": "shockwave", "frames": 17, "billboard": False, "pos": [0, 0.05, 0]}),
        "fx/rain_cloud.json": _fx(_dust(2, 0.8, 2.4)),
    }
    # the original's explosion (RB_Burst_Init): two copies of `warpedburst` (a 30 frame starburst, the second turned a quarter about the view axis) that face the camera, and the red
    # `shockwave` (17 frames) on the ground: every frame baked as a model of its own (item/pickup_burst_<n>, pickup_shockwave_<n>), added to the screen; the fx profile plays them
    burst_files = {}
    for f in range(30):
        burst_files.update(ctr_mesh_pack.mesh_pack_files(vram, pack["warpedburst"], "burst_%d" % f, 1.0 / 108.8, frame=f, up=1, animated=False, honour_cull=False, kart_frame=True,
                                                         pivot_point=(0.0, 0.0, 0.0), shade=True, additive=True))
    for f in range(17):
        burst_files.update(ctr_mesh_pack.mesh_pack_files(vram, pack["shockwavered"], "shockwave_%d" % f, 1.0 / 108.8, frame=f, up=1, animated=False, honour_cull=False, kart_frame=True,
                                                         pivot_point=(0.0, 0.0, 0.0), shade=True, additive=True))
    # the Power Shield's bubble (RB_ShieldDark_ThTick_Grow): `shield` (blue) or `shield_green`, added to the screen, `shielddark` taken from it and the `highlight` that sweeps round it.
    # The model is a cone, 320 x 321 x 117 units (x, y its apex axis, z the short one) with the apex toward the camera it is a circle once z is stretched (client/ShieldBubble). Baked at the
    # resting scale 0x700 / 0x1000 in the kart's scale with its origin at the middle of the cone (x 0, y 33, z 58), which is the middle of the circle.
    shield_files = {}
    for src, name in (("shield", "shield_blue"), ("shield_green", "shield_green"), ("shielddark", "shield_dark"), ("highlight", "shield_highlight")):
        shield_files.update(ctr_mesh_pack.mesh_pack_files(vram, pack[src], name, 0.4375 / 72.53, up=1, animated=False, honour_cull=(src == "highlight"), kart_frame=True, pivot_point=(0.0, 33.5, 58.5),
                                                          shade=True, additive=True, smooth=32, weld_smooth=0.0))
    shield_files.update(sheen_frames.frames(shield_files))        # the sheen's eight frames, wrapped onto the bubble (an approximation of the footage)
    # the Aku Aku and Uka Uka masks that circle a kart under the Mask power-up (STATIC_AKUAKU / STATIC_UKAUKA, the pack's `akumouth` / `ukamouth`, frame 0) and the light round it (`akubeam` /
    # `ukabeam`, added to the screen): in the kart's frame at the kart's scale, the origin at the model's origin (the game puts the head and the beam by it)
    mask_files = {}
    for who in ("aku", "uka"):
        mask_files.update(ctr_mesh_pack.mesh_pack_files(vram, pack[who + "mouth"], "mask_" + who, 1.0 / 72.53, up=1, animated=False, honour_cull=False, kart_frame=True, pivot_point=(0.0, 0.0, 0.0), shade=True))
        # Aku Aku's beam is drawn ADDED to the screen (its texture page's blend mode 1), Uka Uka's SUBTRACTED (mode 2: it darkens, and its pale green colours take the green away, which
        # leaves a dark purple); client/MaskHead picks the blend. The picture is a dim grey gradient (its brightness is the transparency, which makes the streaks) and the colour comes from the
        # vertices (rainbow for Aku, pale green for Uka): the console's add of texel x vertex colour / 128, with no extra gain (a gain also lifts the transparency and saturates the streaks away).
        # the beam is an animation of 24 frames: a strip of 23 pairs of points that goes twice round an ellipse (124 x 64 units, in the model's x and y) while drifting along z (124 units), each
        # frame's drift moved on a step. In the game it is a ring round the kart's VERTICAL axis, so the model's z is made the height, halved (the ring is 62 high), and its y, the ellipse's short
        # axis, doubled (a round ring: 124 across); both laps are kept (the picture's dark-to-light gradient is the strip's transparency, so the second lap shows only as a faint trail below the bright end); the origin is the middle of the ellipse (0, -16) and of the drift (0.25)
        for f in range(24):
            mask_files.update(ctr_mesh_pack.mesh_pack_files(vram, pack[who + "beam"], "mask_beam_%s_%d" % (who, f), 1.0 / 72.53, frame=f, up=1, animated=False, honour_cull=False, kart_frame=True,
                                                            pivot_point=(0.0, -16.0, 0.25), shade=True, additive=True, remap=lambda v: (v[0], 0.5 * v[2], 2.0 * v[1]), tri_space=True))
    # the red beaker's rain cloud (`cloud`, 23 frames, one a game frame): a set of flat 32 x 32 panels (124 x 60 units, 5 thick) STANDING upright (the model's second coordinate is up, as the mask's; the footage shows vertical panels), that slide
    # sideways over the frames so that they circle like a merry-go-round; the instance's rotation is the identity so it keeps its place in the world (no turn to the camera). All 32 triangles are in blend mode 2 (SUBTRACTED): the texels with the semi-transparency bit take
    # their colour away from what is behind (the soft grey edge of each disc darkens the scene by its brightness) and the others are drawn as they are (the black middle, solid). So every
    # frame is TWO models: rain_cloud_<n> the opaque texels, rain_cloud_sub_<n> the blended ones, which client/RainCloudFx draws subtracted.
    cloud_files = {}
    for f in range(23):
        for suffix, select, add in (("", "opaque", False), ("sub_", "stp", True)):
            cloud_files.update(ctr_mesh_pack.mesh_pack_files(vram, pack["cloud"], "rain_cloud_%s%d" % (suffix, f), 1.0 / 72.53, frame=f, up=1, animated=False, honour_cull=False, kart_frame=True,
                                                             pivot_point=(0.0, 0.0, 0.0), shade=True, additive=add, texel_select=select))
    # the Warp Orb (`warpball`, 16 frames, one a game frame): a star 551 units across and as tall (the second coordinate is up), with a gem and lightning arcs 300 deep in the middle, STANDING on the ground where the orb is (its
    # instance has no rotation: it keeps its place in the world, so the depth of the arcs gives it a body), drawn ADDED to the screen (the textures' blend mode 1, black is nothing); the origin at the star's foot, mid-width
    orb_files = {}
    for f in range(16):
        # as the PS1 draws a textured polygon in blend mode 1: the texels with the semi-transparency bit are ADDED to the screen, the others are drawn as they are (warp_orb_solid_<n>)
        for prefix, select, add in (("", "stp", True), ("solid_", "opaque", False)):
            orb_files.update(ctr_mesh_pack.mesh_pack_files(vram, pack["warpball"], "warp_orb_%s%d" % (prefix, f), 1.0 / 72.53, frame=f, up=1, animated=False, honour_cull=False, kart_frame=True,
                                                           pivot_point=(1.3, 0.0, -24.0), shade=True, additive=add, tri_space=True, texel_select=select))
    rk = pack["rocket"]
    rk_abr = [((struct.unpack("<BBHBBHBBBB", ct.layout_bytes(rk, tx))[5] >> 5) & 3) if tx else 0 for (cn, tx, cl, fl) in ct.mesh_triangles(rk)]
    flame_ids = [i for i, a in enumerate(rk_abr) if a == 1]
    body_ids = [i for i, a in enumerate(rk_abr) if a != 1]
    # the body (drawn as it is) and the flame fans (added to the screen, as the boost flame is: a second model, drawn bright)
    missile_files = ctr_mesh_pack.mesh_pack_files(vram, rk, "missile", 1.0 / 108.8, up=1, animated=True, honour_cull=False, kart_frame=True, z_flip=True, pivot_point=(0.0, 0.0, 48.0), shade=True, exclude=flame_ids)
    missile_files.update(ctr_mesh_pack.mesh_pack_files(vram, rk, "missile_flame", 1.0 / 108.8, up=1, animated=True, honour_cull=False, kart_frame=True, z_flip=True, pivot_point=(0.0, 0.0, 48.0),
                                                       shade=True, additive=True, exclude=body_ids))
    # the boost flame (VehTurbo): eight models (STATIC_TURBO_EFFECT and the seven after it, the same shape with another picture each: a flame that crawls), a cone of rays that the game draws
    # in the kart's frame at the exhausts, the second one mirrored. Origin at the middle of the cone's base ring (9, 56, 0); added to the screen in the game (additive): see ctr_mesh_pack.
    flame_files = {}
    for k, mname in enumerate(["turbo"] + ["turbo%d" % i for i in range(1, 8)]):
        fm = pack[mname]
        # the shell's triangles are drawn SUBTRACTIVELY (the texture page's blend mode 2: the picture takes light away, which leaves the red and orange), the spike's and the rest added
        subs = [i for i, (cn, tx, cl, fl) in enumerate(ct.mesh_triangles(fm)) if tx and ((struct.unpack("<BBHBBHBBBB", ct.layout_bytes(fm, tx))[5] >> 5) & 3) == 2]
        others = [i for i in range(len(ct.mesh_triangles(fm))) if i not in subs]
        # the outer ring (every vertex more than 25 units from the hot core) is where the shell and the cone end: black there, so the whole rim fades out the same all the way round (the table gives
        # some of its vertices a little light and others none, which left a solid edge on one side)
        core = np.array([-0.4, 66.0, -63.75])
        verts = np.array(ct.mesh_vertices(fm, 0))
        rim = set(int(k) for k in range(len(verts)) if np.linalg.norm(verts[k] - core) > 25.0)
        shell_ids = set(subs) | set(i for i, (cn, tx, cl, fl) in enumerate(ct.mesh_triangles(fm)) if tx and (struct.unpack("<BBHBBHBBBB", ct.layout_bytes(fm, tx))[5] & 31) == 0x0e)
        # the shell's own 12 triangles leave 4 gaps in the funnel (thin slivers the 16 triangles of the cone cover but the shell does not): in the game they read as pale rays. Here the shell also
        # covers them, with the shell's flat picture and its colours (teal at the hot core, black at the rim), so the cone is one colour all over
        pos = lambda tri: sorted(tuple(int(round(x)) for x in verts[k]) for k in ct.mesh_triangles(fm)[tri][0])
        covered = set(tuple(pos(i)) for i in subs)
        gaps = []          # the game's own 12 shell triangles leave 4 gaps over the cone: they read as pale thin rays, which are part of the effect's shine (kept)
        flat_tex = ct.mesh_triangles(fm)[subs[0]][1]
        override = {}
        for i in gaps:
            corners = ct.mesh_triangles(fm)[i][0]
            override[i] = (flat_tex, [(20, 60, 80) if np.linalg.norm(verts[k] - core) <= 25.0 else (0, 0, 0) for k in corners])
        for tag, skip, ovr in (("boost_flame_%d" % k, subs + [], None), ("boost_flame_sub_%d" % k, [i for i in range(len(ct.mesh_triangles(fm))) if i not in subs and i not in override], override)):
            flame_files.update(ctr_mesh_pack.mesh_pack_files(vram, fm, tag, 1.5 / 108.8, up=1, animated=False, honour_cull=False, shade=True, translucent=True,
                                                             kart_frame=True, pivot_point=(-0.4, 66.0, -63.75), additive=True, smooth=16, exclude=skip, rim_zero=(rim, shell_ids), tri_override=ovr,
                                                             gain=(set(shell_ids) - set(subs) if not tag.startswith('boost_flame_sub') else None, 1.35) if not tag.startswith('boost_flame_sub') else None))
    # the crate's break-up: the original's 22 flying pieces of 12 frames (a particle, client/CrateBreakParticle), picture sheet and positions
    crate_break_files = ctr_mesh_pack.shard_files(vram, pack["crate_explosion"], "crate_break", 1.0 / 108.8)
    packs = []
    if True:
        files = {"textures/item/pickups.png": sheet(scaled(question_96, (32, 32)), scaled(planks_96, (32, 32)), None, fruit, 32),
                 "models/item/pickup_powerup_crate.json": cube([8, 0, 16, 8]),
                 "models/item/pickup_fruit_crate.json": cube([0, 8, 8, 16]),
                 "textures/gui/power_fruit.png": scaled(wumpa, (24, 16))}
        files.update(bomb_files)
        files.update(beaker_files)
        files.update(sprite_files(models["fruit"], 32))
        files.update(wumpa_files)
        files.update(TRANSLATION)
        files.update(missile_files)
        files.update(burst_files)
        files.update(shield_files)
        files.update(mask_files)
        files.update(cloud_files)
        files.update(orb_files)
        files.update(fx_files)
        files.update(crate_break_files)
        files.update(flame_files)
        files.update(crate_cube_files(crates, 2))
        for i, pimg in enumerate(puffs):
            files["textures/particle/dust_puff_%d.png" % i] = scaled(pimg, (16, 16))
        files["textures/particle/clod.png"] = scaled(clod, (8, 8))
        files["textures/particle/skid_mark.png"] = skid
        files["textures/particle/smoke_ring.png"] = scaled(ring, (16, 16))
        for pid, img in icons.items():
            files["textures/gui/power/%s.png" % pid] = scaled(img, (max(1, img.width // 2), max(1, img.height // 2)))
        for k, img in lamps.items():
            files["textures/gui/lights/%s.png" % k] = scaled(img, (24, 16))
        packs.append(write_pack(args.out, "CTRCraft-OriginalTextures",
                                "Craft Team Racing: original textures at Minecraft size, from your own disc (local use only, do not share)", files))
    if True:
        files = {"textures/item/pickups.png": sheet(question_96, planks_96, None, fruit, 96),
                 "models/item/pickup_powerup_crate.json": cube([8, 0, 16, 8]),
                 "models/item/pickup_fruit_crate.json": cube([0, 8, 8, 16]),
                 "textures/gui/power_fruit.png": wumpa}
        files.update(bomb_files)
        files.update(beaker_files)
        files.update(sprite_files(models["fruit"], 96))
        files.update(wumpa_files)
        files.update(TRANSLATION)
        files.update(missile_files)
        files.update(burst_files)
        files.update(shield_files)
        files.update(mask_files)
        files.update(cloud_files)
        files.update(orb_files)
        files.update(fx_files)
        files.update(crate_break_files)
        files.update(flame_files)
        files.update(crate_cube_files(crates, 1))
        files.update({"textures/particle/dust_puff_%d.png" % i: pimg for i, pimg in enumerate(puffs)})
        files["textures/particle/clod.png"] = clod
        files["textures/particle/skid_mark.png"] = skid
        files["textures/particle/smoke_ring.png"] = ring
        files.update({"textures/gui/power/%s.png" % pid: img for pid, img in icons.items()})
        files.update({"textures/gui/lights/%s.png" % k: img for k, img in lamps.items()})
        packs.append(write_pack(args.out, "CTRCraft-OriginalTextures-OriginalScale",
                                "Craft Team Racing: original textures at their original size, from your own disc (local use only, do not share)", files))
    for p in packs:
        print("Pack written to", p)
        for target in args.install or []:
            dst = os.path.join(target, os.path.basename(p))
            if os.path.isdir(dst):
                shutil.rmtree(dst)
            shutil.copytree(p, dst)
            print("  installed to", dst)


if __name__ == "__main__":
    main()
