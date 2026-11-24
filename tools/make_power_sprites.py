"""
The mod's own power-up sprites, 16 x 16 and outline-free (see tools/sprite_outline.py), and the HUD pictures made from them.

  python tools/make_power_sprites.py

Sprites drawn here: the Slime Shield (green, and the blue one of ten power fruit), the Warp Orb (an end-crystal-style glass frame round a gem, in the orb's teal), the Power Fruit, the
Bomb (and the powered-up one) and the Turbo, and the evil totem (drawn by tools/make_evil_totem.py; the good totem and the clock are Minecraft's own sprites, outlined at run time by
client/IconSprites).

The sprites are layered sheets, written as krita/sprites/<name>.kra (a modder's templates; the evil totem is krita/items/mask_uka.kra). tools/build_art.py flattens them into
  textures/item/sprite/<name>.png      the 16 x 16 sprite, no outline (usable by models); the Power Fruit is also textures/item/power_fruit.png
and this script writes, from the same sheets and overwriting (a derived picture, so edit the code, not the PNG):
  textures/gui/power/<name>.png        the HUD picture: the outlined sprite in a 96 x 64 texture   (shield, shield_charged, warpball, mask_dark, bomb, bomb_charged, turbo)
  textures/gui/power_fruit.png         the same for the Power Fruit
Run from the project folder.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402
import make_evil_totem  # noqa: E402
import sprite_outline as so  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures")
POWER = os.path.join(ASSETS, "gui", "power")


def new():
    sheet = kra_layers.Sheet(16, 16)
    return sheet, kra_layers.Painter(sheet)


SLIME = {
    "green": {"edge": (52, 140, 58), "body": (112, 215, 96), "light": (170, 245, 150), "core": (66, 160, 70), "gloss": (240, 255, 235)},
    "blue": {"edge": (44, 84, 170), "body": (96, 160, 245), "light": (160, 205, 255), "core": (56, 104, 205), "gloss": (240, 248, 255)},
}


def slime(colour):
    """A slime cube seen from the front: a translucent body with a darker core, a lighter inner rim and a glossy corner (cut corners: it is a round-edged block)."""
    c = SLIME[colour]
    sheet, p = new()
    p.part("edge")
    p.rectangle([1, 2, 14, 13], fill=c["edge"] + (255,))
    p.rectangle([2, 1, 13, 14], fill=c["edge"] + (255,))
    p.part("body")
    p.rectangle([2, 3, 13, 12], fill=c["body"] + (255,))
    p.rectangle([3, 2, 12, 13], fill=c["body"] + (255,))
    p.part("light rim")
    p.rectangle([4, 4, 11, 11], fill=c["light"] + (255,))
    p.part("core")
    p.rectangle([5, 5, 10, 10], fill=c["core"] + (255,))
    p.part("gloss")
    for x, y in ((3, 3), (4, 3), (3, 4)):
        p.putpixel((x, y), c["gloss"] + (255,))
    p.part("light corner")
    for x, y in ((11, 12), (12, 11)):
        p.putpixel((x, y), c["light"] + (255,))
    return sheet


def warpball():
    """The Warp Orb: the end crystal's idea (a pale glass frame round a faceted gem with a bright core), in the orb's teal."""
    sheet, p = new()
    glass, glass_hi = (190, 240, 240, 255), (235, 255, 255, 255)
    p.part("glass frame")
    p.rectangle([0, 0, 15, 15], outline=glass)
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        p.putpixel((x, y), glass_hi)
    for i in range(1, 15):                                      # a lit left and top edge
        p.putpixel((i, 0), glass_hi if i % 3 else glass)
        p.putpixel((0, i), glass_hi if i % 3 else glass)
    # the gem: a diamond, lit from the top left; rings from the rim in
    p.part("gem")
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            dist = abs(dx) + abs(dy)
            if dist > 6.5:
                continue
            lit = dx + dy < 0
            if dist <= 1.5:
                col = (235, 255, 240)
            elif dist <= 3.5:
                col = (130, 240, 215) if lit else (70, 200, 190)
            elif dist <= 5.0:
                col = (70, 205, 205) if lit else (40, 150, 170)
            else:
                col = (45, 165, 185) if lit else (28, 105, 140)
            p.putpixel((x, y), col + (255,))
    p.part("core cross")
    for x, y in ((7, 5), (8, 5), (5, 7), (5, 8), (10, 7), (10, 8), (7, 10), (8, 10)):     # the core's cross, as the crystal's
        p.putpixel((x, y), (190, 255, 235, 255) if x + y < 15 else (120, 235, 215, 255))
    return sheet


def power_fruit():
    """The Power Fruit: round, red at the top through orange and yellow to green below, a gloss, two leaves."""
    sheet, p = new()
    stops = ((0.0, (205, 55, 40)), (0.35, (250, 150, 45)), (0.62, (250, 210, 50)), (1.0, (120, 190, 60)))
    p.part("fruit")
    for y in range(4, 16):
        t = (y - 4) / 11.0
        col = stops[-1][1]
        for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
            if t0 <= t <= t1:
                u = (t - t0) / (t1 - t0)
                col = tuple(int(c0[i] + (c1[i] - c0[i]) * u) for i in range(3))
        for x in range(16):
            dx, dy = (x - 7.5) / 7.0, (y - 9.5) / 6.0
            if dx * dx + dy * dy <= 1.0:
                edge = dx * dx + dy * dy > 0.72
                p.putpixel((x, y), tuple(int(v * (0.72 if edge else 1.0)) for v in col) + (255,))
    p.part("gloss")
    for x, y in ((4, 7), (5, 6), (4, 8), (5, 7)):
        p.putpixel((x, y), (255, 240, 170, 255))
    p.part("leaf (left)")
    p.polygon([(8, 4), (5, 1), (4, 3), (7, 4)], fill=(70, 170, 60, 255))
    p.part("leaf (right)")
    p.polygon([(8, 4), (11, 0), (13, 2), (9, 4)], fill=(50, 140, 50, 255))
    p.part("stem")
    p.putpixel((8, 3), (120, 80, 40, 255))
    return sheet


def turbo():
    """The Turbo (Redstone Surge): the original's red elongated canister seen on the diagonal, base bottom left and mouth top right, in redstone dust's reds with a metal neck and lip in the
    bucket's greys. Drawn from a capsule profile (round base, straight body, shoulder, neck, lip) shaded across it from the upper left. (A bucket and a canister with a clock-coloured gauge
    were tried and dropped: the bucket read less like the original, the gauge made the canister hard to read.)"""
    base, mouth = (3.2, 12.8), (12.2, 3.8)
    length = math.hypot(mouth[0] - base[0], mouth[1] - base[1])
    ux, uy = (mouth[0] - base[0]) / length, (mouth[1] - base[1]) / length
    nx, ny = uy, -ux                                       # across the canister, toward the upper left (the light)
    if nx + ny > 0:
        nx, ny = -nx, -ny
    metal = {"hi": (216, 216, 216), "mid": (150, 150, 150), "lo": (95, 95, 95), "dk": (53, 53, 53)}
    dust = {"hi": (255, 70, 55), "mid": (222, 20, 14), "lo": (140, 5, 2), "dk": (84, 6, 2)}
    rnd = random.Random(3)
    sheet, p = new()
    p.part("redstone dust")
    dust_layer = p.image
    p.part("metal neck")
    metal_layer = p.image
    p.part("seams")
    seam_layer = p.image
    painted = set()
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - base[0], y + 0.5 - base[1]
            t = (px * ux + py * uy) / length               # 0 at the base, 1 at the mouth
            s = px * nx + py * ny                          # across: positive toward the light
            along = t * length
            if t < 0:
                half = math.sqrt(max(0.0, 3.3 ** 2 - along ** 2)) if along > -3.3 else 0.0
            elif t < 0.60:
                half = 3.3
            elif t < 0.74:
                half = 3.3 - (t - 0.60) / 0.14 * 1.4
            elif t < 0.90:
                half = 1.9
            elif t <= 1.04:
                half = 2.6
            else:
                half = 0.0
            if half <= 0 or abs(s) > half:
                continue
            pal = metal if t >= 0.60 else dust
            k = s / half
            target = metal_layer if pal is metal else dust_layer
            if 0.74 <= t < 0.80 or 0.90 <= t < 0.93:
                col = metal["dk"]                          # the seams
                target = seam_layer
            elif k > 0.55:
                col = pal["hi"]
            elif k > -0.15:
                col = pal["mid"]
            elif k > -0.65:
                col = pal["lo"]
            else:
                col = pal["dk"]
            if pal is dust and col == dust["mid"] and rnd.random() < 0.18:
                col = dust["hi"] if rnd.random() < 0.5 else dust["lo"]       # the dust's grain
            target.putpixel((x, y), col + (255,))
            painted.add((x, y))
    p.part("glints")
    for (x, y), col in (((5, 9), (255, 235, 230)), ((6, 8), (255, 150, 130)), ((10, 5), (240, 240, 240))):       # glints
        if (x, y) in painted:
            p.putpixel((x, y), col + (255,))
    return sheet


def bomb(hot=False):
    """The Bomb, composed as the original's icon: the fuse and a big spark at the top left, the ball low and to the right, lit from the top left, with the skull on its right side turned away
    from us (narrow, foreshortened, in shade) rather than dead centre. {hot}: the powered-up one, glowing cracks (the fire charge) and a bigger flame."""
    sheet, p = new()
    iron = [(30, 30, 36), (46, 46, 52), (66, 66, 70), (89, 88, 88), (122, 122, 128)] if not hot else [(24, 17, 16), (31, 22, 21), (55, 42, 40), (81, 73, 49), (122, 92, 67)]
    cx, cy, r = 9.2, 9.8, 6.3
    p.part("ball")
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
            q = dx * dx + dy * dy
            if q <= 1.0:
                light = -0.62 * dx - 0.62 * dy + 0.55 * math.sqrt(max(0.0, 1.0 - q))          # a light from the top left and the front
                shade = 4 if light > 1.05 else 3 if light > 0.75 else 2 if light > 0.4 else 1 if light > 0.05 else 0
                p.putpixel((x, y), iron[shade] + (255,))
    if hot:
        p.part("glowing cracks")
        for x, y in ((7, 5), (8, 6), (10, 5), (11, 7), (6, 11), (7, 12), (9, 14), (13, 10)):
            p.putpixel((x, y), (255, 150, 30, 255))
        for x, y in ((8, 5), (11, 8)):
            p.putpixel((x, y), (255, 215, 80, 255))
    # the skull on the ball's right side, turned away: its face is narrow and shaded
    bone, light_bone, shade_bone = (232, 229, 210, 255), (252, 251, 237, 255), (150, 146, 124, 255)
    socket = (255, 140, 30, 255) if hot else (24, 24, 28, 255)
    p.part("skull")
    for (x, y, c) in ((11, 8, bone), (12, 8, shade_bone), (11, 9, light_bone), (12, 9, bone), (13, 9, shade_bone), (11, 10, bone), (12, 10, bone), (13, 10, shade_bone),
                      (11, 11, bone), (12, 11, shade_bone), (11, 12, bone), (12, 12, bone), (13, 12, shade_bone), (12, 13, shade_bone)):
        p.putpixel((x, y), c)
    p.part("skull sockets")
    p.putpixel((12, 9), socket)
    p.putpixel((11, 10), socket)
    # the fuse: from the ball's upper left, curling up and out to the spark
    p.part("fuse")
    for x, y, c in ((6, 5, (150, 105, 60, 255)), (5, 4, (190, 150, 90, 255)), (4, 4, (140, 100, 58, 255)), (3, 3, (190, 150, 90, 255)), (3, 2, (140, 100, 58, 255))):
        p.putpixel((x, y), c)
    # the spark: a burst, yellow-white in the middle, orange and dark red out to its points
    burst = [(2, 1), (1, 2), (2, 2), (3, 1), (1, 1), (2, 0), (0, 2), (1, 3)]
    p.part("spark")
    for x, y in ((0, 0), (3, 0), (0, 3), (4, 2), (2, 4), (4, 0)):
        p.putpixel((x, y), (214, 70, 24, 255))
    for x, y in burst:
        p.putpixel((x, y), (255, 150, 30, 255))
    for x, y in ((2, 1), (1, 2), (2, 2)):
        p.putpixel((x, y), (255, 214, 70, 255))
    p.putpixel((2, 2), (255, 255, 224, 255))
    if hot:
        p.part("flame")
        for x, y in ((3, 2), (3, 4), (5, 3)):
            p.putpixel((x, y), (255, 120, 30, 255))
    return sheet


def sprites():
    """Every sprite of this family as {name: layered sheet}, in the order the HUD pictures are made."""
    return {"shield": slime("green"), "shield_charged": slime("blue"), "warpball": warpball(), "mask_dark": make_evil_totem.sheet(),
            "bomb": bomb(), "bomb_charged": bomb(True), "turbo": turbo(), "power_fruit": power_fruit()}


def main():
    inputs = kra_stamp.inputs_for(__file__)
    os.makedirs(POWER, exist_ok=True)
    for name, sheet in sprites().items():
        if name != "mask_dark":                                   # the evil totem's own script writes its .kra
            kra_layers.write_if_stale("krita/sprites/%s.kra" % name, sheet, inputs)
        hud = os.path.join(ASSETS, "gui", name + ".png") if name == "power_fruit" else os.path.join(POWER, name + ".png")
        so.hud_icon(sheet.flatten()).save(hud)
    print("power sprites ok")


if __name__ == "__main__":
    main()
