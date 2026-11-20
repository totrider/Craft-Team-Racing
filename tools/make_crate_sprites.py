"""
The TNT and Creeper crates' power-up sprites: a perspective view of the crate, 16 x 16 without an outline (tools/sprite_outline.py adds that), as the original game's TNT and Nitro icons
show their crates in perspective.

  python tools/make_crate_sprites.py

Step one is a pre-render: the crate's faces (drawn here for the TNT: red sticks and a "TNT" label, in the colours of Minecraft's TNT block; the creeper crate's are the ones made by
tools/make_tnt_models.py) are projected on a cube seen from the front, above and to the right (an oblique view, the faces lit 100 / 100 / 66 per cent), sampled once per sprite pixel. The result
is the baseline; HAND TOUCHES below it (the steel corners, a clearer label) are drawn over it where the plain pre-render is not readable at 16 pixels. Writes
  textures/item/sprite/tnt.png, tnt_charged.png       the sprites, no outline
  textures/gui/power/tnt.png, tnt_charged.png         the HUD pictures (96 x 64, outlined)
and, when called with --preview, a scaled picture of the plain pre-render next to the final sprite to preview.png in the folder given after it. Run from the project folder,
after tools/make_tnt_models.py.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sprite_outline as so  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures")

RED, DRED, LRED = (206, 62, 42), (150, 34, 30), (232, 96, 64)
WHITE, GREY, INK = (226, 226, 226), (176, 172, 172), (44, 40, 72)
CAP, CAP_DARK, FUSE = (214, 188, 140), (150, 120, 80), (60, 44, 30)
STEEL, STEEL_LIGHT, STEEL_DARK = (150, 154, 164), (214, 218, 228), (88, 92, 104)

LETTERS = {
    "T": ["XXX", ".X.", ".X.", ".X.", ".X."],
    "N": ["X...X", "XX..X", "X.X.X", "X..XX", "X...X"],
}


def tnt_side(letters=True):
    """Four red sticks above and below a white label with TNT on it."""
    im = Image.new("RGBA", (16, 16), RED + (255,))
    for y in range(16):
        for x in range(16):
            if x % 4 == 0:
                im.putpixel((x, y), DRED + (255,))
            elif x % 4 == 1 and y % 8 < 2:
                im.putpixel((x, y), LRED + (255,))
    for y in range(4, 11):
        for x in range(16):
            im.putpixel((x, y), (GREY if y == 10 else WHITE) + (255,))
    x = 2
    for ch in "TNT" if letters else "":
        glyph = LETTERS[ch]
        for gy, row in enumerate(glyph):
            for gx, c in enumerate(row):
                if c == "X":
                    im.putpixel((x + gx, 5 + gy), INK + (255,))
        x += len(glyph[0]) + 1
    return im


def tnt_top():
    """The crate's lid: red like the sticks, a darker rim, a black fuse standing up out of the middle."""
    im = Image.new("RGBA", (16, 16), RED + (255,))
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                im.putpixel((x, y), DRED + (255,))
            elif x % 4 == 0:
                im.putpixel((x, y), (178, 48, 36, 255))
    for x in range(5, 11):
        for y in range(5, 11):
            im.putpixel((x, y), DRED + (255,))
    for x in range(7, 9):
        for y in range(6, 10):
            im.putpixel((x, y), (24, 22, 24, 255))
    im.putpixel((7, 6), (60, 56, 58, 255))
    return im


def inside(p, poly):
    """Whether the point is inside the convex polygon (counter-clockwise or clockwise)."""
    sign = 0
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % len(poly)]
        c = (x1 - x0) * (p[1] - y0) - (y1 - y0) * (p[0] - x0)
        if c != 0:
            if sign and (c > 0) != (sign > 0):
                return False
            sign = 1 if c > 0 else -1
    return True


def face(img, origin, u_axis, v_axis, sprite, tex, shade, poly):
    """Fills the pixels whose centre is inside {poly} from the texture, by an affine map: texel (u, v) = ((p - origin) . dual axes)."""
    (ux, uy), (vx, vy) = u_axis, v_axis
    det = ux * vy - uy * vx
    for y in range(16):
        for x in range(16):
            p = (x + 0.5, y + 0.5)
            if not inside(p, poly):
                continue
            dx, dy = p[0] - origin[0], p[1] - origin[1]
            u = (dx * vy - dy * vx) / det
            v = (ux * dy - uy * dx) / det
            c = tex.getpixel((min(15, max(0, int(u * 16))), min(15, max(0, int(v * 16)))))
            sprite.putpixel((x, y), tuple(int(c[i] * shade) for i in range(3)) + (255,))


