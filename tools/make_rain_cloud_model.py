"""
The red beaker's rain cloud: a stand-in model (a few flat grey slabs), the same for all 23 frames of its animation.

  python tools/make_rain_cloud_model.py

Models are Blockbench's: blockbench/rain_cloud/rain_cloud.bbmodel is the SOURCE (written once from the shape below if it is missing, then it is yours; this script only reads it). One
texture slot, "cloud": a small translucent grey picture. Writes models/item/pickup_rain_cloud.json and its 23 frame models (pickup_rain_cloud_<n>, each a child of it: a resource pack
brings the original's 23 frames, which differ), and the texture once if it is missing. Run from the project folder. The original's cloud only ever exists in the local pack.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "craftteamracing")
TEX = os.path.join(ASSETS, "textures", "item", "rain_cloud.png")
BB = os.path.join("blockbench", "rain_cloud", "rain_cloud.bbmodel")


def box(frm, to):
    return {"from": frm, "to": to, "faces": {f: {"uv": [0, 0, 2, 2], "texture": "#cloud"} for f in bbmodel.FACES}}


def shape():
    """A cloud of overlapping upright slabs, 27 px (1.7 blocks) across and 3 thick, centred on the block's middle (the original's is a set of upright panels that slide round, drawn dark; see extract_ctr_textures)."""
    return [("cloud", [box([-2.5, 5, 6.5], [10, 10, 9.5]), box([4, 4, 6], [18.5, 12, 10]), box([1, 6, 7], [14, 14, 9]), box([9, 5, 6.5], [21.5, 10, 9.5])])]


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.4, 0.4, 0.4]}, "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]}}


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


def main():
    if not os.path.exists(TEX):
        Image.new("RGBA", (2, 2), (70, 72, 84, 215)).save(TEX)
    if not os.path.exists(BB):
        bbmodel.write(BB, "rain_cloud", {"cloud": TEX}, shape(), display=display(), gui_light_front=False,
                      comment="Stand-in rain cloud: upright grey slabs. Texture slot 'cloud' is one translucent grey (see tools/make_rain_cloud_model.py).")
    model = bbmodel.read(BB)
    dump(os.path.join(ASSETS, "models", "item", "pickup_rain_cloud.json"), {
        "textures": {"cloud": "craftteamracing:item/rain_cloud", "particle": "craftteamracing:item/rain_cloud"},
        "elements": bbmodel.elements(model),
        "display": model["display"] or display(),
    })
    for n in range(23):
        dump(os.path.join(ASSETS, "models", "item", "pickup_rain_cloud_%d.json" % n), {"parent": "craftteamracing:item/pickup_rain_cloud"})
        dump(os.path.join(ASSETS, "models", "item", "pickup_rain_cloud_sub_%d.json" % n), {"elements": []})          # the subtracted texels: the pack's original has them, the stand-in none
    print("rain cloud models written")


if __name__ == "__main__":
    main()
