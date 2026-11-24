"""
The podium's confetti and streamer particle pictures (white shapes the particles colour): a small square and a long wavy ribbon.

  python tools/make_confetti.py

Writes the layered krita/particles/confetti.kra (8 x 8) and streamer.kra (16 x 16, a thin ribbon down the middle) (a modder's templates; tools/build_art.py flattens them into
textures/particle/confetti.png and streamer.png), and the two particle definitions.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "craftteamracing")


def main():
    os.makedirs(os.path.join(ASSETS, "particles"), exist_ok=True)
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    px = img.load()
    for y in range(1, 7):
        for x in range(1, 7):
            px[x, y] = (255, 255, 255, 255)
    inputs = kra_stamp.inputs_for(__file__)
    sheet = kra_layers.Sheet(8, 8)
    sheet.add("square", img)
    kra_layers.write_if_stale("krita/particles/confetti.kra", sheet, inputs)
    rib = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = rib.load()
    for y in range(0, 16):
        x0 = 6 + (1 if (y // 3) % 2 else 0)          # a slight wave along the ribbon
        for x in range(x0, x0 + 3):
            px[x, y] = (255, 255, 255, 255)
    sheet = kra_layers.Sheet(16, 16)
    sheet.add("ribbon", rib)
    kra_layers.write_if_stale("krita/particles/streamer.kra", sheet, inputs)
    for name in ("confetti", "streamer"):
        with open(os.path.join(ASSETS, "particles", name + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"textures": ["craftteamracing:" + name]}, f, indent=2)
    print("confetti pictures written")


if __name__ == "__main__":
    main()
