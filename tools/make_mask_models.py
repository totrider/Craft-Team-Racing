"""
The heads of the Totem (Mask) power-up that circle a kart: flat item sprites (item/generated): the good one is Minecraft's own totem of undying (used from the game, never copied into the mod),
the evil one is the mod's own evil totem (tools/make_evil_totem.py). Neither has geometry of its own, so there is no model source for them. The swirling light round the kart is a Blender
model: tools/make_mask_beam.py.

  python tools/make_mask_models.py

Writes models/item/pickup_mask_aku.json and pickup_mask_uka.json. Run from the project folder. The original game's masks only ever exist in the local pack made by tools/ctr_disc/extract_ctr_textures.py.
"""
import json
import os


RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "craftteamracing")
TEX_DIR = os.path.join(ASSETS, "textures", "item")

FLAT = {"aku": "minecraft:item/totem_of_undying", "uka": "craftteamracing:item/mask_uka"}


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.6, 0.6, 0.6]}, "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]}}


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


def main():
    os.makedirs(TEX_DIR, exist_ok=True)
    for who in FLAT:
        dump(os.path.join(ASSETS, "models", "item", "pickup_mask_%s.json" % who), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": FLAT[who]},
        })
    print("mask models written")


if __name__ == "__main__":
    main()
