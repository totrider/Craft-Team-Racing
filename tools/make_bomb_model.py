"""
The Bomb: the mod's own 3D model, its two textures, its spark and where the spark is.

  python tools/make_bomb_model.py

A round iron bomb with a rope fuse, modelled in Blender (blender/bomb/bomb.blend, made once by this script and yours after that) and exported by tools/export_blender_models.py to
models/item/bomb.obj + pickup_bomb.json, and, for the powered-up bomb (ten power fruit), bomb_charged.obj + pickup_bomb_charged.json (the same mesh, the glowing texture).
The ball is a sphere flattened at the two ends of the axle it rolls about, with a skull plate on each end, a steel belt round its middle and a collar where the fuse comes out. A resource
pack's original bomb (models/item/bomb.obj, pickup_bomb.json) replaces it; the powered-up model is then not used (the renderer tints the pack's bomb red, as before).

Textures (drawn here every run: edit the code, not the PNGs), 32 x 32, in the grey of Minecraft's cauldron and anvil for the iron, the skeleton's face and the bone item's cream for the skulls:
  bomb_iron.png   iron (top left), skull plate (top right), steel belt (bottom left, upper half), collar (bottom left, lower half), rope (bottom right, upper half), burnt tip (lower half)
  bomb_hot.png    the same layout for the powered-up bomb: heated iron with glowing orange cracks (after the fire charge), the skulls' eyes alight, a flame-coloured fuse
Also writes textures/entity/bomb_spark.png (the glow at the fuse's tip) and bomb/bomb_fuse.json ({tip: [x, y, z] in blocks from the ball's middle, spark_size, ground: the ball's radius}):
the tip is where BombFx puts the flames, so the model's fuse ends there. The model's middle (0.5, 0.5, 0.5) is the ball's middle; its front is north (-z); the fuse leaves the -x end.
Run from the project folder.
"""
import json
import math
import os
import random
import subprocess
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import export_blender_models as ebm  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "craftteamracing")
TEX = os.path.join(ASSETS, "textures", "item")
SPARK = os.path.join(ASSETS, "textures", "entity", "bomb_spark.png")
FUSE = os.path.join(ASSETS, "bomb", "bomb_fuse.json")
BLEND = os.path.join("blender", "bomb", "bomb.blend")

TIP = (-0.55, 0.09, 0.31)    # the fuse's tip, blocks from the ball's middle
SPARK_SIZE = 0.8
GROUND = 0.4                # the ball's radius (blocks)

# iron: the cauldron's and the anvil's greys (colours only; the grain is drawn here)
IRON = [(45, 45, 50), (52, 52, 56), (63, 62, 66), (73, 72, 72), (79, 79, 79), (89, 88, 88)]
# bone: the bone item's creams and the skeleton face's greys
BONE, BONE_LIGHT, BONE_DARK, BONE_SHADE = (232, 229, 210), (252, 251, 237), (203, 198, 165), (123, 126, 107)
SOCKET = (46, 46, 46)
# the fire charge: the dark crust and the glowing cracks
CRUST = [(31, 22, 21), (40, 30, 28), (55, 42, 40), (81, 73, 49)]
GLOW = [(255, 200, 60), (255, 150, 30), (230, 100, 20)]


def draw_spark():
    n = 32
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    for y in range(n):
        for x in range(n):
            d = math.hypot(x + 0.5 - n / 2, y + 0.5 - n / 2) / (n / 2)
            star = max(0.0, 1.0 - d) ** 1.6 + max(0.0, 1.0 - min(abs(x + 0.5 - n / 2), abs(y + 0.5 - n / 2)) / 1.6) * max(0.0, 1.0 - d) * 0.7
            a = max(0.0, min(1.0, star))
            im.putpixel((x, y), (255, int(120 + 135 * a), int(30 + 150 * a * a), int(255 * a)))
    return im


SKULL = [  # 16 x 16: a skull over two crossed bones (B bone, L light, D shade, S socket, . plate)
    "................",
    "....LLLLLLLL....",
    "...LBBBBBBBBL...",
    "..LBBBBBBBBBBD..",
    "..LBBBBBBBBBBD..",
    "..LBSSSBBSSSBD..",
    "..LBSSSBBSSSBD..",
    "..LBSSSBBSSSBD..",
    "...BBBBSSBBBD...",
    "....BBBSSBBD....",
    "....BBBBBBBD....",
    "....BLBLBLBD....",
    "..BB.DDDDDD.BB..",
    ".BBB........BBB.",
    ".LB..........BL.",
    "................",
]


