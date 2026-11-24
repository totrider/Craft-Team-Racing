"""
The track music sheet item's inventory picture (16 x 16, stand-in): a sheet of paper with a note.

  python tools/make_music_sheet_icon.py

Writes the layered krita/items/track_music_sheet.kra (a modder's template; tools/build_art.py flattens it into textures/item/track_music_sheet.png).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

KRA = "krita/items/track_music_sheet.kra"


def draw():
    sheet = kra_layers.Sheet(16, 16)
    paper, edge, ink = (240, 232, 205, 255), (110, 90, 60, 255), (40, 40, 60, 255)
    px = sheet.layer("paper")
    for y in range(1, 15):
        for x in range(2, 14):
            px[x, y] = paper
    px = sheet.layer("edge")
    for x in range(2, 14):
        px[x, 1] = edge
        px[x, 14] = edge
    for y in range(1, 15):
        px[2, y] = edge
        px[13, y] = edge
    px = sheet.layer("staff lines")
    for y in (4, 6, 8, 10):
        for x in range(4, 12):
            px[x, y] = (200, 190, 160, 255)
    px = sheet.layer("quaver")                       # stem, head and flag
    for y in range(4, 11):
        px[9, y] = ink
    for x, y in ((6, 10), (7, 10), (8, 10), (6, 11), (7, 11), (8, 11), (7, 12)):
        px[x, y] = ink
    px[10, 4] = ink
    px[11, 5] = ink
    px[11, 6] = ink
    return sheet


def main():
    kra_layers.write_if_stale(KRA, draw(), kra_stamp.inputs_for(__file__))
    print("music sheet icon ok")


if __name__ == "__main__":
    main()
