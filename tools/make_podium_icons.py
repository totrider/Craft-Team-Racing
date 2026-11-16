"""
The podium node items' inventory pictures (16 x 16, stand-ins): a gold, silver or bronze step with its number.

  python tools/make_podium_icons.py
"""
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "ctrcraft", "textures", "item")
COLOURS = {1: (235, 190, 40), 2: (195, 205, 215), 3: (190, 120, 60)}
DIGITS = {1: ["010", "110", "010", "010", "111"], 2: ["111", "001", "111", "100", "111"], 3: ["111", "001", "111", "001", "111"]}


def main():
    for place, rgb in COLOURS.items():
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        dark = tuple(int(c * 0.6) for c in rgb) + (255,)
        for y in range(4, 14):
            for x in range(2, 14):
                px[x, y] = rgb + (255,)
        for x in range(2, 14):
            px[x, 4] = dark
            px[x, 13] = dark
        for y in range(4, 14):
            px[2, y] = dark
            px[13, y] = dark
        for r, row in enumerate(DIGITS[place]):
            for c, ch in enumerate(row):
                if ch == "1":
                    px[6 + c * 2, 6 + r] = (30, 30, 30, 255)
                    px[7 + c * 2, 6 + r] = (30, 30, 30, 255)
        img.save(os.path.join(OUT, "podium_%d_node.png" % place))
    print("written")


if __name__ == "__main__":
    main()
