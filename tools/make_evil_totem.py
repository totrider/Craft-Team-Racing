"""
The picture of the evil mask (Uka Uka): an evil totem, drawn in the spirit of the good one (which is Minecraft's own totem of undying, used from the game, not copied).
A 16 x 16 sprite with the same outstretched-arms silhouette, in black and purple, horned, with red eyes and fangs. It is the HUD icon of the dark mask and, as a flat
item model, the head that orbits a kart under the Mask power-up (tools/make_mask_models.py writes the model).

  python tools/make_evil_totem.py

Writes src/main/resources/assets/craftteamracing/textures/item/mask_uka.png (overwritten each run: edit the grid below, not the PNG). Run from the project folder.
"""
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "item", "mask_uka.png")

PALETTE = {
    ".": None,
    "K": (28, 10, 38),        # outline
    "D": (60, 24, 86),        # dark purple
    "P": (98, 46, 132),       # purple
    "L": (146, 92, 180),      # light purple (highlight)
    "R": (240, 40, 36),       # red (eyes, the stone on the belly)
    "W": (236, 226, 200),     # bone (horns, fangs)
}

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


def main():
    assert len(GRID) == 16 and all(len(r) == 16 for r in GRID), "the grid is 16 x 16"
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(GRID):
        for x, c in enumerate(row):
            if PALETTE[c]:
                im.putpixel((x, y), PALETTE[c] + (255,))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im.save(OUT)
    print("evil totem written")


if __name__ == "__main__":
    main()
