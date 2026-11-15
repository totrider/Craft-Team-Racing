"""
The kart upgrades' inventory pictures (16 x 16, stand-ins): offroad tires (a fat treaded tyre) and a soft bumper (a padded bar).

  python tools/make_upgrade_icons.py
"""
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "ctrcraft", "textures", "item")


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


def main():
    os.makedirs(OUT, exist_ok=True)
    tires().save(os.path.join(OUT, "offroad_tires.png"))
    bumper().save(os.path.join(OUT, "soft_bumper.png"))
    floater().save(os.path.join(OUT, "floater.png"))
    obsidian_floater().save(os.path.join(OUT, "obsidian_floater.png"))
    gravity().save(os.path.join(OUT, "low_gravity_module.png"))
    spring().save(os.path.join(OUT, "jump_spring.png"))
    print("written")


if __name__ == "__main__":
    main()
