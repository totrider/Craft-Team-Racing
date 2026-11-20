#!/usr/bin/env python3
"""The mod's 512 x 512 icon (Modrinth, CurseForge, the mods screen), in a few variants: docs/img/icon/icon_<name>.png.

The kart is drawn from its own item model exactly as the inventory draws it (tools/make_recipe_grids.py), with the frame tinted red and the seat yellow, driving across a boost pad that lies
on asphalt. The pad is drawn on the ground plane under the kart using the same view as the kart, so the wheels touch it and its arrows point the way the kart faces. No text.
Textures come from this mod's resources only. Needs Pillow.
The result is also the mods screen logo (src/main/resources/logo.png, a copy of docs/img/icon/icon_asphalt_pad.png). Usage: python tools/make_mod_icon.py
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_recipe_grids as g  # noqa: E402

ROOT = g.ROOT
OUT = ROOT / "docs/img/icon"
SIZE = 512
RED = (214, 38, 38)
YELLOW = (250, 204, 28)
# Boost pads are one block (16 units) each. The kart is a little bigger than one (wheels span x -1.5..17.5, z -6.5..16.5), the arrows point to -z, the kart's nose.
PADS = [(0.0, 16.0, -3.0, 13.0)]                  # one pad, centred under the kart (the kart body spans z -6.5..16.5, centre 5, and x -1.5..17.5, centre 8)
FOCUS = (-2.0, 18.0, -8.0, 17.0)         # the part of the pad kept in view with the kart: the rest of the pad may run off the picture         # x1, x2, z1, z2 of the pad in model units (the kart's wheels span x -1.5..17.5, z -6.5..16.5); the arrows point to -z, which is the kart's nose


def kart(px, info=None):
    """The kart, red frame and yellow seat, as a transparent square picture of px pixels."""
    model = g.load_model("craftteamracing:item/kart")
    big = g.iso(model, k=48, tints={0: RED, 1: YELLOW}, size=px * 2, fit=15.5, info=info)
    return big.resize((px, px), Image.LANCZOS)


def projector(info, px):
    """Model point -> position in the kart picture of px pixels."""
    def project(x, y, z):
        cx, cy, _ = info["view"]((x, y, z))
        fx = (cx - info["box"][0]) * info["f"] + info["paste"][0]
        fy = (cy - info["box"][1]) * info["f"] + info["paste"][1]
        return (fx * px / info["side"], fy * px / info["side"])
    return project


def outline(img, width, colour):
    grown = img.getchannel("A").filter(ImageFilter.MaxFilter(width * 2 + 1))
    out = Image.new("RGBA", img.size, colour + (0,))
    out.putalpha(grown)
    return out


def vignette(img, amount):
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).ellipse((-w * 0.15, -h * 0.15, w * 1.15, h * 1.15), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(w * 0.18))
    dark = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dark.putalpha(mask.point(lambda p: int((255 - p) * amount)))
    img.alpha_composite(dark)


def gradient(top, bottom):
    img = Image.new("RGBA", (SIZE, SIZE))
    d = ImageDraw.Draw(img)
    for y in range(SIZE):
        t = y / (SIZE - 1)
        d.line((0, y, SIZE, y), fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)) + (255,))
    return img


def pad_texture():
    base = g.load_texture("craftteamracing:block/boost_top")
    over = g.load_texture("craftteamracing:block/boost_top_glow")
    pad = Image.new("RGBA", (16, 16), (20, 24, 30, 255))
    if base is not None:
        pad = base.crop((0, 0, base.width, base.width)).convert("RGBA")
    if over is not None:
        pad.alpha_composite(over.crop((0, 0, over.width, over.width)).convert("RGBA"))
    return pad


def scene(name, streaks=True, lane=False):
    """The kart driving across a boost pad on asphalt."""
    # fit the kart and the pad into the picture: size the kart picture once, measure, then draw it at the size that fits
    px = int(SIZE * 1.1)
    info = {}
    k_img = kart(px, info)
    P = projector(info, px)
    xs, ys = [], []
    for x in FOCUS[:2]:
        for z in FOCUS[2:]:
            c = P(x, 0, z)
            xs.append(c[0])
            ys.append(c[1])
    kb = k_img.getchannel("A").getbbox()
    xs += [kb[0], kb[2]]
    ys += [kb[1], kb[3]]
    px = int(px * min(1.0, 470.0 / max(max(xs) - min(xs), max(ys) - min(ys))))
    info = {}
    k_img = kart(px, info)
    P = projector(info, px)
    xs, ys = [], []
    for x in FOCUS[:2]:
        for z in FOCUS[2:]:
            c = P(x, 0, z)
            xs.append(c[0])
            ys.append(c[1])
    kb = k_img.getchannel("A").getbbox()
    xs += [kb[0], kb[2]]
    ys += [kb[1], kb[3]]
    ox = SIZE / 2 - (min(xs) + max(xs)) / 2
    oy = SIZE / 2 - (min(ys) + max(ys)) / 2

    def Q(x, y, z):
        c = P(x, y, z)
        return (c[0] + ox, c[1] + oy)

    rnd = random.Random(7)
    img = gradient((52, 56, 66), (22, 24, 30))
    d = ImageDraw.Draw(img)
    for _ in range(2600):                                          # asphalt grain
        x, y = rnd.randrange(SIZE), rnd.randrange(SIZE)
        v = rnd.randrange(60, 110)
        d.point((x, y), fill=(v, v, v + 6, 255))

    nose = Q(0, 0, -1)
    tail = Q(0, 0, 0)
    ln = math.hypot(nose[0] - tail[0], nose[1] - tail[1])
    fwd = ((nose[0] - tail[0]) / ln, (nose[1] - tail[1]) / ln)      # the way the kart faces, on the screen
    side = (-fwd[1], fwd[0])
    mid = (SIZE / 2, SIZE / 2)

    if lane:                                                       # dashed lane lines along the driving direction, either side of the pad
        for sgn in (1, -1):
            for t in range(-9, 10):
                c = (mid[0] + side[0] * 215 * sgn + fwd[0] * t * 110, mid[1] + side[1] * 215 * sgn + fwd[1] * t * 110)
                d.line((c[0], c[1], c[0] + fwd[0] * 60, c[1] + fwd[1] * 60), fill=(235, 235, 235, 255), width=6)

    glow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    big = Image.new("RGBA", (SIZE * 2, SIZE * 2), (0, 0, 0, 0))     # the pads at twice the size, brought down for clean edges
    texture = pad_texture()
    for x1, x2, z1, z2 in PADS:
        ImageDraw.Draw(glow).polygon([Q(x1, 0, z1), Q(x2, 0, z1), Q(x2, 0, z2), Q(x1, 0, z2)], fill=(70, 255, 150, 255))
        o, u, v = (tuple(c * 2 for c in Q(x1, 0, z1)), tuple(c * 2 for c in Q(x2, 0, z1)), tuple(c * 2 for c in Q(x1, 0, z2)))
        g.draw_face(big, texture, (0, 0, 16, 16), o, u, v, 1.0)
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(30)))
    img.alpha_composite(big.resize((SIZE, SIZE), Image.LANCZOS))

    contact = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))         # the kart's shadow on the pad: it is down on the surface
    ImageDraw.Draw(contact).polygon([Q(-2, 0, -7), Q(18, 0, -7), Q(18, 0, 17), Q(-2, 0, 17)], fill=(0, 0, 0, 55))
    img.alpha_composite(contact.filter(ImageFilter.GaussianBlur(9)))

    if streaks:                                                    # speed streaks behind the kart, along the way it faces
        sd = ImageDraw.Draw(img)
        for i in range(16):
            off = (i - 8) * 34 + rnd.randrange(-10, 10)
            back = rnd.randrange(40, 300)
            length = rnd.randrange(90, 230)
            p0 = (mid[0] + side[0] * off - fwd[0] * back, mid[1] + side[1] * off - fwd[1] * back)
            p1 = (p0[0] - fwd[0] * length, p0[1] - fwd[1] * length)
            sd.line((*p0, *p1), fill=(255, 255, 255, 80), width=3)

    full = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    full.alpha_composite(k_img, (int(round(ox)), int(round(oy))))
    img.alpha_composite(outline(full, 6, (18, 18, 24)))
    img.alpha_composite(full)
    vignette(img, 0.45)
    OUT.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(OUT / f"icon_{name}.png")


def main():
    scene("asphalt_pad")
    print(f"icons in {OUT}")


if __name__ == "__main__":
    main()
