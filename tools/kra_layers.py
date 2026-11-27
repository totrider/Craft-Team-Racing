"""Helper for the art scripts that draw pixel art and write it as a layered .kra (script -> .kra -> PNG, see krita/README.md).

    import kra_layers
    sheet = kra_layers.Sheet(16, 16)
    px = sheet.layer("cover")            # a new layer ("base: cover"), above the ones made before; px is its pixel access
    px[3, 4] = (255, 0, 0, 255)
    ...
    kra_layers.write_if_stale("krita/items/thing.kra", sheet, inputs)

Layers are drawn bottom to top in the order they are made, like the script's own drawing order, so the flattened PNG (tools/build_art.py) is
exactly what the old script drew straight to a PNG. Every layer is named "base: <name>": tools/build_art.py puts all of the "base" group in the PNG.
"""
import os
import sys

from PIL import Image, ImageDraw

import kra_stamp
from kra_writer import Layer, write_kra


class Sheet:
    def __init__(self, width, height):
        self.width, self.height = width, height
        self.layers = []                         # (name, image), bottom first

    def layer(self, name, group="base"):
        """Adds a transparent layer on top of the stack and returns its pixel access."""
        img = Image.new("RGBA", (self.width, self.height), (0, 0, 0, 0))
        self.layers.append((f"{group}: {name}", img))
        return img.load()

    def add(self, name, image, group="base"):
        """Adds a finished image as a layer on top of the stack."""
        self.layers.append((f"{group}: {name}", image))

    def replace_semantics(self):
        """For art where a later pixel REPLACES what is under it (also when it is only partly opaque): clears the pixels under every partly see-through pixel in the layers below, so the
        stack flattens to the upper pixel itself and not to a blend of it with the one under it."""
        covered = set()
        for _, img in reversed(self.layers):                     # from the top
            px = img.load()
            for xy in covered:
                px[xy] = (0, 0, 0, 0)
            for y in range(img.height):
                for x in range(img.width):
                    if 0 < px[x, y][3] < 255:
                        covered.add((x, y))

    def flatten(self):
        out = Image.new("RGBA", (self.width, self.height), (0, 0, 0, 0))
        for _, img in self.layers:
            out.alpha_composite(img)
        return out


class Painter:
    """Draws onto the layers of a Sheet like a PIL ImageDraw does: part("name") starts a new layer on top, and everything drawn after it (rectangle, ellipse, polygon,
    line, point, ... and putpixel) lands on that layer. Drawing is opaque and replaces, so the stack flattens to exactly what drawing on one image gives."""

    def __init__(self, sheet):
        self.sheet = sheet
        self.image = None
        self.d = None

    def part(self, name):
        self.sheet.layer(name)
        self.image = self.sheet.layers[-1][1]
        self.d = ImageDraw.Draw(self.image)
        return self

    def putpixel(self, xy, colour):
        self.image.putpixel(xy, colour)

    def __getattr__(self, name):                 # rectangle, ellipse, polygon, line, point, text ...
        if name in ("sheet", "image", "d"):
            raise AttributeError(name)
        return getattr(self.d, name)


def write_if_stale(kra_rel, sheet, inputs):
    """Writes the whole .kra when the script, a helper or a data file it reads changed (tools/kra_stamp.py). True when it wrote."""
    if not kra_stamp.stale(kra_rel, inputs):
        return False
    os.makedirs(os.path.dirname(kra_rel) or ".", exist_ok=True)
    name = os.path.splitext(os.path.basename(kra_rel))[0]
    wrote = write_kra(kra_rel, sheet.width, sheet.height, [Layer(n, im) for n, im in reversed(sheet.layers)], name=name,   # the .kra lists the top layer first
                      info=kra_stamp.provenance(inputs, sys._getframe(1).f_code.co_filename))     # the caller is the script that owns this .kra
    kra_stamp.record(kra_rel, inputs)
    print("wrote" if wrote else "unchanged", kra_rel)     # unchanged: the rebuild gave identical bytes, so git sees nothing
    return wrote


def export_png(kra_rel):
    """Flattens one .kra into its PNGs right now (tools/build_art.py does all of them at the end of a build): for a script that needs the PNG at once, e.g. to embed it in a Blockbench model."""
    import build_art
    build_art.build_for(kra_rel)


def place(sheet, layers, offset, prefix="", scale=1):
    """Adds the (name, image) layers of a smaller picture to a bigger sheet, pasted at {offset} (and enlarged {scale} times with hard pixels); names get {prefix}."""
    for name, img in layers:
        if scale != 1:
            img = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        canvas = Image.new("RGBA", (sheet.width, sheet.height), (0, 0, 0, 0))
        canvas.paste(img, (offset[0] * scale, offset[1] * scale))
        sheet.add(prefix + name, canvas)
