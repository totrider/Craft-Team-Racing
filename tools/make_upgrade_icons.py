"""
The kart upgrades' inventory pictures (16 x 16, stand-ins): offroad tires (a fat treaded tyre), a soft bumper (a padded bar), the floater and its obsidian
version, the paddle wheel, the low gravity module, the jump spring and the snow skis.

  python tools/make_upgrade_icons.py

Writes one layered file per icon, krita/items/<name>.kra (modder templates; tools/build_art.py flattens them into textures/item/<name>.png).
Each script step draws one layer, bottom to top in the order the picture used to be drawn.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402


def tires():
    sheet = kra_layers.Sheet(16, 16)
    body, tread, hub, hub_centre = (sheet.layer(n) for n in ("tyre", "tread", "hub", "hub centre"))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 7.2:
                body[x, y] = (35, 35, 38, 255)
            if d <= 7.2 and d > 5.2 and (x + y) % 3 == 0:
                tread[x, y] = (95, 95, 100, 255)
            if d <= 3.2:
                hub[x, y] = (170, 170, 175, 255)
            if d <= 1.2:
                hub_centre[x, y] = (80, 80, 85, 255)
    return sheet


def bumper():
    sheet = kra_layers.Sheet(16, 16)
    padding, padding_edge = sheet.layer("padding"), sheet.layer("padding edge")
    for y in range(5, 12):
        for x in range(1, 15):
            edge = x in (1, 14) or y in (5, 11)
            if edge:
                padding_edge[x, y] = (150, 20, 20, 255)
            else:
                padding[x, y] = (235, 60, 55, 255)
    px = sheet.layer("sheen")                            # the sheen of the padding
    for x in range(3, 13):
        px[x, 7] = (255, 130, 120, 255)
    px = sheet.layer("brackets")
    for x in (3, 12):
        for y in range(11, 14):
            px[x, y] = (110, 110, 115, 255)
    return sheet


FLOATER_LAYERS = ("red segments", "white segments", "rim")


def floater():
    """A ring buoy: red and white segments round a hole."""
    sheet = kra_layers.Sheet(16, 16)
    segments = (sheet.layer(FLOATER_LAYERS[0]), sheet.layer(FLOATER_LAYERS[1]))
    rim = sheet.layer(FLOATER_LAYERS[2])
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 3.0 <= d <= 7.3:
                seg = int((math.atan2(y - 7.5, x - 7.5) + 3.1416) / (6.2832 / 8)) % 2
                segments[seg][x, y] = (215, 45, 45, 255) if seg == 0 else (235, 235, 235, 255)
                if d >= 6.5 or d <= 3.8:
                    rim[x, y] = (110, 30, 30, 255) if seg == 0 else (150, 150, 155, 255)
    return sheet


def obsidian_floater():
    """The ring buoy in obsidian: violet-black and dark purple segments with a glowing orange rim."""
    src = floater()
    sheet = kra_layers.Sheet(16, 16)
    names = ("dark segments", "violet segments", "rim")
    for name, (_, img) in zip(names, src.layers):
        px = sheet.layer(name)
        sp = img.load()
        for y in range(16):
            for x in range(16):
                r, g, b, a = sp[x, y]
                if a == 0:
                    continue
                if r > 200 and g < 80:                      # the red segments
                    px[x, y] = (28, 18, 46, 255)
                elif r > 200:                               # the white segments
                    px[x, y] = (88, 52, 140, 255)
                elif r > 140 and g < 60:                    # the dark red edge
                    px[x, y] = (12, 8, 22, 255)
                else:
                    px[x, y] = (255, 120, 30, 255) if (x + y) % 4 == 0 else (50, 40, 70, 255)
    return sheet


def paddle():
    """A steamboat's paddle wheel: a rim, eight spokes and a wooden blade at the end of each, round an iron hub."""
    sheet = kra_layers.Sheet(16, 16)
    rim_px, blades_px = sheet.layer("rim"), sheet.layer("spokes and blades")
    cx = cy = 7.5
    wood, wood_dark, iron, rim = (176, 124, 70, 255), (112, 72, 38, 255), (120, 124, 136, 255), (150, 154, 166, 255)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if 6.2 <= d <= 7.5:
                rim_px[x, y] = rim                               # the rim
    for k in range(8):                                           # eight spokes with a blade each
        a = k * math.pi / 4 + math.pi / 8
        for t in range(2, 7):
            x, y = int(round(cx + math.cos(a) * t)), int(round(cy + math.sin(a) * t))
            blades_px[x, y] = wood_dark
        bx, by = cx + math.cos(a) * 6.8, cy + math.sin(a) * 6.8
        for ox in (-1, 0, 1):
            for oy in (-1, 0, 1):
                x, y = int(round(bx + ox)), int(round(by + oy))
                if 0 <= x < 16 and 0 <= y < 16 and math.hypot(x - cx, y - cy) > 5.4:
                    blades_px[x, y] = wood_dark if (ox == 1 and oy == 1) or (ox == -1 and oy == -1) else wood
    hub_px, glint = sheet.layer("hub"), sheet.layer("hub highlights")
    for y in range(6, 10):
        for x in range(6, 10):
            if math.hypot(x - cx, y - cy) <= 2.4:
                hub_px[x, y] = iron                              # the hub
    glint[7, 7] = glint[8, 8] = (160, 164, 176, 255)
    return sheet


