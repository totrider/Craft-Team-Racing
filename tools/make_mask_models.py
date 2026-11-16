"""
The Aku Aku and Uka Uka masks that orbit a kart under the Mask power-up: stand-in models, one shape each (the good mask is a tall tiki with a crown of feathers, the evil one
broader, with horns, tusks, a scowl and a jagged grin).

  python tools/make_mask_models.py

Models are Blockbench's: blockbench/mask/*.bbmodel are the SOURCE (each is written once from the shapes below if it is missing, then it is yours; this script only reads
them). One texture slot, "mask": a 4 x 4 palette of 4 x 4 px cells (every box takes one cell, so recolouring a part is repainting a cell); the cell names are in CELLS.
Writes models/item/pickup_mask_aku.json and pickup_mask_uka.json, and the palette textures once if they are missing (yours to repaint). Run from the project folder.
The original game's masks only ever exist in the local pack made by tools/extract_ctr_textures.py, never in the jar. The HUD pictures of the two (textures/gui/power/mask.png and
mask_dark.png) are made by tools/make_power_icons.py.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "ctrcraft")
TEX_DIR = os.path.join(ASSETS, "textures", "item")
BB_DIR = os.path.join("blockbench", "mask")

CELLS = ["wood", "wood_dark", "white", "black", "mouth", "f1", "f2", "f3", "f4", "teeth", "rim", "spare"]
COLOURS = {
    "aku": {"wood": (176, 112, 60), "wood_dark": (120, 72, 38), "white": (245, 245, 235), "black": (30, 24, 24), "mouth": (150, 30, 30), "f1": (60, 120, 235),
            "f2": (70, 190, 70), "f3": (255, 215, 40), "f4": (215, 55, 50), "teeth": (240, 235, 220), "rim": (90, 220, 90), "spare": (255, 255, 255)},
    "uka": {"wood": (92, 52, 112), "wood_dark": (50, 28, 66), "white": (235, 70, 50), "black": (16, 12, 20), "mouth": (30, 10, 20), "f1": (40, 40, 52),
            "f2": (150, 30, 40), "f3": (70, 70, 84), "f4": (190, 40, 50), "teeth": (210, 200, 175), "rim": (150, 235, 150), "spare": (255, 255, 255)},
}


def cell_uv(name):
    i = CELLS.index(name)
    cx, cy = i % 4, i // 4
    return [cx * 4 + 0.5, cy * 4 + 0.5, cx * 4 + 3.5, cy * 4 + 3.5]


def box(frm, to, cell):
    """A box, turned half a turn about the vertical (the shapes below are drawn with the face on -z; the original's masks look along +z and the mod draws them that way)."""
    frm, to = [16 - to[0], frm[1], 16 - to[2]], [16 - frm[0], to[1], 16 - frm[2]]
    return {"from": frm, "to": to, "faces": {f: {"uv": cell_uv(cell), "texture": "#mask"} for f in bbmodel.FACES}}


def beam_box(frm, to):
    return {"from": frm, "to": to, "faces": {f: {"uv": [0, 0, 2, 2], "texture": "#beam"} for f in bbmodel.FACES}}


def beam():
    """The light round the masked kart (akubeam / ukabeam): a square ring round the vertical axis through the block's middle, drawn added to the screen, turning with the mask."""
    return [("beam", [beam_box([-5.6, 5, -5.6], [21.6, 11, -4.6]), beam_box([-5.6, 5, 20.6], [21.6, 11, 21.6]),
                      beam_box([-5.6, 5, -4.6], [-4.6, 11, 20.6]), beam_box([20.6, 5, -4.6], [21.6, 11, 20.6])])]


def aku():
    """Aku Aku: a tall wooden tiki, kind eyes, a smile, a crown of four feathers. About half a block high, its face on the north side."""
    return [
        ("head", [box([4, 0, 6], [12, 10, 9], "wood"), box([3.5, 2, 6.5], [4, 8, 8.5], "wood_dark"), box([12, 2, 6.5], [12.5, 8, 8.5], "wood_dark")]),
        ("face", [box([5, 6, 5.5], [7, 8, 6], "white"), box([9, 6, 5.5], [11, 8, 6], "white"), box([5.7, 6.3, 5.3], [6.5, 7.3, 5.6], "black"),
                  box([9.5, 6.3, 5.3], [10.3, 7.3, 5.6], "black"), box([5.5, 2, 5.5], [10.5, 4, 6], "mouth"), box([6, 3.3, 5.3], [10, 4, 5.6], "teeth")]),
        ("crown", [box([3.5, 10, 6.5], [5.5, 14, 8.5], "f1"), box([5.7, 10, 6.5], [7.7, 15, 8.5], "f2"), box([8.3, 10, 6.5], [10.3, 15, 8.5], "f3"),
                   box([10.5, 10, 6.5], [12.5, 14, 8.5], "f4")]),
    ]


def uka():
    """Uka Uka: broader and darker, a heavy scowling brow over red eyes, tusks and a jagged grin, five spikes and a horn each side."""
    return [
        ("head", [box([3, 0, 6], [13, 10, 9.5], "wood"), box([2.5, 2, 6.5], [3, 9, 8.5], "wood_dark"), box([13, 2, 6.5], [13.5, 9, 8.5], "wood_dark")]),
        ("face", [box([3.5, 7.6, 5.4], [7.6, 9, 6], "black"), box([8.4, 7.6, 5.4], [12.5, 9, 6], "black"),
                  box([4.4, 5.6, 5.5], [7, 7.4, 6], "white"), box([9, 5.6, 5.5], [11.6, 7.4, 6], "white"),
                  box([5.3, 5.8, 5.3], [6.1, 6.8, 5.6], "black"), box([9.9, 5.8, 5.3], [10.7, 6.8, 5.6], "black"),
                  box([4, 1.5, 5.5], [12, 4.5, 6], "mouth"),
                  box([4.5, 3.4, 5.3], [5.7, 4.5, 5.6], "teeth"), box([6.3, 2.4, 5.3], [7.5, 3.5, 5.6], "teeth"), box([8.5, 2.4, 5.3], [9.7, 3.5, 5.6], "teeth"),
                  box([10.3, 3.4, 5.3], [11.5, 4.5, 5.6], "teeth"),
                  box([3.3, -1.5, 5.6], [4.5, 2.5, 6.6], "teeth"), box([11.5, -1.5, 5.6], [12.7, 2.5, 6.6], "teeth")]),
        ("spikes", [box([3, 10, 6.5], [4.5, 13, 8.5], "f1"), box([5, 10, 6.5], [6.5, 15, 8.5], "f3"), box([7.2, 10, 6.5], [8.8, 16.5, 8.5], "f2"),
                    box([9.5, 10, 6.5], [11, 15, 8.5], "f3"), box([11.5, 10, 6.5], [13, 13, 8.5], "f1")]),
        ("horns", [box([1.2, 8, 6.7], [3, 10, 8.3], "f2"), box([0.2, 10, 6.9], [1.8, 13, 8.1], "f4"),
                   box([13, 8, 6.7], [14.8, 10, 8.3], "f2"), box([14.2, 10, 6.9], [15.8, 13, 8.1], "f4")]),
    ]


SHAPES = {"aku": aku, "uka": uka}


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.6, 0.6, 0.6]}, "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]}}


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


