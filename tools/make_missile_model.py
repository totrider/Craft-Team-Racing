"""
The Tracking Missile: the jar's stand-in 3D model and its texture.

  python tools/make_missile_model.py

The model is Blockbench's: blockbench/pickups/missile.bbmodel is the source (written once from the shape below if it is missing, then it is yours; this script only reads it). Writes
src/main/resources/assets/ctrcraft/models/item/pickup_missile.json (the missile renderer fetches that model) and, once if missing, textures/item/missile.png (a 16 x 16 palette of
flat 4 x 4 cells, yours to repaint). The model's middle (8, 8, 8) is the missile's middle; its nose points north (-z), the way the game turns it to face where it flies. It is 12 px
long and 2.5 px through, slim like the original's: a white body with a red band, a stepped white nose with a red tip, two thin green fins (one flat, one upright) and a dark nozzle.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "ctrcraft")
TEX = os.path.join(ASSETS, "textures", "item", "missile.png")
OUT = os.path.join(ASSETS, "models", "item", "pickup_missile.json")
BB = os.path.join("blockbench", "pickups", "missile.bbmodel")

PALETTE = {
    "body": (0, 0, (240, 240, 244)),
    "band": (4, 0, (214, 52, 48)),
    "nose": (8, 0, (70, 72, 84)),
    "tip": (12, 0, (255, 150, 40)),
    "fin": (0, 4, (60, 170, 70)),
    "nozzle": (4, 4, (40, 40, 48)),
}


def draw_texture():
    im = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
    for _, (cx, cy, rgb) in PALETTE.items():
        for y in range(cy, cy + 4):
            for x in range(cx, cx + 4):
                im.putpixel((x, y), rgb + (255,))
    return im


def box(frm, to, colour):
    cx, cy, _ = PALETTE[colour]
    face = {"uv": [cx + 1, cy + 1, cx + 3, cy + 3], "texture": "#missile"}
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": {f: dict(face) for f in bbmodel.FACES}}


def parts():
    """Slim, like the original's: a 2.5 px body (a long thin rocket), a stepped white nose with a red tip, a red band, a dark nozzle and four thin green fins at the tail."""
    body = [
        box([6.75, 6.75, 6.0], [9.25, 9.25, 13.5], "body"),
        box([6.65, 6.65, 7.6], [9.35, 9.35, 8.6], "band"),
        box([7.0, 7.0, 13.5], [9.0, 9.0, 14.3], "nozzle"),
    ]
    nose = [
        box([7.0, 7.0, 4.2], [9.0, 9.0, 6.0], "body"),
        box([7.4, 7.4, 2.9], [8.6, 8.6, 4.2], "body"),
        box([7.75, 7.75, 1.8], [8.25, 8.25, 2.9], "band"),
    ]
    fins = [
        box([4.5, 7.8, 11.5], [11.5, 8.2, 13.8], "fin"),         # flat, across
        box([7.8, 4.5, 11.5], [8.2, 11.5, 13.8], "fin"),         # upright
    ]
    return {"body": body, "nose": nose, "fins": fins}


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.8, 0.8, 0.8]}, "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}}


def main():
    if not os.path.exists(TEX):
        os.makedirs(os.path.dirname(TEX), exist_ok=True)
        draw_texture().save(TEX)
    if not os.path.exists(BB):
        bbmodel.write(BB, "missile", {"missile": TEX}, list(parts().items()), display=display(), gui_light_front=False,
                      comment="Stand-in Tracking Missile: a 4 px square body with a red band, a stepped dark nose with an orange tip, a nozzle, and two green fins. Nose to -z; the "
                              "model's middle (8, 8, 8) is the missile's middle. Palette cells in textures/item/missile.png, see tools/make_missile_model.py.")
    model = bbmodel.read(BB)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"textures": {"missile": "ctrcraft:item/missile", "particle": "ctrcraft:item/missile"}, "elements": bbmodel.elements(model), "display": model["display"] or display()}, f, indent=2)
    # the pack's missile has its flame fans as a second model (drawn added to the screen); the jar has none (an empty model)
    with open(os.path.join(os.path.dirname(OUT), "pickup_missile_flame.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"textures": {"particle": "ctrcraft:item/missile"}, "elements": []}, f, indent=2)
    print("missile written")


if __name__ == "__main__":
    main()
