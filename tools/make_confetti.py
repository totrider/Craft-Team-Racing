"""
The podium's confetti and streamer particle pictures (white shapes the particles colour): a small square and a long wavy ribbon.

  python tools/make_confetti.py

Writes textures/particle/confetti.png (8 x 8) and streamer.png (16 x 16, a thin ribbon down the middle), and the two particle definitions.
"""
import json
import os

from PIL import Image

ASSETS = os.path.join("src", "main", "resources", "assets", "ctrcraft")


def main():
    os.makedirs(os.path.join(ASSETS, "textures", "particle"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "particles"), exist_ok=True)
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    px = img.load()
    for y in range(1, 7):
        for x in range(1, 7):
            px[x, y] = (255, 255, 255, 255)
    img.save(os.path.join(ASSETS, "textures", "particle", "confetti.png"))
    rib = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = rib.load()
    for y in range(0, 16):
        x0 = 6 + (1 if (y // 3) % 2 else 0)          # a slight wave along the ribbon
        for x in range(x0, x0 + 3):
            px[x, y] = (255, 255, 255, 255)
    rib.save(os.path.join(ASSETS, "textures", "particle", "streamer.png"))
    for name in ("confetti", "streamer"):
        with open(os.path.join(ASSETS, "particles", name + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"textures": ["ctrcraft:" + name]}, f, indent=2)
    print("confetti pictures written")


if __name__ == "__main__":
    main()
