"""
Takes the "?" out of the power-up crate's face picture (a local-pack helper; the art is Sony / Naughty Dog's and only ever goes into git-ignored local files).

The original's panel is a wooden square with crossed braces and the "?" on it. The braces and the planks are symmetric, so the wood under the "?" is taken from the mirrored places of
the same picture (left-right, top-bottom, both); what no mirror can give is filled from its neighbours. The picture is the game's own flat 64 x 64 texture, before any shading, so
there is no light to match. Used by tools/ctr_disc/extract_ctr_textures.py.
"""
import numpy as np


def _dilate(m, n=1):
    for _ in range(n):
        p = np.pad(m, 1)
        m = p[1:-1, 1:-1] | p[:-2, 1:-1] | p[2:, 1:-1] | p[1:-1, :-2] | p[1:-1, 2:] | p[:-2, :-2] | p[2:, 2:] | p[:-2, 2:] | p[2:, :-2]
    return m


def _erode(m, n=1):
    return ~_dilate(~m, n)


def question_mask(rgba):
    """The pixels of the "?" and its red outline, with its dark anti-aliasing lines and a one pixel halo; None when the picture has no "?"."""
    a = rgba[:, :, :3].astype(np.float64)
    r, g, b = a[:, :, 0], a[:, :, 1], a[:, :, 2]
    ratio = g / (r + 1.0)
    core = (r > 100) & (b < 0.5 * r) & ((ratio > 0.8) | (ratio < 0.26))
    if core.sum() < 200:
        return None
    closed = _erode(_dilate(core, 2), 2)
    return _dilate(closed | core, 1)


BRACE_HALF_WIDTH = 8          # the braces of the 64 x 64 panel are about 14 texels across, measured across the diagonal in the picture


def blank(rgba, half_width=BRACE_HALF_WIDTH):
    """The picture without the "?" (the input is not changed); the picture itself when it has none. The wood under the "?" is rebuilt from the panel's own structure: a brace is
    followed along its diagonal from the nearest bare texel on either side (so its grain and edges run on), a plank along its row; where the two braces cross, either brace will do."""
    mask = question_mask(rgba)
    if mask is None:
        return rgba
    out = rgba.copy()
    h, w = mask.shape
    known = ~mask

    def along(y, x, dy, dx, ok):
        """The nearest known texel of the same kind in both directions of (dy, dx), or None."""
        best = None
        for sign in (1, -1):
            for k in range(1, max(h, w)):
                yy, xx = y + sign * dy * k, x + sign * dx * k
                if not (0 <= yy < h and 0 <= xx < w):
                    break
                if known[yy, xx] and ok(yy, xx):
                    if best is None or k < best[0]:
                        best = (k, yy, xx)
                    break
        return best

    def d1(y, x):
        return abs(x - y) / 1.4142

    def d2(y, x):
        return abs(x + y - (w - 1)) / 1.4142

    on1 = lambda y, x: d1(y, x) <= half_width
    on2 = lambda y, x: d2(y, x) <= half_width
    result = {}
    for y in range(h):
        for x in range(w):
            if not mask[y, x]:
                continue
            a1, a2 = on1(y, x), on2(y, x)
            src = None
            if a1 and not a2:
                src = along(y, x, 1, 1, lambda yy, xx: on1(yy, xx) and not on2(yy, xx))
            elif a2 and not a1:
                src = along(y, x, 1, -1, lambda yy, xx: on2(yy, xx) and not on1(yy, xx))
            elif a1 and a2:
                src = along(y, x, 1, 1, lambda yy, xx: on1(yy, xx) and not on2(yy, xx))
                alt = along(y, x, 1, -1, lambda yy, xx: on2(yy, xx) and not on1(yy, xx))
                if src is None or (alt is not None and alt[0] < src[0]):
                    src = alt
            else:
                # a plank: along its row, from bare plank texels inside the frame's dark edge (the outer four texels) and clear of the braces' edges
                plank = lambda yy, xx: 4 <= xx < w - 4 and 4 <= yy < h - 4 and d1(yy, xx) > half_width + 2 and d2(yy, xx) > half_width + 2
                src = along(y, x, 0, 1, plank)
                if src is None:
                    # no bare plank texel in the row: the mirrored places (the panel is symmetric), then down the column
                    for sy, sx in ((y, w - 1 - x), (h - 1 - y, x), (h - 1 - y, w - 1 - x)):
                        if known[sy, sx] and plank(sy, sx):
                            src = (0, sy, sx)
                            break
                if src is None:
                    src = along(y, x, 1, 0, plank)
                if src is None:
                    # the triangles above and below the braces' crossing have no bare plank in line with them: a patch of the plank left of the "?" is tiled over them
                    py, px = 28 + (y % 12), 5 + (x % 8)
                    if known[py, px] and plank(py, px):
                        src = (0, py, px)
            if src is not None:
                result[(y, x)] = rgba[src[1], src[2]]
    for (y, x), c in result.items():
        out[y, x] = c
    # anything still open (no bare texel of its kind in line): the mean of its filled neighbours, from the edge of the hole inwards
    left = [(y, x) for y in range(h) for x in range(w) if mask[y, x] and (y, x) not in result]
    filled = known.copy()
    for (y, x) in result:
        filled[y, x] = True
    while left:
        later = []
        for y, x in left:
            neigh = [(y + dy, x + dx) for dy in (-1, 0, 1) for dx in (-1, 0, 1) if (dy or dx) and 0 <= y + dy < h and 0 <= x + dx < w and filled[y + dy, x + dx]]
            if neigh:
                out[y, x] = np.mean([out[p] for p in neigh], axis=0).astype(out.dtype)
            else:
                later.append((y, x))
        for y, x in left:
            if (y, x) not in later:
                filled[y, x] = True
        if len(later) == len(left):
            break
        left = later
    return out


