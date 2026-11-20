"""
The rulebook's inventory picture (16 x 16, stand-in): a book with a red cover, a chequered flag on it and a white page edge.

  python tools/make_rulebook_icon.py          (writes src/main/resources/assets/craftteamracing/textures/item/rulebook.png)
"""
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "item", "rulebook.png")


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cover, dark, page, edge = (190, 40, 40, 255), (120, 20, 25, 255), (240, 235, 220, 255), (60, 15, 20, 255)
    for y in range(1, 15):
        for x in range(2, 14):
            px[x, y] = cover
    for y in range(1, 15):
        px[2, y] = dark                  # the spine
        px[3, y] = dark
        px[13, y] = page                 # the page edge
    for x in range(2, 14):
        px[x, 1] = edge
        px[x, 14] = edge
    for y in range(1, 15):
        px[2, y] = edge
    # the chequered flag
    for fy in range(3):
        for fx in range(4):
            px[6 + fx, 4 + fy] = (255, 255, 255, 255) if (fx + fy) % 2 == 0 else (20, 20, 20, 255)
    for y in range(4, 11):
        px[5, y] = (230, 230, 230, 255)  # its pole
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("written", OUT)


if __name__ == "__main__":
    main()
