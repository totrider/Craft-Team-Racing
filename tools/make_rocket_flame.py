"""
The Rocket's exhaust flame: a 3D model that streams from the tail (like the original missile's flame fans, drawn added to the screen), in addition to the smoke particles.

  python tools/make_rocket_flame.py

1. Draws its picture (textures/item/missile_flame.png, 32 x 16, drawn every run: edit the code, not the PNG): along it (u) white-hot, yellow, orange, red, gone; across it (v) soft.
2. Creates blender/rocket/missile_flame.blend once if it is missing (tools/blender_create_rocket_flame.py); after that the .blend is YOURS and is never overwritten.
3. Exports every .blend under blender/ (tools/export_blender_models.py): models/item/missile_flame.obj and pickup_missile_flame.json (client/MissileRenderer draws it, flickering).
A resource pack with the original's flame (tools/extract_ctr_textures.py) overrides it. Run from the project folder.
"""
import os
import subprocess
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import export_blender_models as ebm  # noqa: E402

TEX = os.path.join("src", "main", "resources", "assets", "craftteamracing", "textures", "item", "missile_flame.png")
BLEND = os.path.join("blender", "rocket", "missile_flame.blend")
STOPS = ((0.0, (255, 250, 230)), (0.18, (255, 225, 110)), (0.45, (255, 140, 40)), (0.75, (220, 50, 20)), (1.0, (120, 10, 0)))


def draw():
    im = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
    for x in range(32):
        u = x / 31.0
        col = STOPS[-1][1]
        for (u0, c0), (u1, c1) in zip(STOPS, STOPS[1:]):
            if u0 <= u <= u1:
                k = (u - u0) / (u1 - u0)
                col = tuple(int(c0[i] + (c1[i] - c0[i]) * k) for i in range(3))
        for y in range(16):
            across = 1.0 - abs((y - 7.5) / 7.5)
            alpha = (across ** 1.2) * (1.0 - u ** 1.5) * 0.95
            im.putpixel((x, y), col + (int(255 * max(0.0, min(1.0, alpha))),))
    return im


def main():
    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    draw().save(TEX)
    if not os.path.exists(BLEND):
        os.makedirs(os.path.dirname(BLEND), exist_ok=True)
        exe = ebm.find_blender()
        if not exe:
            sys.exit("Blender not found (set BLENDER to its blender.exe): cannot create " + BLEND)
        subprocess.run([exe, "--background", "--factory-startup", "--python", os.path.join("tools", "blender_create_rocket_flame.py"), "--", BLEND], check=True)
    ebm.export_all()
    print("rocket flame ok")


if __name__ == "__main__":
    main()