def plate(hot, rnd):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    base = CRUST if hot else IRON
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), rnd.choice(base[:3]) + (255,))
    col = {"B": BONE, "L": BONE_LIGHT, "D": BONE_DARK, "S": (255, 150, 30) if hot else SOCKET}
    for y, row in enumerate(SKULL):
        for x, c in enumerate(row):
            if c in col:
                im.putpixel((x, y), col[c] + (255,))
    return im


def iron(hot, rnd):
    """16 x 16 iron: the anvil's grain (long strokes across the roll), or the fire charge's crust with glowing cracks."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    if not hot:
        for y in range(16):
            row = rnd.choice(IRON[1:5])
            for x in range(16):
                v = row if rnd.random() < 0.75 else rnd.choice(IRON)
                im.putpixel((x, y), v + (255,))
        for x, y in ((3, 4), (4, 4), (3, 5)):                              # a glint
            im.putpixel((x, y), IRON[5] + (255,))
        return im
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), rnd.choice(CRUST[:3]) + (255,))
    for _ in range(6):                                                      # cracks that wander across
        x, y = rnd.randrange(16), rnd.randrange(16)
        for _ in range(rnd.randint(4, 7)):
            im.putpixel((x % 16, y % 16), rnd.choice(GLOW) + (255,))
            x += rnd.choice((-1, 0, 1, 1))
            y += rnd.choice((-1, 0, 1))
    return im


def belt(hot, rnd):
    """16 x 8: the steel belt round the middle: lighter, with rivets."""
    im = Image.new("RGBA", (16, 8), (0, 0, 0, 255))
    for y in range(8):
        for x in range(16):
            c = rnd.choice(IRON[3:]) if not hot else rnd.choice(CRUST[1:])
            if y in (0, 7):
                c = IRON[1] if not hot else CRUST[0]
            im.putpixel((x, y), c + (255,))
    for x in (2, 6, 10, 14):
        im.putpixel((x, 3), IRON[5] + (255,) if not hot else GLOW[1] + (255,))
        im.putpixel((x, 4), IRON[2] + (255,) if not hot else GLOW[2] + (255,))
    return im


def collar(hot, rnd):
    im = Image.new("RGBA", (16, 8), (0, 0, 0, 255))
    for y in range(8):
        for x in range(16):
            c = rnd.choice(IRON[:3]) if not hot else rnd.choice(CRUST[:2])
            if y == 0:
                c = IRON[4] if not hot else GLOW[2]
            im.putpixel((x, y), c + (255,))
    return im


def rope(hot):
    """16 x 8: twisted rope in diagonal bands."""
    im = Image.new("RGBA", (16, 8), (0, 0, 0, 255))
    a, b = ((150, 124, 78), (112, 90, 56)) if not hot else ((205, 120, 40), (150, 70, 25))
    for y in range(8):
        for x in range(16):
            im.putpixel((x, y), (a if (x + y) % 4 < 2 else b) + (255,))
    return im


def tip(hot):
    """16 x 8: the burnt end of the fuse."""
    im = Image.new("RGBA", (16, 8), (30, 24, 20, 255))
    if hot:
        for x in range(16):
            for y in range(8):
                im.putpixel((x, y), GLOW[(x + y) % 3] + (255,))
    return im


def draw_texture(hot):
    rnd = random.Random(21 if hot else 12)
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    im.paste(iron(hot, rnd), (0, 0))
    im.paste(plate(hot, rnd), (16, 0))
    im.paste(belt(hot, rnd), (0, 16))
    im.paste(collar(hot, rnd), (0, 24))
    im.paste(rope(hot), (16, 16))
    im.paste(tip(hot), (16, 24))
    return im


def main():
    os.makedirs(TEX, exist_ok=True)
    draw_texture(False).save(os.path.join(TEX, "bomb_iron.png"))
    draw_texture(True).save(os.path.join(TEX, "bomb_hot.png"))
    os.makedirs(os.path.dirname(SPARK), exist_ok=True)
    draw_spark().save(SPARK)
    os.makedirs(os.path.dirname(FUSE), exist_ok=True)
    with open(FUSE, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"tip": list(TIP), "spark_size": SPARK_SIZE, "ground": GROUND}, f, indent=2)
    if not os.path.exists(BLEND):
        os.makedirs(os.path.dirname(BLEND), exist_ok=True)
        exe = ebm.find_blender()
        if not exe:
            sys.exit("Blender not found (set BLENDER to its blender.exe): cannot create " + BLEND)
        subprocess.run([exe, "--background", "--factory-startup", "--python", os.path.join("tools", "blender_create_bomb.py"), "--", BLEND], check=True)
    ebm.export_all()
    print("bomb ok")


if __name__ == "__main__":
    main()
