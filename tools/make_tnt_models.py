"""
The TNT power-up's crate and the charged one (the "Nitro", here creeper themed): the jar's stand-in models.

  python tools/make_tnt_models.py

The TNT keeps the Minecraft TNT block's own textures (as decided with the author) and gets what makes it a power-up and not a placed block: a steel frame, a rivetted corner
block at each of the eight corners and a strap along each of the twelve edges. The charged one is a creeper crate: green with a creeper's face on its four sides and the same steel.
The models are Blockbench's: blockbench/pickups/tnt.bbmodel and nitro.bbmodel are the source (written once from the shapes below if missing, then they are yours; this script only
reads them). Writes models/item/pickup_tnt.json and pickup_nitro.json, and the layered krita/items/tnt_metal.kra (16 x 16: the steel plate, the
top left 4 x 4 is what the frame's pieces show), creeper_face.kra and creeper_skin.kra (16 x 16; the creeper's, from Minecraft's creeper head) (a modder's templates;
tools/build_art.py flattens them into textures/item/*.png). Origin: the bottom centre of the block; 16 px is one block.
"""
import json
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "craftteamracing")
TEX = os.path.join(ASSETS, "textures", "item")
BB = os.path.join("blockbench", "pickups")


def metal_sheet():
    """Brushed steel with a darker rim and a rivet; the frame pieces use its top left 4 x 4. Layers: the steel, the rim (the top left 4 x 4's edge, darkened) and the rivet."""
    rnd = random.Random(7)
    sheet = kra_layers.Sheet(16, 16)
    steel, rim, rivet = sheet.layer("brushed steel"), sheet.layer("rim"), sheet.layer("rivet")
    for y in range(16):
        for x in range(16):
            v = 150 + rnd.randint(-8, 8) + (6 if y % 4 == 1 else 0)
            steel[x, y] = (v, v + 4, v + 14, 255)
    for i in range(4):
        for x, y in ((i, 0), (i, 3), (0, i), (3, i)):
            c = rim[x, y] if rim[x, y][3] else steel[x, y]          # a corner is darkened twice: it is in the rim twice
            rim[x, y] = (c[0] - 55, c[1] - 55, c[2] - 50, 255)
    for x, y in ((1, 1), (2, 1), (1, 2), (2, 2)):
        rivet[x, y] = (210, 214, 226, 255) if (x, y) == (1, 1) else (96, 100, 112, 255)
    return sheet


# The creeper's face, taken from Minecraft's creeper head (an 8 x 8 face): eyes, a nose block, a frown with two feet. X is black. Here every pixel is two wide and two tall, on a 16 x 16 crate face.
CREEPER_FACE = [
    "........",
    "........",
    ".XX..XX.",
    ".XX..XX.",
    "...XX...",
    "..XXXX..",
    "..XXXX..",
    "..X..X..",
]
# the head's greens, from the dark rim round the features through the mid greens to the pale flecks (colours only; the mottling is drawn here)
GREENS = [((76, 179, 65), 8), ((103, 215, 85), 6), ((60, 150, 52), 5), ((133, 220, 116), 3), ((30, 78, 24), 2), ((165, 205, 159), 1), ((19, 169, 15), 1)]
BLACK = (14, 18, 14)


def mottle(rnd, n):
    pool = [c for c, w in GREENS for _ in range(w)]
    return [[rnd.choice(pool) for _ in range(n)] for _ in range(n)]


def creeper_sheets():
    """The creeper crate's pictures as layered sheets: the face (side, 16 x 16) and the skin (top and bottom): the head's mottled greens at the crate's resolution, a dark halo round
    the features as the head has, the face two pixels to the head's one. Layers of the face: the head, the halo round the features and the features."""
    rnd = random.Random(11)
    face, skin = kra_layers.Sheet(16, 16), kra_layers.Sheet(16, 16)
    head, halo, features = face.layer("head"), face.layer("halo"), face.layer("features")
    skin_px = skin.layer("mottled greens")
    cells = mottle(rnd, 8)
    skin_cells = mottle(rnd, 8)
    dark = lambda c, k: tuple(int(v * k) for v in c)
    for fy in range(8):
        for fx in range(8):
            c = cells[fy][fx]
            near = any(0 <= fy + dy < 8 and 0 <= fx + dx < 8 and CREEPER_FACE[fy + dy][fx + dx] == "X" for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            target = head
            if CREEPER_FACE[fy][fx] == "X":
                c = BLACK
                target = features
            elif near:
                c = dark(c, 0.55)
                target = halo
            for dy in (0, 1):
                for dx in (0, 1):
                    jitter = 1.0 + rnd.choice((-0.07, 0.0, 0.0, 0.07)) if c != BLACK else 1.0          # the head's pixels are flat: a whisper of grain at the finer grid
                    target[fx * 2 + dx, fy * 2 + dy] = tuple(min(255, int(v * jitter)) for v in c) + (255,)
            sc = skin_cells[fy][fx]
            for dy in (0, 1):
                for dx in (0, 1):
                    jitter = 1.0 + rnd.choice((-0.07, 0.0, 0.0, 0.07))
                    skin_px[fx * 2 + dx, fy * 2 + dy] = tuple(min(255, int(v * jitter)) for v in sc) + (255,)
    return face, skin


def draw_creeper():
    """The creeper crate's face and skin as flat 16 x 16 pictures (the layered sheets flattened)."""
    face, skin = creeper_sheets()
    return face.flatten(), skin.flatten()


def box(frm, to, faces):
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}


