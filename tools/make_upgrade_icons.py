"""
The kart upgrades' inventory pictures (16 x 16, stand-ins): offroad tires (a fat treaded tyre) and a soft bumper (a padded bar).

  python tools/make_upgrade_icons.py
"""
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "item")


def tires():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 7.2:
                px[x, y] = (35, 35, 38, 255)
            if d <= 7.2 and d > 5.2 and (x + y) % 3 == 0:
                px[x, y] = (95, 95, 100, 255)            # the tread
            if d <= 3.2:
                px[x, y] = (170, 170, 175, 255)          # the hub
            if d <= 1.2:
                px[x, y] = (80, 80, 85, 255)
    return img


def bumper():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(5, 12):
        for x in range(1, 15):
            edge = x in (1, 14) or y in (5, 11)
            px[x, y] = (150, 20, 20, 255) if edge else (235, 60, 55, 255)
    for x in range(3, 13):
        px[x, 7] = (255, 130, 120, 255)                  # the sheen of the padding
    for x in (3, 12):
        for y in range(11, 14):
            px[x, y] = (110, 110, 115, 255)              # the brackets
    return img


def floater():
    """A ring buoy: red and white segments round a hole."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 3.0 <= d <= 7.3:
                seg = int((math_atan(y - 7.5, x - 7.5) + 3.1416) / (6.2832 / 8)) % 2
                px[x, y] = (235, 235, 235, 255) if seg else (215, 45, 45, 255)
                if d >= 6.5 or d <= 3.8:
                    px[x, y] = (110, 30, 30, 255) if seg == 0 else (150, 150, 155, 255)
    return img


def obsidian_floater():
    """The ring buoy in obsidian: violet-black and dark purple segments with a glowing orange rim."""
    img = floater()
    px = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if r > 200 and g < 80:                      # the red segments
                px[x, y] = (28, 18, 46, 255)
            elif r > 200:                               # the white segments
                px[x, y] = (88, 52, 140, 255)
            elif r > 140 and g < 60:                    # the dark red edge
                px[x, y] = (12, 8, 22, 255)
            else:
                px[x, y] = (255, 120, 30, 255) if (x + y) % 4 == 0 else (50, 40, 70, 255)
    return img


def paddle():
    """A steamboat's paddle wheel: a rim, eight spokes and a wooden blade at the end of each, round an iron hub."""
    import math
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx = cy = 7.5
    wood, wood_dark, iron, rim = (176, 124, 70, 255), (112, 72, 38, 255), (120, 124, 136, 255), (150, 154, 166, 255)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if 6.2 <= d <= 7.5:
                px[x, y] = rim                                   # the rim
    for k in range(8):                                           # eight spokes with a blade each
        a = k * math.pi / 4 + math.pi / 8
        for t in range(2, 7):
            x, y = int(round(cx + math.cos(a) * t)), int(round(cy + math.sin(a) * t))
            px[x, y] = wood_dark
        bx, by = cx + math.cos(a) * 6.8, cy + math.sin(a) * 6.8
        for ox in (-1, 0, 1):
            for oy in (-1, 0, 1):
                x, y = int(round(bx + ox)), int(round(by + oy))
                if 0 <= x < 16 and 0 <= y < 16 and math.hypot(x - cx, y - cy) > 5.4:
                    px[x, y] = wood_dark if (ox == 1 and oy == 1) or (ox == -1 and oy == -1) else wood
    for y in range(6, 10):
        for x in range(6, 10):
            if math.hypot(x - cx, y - cy) <= 2.4:
                px[x, y] = iron                                  # the hub
    px[7, 7] = px[8, 8] = (160, 164, 176, 255)
    return img


def math_atan(dy, dx):
    import math
    return math.atan2(dy, dx)


def gravity():
    """A small violet box with a feather-light arrow pointing down."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 13)
            px[x, y] = (70, 45, 120, 255) if edge else (130, 100, 200, 255)
    for k in range(5):
        for x in range(7 - k, 9 + k):
            px[x, 5 + k] = (230, 225, 255, 255)
    for y in range(4, 7):
        px[7, y] = px[8, y] = (230, 225, 255, 255)
    return img


def spring():
    """A coil spring: steel loops on a plate."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(3, 13):
        px[x, 13] = px[x, 14] = (90, 90, 98, 255)
        px[x, 1] = px[x, 2] = (90, 90, 98, 255)
    for k, y in enumerate(range(3, 13, 2)):
        for x in range(4, 12):
            px[x, y] = (200, 200, 208, 255) if k % 2 == 0 else (140, 140, 150, 255)
        px[4 if k % 2 == 0 else 11, y + 1] = (200, 200, 208, 255)
    return img


def snow_skis():
    """A pair of skis set side by side, seen from above at a slant: light blue boards with upturned tips, dark bindings and a white rim of snow."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for off in (0, 5):
        for t in range(14):                                   # along the board, from the tail (bottom left) to the tip (top right)
            x = 1 + t + off // 2
            y = 14 - t + off // 2 - (1 if t > 11 else 0)      # the tip turns up
            for w in range(-1, 2):
                xx, yy = x + (w if t % 2 == 0 else 0), y + (0 if t % 2 == 0 else w)
                if 0 <= xx < 16 and 0 <= yy < 16:
                    px[xx, yy] = (120, 185, 225, 255) if w == 0 else (70, 130, 185, 255)
            if 0 <= x < 16 and 0 <= y - 1 < 16:
                px[x, y - 1] = (235, 245, 255, 255)               # the snow on top
        bx, by = 7 + off // 2, 8 + off // 2
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):          # the binding
            if 0 <= bx + dx < 16 and 0 <= by + dy < 16:
                px[bx + dx, by + dy] = (45, 40, 50, 255)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    tires().save(os.path.join(OUT, "offroad_tires.png"))
    bumper().save(os.path.join(OUT, "soft_bumper.png"))
    floater().save(os.path.join(OUT, "floater.png"))
    paddle().save(os.path.join(OUT, "paddle.png"))
    obsidian_floater().save(os.path.join(OUT, "obsidian_floater.png"))
    gravity().save(os.path.join(OUT, "low_gravity_module.png"))
    spring().save(os.path.join(OUT, "jump_spring.png"))
    snow_skis().save(os.path.join(OUT, "snow_skis.png"))
    print("written")


if __name__ == "__main__":
    main()
