"""
The Kart Barrier's pictures and model files: a 16 x 16 tile (red and white hazard stripes in a cyan frame), used for the inventory icon and the break particles. The block itself is invisible in the
world (client/BarrierGhostRenderer draws it while a track building item is held), so this is not what it looks like when placed.

  python tools/make_barrier_assets.py
"""
import json
import os

from PIL import Image

ASSETS = os.path.join("src", "main", "resources", "assets", "craftteamracing")


def tile():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                px[x, y] = (40, 200, 220, 255)
            else:
                px[x, y] = (214, 52, 52, 255) if ((x + y) // 3) % 2 == 0 else (245, 245, 245, 255)
    return img


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def main():
    t = tile()
    for kind in ("item", "block"):
        os.makedirs(os.path.join(ASSETS, "textures", kind), exist_ok=True)
        t.save(os.path.join(ASSETS, "textures", kind, "race_barrier.png"))
    write(os.path.join(ASSETS, "models", "block", "race_barrier.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": "craftteamracing:block/race_barrier"}})
    write(os.path.join(ASSETS, "blockstates", "race_barrier.json"), {"variants": {"": {"model": "craftteamracing:block/race_barrier"}}})
    write(os.path.join(ASSETS, "models", "item", "race_barrier.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "craftteamracing:item/race_barrier"}})
    write(os.path.join(ASSETS, "models", "item", "snow_skis.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "craftteamracing:item/snow_skis"}})
    print("written")


if __name__ == "__main__":
    main()
