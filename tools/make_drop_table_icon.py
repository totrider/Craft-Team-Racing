"""
The drop table item's inventory picture (16 x 16, stand-in): a green book with a die on the cover.

  python tools/make_drop_table_icon.py

Writes the layered krita/items/drop_table.kra (a modder's template; tools/build_art.py flattens it into textures/item/drop_table.png).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

KRA = "krita/items/drop_table.kra"


def draw():
    sheet = kra_layers.Sheet(16, 16)
    cover, dark, page, edge = (45, 150, 70, 255), (25, 95, 45, 255), (240, 235, 220, 255), (15, 50, 25, 255)
    px = sheet.layer("cover")
    for y in range(1, 15):
        for x in range(2, 14):
            px[x, y] = cover
    px = sheet.layer("spine")
    for y in range(1, 15):
        px[2, y] = edge
        px[3, y] = dark
    px = sheet.layer("page edge")
    for y in range(1, 15):
        px[13, y] = page
    px = sheet.layer("top and bottom edge")
    for x in range(2, 14):
        px[x, 1] = edge
        px[x, 14] = edge
    px = sheet.layer("die")                          # white with pips
    for y in range(5, 11):
        for x in range(6, 12):
            px[x, y] = (250, 250, 250, 255)
    px = sheet.layer("pips")
    for x, y in ((7, 6), (10, 6), (8, 7), (9, 8), (7, 9), (10, 9)):
        px[x, y] = (30, 30, 30, 255)
    return sheet


def main():
    kra_layers.write_if_stale(KRA, draw(), kra_stamp.inputs_for(__file__))
    print("drop table icon ok")


if __name__ == "__main__":
    main()
