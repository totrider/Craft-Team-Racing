"""
The flat sprites drawn on the ground for a race track: the start grid's kart spots and the gathering area behind the Start node.

  python tools/make_track_overlays.py

Writes the layered files krita/blocks/start_grid_slot.kra and start_grid_zone.kra (a script -> .kra -> PNG pipeline: tools/build_art.py flattens them into
textures/block/start_grid_slot.png (one kart's spot: an outline with an arrow pointing the way the kart faces, UP in the picture is forward) and
textures/block/start_grid_zone.png (the square gathering area: a dashed border, corner brackets and a chevron pattern pointing at the start line, UP is toward the line). They are in the
block atlas and are cutouts (pixels are fully drawn or not at all). Every line is drawn pixel by pixel: whole corners, arrows and chevrons at exactly 45 degrees, and strokes of
a width that is a power of two (4 pixels, run of 16). The .kra files are rewritten whole from this script when it changes (tools/kra_stamp.py); they are templates for modders, not
edited in the project. Run from the project folder.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_stamp  # noqa: E402
from kra_writer import Layer, write_kra  # noqa: E402

KRITA = os.path.join("krita", "blocks")

WHITE = (245, 245, 245, 255)
GOLD = (255, 210, 31, 255)
GOLD_EDGE = (40, 30, 10, 255)
GREEN = (70, 235, 120, 255)
GREEN_EDGE = (20, 90, 50, 255)


def rect(im, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            im.putpixel((x, y), colour)


def blank(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def grid_slot():
    """The start grid spot as layers, top of the stack first (a list of (name, image))."""
    # 16 px per block: a spot is 2 blocks wide and 2.5 long
    w, h = 32, 40
    im = blank(w, h)
    rect(im, 0, 0, w - 1, 1, WHITE)                 # the outline, two pixels, corners whole
    rect(im, 0, h - 2, w - 1, h - 1, WHITE)
    rect(im, 0, 0, 1, h - 1, WHITE)
    rect(im, w - 2, 0, w - 1, h - 1, WHITE)
    # the arrow, forward is up: a head whose sides run at exactly 45 degrees (one pixel in per row) on a shaft six pixels wide
    body = set()
    for r in range(9):
        for x in range(15 - r, 17 + r):
            body.add((x, 7 + r))
    for y in range(16, 31):
        for x in range(13, 19):
            body.add((x, y))
    arrow, edge = blank(w, h), blank(w, h)
    for (x, y) in body:
        arrow.putpixel((x, y), GOLD)
    for (x, y) in body:                              # a one pixel edge round it
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in body and 0 <= x + dx < w and 0 <= y + dy < h:
                    edge.putpixel((x + dx, y + dy), GOLD_EDGE)
    return [("base: arrow edge", edge), ("base: arrow", arrow), ("base: outline", im)]


def gather_zone():
    """The gathering area as layers, top of the stack first (a list of (name, image))."""
    # 8 px per block: 16 x 16 blocks
    n = 128
    brackets, dashes = blank(n, n), blank(n, n)
    chevrons, chevron_edges = blank(n, n), blank(n, n)
    length, thick = 24, 6                            # the corner brackets: two bars, the corner pixels whole
    for fx in (0, 1):
        for fy in (0, 1):
            rect(brackets, 0 if fx == 0 else n - length, 0 if fy == 0 else n - thick, (length - 1) if fx == 0 else n - 1, (thick - 1) if fy == 0 else n - 1, GREEN)
            rect(brackets, 0 if fx == 0 else n - thick, 0 if fy == 0 else n - length, (thick - 1) if fx == 0 else n - 1, (length - 1) if fy == 0 else n - 1, GREEN)
    # the dashed border between the brackets (80 pixels): four dashes of 10 and five gaps of 8, four pixels thick, so the gap at a bracket is the gap between dashes
    for x in range(length + 8, n - length - 8, 18):
        rect(dashes, x, 0, x + 9, 3, GREEN)
        rect(dashes, x, n - 4, x + 9, n - 1, GREEN)
        rect(dashes, 0, x, 3, x + 9, GREEN)
        rect(dashes, n - 4, x, n - 1, x + 9, GREEN)
    # chevrons pointing at the line (up): arms at exactly 45 degrees (a run of 16 over a rise of 16, one pixel a row), four pixels across, with a one pixel shade on the outer side
    # a point at the top (the outer edges run up and meet three rows above the arms' first row) and each arm ends in a diagonal cut across it
    for y0 in (32, 60, 88):
        for i in range(-3, 16):
            y = y0 + i
            for u in range(max(0, i), i + 4):                 # u: how far a pixel is from the middle line (0 is the pair of pixels next to it)
                if i + u <= 31:
                    chevrons.putpixel((63 - u, y), GREEN)
                    chevrons.putpixel((64 + u, y), GREEN)
            if i >= -2 and i + i + 4 <= 31 - 0:
                chevron_edges.putpixel((63 - (i + 4), y), GREEN_EDGE)
                chevron_edges.putpixel((64 + (i + 4), y), GREEN_EDGE)
    return [("base: chevron edges", chevron_edges), ("base: chevrons", chevrons), ("base: dashes", dashes), ("base: brackets", brackets)]


def main():
    os.makedirs(KRITA, exist_ok=True)
    inputs = kra_stamp.inputs_for(__file__)
    for name, make in (("start_grid_slot", grid_slot), ("start_grid_zone", gather_zone)):
        path = os.path.join(KRITA, name + ".kra")
        if kra_stamp.stale(path, inputs):
            layers = make()
            w, h = layers[0][1].size
            write_kra(path, w, h, [Layer(n, im) for n, im in layers], name=name)
            kra_stamp.record(path, inputs)
            print("wrote", path)
    print("track overlay sprites ok")


if __name__ == "__main__":
    main()