def main():
    os.makedirs(TEX_DIR, exist_ok=True)
    for who, shape in SHAPES.items():
        tex = os.path.join(TEX_DIR, "mask_%s.png" % who)
        if not os.path.exists(tex):
            im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            for i, name in enumerate(CELLS):
                cx, cy = i % 4, i // 4
                im.paste(Image.new("RGBA", (4, 4), COLOURS[who][name] + (255,)), (cx * 4, cy * 4))
            im.save(tex)
        path = os.path.join(BB_DIR, "mask_%s.bbmodel" % who)
        if not os.path.exists(path):
            bbmodel.write(path, "mask_%s" % who, {"mask": tex}, shape(), display=display(), gui_light_front=False,
                          comment="Stand-in mask head, its own shape. Texture slot 'mask' is a 4 x 4 palette of 4 px cells (see tools/make_mask_models.py).")
        btex = os.path.join(TEX_DIR, "mask_beam_%s.png" % who)
        if not os.path.exists(btex):
            r, g, b = COLOURS[who]["rim"]
            Image.new("RGBA", (2, 2), (r, g, b, 110)).save(btex)
        bpath = os.path.join(BB_DIR, "mask_beam_%s.bbmodel" % who)
        if not os.path.exists(bpath):
            bbmodel.write(bpath, "mask_beam_%s" % who, {"beam": btex}, beam(), display=display(), gui_light_front=False,
                          comment="Stand-in light round the masked kart: a square ring, one translucent colour (the 'beam' slot), drawn added to the screen.")
        bm = bbmodel.read(bpath)
        dump(os.path.join(ASSETS, "models", "item", "pickup_mask_beam_%s.json" % who), {
            "textures": {"beam": "ctrcraft:item/mask_beam_%s" % who, "particle": "ctrcraft:item/mask_beam_%s" % who},
            "elements": bbmodel.elements(bm),
            "display": bm["display"] or display(),
        })
        for n in range(24):          # the animation's frames (a resource pack brings the original's, which twist); here each is the same stand-in
            dump(os.path.join(ASSETS, "models", "item", "pickup_mask_beam_%s_%d.json" % (who, n)), {"parent": "ctrcraft:item/pickup_mask_beam_%s" % who})
        model = bbmodel.read(path)
        dump(os.path.join(ASSETS, "models", "item", "pickup_mask_%s.json" % who), {
            "textures": {"mask": "ctrcraft:item/mask_%s" % who, "particle": "ctrcraft:item/mask_%s" % who},
            "elements": bbmodel.elements(model),
            "display": model["display"] or display(),
        })
    print("mask models written")


if __name__ == "__main__":
    main()
