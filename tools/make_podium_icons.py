"""
The podium node items' inventory pictures (16 x 16, stand-ins): a gold, silver or bronze step with its number.

  python tools/make_podium_icons.py

Writes the layered krita/items/podium_<place>_node.kra (a modder's template; tools/build_art.py flattens them into textures/item/podium_<place>_node.png).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

COLOURS = {1: (235, 190, 40), 2: (195, 205, 215), 3: (190, 120, 60)}
DIGITS = {1: ["010", "110", "010", "010", "111"], 2: ["111", "001", "111", "100", "111"], 3: ["111", "001", "111", "001", "111"]}


def draw(place):
    sheet = kra_layers.Sheet(16, 16)
    rgb = COLOURS[place]
    dark = tuple(int(c * 0.6) for c in rgb) + (255,)
    px = sheet.layer("step")
    for y in range(4, 14):
        for x in range(2, 14):
            px[x, y] = rgb + (255,)
    px = sheet.layer("edge")
    for x in range(2, 14):
        px[x, 4] = dark
        px[x, 13] = dark
    for y in range(4, 14):
        px[2, y] = dark
        px[13, y] = dark
    px = sheet.layer("number")
    for r, row in enumerate(DIGITS[place]):
        for c, ch in enumerate(row):
            if ch == "1":
                px[6 + c * 2, 6 + r] = (30, 30, 30, 255)
                px[7 + c * 2, 6 + r] = (30, 30, 30, 255)
    return sheet


def main():
    inputs = kra_stamp.inputs_for(__file__)
    for place in COLOURS:
        kra_layers.write_if_stale("krita/items/podium_%d_node.kra" % place, draw(place), inputs)
    print("podium icons ok")


if __name__ == "__main__":
    main()
