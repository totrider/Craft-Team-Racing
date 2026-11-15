"""
The drop table item's inventory picture (16 x 16, stand-in): a green book with a die on the cover.

  python tools/make_drop_table_icon.py
"""
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "ctrcraft", "textures", "item", "drop_table.png")


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cover, dark, page, edge = (45, 150, 70, 255), (25, 95, 45, 255), (240, 235, 220, 255), (15, 50, 25, 255)
    for y in range(1, 15):
        for x in range(2, 14):
            px[x, y] = cover
        px[2, y] = edge
        px[3, y] = dark
        px[13, y] = page
    for x in range(2, 14):
        px[x, 1] = edge
        px[x, 14] = edge
    # a die: white with pips
    for y in range(5, 11):
        for x in range(6, 12):
            px[x, y] = (250, 250, 250, 255)
    for x, y in ((7, 6), (10, 6), (8, 7), (9, 8), (7, 9), (10, 9)):
        px[x, y] = (30, 30, 30, 255)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("written", OUT)


if __name__ == "__main__":
    main()
