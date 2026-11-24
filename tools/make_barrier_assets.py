"""
The Kart Barrier's pictures and model files: a 16 x 16 tile (red and white hazard stripes in a cyan frame), used for the inventory icon and the break particles. The block itself is invisible in the
world (client/BarrierGhostRenderer draws it while a track building item is held), so this is not what it looks like when placed.

  python tools/make_barrier_assets.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "craftteamracing")


def tile():
    """The barrier tile as layers: the stripes and the cyan frame."""
    sheet = kra_layers.Sheet(16, 16)
    stripes, frame = sheet.layer("hazard stripes"), sheet.layer("frame")
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                frame[x, y] = (40, 200, 220, 255)
            else:
                stripes[x, y] = (214, 52, 52, 255) if ((x + y) // 3) % 2 == 0 else (245, 245, 245, 255)
    return sheet


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def main():
    # one .kra: tools/build_art.py flattens it into the item's and the block's picture (the same tile)
    kra_layers.write_if_stale("krita/blocks/race_barrier.kra", tile(), kra_stamp.inputs_for(__file__))
    write(os.path.join(ASSETS, "models", "block", "race_barrier.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": "craftteamracing:block/race_barrier"}})
    write(os.path.join(ASSETS, "blockstates", "race_barrier.json"), {"variants": {"": {"model": "craftteamracing:block/race_barrier"}}})
    write(os.path.join(ASSETS, "models", "item", "race_barrier.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "craftteamracing:item/race_barrier"}})
    write(os.path.join(ASSETS, "models", "item", "snow_skis.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "craftteamracing:item/snow_skis"}})
    print("written")


if __name__ == "__main__":
    main()