def metal_faces(slot="#metal"):
    f = {"uv": [0, 0, 4, 4], "texture": slot}
    return {d: dict(f) for d in bbmodel.FACES}


def frame():
    """Eight corner blocks (4.5 px, a little proud of the crate) and twelve straps between them, in steel."""
    lo, hi = (-0.4, 4.1), (11.9, 16.4)
    out = []
    for x in lo + hi if False else (lo, hi):
        for y in (lo, hi):
            for z in (lo, hi):
                out.append(box((x[0], y[0], z[0]), (x[1], y[1], z[1]), metal_faces()))
    s = (-0.3, 0.9)
    t = (15.1, 16.3)
    mid = (4.1, 11.9)
    for a in (s, t):
        for b in (s, t):
            out.append(box((mid[0], a[0], b[0]), (mid[1], a[1], b[1]), metal_faces()))     # along x
            out.append(box((a[0], mid[0], b[0]), (a[1], mid[1], b[1]), metal_faces()))     # along y
            out.append(box((a[0], b[0], mid[0]), (a[1], b[1], mid[1]), metal_faces()))     # along z
    return out


def body_tnt():
    side = lambda d: {"uv": [0, 0, 16, 16], "texture": d}
    return box((0, 0, 0), (16, 16, 16), {"north": side("#side"), "south": side("#side"), "east": side("#side"), "west": side("#side"), "up": side("#top"), "down": side("#bottom")})


def body_creeper():
    side = lambda d: {"uv": [0, 0, 16, 16], "texture": d}
    return box((0, 0, 0), (16, 16, 16), {"north": side("#face"), "south": side("#face"), "east": side("#face"), "west": side("#face"), "up": side("#skin"), "down": side("#skin")})


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.6, 0.6, 0.6]}, "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}}


def embed(path, files):
    """Refresh the copies of the generated pictures that the bbmodel carries in its texture slots (so Blockbench shows the current ones)."""
    import base64
    m = json.load(open(path, encoding="utf-8"))
    for t in m["textures"]:
        if t.get("name") in files:
            t["source"] = "data:image/png;base64," + base64.b64encode(open(files[t["name"]], "rb").read()).decode()
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(m, f, indent=1)


def main():
    metal, face, skin = (os.path.join(TEX, n + ".png") for n in ("tnt_metal", "creeper_face", "creeper_skin"))
    inputs = kra_stamp.inputs_for(__file__)
    face_sheet, skin_sheet = creeper_sheets()
    for kra, sheet in (("krita/items/tnt_metal.kra", metal_sheet()), ("krita/items/creeper_face.kra", face_sheet), ("krita/items/creeper_skin.kra", skin_sheet)):
        kra_layers.write_if_stale(kra, sheet, inputs)
        kra_layers.export_png(kra)                           # the PNGs now, not at the end of the build: the nitro bbmodel embeds them
    os.makedirs(BB, exist_ok=True)
    jobs = {
        "tnt": ({"side": None, "top": None, "bottom": None, "metal": metal}, [("body", [body_tnt()]), ("frame", frame())],
                {"side": "minecraft:block/tnt_side", "top": "minecraft:block/tnt_top", "bottom": "minecraft:block/tnt_bottom", "metal": "craftteamracing:item/tnt_metal", "particle": "craftteamracing:item/tnt_metal"}),
        "nitro": ({"face": face, "skin": skin, "metal": metal}, [("body", [body_creeper()]), ("frame", frame())],
                  {"face": "craftteamracing:item/creeper_face", "skin": "craftteamracing:item/creeper_skin", "metal": "craftteamracing:item/tnt_metal", "particle": "craftteamracing:item/creeper_skin"}),
    }
    for name, (slots, groups, textures) in jobs.items():
        path = os.path.join(BB, name + ".bbmodel")
        if not os.path.exists(path):
            bbmodel.write(path, name, slots, groups, display=display(), gui_light_front=False,
                          comment="Stand-in %s crate: a 16 px cube (group body) in a steel frame (group frame: eight corner blocks and twelve straps, a little proud of the cube, "
                                  "all showing the top left 4 x 4 of the steel texture). See tools/make_tnt_models.py." % name)
        model = bbmodel.read(path)
        out = os.path.join(ASSETS, "models", "item", "pickup_" + name + ".json")
        with open(out, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"textures": textures, "elements": bbmodel.elements(model), "display": model["display"] or display()}, f, indent=2)
    embed(os.path.join(BB, "nitro.bbmodel"), {"face.png": face, "skin.png": skin})
    print("tnt models written")


if __name__ == "__main__":
    main()