def exclaim(question, blank):
    """The trigger crate's face for the local pack: the blank face (the "?" taken out, see blank) with a "!" in the "?"'s own look. Where the "?" stood is found by comparing the two
    faces, its yellow and its red outline are taken from its own pixels, and the "!" (a bar that narrows downwards, a gap, a dot) is drawn to the height of the "?", smoothed, with an
    outline as thick as the "?"'s. {question} and {blank}: PIL images of the same size; the "!" stands upright, or lies when the "?" does (a face seen from the side of the crate)."""
    from PIL import Image, ImageDraw, ImageFilter
    q = np.asarray(question.convert("RGBA")).astype(int)
    b = np.asarray(blank.convert("RGBA")).astype(int)
    diff = (np.abs(q - b)[:, :, :3].sum(axis=2) > 60) & (q[:, :, 3] > 0)
    if diff.sum() < 20:
        return blank
    ys, xs = np.nonzero(diff)
    y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max()
    px = q[diff]
    yellow = px[(px[:, 1] > 0.75 * px[:, 0]) & (px[:, 2] < 0.5 * px[:, 0])]
    red = px[(px[:, 1] < 0.35 * px[:, 0]) & (px[:, 0] > 100)]
    fill = tuple(int(v) for v in np.median(yellow[:, :3], axis=0)) if len(yellow) else (250, 210, 40)
    edge = tuple(int(v) for v in np.median(red[:, :3], axis=0)) if len(red) else (200, 40, 40)
    h, w = int(y1 - y0 + 1), int(x1 - x0 + 1)
    lying = w > h * 1.1
    long_side, short_side = (w, h) if lying else (h, w)
    thick = max(1, int(round(long_side / 18.0)))
    ss = 4
    glyph = Image.new("L", (long_side * ss, long_side * ss), 0)               # drawn upright in a square, turned afterwards when lying
    g = ImageDraw.Draw(glyph)
    top, bottom = thick * ss * 1.5, (long_side - thick * 1.5) * ss
    bar_w = max(3, long_side * 0.30) * ss
    cx = long_side * ss / 2.0
    stem_end = top + (bottom - top) * 0.66
    g.polygon([(cx - bar_w / 2, top), (cx + bar_w / 2, top), (cx + bar_w * 0.28, stem_end), (cx - bar_w * 0.28, stem_end)], fill=255)
    dot = bar_w * 0.62
    g.rectangle([cx - dot / 2, bottom - dot, cx + dot / 2, bottom], fill=255)
    glyph = glyph.resize((long_side, long_side), Image.LANCZOS)
    if lying:
        glyph = glyph.rotate(90)
    ring = glyph.filter(ImageFilter.MaxFilter(2 * thick + 1))
    out = blank.convert("RGBA").copy()
    layer_edge = Image.new("RGBA", out.size, edge + (255,))
    layer_fill = Image.new("RGBA", out.size, fill + (255,))
    cx_mid, cy_mid = (x0 + x1) // 2, (y0 + y1) // 2
    size = glyph.size[0]
    ox, oy = cx_mid - size // 2, cy_mid - size // 2
    ring_full = Image.new("L", out.size, 0)
    ring_full.paste(ring, (ox, oy))
    glyph_full = Image.new("L", out.size, 0)
    glyph_full.paste(glyph, (ox, oy))
    out.paste(layer_edge, (0, 0), ring_full)
    out.paste(layer_fill, (0, 0), glyph_full)
    return out
