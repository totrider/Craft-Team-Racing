"""
The HUD pictures: the older 32 x 48 power-up icons and the four start lamps. The icons that follow the sprite rule (16 x 16, outlined; the Slime Shield, Warp Orb, Totem and Power Fruit) are
made by tools/make_power_sprites.py; the clock and the good totem are Minecraft's own sprites. Stand-in pixel art in the original game's size, proportions and style (drawn
from memory as look-alikes, not the original game's art).

  python tools/make_power_icons.py

Writes textures/gui/power/<id>.png (32 x 48, portrait, as the original's; one per power-up in com.craftteamracing.items.HeldItem), textures/gui/power_fruit.png (48 x 32)
and textures/gui/lights/{red,green}_{on,off}.png (48 x 32). Placeholder art: a file that exists is yours and is not overwritten, so repaint them (delete one to
have it drawn again). Run from the project folder.
"""
import math
import os

from PIL import Image, ImageDraw

GUI = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "gui")

OUT = (22, 18, 30, 255)
WHITE = (245, 245, 245, 255)
GREY = (170, 174, 184, 255)
DGREY = (90, 92, 102, 255)
BLACK = (32, 30, 38, 255)
RED = (215, 55, 50, 255)
DRED = (150, 30, 30, 255)
ORANGE = (255, 140, 40, 255)
YELLOW = (255, 215, 40, 255)
GREEN = (70, 190, 70, 255)
LGREEN = (150, 235, 120, 255)
DGREEN = (35, 120, 50, 255)
BLUE = (60, 120, 235, 255)
DBLUE = (30, 50, 150, 255)
LBLUE = (140, 200, 255, 255)
BROWN = (150, 100, 55, 255)
DBROWN = (105, 70, 40, 255)
GOLD = (225, 170, 40, 255)
DGOLD = (150, 100, 20, 255)


SCALE = 2.0 / 3.0                 # the icons are drawn on the original's 32 x 48 grid and put on a 32 x 32 canvas at two thirds, so they have the pixel size of the mod's other pictures
OX = 5


class SD:
    """An ImageDraw that takes the 32 x 48 grid's coordinates and draws them at {@link SCALE} (crisp: the shapes are drawn at the small size, not shrunk afterwards)."""

    def __init__(self, im):
        self.d = ImageDraw.Draw(im)

    @staticmethod
    def _flat(c):
        out = []
        for v in c:
            if isinstance(v, (tuple, list)):
                out += [v[0], v[1]]
            else:
                out.append(v)
        return out

    def _pts(self, c):
        f = self._flat(c)
        return [round(f[i] * SCALE) + (OX if i % 2 == 0 else 0) for i in range(len(f))]

    def _box(self, c):
        x0, y0, x1, y1 = self._pts(c)
        return [x0, y0, max(x0, x1), max(y0, y1)]

    def ellipse(self, box, **kw):
        self.d.ellipse(self._box(box), **kw)

    def rectangle(self, box, **kw):
        self.d.rectangle(self._box(box), **kw)

    def rounded_rectangle(self, box, radius=0, **kw):
        self.d.rounded_rectangle(self._box(box), radius=max(1, round(radius * SCALE)), **kw)

    def polygon(self, pts, **kw):
        self.d.polygon(self._pts(pts), **kw)

    def line(self, pts, width=1, **kw):
        self.d.line(self._pts(pts), width=max(1, round(width * SCALE)), **kw)

    def point(self, pts, **kw):
        self.d.point(self._pts(pts), **kw)


