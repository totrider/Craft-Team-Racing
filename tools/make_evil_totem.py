"""
The picture of the evil mask (Uka Uka): an evil totem, drawn in the spirit of the good one (which is Minecraft's own totem of undying, used from the game, not copied).
A 16 x 16 sprite with the same outstretched-arms silhouette, in black and purple, horned, with red eyes and fangs. It is the HUD icon of the dark mask and, as a flat
item model, the head that orbits a kart under the Mask power-up (tools/make_mask_models.py writes the model).

  python tools/make_evil_totem.py

Writes the layered krita/items/mask_uka.kra (a modder's template, one layer per colour of the grid below; edit the grid, not the files). tools/build_art.py flattens it into
textures/item/mask_uka.png and textures/item/sprite/mask_dark.png (the sprite the HUD picture is made from). Run from the project folder.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

KRA = "krita/items/mask_uka.kra"

PALETTE = {
    ".": None,
    "K": (28, 10, 38),        # outline
    "D": (60, 24, 86),        # dark purple
    "P": (98, 46, 132),       # purple
    "L": (146, 92, 180),      # light purple (highlight)
    "R": (240, 40, 36),       # red (eyes, the stone on the belly)
    "W": (236, 226, 200),     # bone (horns, fangs)
}
LAYERS = {"K": "outline", "D": "dark purple", "P": "purple", "L": "light purple", "R": "red", "W": "bone"}

GRID = [
    "...W........W...",
    "...WK......KW...",
    "...KWKKKKKKWK...",
    "....KLPPPPLK....",
    "....KDDPPDDK....",
    "....KRRPPRRK....",
    "....KPPDDPPK....",
    "....KDWDDWDK....",
    ".KKKKKPPPPKKKKK.",
    "KLPDKPPPPPPKDPLK",
    ".KKKKDPDDPDKKKK.",
    "....KPPPPPPK....",
    "....KDDDDDDK....",
    ".....KPRRPK.....",
    ".....KPPPPK.....",
    "......KKKK......",
]


def sheet():
    """The totem as layers, one per colour (a list of kra_layers.Sheet layers; flatten() gives the 16 x 16 sprite)."""
    assert len(GRID) == 16 and all(len(r) == 16 for r in GRID), "the grid is 16 x 16"
    s = kra_layers.Sheet(16, 16)
    px = {c: s.layer(name) for c, name in LAYERS.items()}
    for y, row in enumerate(GRID):
        for x, c in enumerate(row):
            if PALETTE[c]:
                px[c][x, y] = PALETTE[c] + (255,)
    return s


def main():
    kra_layers.write_if_stale(KRA, sheet(), kra_stamp.inputs_for(__file__))
    print("evil totem ok")


if __name__ == "__main__":
    main()
