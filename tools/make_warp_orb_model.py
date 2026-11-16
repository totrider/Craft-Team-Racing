"""
The Warp Orb: a stand-in model (a flat blue star of two crossed slabs round a small gem), the same for all 16 frames of its animation.

  python tools/make_warp_orb_model.py

Models are Blockbench's: blockbench/warp_orb/warp_orb.bbmodel is the SOURCE (written once from the shape below if it is missing, then it is yours; this script only reads it). One texture
slot, "orb": a small translucent blue picture; the mod draws the orb added to the screen. Writes models/item/pickup_warp_orb.json and its 16 frame models (pickup_warp_orb_<n>, each a child
of it: a resource pack brings the original's 16 frames, which differ), and the texture once if it is missing. Run from the project folder. The original's orb only exists in the local pack.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "ctrcraft")
TEX = os.path.join(ASSETS, "textures", "item", "warp_orb.png")
BB = os.path.join("blockbench", "warp_orb", "warp_orb.bbmodel")


def box(frm, to):
    return {"from": frm, "to": to, "faces": {f: {"uv": [0, 0, 2, 2], "texture": "#orb"} for f in bbmodel.FACES}}


def shape():
    """Two crossed upright slabs (a star standing on the ground), 2.5 blocks across, and a small gem in its middle; the origin is at its foot, mid-width."""
    return [("star", [box([-12, 18, 7.5], [28, 22, 8.5]), box([6, 0, 7.5], [10, 40, 8.5])]), ("gem", [box([6, 17, 6], [10, 23, 10])])]


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.3, 0.3, 0.3]}, "ground": {"translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]}}


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


def main():
    if not os.path.exists(TEX):
        Image.new("RGBA", (2, 2), (70, 130, 255, 255)).save(TEX)
    if not os.path.exists(BB):
        bbmodel.write(BB, "warp_orb", {"orb": TEX}, shape(), display=display(), gui_light_front=False,
                      comment="Stand-in Warp Orb: crossed upright slabs and a gem. Texture slot 'orb' is one blue (see tools/make_warp_orb_model.py).")
    model = bbmodel.read(BB)
    dump(os.path.join(ASSETS, "models", "item", "pickup_warp_orb.json"), {
        "textures": {"orb": "ctrcraft:item/warp_orb", "particle": "ctrcraft:item/warp_orb"},
        "elements": bbmodel.elements(model),
        "display": model["display"] or display(),
    })
    for n in range(16):
        dump(os.path.join(ASSETS, "models", "item", "pickup_warp_orb_%d.json" % n), {"parent": "ctrcraft:item/pickup_warp_orb"})
        dump(os.path.join(ASSETS, "models", "item", "pickup_warp_orb_solid_%d.json" % n), {"elements": []})          # the texels drawn as they are: the pack's original has them, the stand-in none
    print("warp orb models written")


if __name__ == "__main__":
    main()