def canvas(w=32, h=48):
    if (w, h) == (32, 48):
        im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        return im, SD(im)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def gloss(d, box, colour=WHITE):
    x0, y0, x1, y1 = box
    d.ellipse([x0, y0, x0 + max(2, (x1 - x0) // 3), y0 + max(2, (y1 - y0) // 3)], fill=colour)


def bomb_at(im, cx, cy, r):
    d = SD(im)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=DBLUE, outline=OUT)
    d.ellipse([cx - r + 2, cy - r + 2, cx - r + 2 + r // 2, cy - r + 2 + r // 2], fill=BLUE)
    # a skull
    d.ellipse([cx - r // 2, cy - r // 3, cx + r // 2, cy + r // 2], fill=WHITE, outline=OUT)
    d.rectangle([cx - r // 3, cy, cx - r // 6, cy + r // 6], fill=BLACK)
    d.rectangle([cx + r // 6, cy, cx + r // 3, cy + r // 6], fill=BLACK)
    # the fuse and its spark
    d.line([(cx + r // 2, cy - r + 1), (cx + r, cy - r - r // 2)], fill=BROWN, width=2)
    sx, sy = cx + r, cy - r - r // 2
    d.polygon([(sx, sy - 4), (sx + 2, sy - 1), (sx + 5, sy), (sx + 2, sy + 1), (sx, sy + 4), (sx - 2, sy + 1), (sx - 5, sy), (sx - 2, sy - 1)], fill=YELLOW)
    d.ellipse([sx - 1, sy - 1, sx + 1, sy + 1], fill=RED)


def rocket_at(im, cx, cy, s):
    """A tracking missile pointing down: light body, dark nose, green fins and flame at the top."""
    d = SD(im)

    def p(x, y):
        return (cx + x * s, cy + y * s)
    d.polygon([p(-9, -14), p(0, -22), p(9, -14), p(7, -10), p(-7, -10)], fill=ORANGE)
    d.rectangle([p(-6, -12), p(6, 8)], fill=GREY, outline=OUT)
    d.rectangle([p(-3, -10), p(-1, 6)], fill=WHITE)
    d.polygon([p(-6, 8), p(0, 20), p(6, 8)], fill=DGREY, outline=OUT)
    d.polygon([p(-6, -4), p(-14, -10), p(-6, 4)], fill=GREEN, outline=OUT)
    d.polygon([p(6, -4), p(14, -10), p(6, 4)], fill=GREEN, outline=OUT)
    d.rectangle([p(-6, -3), p(6, 0)], fill=RED)


def spring():
    im, d = canvas()
    for i in range(6):
        y = 10 + i * 6
        d.line([(8, y), (24, y + 3)], fill=GREY, width=3)
        d.line([(24, y + 3), (8, y + 6)], fill=DGREY, width=2)
    d.rounded_rectangle([5, 5, 27, 9], radius=1, fill=RED, outline=OUT)
    d.rounded_rectangle([5, 42, 27, 46], radius=1, fill=RED, outline=OUT)
    return im


def invisibility():
    im, d = canvas()
    pts = [(6, 44), (6, 20), (9, 9), (16, 5), (23, 9), (26, 20), (26, 44), (22, 39), (19, 44), (16, 39), (13, 44), (10, 39)]
    for a, b in zip(pts, pts[1:] + pts[:1]):
        d.line([a, b], fill=LBLUE, width=2)
    d.ellipse([11, 19, 14, 24], fill=LBLUE)
    d.ellipse([18, 19, 21, 24], fill=LBLUE)
    return im


def super_engine():
    im, d = canvas()
    for k in range(8):
        a = k * math.pi / 4
        x, y = 16 + 12 * math.cos(a), 22 + 12 * math.sin(a)
        d.rectangle([x - 3, y - 3, x + 3, y + 3], fill=GOLD, outline=OUT)
    d.ellipse([5, 11, 27, 33], fill=YELLOW, outline=OUT)
    d.ellipse([12, 18, 20, 26], fill=DGREY, outline=OUT)
    d.polygon([(8, 47), (12, 36), (16, 47)], fill=ORANGE)
    d.polygon([(16, 47), (20, 36), (24, 47)], fill=ORANGE)
    return im


def lamp(colour, lit):
    """A start lamp, 48 x 32: a grey housing with blue clips either side and a glossy red or green bulb."""
    im, d = canvas(48, 32)
    d.rectangle([0, 11, 3, 21], fill=(60, 110, 200, 255), outline=OUT)
    d.rectangle([44, 11, 47, 21], fill=(60, 110, 200, 255), outline=OUT)
    d.ellipse([3, 1, 44, 30], fill=(70, 72, 84, 255), outline=OUT)
    d.ellipse([5, 3, 42, 28], fill=(120, 124, 138, 255))
    if colour == "red":
        bulb, edge, shine = ((255, 70, 60, 255), (200, 20, 20, 255), (255, 200, 190, 255)) if lit else ((110, 28, 28, 255), (70, 15, 15, 255), (150, 60, 60, 255))
    else:
        bulb, edge, shine = ((90, 255, 110, 255), (30, 170, 60, 255), (210, 255, 215, 255)) if lit else ((25, 80, 35, 255), (15, 50, 25, 255), (60, 120, 70, 255))
    d.ellipse([8, 5, 39, 26], fill=edge)
    d.ellipse([10, 6, 37, 24], fill=bulb)
    d.ellipse([13, 8, 26, 14], fill=shine)
    d.rectangle([20, 29, 27, 31], fill=(60, 110, 200, 255))
    return im


def unknown():
    """The fallback for a power-up that has no picture of its own (one from a data pack, say): a purple tile with a white question mark."""
    im, d = canvas()
    d.rounded_rectangle([4, 6, 28, 44], radius=5, fill=(112, 72, 172, 255), outline=OUT)
    d.rounded_rectangle([6, 8, 26, 14], radius=3, fill=(150, 110, 210, 255))
    mark = [(10, 18), (11, 13), (16, 11), (21, 13), (22, 18), (19, 22), (16, 25), (16, 29)]
    d.line(mark, fill=OUT, width=7, joint="curve")
    d.line(mark, fill=WHITE, width=4, joint="curve")
    d.ellipse([13, 32, 19, 38], fill=OUT)
    d.ellipse([14, 33, 18, 37], fill=WHITE)
    return im


ICONS = {
    "unknown": unknown,
    "spring": spring,
    "invisibility": invisibility, "super_engine": super_engine,
    
}


def main():
    os.makedirs(os.path.join(GUI, "power"), exist_ok=True)
    os.makedirs(os.path.join(GUI, "lights"), exist_ok=True)
    for name, make in ICONS.items():
        path = os.path.join(GUI, "power", name + ".png")
        if not os.path.exists(path):
            make().save(path)
    for colour in ("red", "green"):
        for lit in (True, False):
            path = os.path.join(GUI, "lights", "%s_%s.png" % (colour, "on" if lit else "off"))
            if not os.path.exists(path):
                lamp(colour, lit).save(path)
    print("HUD pictures ok")


if __name__ == "__main__":
    main()
