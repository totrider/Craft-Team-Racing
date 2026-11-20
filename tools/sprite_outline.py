"""
The mod's own power-up sprites: 16 x 16 pixel art WITHOUT an outline, and the step that makes them readable on any background.

  outline(sprite)      adds a one pixel black outline all round (8-neighbourhood): at most 18 x 18
  hud_icon(sprite)     the HUD / billboard picture: the outlined sprite in a 96 x 64 texture

Why 96 x 64: the original game's icons are 3 : 2 pictures (24 x 16 texels) that the game stretches over a box of 48 x 32 reference pixels, and a reference pixel is 0.7361 as wide as it is
tall, so a picture of the original's shape is shown 1.104 : 1 on the screen (client/ItemHud, IconBillboard). To stay compatible with that shape and still show a square sprite square, the
outlined sprite is put into a 3 : 2 texture, stretched 4 x 3 texels a sprite pixel (4 / 3 is 1 / 0.7361 to within two per cent), centred: 72 x 54 of the 96 x 64. The same is done at run
time for Minecraft's own sprites the mod borrows (client/IconSprites). Used by tools/make_power_sprites.py.
"""
from PIL import Image

OUTLINE = (0, 0, 0, 255)
CANVAS = (96, 64)
STRETCH = (4, 3)


def outline(sprite):
    w, h = sprite.size
    out = Image.new("RGBA", (w + 2, h + 2), (0, 0, 0, 0))
    src = sprite.convert("RGBA")
    solid = lambda x, y: 0 <= x < w and 0 <= y < h and src.getpixel((x, y))[3] > 0
    for y in range(h + 2):
        for x in range(w + 2):
            if solid(x - 1, y - 1):
                out.putpixel((x, y), src.getpixel((x - 1, y - 1)))
            elif any(solid(x - 1 + dx, y - 1 + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                out.putpixel((x, y), OUTLINE)
    return out


def hud_icon(sprite):
    o = outline(sprite)
    sx, sy = STRETCH
    big = o.resize((o.width * sx, o.height * sy), Image.NEAREST)
    canvas = Image.new("RGBA", CANVAS, (0, 0, 0, 0))
    canvas.alpha_composite(big, ((CANVAS[0] - big.width) // 2, (CANVAS[1] - big.height) // 2))
    return canvas
