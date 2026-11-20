"""Generates the resources of the three pickup spot blocks (fruit, fruit crate, power-up crate): the textures, block models, item models,
blockstates and loot tables. Stand-in art: a small marker block per kind, to be replaced by real models later.

Run from the project root:  python tools/make_pickup_spots.py
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "craftteamracing")
DATA = os.path.join(RES, "data", "craftteamracing")

SPOTS = {
    "fruit_spot": ((255, 140, 40), "F"),
    "fruit_crate_spot": ((190, 120, 60), "C"),
    "powerup_crate_spot": ((250, 210, 40), "?"),
    "trigger_crate_spot": ((235, 80, 60), "!"),
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def pastel(colour, k=0.5):
    """The colour a share {k} of the way to white: the spot markers' body, so the glyph and the marker read against the ground."""
    return tuple(int(c + (255 - c) * k) for c in colour)


def texture(name, colour, glyph):
    colour = pastel(colour)
    img = Image.new("RGBA", (16, 16), colour + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=(30, 20, 40, 255))
    d.rectangle([1, 1, 14, 14], outline=tuple(max(0, c - 60) for c in colour) + (255,))
    d.text((6 if glyph == "!" else 5, 2), glyph, fill=(30, 20, 40, 255))
    path = os.path.join(ASSETS, "textures", "block", name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


for name, (colour, glyph) in SPOTS.items():
    texture(name, colour, glyph)
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"), {
        "textures": {"all": "craftteamracing:block/" + name, "particle": "craftteamracing:block/" + name},
        "elements": [{
            "from": [4, 0, 4], "to": [12, 8, 12],
            "faces": {side: {"uv": [0, 0, 16, 16], "texture": "#all"} for side in ("down", "up", "north", "south", "west", "east")},
        }],
    })
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": "craftteamracing:block/" + name}}})
    # the item: a flat picture of the marker's texture (like the track nodes' items): centred in the slot, in the texture's own colours, and a small sprite in the hand
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "craftteamracing:block/" + name}})
    write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0, "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": "craftteamracing:" + name}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })


def link_node():
    """The Redstone Linking Node: a small visible block (a dark frame round a redstone core; lit when it gives a signal) with its models, state and loot."""
    for suffix, core, glow in (("", (210, 50, 40), (255, 130, 100)), ("_on", (255, 90, 60), (255, 225, 170))):
        img = Image.new("RGBA", (16, 16), (150, 142, 156, 255))
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, 15, 15], outline=(96, 88, 100, 255))
        d.rectangle([1, 1, 14, 14], outline=(196, 190, 204, 255))
        d.rectangle([4, 4, 11, 11], fill=core + (255,), outline=(96, 40, 40, 255))
        d.rectangle([6, 6, 9, 9], fill=glow + (255,))
        path = os.path.join(ASSETS, "textures", "block", "redstone_link_node" + suffix + ".png")
        img.save(path)
        write_json(os.path.join(ASSETS, "models", "block", "redstone_link_node" + suffix + ".json"), {
            "textures": {"all": "craftteamracing:block/redstone_link_node" + suffix, "particle": "craftteamracing:block/redstone_link_node" + suffix},
            "elements": [{"from": [4, 4, 4], "to": [12, 12, 12], "faces": {side: {"uv": [0, 0, 16, 16], "texture": "#all"} for side in ("down", "up", "north", "south", "west", "east")}}],
        })
    write_json(os.path.join(ASSETS, "blockstates", "redstone_link_node.json"), {"variants": {
        "powered=false": {"model": "craftteamracing:block/redstone_link_node"}, "powered=true": {"model": "craftteamracing:block/redstone_link_node_on"}}})
    write_json(os.path.join(ASSETS, "models", "item", "redstone_link_node.json"), {"parent": "craftteamracing:block/redstone_link_node"})
    write_json(os.path.join(DATA, "loot_table", "blocks", "redstone_link_node.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": "craftteamracing:redstone_link_node"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })


link_node()


def icons():
    """A flat, bright item picture for the Redstone Linking Node (its small block model is tiny and dark in a slot); the trigger crate spot looks like the other spots."""
    tex = os.path.join(ASSETS, "textures", "item")
    os.makedirs(tex, exist_ok=True)
    node = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(node)
    d.rectangle([3, 3, 12, 12], fill=(170, 162, 176, 255), outline=(96, 88, 100, 255))
    d.rectangle([5, 5, 10, 10], fill=(215, 52, 42, 255), outline=(110, 40, 40, 255))
    d.rectangle([7, 7, 8, 8], fill=(255, 190, 150, 255))
    for x, y in ((1, 7), (1, 8), (14, 7), (14, 8), (7, 1), (8, 1), (7, 14), (8, 14)):
        node.putpixel((x, y), (255, 120, 90, 255))                       # a wireless signal
    node.save(os.path.join(tex, "redstone_link_node.png"))
    for name in ("redstone_link_node",):
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "craftteamracing:item/" + name}})


icons()
print("pickup spot resources written")
