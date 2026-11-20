#!/usr/bin/env python3
"""Pictures of the power-up sprites for docs/POWERUPS.md (docs/img/powerups/<power-up>.png): the sprite as the HUD shows it, normal form and, where it differs, the charged one beside it.

The mod's own sprites are read from textures/gui/power (96 x 64 HUD pictures: the 18 x 18 outlined sprite stretched 4 x 3); the ones the mod borrows from Minecraft (totem, clock, firework rocket, potion bottle)
are built here the way client/IconSprites builds them at run time (outlined, the potion's liquid tinted), from the 1.21.1 client jar Gradle has downloaded. Only the finished pictures are written.
Needs Pillow.  Usage: python tools/make_powerup_doc_images.py
"""
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sprite_outline import outline  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
GUI = ROOT / "src/main/resources/assets/craftteamracing/textures/gui/power"
OUT = ROOT / "docs/img/powerups"
JAR = Path.home() / ".gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"
ZOOM = 4


def own(name):
    img = Image.open(GUI / f"{name}.png").convert("RGBA")
    return img.crop((12, 5, 84, 59)).resize((18, 18), Image.NEAREST)       # undo the 4 x 3 stretch


def vanilla(jar, path):
    img = Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/{path}.png"))).convert("RGBA")
    return img.crop((0, 0, 16, 16))


def potion(jar, red):
    glass = vanilla(jar, "item/potion")
    fluid = vanilla(jar, "item/potion_overlay")
    tint = (222, 52, 40) if red else (62, 200, 60)
    r, g, b, a = fluid.split()
    fluid = Image.merge("RGBA", [r.point(lambda p: p * tint[0] // 255), g.point(lambda p: p * tint[1] // 255), b.point(lambda p: p * tint[2] // 255), a])
    base = Image.new("RGBA", glass.size, (0, 0, 0, 0))
    base.alpha_composite(fluid)
    base.alpha_composite(glass)
    return outline(base)


def pair(*sprites):
    out = Image.new("RGBA", (2 * 20 * ZOOM, 20 * ZOOM), (0, 0, 0, 0))
    for i, s in enumerate(sprites):
        big = s.resize((s.width * ZOOM, s.height * ZOOM), Image.NEAREST)
        out.alpha_composite(big, (i * 20 * ZOOM + (20 * ZOOM - big.width) // 2, (20 * ZOOM - big.height) // 2))
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    jar = zipfile.ZipFile(JAR)
    sets = {
        "turbo": [own("turbo")],
        "bomb": [own("bomb"), own("bomb_charged")],
        "missile": [outline(vanilla(jar, "item/firework_rocket"))],
        "tnt": [own("tnt"), own("tnt_charged")],
        "potion": [potion(jar, False), potion(jar, True)],
        "shield": [own("shield"), own("shield_charged")],
        "mask": [outline(vanilla(jar, "item/totem_of_undying")), own("mask_dark")],
        "warpball": [own("warpball")],
        "clock": [outline(vanilla(jar, "item/clock_00"))],
    }
    for name, sprites in sets.items():
        pair(*sprites).save(OUT / f"{name}.png")
    print(f"{len(sets)} pictures in {OUT}")


if __name__ == "__main__":
    main()
