"""
The track music sheet item's inventory picture (16 x 16, stand-in): a sheet of paper with a note.

  python tools/make_music_sheet_icon.py
"""
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "ctrcraft", "textures", "item", "track_music_sheet.png")


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    paper, edge, ink = (240, 232, 205, 255), (110, 90, 60, 255), (40, 40, 60, 255)
    for y in range(1, 15):
        for x in range(2, 14):
            px[x, y] = paper
    for x in range(2, 14):
        px[x, 1] = edge
        px[x, 14] = edge
    for y in range(1, 15):
        px[2, y] = edge
        px[13, y] = edge
    for y in (4, 6, 8, 10):
        for x in range(4, 12):
            px[x, y] = (200, 190, 160, 255)
    # a quaver: stem, head and flag
    for y in range(4, 11):
        px[9, y] = ink
    for x, y in ((6, 10), (7, 10), (8, 10), (6, 11), (7, 11), (8, 11), (7, 12)):
        px[x, y] = ink
    px[10, 4] = ink
    px[11, 5] = ink
    px[11, 6] = ink
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("written", OUT)


if __name__ == "__main__":
    main()
