"""
The rulebook's inventory picture (16 x 16, stand-in): a book with a red cover, a chequered flag on it and a white page edge.

  python tools/make_rulebook_icon.py

Writes the layered krita/items/rulebook.kra (a modder's template; tools/build_art.py flattens it into textures/item/rulebook.png).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

KRA = "krita/items/rulebook.kra"


def draw():
    sheet = kra_layers.Sheet(16, 16)
    cover, dark, page, edge = (190, 40, 40, 255), (120, 20, 25, 255), (240, 235, 220, 255), (60, 15, 20, 255)
    px = sheet.layer("cover")
    for y in range(1, 15):
        for x in range(2, 14):
            px[x, y] = cover
    px = sheet.layer("spine")
    for y in range(1, 15):
        px[2, y] = dark
        px[3, y] = dark
    px = sheet.layer("page edge")
    for y in range(1, 15):
        px[13, y] = page
    px = sheet.layer("edge")                         # the top and bottom rows and the spine's outer column
    for x in range(2, 14):
        px[x, 1] = edge
        px[x, 14] = edge
    for y in range(1, 15):
        px[2, y] = edge
    px = sheet.layer("chequered flag")
    for fy in range(3):
        for fx in range(4):
            px[6 + fx, 4 + fy] = (255, 255, 255, 255) if (fx + fy) % 2 == 0 else (20, 20, 20, 255)
    px = sheet.layer("flag pole")
    for y in range(4, 11):
        px[5, y] = (230, 230, 230, 255)
    return sheet


def main():
    kra_layers.write_if_stale(KRA, draw(), kra_stamp.inputs_for(__file__))
    print("rulebook icon ok")


if __name__ == "__main__":
    main()