def gravity():
    """A small violet box with a feather-light arrow pointing down."""
    sheet = kra_layers.Sheet(16, 16)
    box, box_edge = sheet.layer("box"), sheet.layer("box edge")
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 13)
            if edge:
                box_edge[x, y] = (70, 45, 120, 255)
            else:
                box[x, y] = (130, 100, 200, 255)
    head = sheet.layer("arrow head")
    for k in range(5):
        for x in range(7 - k, 9 + k):
            head[x, 5 + k] = (230, 225, 255, 255)
    shaft = sheet.layer("arrow shaft")
    for y in range(4, 7):
        shaft[7, y] = shaft[8, y] = (230, 225, 255, 255)
    return sheet


def spring():
    """A coil spring: steel loops on a plate."""
    sheet = kra_layers.Sheet(16, 16)
    plates, loops, ends = sheet.layer("plates"), sheet.layer("coil loops"), sheet.layer("coil ends")
    for x in range(3, 13):
        plates[x, 13] = plates[x, 14] = (90, 90, 98, 255)
        plates[x, 1] = plates[x, 2] = (90, 90, 98, 255)
    for k, y in enumerate(range(3, 13, 2)):
        for x in range(4, 12):
            loops[x, y] = (200, 200, 208, 255) if k % 2 == 0 else (140, 140, 150, 255)
        ends[4 if k % 2 == 0 else 11, y + 1] = (200, 200, 208, 255)
    return sheet


def snow_skis():
    """A pair of skis set side by side, seen from above at a slant: light blue boards with upturned tips, dark bindings and a white rim of snow."""
    sheet = kra_layers.Sheet(16, 16)
    for name, off in (("back ski", 0), ("front ski", 5)):
        px = sheet.layer(name)                                # one ski: the board, the snow on it and its binding
        for t in range(14):                                   # along the board, from the tail (bottom left) to the tip (top right)
            x = 1 + t + off // 2
            y = 14 - t + off // 2 - (1 if t > 11 else 0)      # the tip turns up
            for w in range(-1, 2):
                xx, yy = x + (w if t % 2 == 0 else 0), y + (0 if t % 2 == 0 else w)
                if 0 <= xx < 16 and 0 <= yy < 16:
                    px[xx, yy] = (120, 185, 225, 255) if w == 0 else (70, 130, 185, 255)
            if 0 <= x < 16 and 0 <= y - 1 < 16:
                px[x, y - 1] = (235, 245, 255, 255)               # the snow on top
        bx, by = 7 + off // 2, 8 + off // 2
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):          # the binding
            if 0 <= bx + dx < 16 and 0 <= by + dy < 16:
                px[bx + dx, by + dy] = (45, 40, 50, 255)
    return sheet


ICONS = {
    "offroad_tires": tires,
    "soft_bumper": bumper,
    "floater": floater,
    "paddle": paddle,
    "obsidian_floater": obsidian_floater,
    "low_gravity_module": gravity,
    "jump_spring": spring,
    "snow_skis": snow_skis,
}


def main():
    inputs = kra_stamp.inputs_for(__file__)
    for name, make in ICONS.items():
        kra_layers.write_if_stale("krita/items/%s.kra" % name, make(), inputs)
    print("upgrade icons ok")


if __name__ == "__main__":
    main()