def cube(side, top):
    """The crate seen from the front and a little above and to the right (an oblique view: the front face square, the top and the right side running back at 45 degrees): the
    front face is the big flat one the eye reads, the top and the right side give it the perspective."""
    sprite = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    fl, fr = (0.25, 4.25), (11.75, 4.25)                      # the front face's top left and top right corners
    d = (3.75, -3.75)                                         # one cube depth, back
    bl = (fl[0], fl[1] + 11.5)
    br = (fr[0], fr[1] + 11.5)
    # top: u along the front edge, v back
    face(sprite, fl, (11.5, 0.0), d, sprite, top, 1.0, [fl, fr, (fr[0] + d[0], fr[1] + d[1]), (fl[0] + d[0], fl[1] + d[1])])
    # right side: u back, v down
    face(sprite, fr, d, (0.0, 11.5), sprite, side, 0.66, [fr, (fr[0] + d[0], fr[1] + d[1]), (br[0] + d[0], br[1] + d[1]), br])
    # front: u right, v down
    face(sprite, fl, (11.5, 0.0), (0.0, 11.5), sprite, side, 1.0, [fl, fr, br, bl])
    return sprite


BLACK = (14, 18, 14)
# hand-drawn at the sprite's own resolution (the pre-render samples the 16 x 16 pictures down to 12 pixels, which smears a three-pixel letter and a two-pixel eye)
# front face columns 0 to 10; the letters are 3 wide and 4 tall (the N an arch)
TNT_LABEL = ["TTT.NN..TTT", ".T..N.N..T.", ".T..N.N..T.", ".T..N.N..T."]
CREEPER_FEATURES = [(1, 2, 3, 3), (8, 2, 3, 3), (4, 5, 4, 2), (3, 7, 6, 2), (3, 9, 2, 3), (7, 9, 2, 3)]     # x, y, w, h on the 12 x 12 front face: big eyes, nose, frown, two feet


def front_pixel(sprite, x, y, colour):
    sprite.putpixel((x, y + 4), colour + (255,))


def hand_tnt(sprite):
    """The label band across the front, with TNT in it, over the pre-render's smeared one."""
    for y in range(3, 9):
        for x in range(12):
            front_pixel(sprite, x, y, GREY if y == 8 else WHITE)
    for gy, row in enumerate(TNT_LABEL):
        for gx, c in enumerate(row):
            if c != ".":
                front_pixel(sprite, gx, 4 + gy, INK)


def hand_creeper(sprite, skin):
    """The face on the front: eyes, nose, frown and two feet over the plain mottled front (no shaded rim: at this size it only muddies them)."""
    black = {(x + dx, y + dy) for x, y, w, h in CREEPER_FEATURES for dx in range(w) for dy in range(h)}
    for y in range(12):
        for x in range(12):
            c = skin.getpixel((int((x + 0.5) / 12 * 16), int((y + 0.5) / 12 * 16)))[:3]
            if (x, y) in black:
                c = BLACK
            front_pixel(sprite, x, y, c)


def steel_corners(sprite):
    """The crate's steel frame: a block at each front corner (2 x 2, lit top left)."""
    for x0, y0 in ((0, 4), (10, 4), (0, 14), (10, 14)):
        for dx in (0, 1):
            for dy in (0, 1):
                sprite.putpixel((x0 + dx, y0 + dy), (STEEL_LIGHT if (dx, dy) == (0, 0) else STEEL_DARK if (dx, dy) == (1, 1) else STEEL) + (255,))


def main():
    tex = os.path.join(ASSETS, "item")
    creeper_skin = Image.open(os.path.join(tex, "creeper_skin.png")).convert("RGBA")
    plain_tnt = tnt_side(letters=False)
    pre = {"tnt": cube(tnt_side(), tnt_top()), "tnt_charged": cube(Image.open(os.path.join(tex, "creeper_face.png")).convert("RGBA"), creeper_skin)}
    out = {"tnt": cube(plain_tnt, tnt_top()), "tnt_charged": cube(creeper_skin, creeper_skin)}
    hand_tnt(out["tnt"])
    hand_creeper(out["tnt_charged"], creeper_skin)
    for im in out.values():
        steel_corners(im)
    os.makedirs(os.path.join(tex, "sprite"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "gui", "power"), exist_ok=True)
    for name, im in out.items():
        im.save(os.path.join(tex, "sprite", name + ".png"))
        so.hud_icon(im).save(os.path.join(ASSETS, "gui", "power", name + ".png"))
    if "--preview" in sys.argv:
        folder = sys.argv[sys.argv.index("--preview") + 1]
        k = 12
        sheet = Image.new("RGBA", (4 * 16 * k + 30, 16 * k), (70, 80, 120, 255))
        for i, im in enumerate([pre["tnt"], out["tnt"], pre["tnt_charged"], out["tnt_charged"]]):
            sheet.alpha_composite(im.resize((16 * k, 16 * k), Image.NEAREST), (i * (16 * k + 10), 0))
        sheet.save(os.path.join(folder, "preview.png"))
        small = Image.new("RGBA", (2 * 100, 70), (70, 80, 120, 255))
        for i, n in enumerate(("tnt", "tnt_charged")):
            small.alpha_composite(so.hud_icon(out[n]).resize((96, 64), Image.NEAREST), (i * 100, 3))
        small.resize((small.width * 2, small.height * 2), Image.NEAREST).save(os.path.join(folder, "preview_hud.png"))
    print("crate sprites ok")


if __name__ == "__main__":
    main()
