"""
The viewing console: a full block with a monitor on one face and the console's details round the others (see spectate/ViewingConsoleBlock).

  * the textures: five 16 x 16 pictures drawn in layers and written as krita/blocks/viewing_console_<slot>.kra (tools/build_art.py flattens them into the PNGs):
        front   the monitor face (the screen shows a track with two karts on it, buttons and a slider under it)
        side    the left and right faces and the underside
        back    the rear face
        top     the keys
        parts   small coloured swatches the buttons, knobs and the joystick are drawn from
  * the model: blockbench/spectate/viewing_console.bbmodel is the SOURCE. It is written here only when it is missing; after that it is only read, so edits made in Blockbench survive.
        group "base"     the full block: front (the monitor) on the north face, back on the south face
        group "details"  what stands out of the block by a pixel or two: buttons on top and on both sides, a joystick
    The JSON model, the blockstates (the monitor faces the way the player faces when placing it: the file has it on the north face) and the item model are written from it.

  python tools/make_viewing_console.py
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "craftteamracing")
BB = os.path.join(ROOT, "blockbench", "spectate", "viewing_console.bbmodel")
SLOTS = ["front", "side", "back", "top", "parts"]


def png(slot):
    return os.path.join(ASSETS, "textures", "block", "viewing_console_%s.png" % slot)


# ---- the pictures ----

METAL = (66, 70, 80, 255)
METAL_DARK = (40, 42, 50, 255)
METAL_LIGHT = (96, 102, 114, 255)


def panel(sheet, fill, name="panel"):
    """The metal plate every face starts from: a darker edge and a lighter inner corner."""
    px = sheet.layer(name)
    for y in range(16):
        for x in range(16):
            px[x, y] = METAL_DARK if x in (0, 15) or y in (0, 15) else fill
    for i in range(1, 15):
        px[i, 1] = METAL_LIGHT
        px[1, i] = METAL_LIGHT
    return px


def front():
    sheet = kra_layers.Sheet(16, 16)
    panel(sheet, (58, 62, 72, 255))
    bezel = sheet.layer("bezel")
    for y in range(1, 12):
        for x in range(1, 15):
            bezel[x, y] = (18, 20, 24, 255)
    screen = sheet.layer("screen")
    for y in range(2, 11):
        for x in range(2, 14):
            screen[x, y] = (8, 36, 46, 255) if y % 2 == 0 else (6, 29, 38, 255)
    track = sheet.layer("track")
    for i in range(40):                          # the track: an oval
        a = i / 40 * 2 * math.pi
        track[int(round(8 + 4.4 * math.cos(a))), int(round(6 + 2.6 * math.sin(a)))] = (60, 220, 230, 255)
    track[12, 6] = (236, 60, 50, 255)           # two karts
    track[6, 4] = (250, 210, 60, 255)
    track[3, 3] = (60, 220, 230, 255)           # the finish line
    buttons = sheet.layer("buttons")
    for x0, c in ((2, (214, 52, 52, 255)), (5, (70, 200, 90, 255)), (8, (240, 180, 50, 255))):
        for dx in range(2):
            for dy in range(2):
                buttons[x0 + dx, 12 + dy] = c
        buttons[x0, 12] = tuple(min(255, v + 40) for v in c[:3]) + (255,)
    for x in range(11, 14):                      # the slider
        buttons[x, 13] = (24, 26, 30, 255)
    buttons[12, 12] = (180, 186, 198, 255)
    buttons[12, 13] = (150, 156, 168, 255)
    return sheet


def side():
    sheet = kra_layers.Sheet(16, 16)
    panel(sheet, METAL)
    vents = sheet.layer("vents")
    for y in (4, 6, 8):
        for x in range(4, 12):
            vents[x, y] = (28, 30, 36, 255)
            vents[x, y + 1] = (88, 94, 106, 255)
    screws = sheet.layer("screws")
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        screws[x, y] = (128, 134, 146, 255)
    return sheet


def back():
    sheet = kra_layers.Sheet(16, 16)
    panel(sheet, (60, 64, 74, 255))
    port = sheet.layer("port")
    for y in range(4, 10):
        for x in range(5, 11):
            port[x, y] = (22, 24, 28, 255)
    for x, y in ((6, 5), (8, 5), (6, 7), (8, 7), (9, 8)):
        port[x, y] = (204, 172, 60, 255)
    vents = sheet.layer("vents")
    for y in (11, 13):
        for x in range(3, 13):
            vents[x, y] = (28, 30, 36, 255)
    return sheet


def top():
    sheet = kra_layers.Sheet(16, 16)
    panel(sheet, (50, 54, 62, 255))
    slot = sheet.layer("slider slot")
    for x in range(10, 15):
        slot[x, 3] = (22, 24, 28, 255)
    keys = sheet.layer("keys")
    for y0 in (6, 9, 12):                        # the keys, three rows of three
        for x0 in (2, 5, 8):
            for dx in range(2):
                for dy in range(2):
                    keys[x0 + dx, y0 + dy] = (92, 98, 112, 255)
            keys[x0, y0] = (132, 138, 152, 255)
    return sheet


SWATCHES = [((214, 52, 52), "red"), ((70, 200, 90), "green"), ((240, 180, 50), "amber"), ((150, 156, 168), "light"), ((84, 88, 98), "dark")]


def parts():
    sheet = kra_layers.Sheet(16, 16)
    px = sheet.layer("swatches")
    for i, (c, _) in enumerate(SWATCHES):
        for dx in range(2):
            for dy in range(2):
                px[i * 2 + dx, dy] = c + (255,)
        px[i * 2, 0] = tuple(min(255, v + 40) for v in c) + (255,)
    return sheet


# ---- the model ----

def swatch_uv(name):
    i = [n for _, n in SWATCHES].index(name)
    return [i * 2, 0, i * 2 + 2, 2]


def all_faces(slot, uv, cull=False):
    return {f: dict({"uv": uv, "texture": "#" + slot}, **({"cullface": f} if cull else {})) for f in bbmodel.FACES}


def small(x0, y0, z0, x1, y1, z1, colour, name):
    return {"name": name, "from": [x0, y0, z0], "to": [x1, y1, z1], "faces": all_faces("parts", swatch_uv(colour))}


def starting_groups():
    full = [0, 0, 16, 16]
    base = {"name": "console", "from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "north": {"uv": full, "texture": "#front", "cullface": "north"},
        "south": {"uv": full, "texture": "#back", "cullface": "south"},
        "east": {"uv": full, "texture": "#side", "cullface": "east"},
        "west": {"uv": full, "texture": "#side", "cullface": "west"},
        "up": {"uv": full, "texture": "#top", "cullface": "up"},
        "down": {"uv": full, "texture": "#side", "cullface": "down"}}}
    details = [
        small(2, 16, 3, 4, 17, 5, "red", "button_red"), small(5, 16, 3, 7, 17, 5, "green", "button_green"), small(8, 16, 3, 10, 17, 5, "amber", "button_amber"),
        small(11, 16, 3, 14, 17, 4, "light", "slider"),
        small(13, 16, 11, 15, 18, 13, "dark", "joystick_stem"), small(12.5, 18, 10.5, 15.5, 20, 13.5, "red", "joystick_ball"),
        small(16, 9, 3, 17, 11, 5, "green", "east_button_1"), small(16, 9, 6, 17, 11, 8, "amber", "east_button_2"),
        small(-1, 9, 3, 0, 11, 5, "amber", "west_button_1"), small(-1, 9, 6, 0, 11, 8, "red", "west_button_2"),
    ]
    return [("base", [base]), ("details", details)]


def ensure_bbmodel():
    if not os.path.exists(BB):
        bbmodel.write(BB, "viewing_console", {s: png(s) for s in SLOTS}, starting_groups(), gui_light_front=False,
                      comment="The viewing console. The monitor (texture slot front) is on the NORTH face: the blockstate turns it to the way the player faces when placing. "
                              "base is the full block; details stands out of it by a pixel or two. Slots: front, side (left, right, underside), back, top, parts (2 x 2 colour swatches along the top row).")
    return bbmodel.read(BB)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)


def main():
    inputs = kra_stamp.inputs_for(__file__)
    for slot, fn in (("front", front), ("side", side), ("back", back), ("top", top), ("parts", parts)):
        kra_layers.write_if_stale("krita/blocks/viewing_console_%s.kra" % slot, fn(), inputs)
    model = ensure_bbmodel()
    textures = {s: "craftteamracing:block/viewing_console_%s" % s for s in SLOTS}
    textures["particle"] = textures["side"]
    out = {"textures": textures, "elements": bbmodel.elements(model)}
    if model.get("display"):
        out["display"] = model["display"]
    write(os.path.join(ASSETS, "models", "block", "viewing_console.json"), out)
    variants = {"facing=%s" % d: {"model": "craftteamracing:block/viewing_console", **({"y": y} if y else {})}
                for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
    write(os.path.join(ASSETS, "blockstates", "viewing_console.json"), {"variants": variants})
    write(os.path.join(ASSETS, "models", "item", "viewing_console.json"), {"parent": "craftteamracing:block/viewing_console"})
    print("written")


if __name__ == "__main__":
    main()
