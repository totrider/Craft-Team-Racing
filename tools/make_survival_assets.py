"""
The survival side's assets and data (docs/SURVIVAL.md): textures, models, blockstates, language entries, recipes, loot tables, tags and world generation for the Power Palm and its wood set,
the Power Fruit's chain (juice, raw power ore, power ingot, nugget, block), the hanging fruit clusters, the wheel block, the bag of power fruit and the crate blocks. Run from the project
folder (also run by the build script):

  python tools/make_survival_assets.py

Everything it writes is generated and rewritten each run (edit this script, not the files), except the Blockbench models in blockbench/survival/, which are written once and are then yours
(the models are read from them every run):
  power_palm_leaves.bbmodel  the fluffy fronds: a cube and crossed planes that stick out of it (groups base, v1a ... v4b; variant n is base + vna + vnb)
  power_fruit_pods.bbmodel   the hanging fruit: one to four of the race track's Power Fruit (groups p1 ... p4), top at the underside of a frond
  wheel_block.bbmodel        the wheel block: groups w1 ... w4, the kart's wheel flat (1), stacked (2), two stacked and one leaning (3), a two by two (4)
  power_fruit_bag.bbmodel    the bag of power fruit
Textures are original pixel art in the power fruit's colours (red, orange, yellow, lime): the ingot and nugget carry a subtle yellow spark (an animated picture); outlines are the dark of
the colours they enclose, not black. They are drawn as layered sheets by tools/survival_art.py and written as krita/blocks/*.kra and krita/items/*.kra (a modder's templates); tools/build_art.py
flattens those into the PNGs. The crate pictures are layered sheets too (made by tools/make_pickup_models.py's functions); the Power Fruit's item picture comes from tools/make_power_sprites.py. The fluffy fronds are in the manner of the "Better Leaves" resource pack's models (crossed planes round a cube; MIT licence, (c) Motschen).
"""
import copy
import json
import math
import os
import random
import re
import shutil
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402
import survival_art  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "craftteamracing")
DATA = os.path.join(RES, "data")
NS = "craftteamracing"
C = NS + ":"
BB = os.path.join(ROOT, "blockbench", "survival")

def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


def save(img, rel):
    path = os.path.join(ASSETS, "textures", rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def mcmeta(rel, frames, frametime):
    """The animation file of a strip texture (the strip itself is built from its .kra by tools/build_art.py)."""
    write_json(os.path.join(ASSETS, "textures", rel + ".mcmeta"), {"animation": {"frametime": frametime, "frames": list(range(frames))}})


def crate_sheets():
    """The planked crates' pictures as layered sheets, from the pickups' own art: the side (a frame round the panel; the crate's face is the same picture), the "?" crate's, and the top
    (the frame round plain oak). {name: sheet}; tools/build_art.py flattens them into the PNGs listed in krita/manifest.json."""
    import make_pickup_models as pm
    return {
        "item_crate": pm.crate_block_sheet(pm.blank_panel_layers),             # also item_crate_planked_side
        "item_crate_random": pm.crate_block_sheet(pm.question_panel_layers),
        "item_crate_planked_top": pm.crate_block_sheet(pm.oak_top_panel_layers),
        "fruit_crate": pm.crate_block_sheet(pm.plank_panel_layers),            # also fruit_crate_planked_side
        "fruit_crate_planked_top": pm.crate_block_sheet(pm.oak_top_panel_layers),
    }


# ======================================================================== blockbench sources (written once, then yours)
def fruit_elements():
    model = bbmodel.read(os.path.join(ROOT, "blockbench", "pickups", "power_fruit.bbmodel"))
    return bbmodel.elements(model, "body", "leaves"), model["display"]


def moved(el, dx, dy, dz):
    e = copy.deepcopy(el)
    e["from"] = [round(e["from"][0] + dx, 4), round(e["from"][1] + dy, 4), round(e["from"][2] + dz, 4)]
    e["to"] = [round(e["to"][0] + dx, 4), round(e["to"][1] + dy, 4), round(e["to"][2] + dz, 4)]
    if "rotation" in e:
        e["rotation"]["origin"] = [round(e["rotation"]["origin"][0] + dx, 4), round(e["rotation"]["origin"][1] + dy, 4), round(e["rotation"]["origin"][2] + dz, 4)]
    return e


POD_LAYOUT = {
    1: [(0.0, 9.5, 0.0)],
    2: [(-2.6, 9.5, -1.5), (2.6, 8.5, 1.5)],
    3: [(-3.0, 9.5, -2.5), (3.0, 8.5, -1.0), (0.0, 9.0, 3.0)],
    4: [(-3.0, 9.5, -3.0), (3.0, 8.5, -3.0), (-3.0, 8.5, 3.0), (3.0, 9.5, 3.0)],
}


def ensure_pods_bbmodel():
    path = os.path.join(BB, "power_fruit_pods.bbmodel")
    if not os.path.exists(path):
        els, _ = fruit_elements()
        groups = [("p%d" % n, [moved(el, dx, dy, dz) for (dx, dy, dz) in pos for el in els]) for n, pos in POD_LAYOUT.items()]
        tex = os.path.join(ROOT, "src", "main", "resources", "assets", "craftteamracing", "textures", "item", "pickups.png")
        bbmodel.write(path, "power_fruit_pods", {"pickups": tex}, groups, gui_light_front=False,
                      comment="Power Fruit hanging under palm fronds: groups p1 to p4 are the cluster of one to four fruit (the race track's Power Fruit, copied and spread). The top of "
                              "each fruit is at the underside of the frond above (y 16). Texture slot 'pickups' is the pickups sheet.")
    return bbmodel.read(path)


def leaf_plane(z, angle, along="z", span=32, lift=-6):
    """A plane of the fluffy fronds, both sides, tilted by {angle} about the vertical through its middle; along "z" it faces north/south at depth z, along "x" east/west at depth z."""
    face = {"uv": [0, 0, 16, 16], "texture": "#all"}
    if along == "z":
        el = {"from": [-8, lift, z], "to": [-8 + span, lift + 28, z], "faces": {"north": dict(face), "south": dict(face)}, "rotation": {"origin": [8, 8, z], "axis": "y", "angle": angle}}
    else:
        el = {"from": [z, lift, -8], "to": [z, lift + 28, -8 + span], "faces": {"east": dict(face), "west": dict(face)}, "rotation": {"origin": [z, 8, 8], "axis": "y", "angle": angle}}
    return el


def ensure_leaves_bbmodel():
    path = os.path.join(BB, "power_palm_leaves.bbmodel")
    if not os.path.exists(path):
        base_face = {"uv": [4, 4, 12, 12], "texture": "#all"}                      # the middle of the 32 x 32 picture
        base = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {f: dict(base_face, cullface=f) for f in bbmodel.FACES}}
        groups = [("base", [base]),
                  ("v1a", [leaf_plane(8, 22.5)]), ("v1b", [leaf_plane(4, -45)]),
                  ("v2a", [leaf_plane(12, 45)]), ("v2b", [leaf_plane(8, -22.5)]),
                  ("v3a", [leaf_plane(9, 22.5)]), ("v3b", [leaf_plane(9, 22.5, along="x")]),
                  ("v4a", [leaf_plane(8, -22.5, lift=-5)]), ("v4b", [leaf_plane(7, -22.5, along="x", lift=-5)])]
        tex = os.path.join(RES, "resourcepacks", "fluffy_fronds", "assets", NS, "textures", "block", "power_palm_leaves_fluffy.png")
        bbmodel.write(path, "power_palm_leaves", {"all": tex}, groups, gui_light_front=False,
                      comment="The fluffy Power Palm fronds, in the manner of the Better Leaves resource pack: a plain cube (group base) and crossed planes that stick out of it (two per "
                              "variant: v1a + v1b, v2a + v2b ...). The block state picks one of the four variants and a turn at random. Texture slot 'all' is the fronds' picture.")
    return bbmodel.read(path)


def ensure_bag_bbmodel():
    path = os.path.join(BB, "power_fruit_bag.bbmodel")
    if not os.path.exists(path):
        def face(uv, tex):
            return {"uv": uv, "texture": "#" + tex}
        lower = {"from": [2, 0, 2], "to": [14, 10, 14], "faces": {f: face([2, 6, 14, 16], "side") for f in ("north", "east", "south", "west")}}
        lower["faces"]["down"] = face([2, 2, 14, 14], "bottom")
        rim = {"from": [1, 10, 1], "to": [15, 14, 15], "faces": {f: face([0, 3, 16, 7], "side") for f in ("north", "east", "south", "west")}}
        rim["faces"]["up"] = face([0, 0, 16, 16], "top")
        rim["faces"]["down"] = face([0, 0, 16, 16], "bottom")
        tex = lambda n: os.path.join(ASSETS, "textures", "block", n + ".png")
        bbmodel.write(path, "power_fruit_bag", {"side": tex("power_fruit_bag_side"), "top": tex("power_fruit_bag_top"), "bottom": tex("power_fruit_bag_bottom")},
                      [("body", [lower, rim])], gui_light_front=False,
                      comment="The bag of power fruit (own sack pictures; with Farm & Charm installed a built-in resource pack puts its sack model and side/bottom pictures under our top picture). "
                              "Slots: side, top (the heap of fruit with a two pixel burlap edge), bottom.")
    return bbmodel.read(path)


# ---- the wheel block: the kart's wheel (blockbench/kart/kart_wheel.bbmodel), moved and turned
def rot_x(v):                                  # the wheel's axle (x) becomes the upright (y): flat wheels
    return (-v[1], v[0], v[2])


def rot_z(v):                                  # the axle becomes z: standing wheels
    return (v[2], v[1], -v[0])


NORMALS = {"east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0), "south": (0, 0, 1), "north": (0, 0, -1)}
AXIS_NAME = {(1, 0, 0): "x", (0, 1, 0): "y", (0, 0, 1): "z"}


def turn(el, M, centre, tilt=None):
    """The wheel element {el} (centred on 8, 8, 8) with its axes sent through {M} (a signed axis permutation) and moved to {centre}; {tilt}: degrees about x for the whole wheel."""
    f = [el["from"][i] - 8 for i in range(3)]
    t = [el["to"][i] - 8 for i in range(3)]
    a, b = M(f), M(t)
    out = {"from": [round(min(a[i], b[i]) + centre[i], 4) for i in range(3)], "to": [round(max(a[i], b[i]) + centre[i], 4) for i in range(3)], "faces": {}}
    for name, face in el["faces"].items():
        n = M(NORMALS[name])
        new = [k for k, v in NORMALS.items() if v == tuple(int(round(c)) for c in n)][0]
        out["faces"][new] = copy.deepcopy(face)
    if "rotation" in el:
        r = el["rotation"]
        axis_vec = tuple((1, 0, 0) if r["axis"] == "x" else (0, 1, 0) if r["axis"] == "y" else (0, 0, 1))
        m = M(axis_vec)
        sign = 1 if sum(m) > 0 else -1
        o = M([r["origin"][i] - 8 for i in range(3)])
        out["rotation"] = {"origin": [round(o[i] + centre[i], 4) for i in range(3)], "axis": AXIS_NAME[tuple(abs(int(round(c))) for c in m)], "angle": r["angle"] * sign}
    elif tilt:
        out["rotation"] = {"origin": [round(c, 4) for c in centre], "axis": "x", "angle": tilt}
    return out


def ensure_wheel_bbmodel():
    path = os.path.join(BB, "wheel_block.bbmodel")
    if not os.path.exists(path):
        src = bbmodel.read(os.path.join(ROOT, "blockbench", "kart", "kart_wheel.bbmodel"))
        tyre, faces = src["groups"]["tyre"], src["groups"]["faces"]
        wheel = tyre + faces

        def flat(x, y, z):
            return [turn(e, rot_x, (x, y, z)) for e in wheel]
        w3 = flat(4.2, 1.5, 8.0) + flat(11.8, 1.5, 8.0) + flat(8.0, 4.5, 8.0)                    # a pyramid: two side by side, one on top of both
        groups = [("w1", flat(8, 1.5, 8)), ("w2", flat(8, 1.5, 8) + flat(8, 4.5, 8)), ("w3", w3),
                  ("w4", flat(4.0, 1.5, 4.0) + flat(12.0, 1.5, 4.0) + flat(4.0, 1.5, 12.0) + flat(12.0, 1.5, 12.0))]
        tex = os.path.join(ASSETS, "textures", "item", "kart_wheel.png")
        bbmodel.write(path, "wheel_block", {"wheel": tex}, groups, gui_light_front=False,
                      comment="The wheel block, made from the kart's wheel (blockbench/kart/kart_wheel.bbmodel): w1 one wheel lying in the middle, w2 two stacked, w3 a pyramid (two side by side, one on top), "
                              "w4 a two by two. Texture slot 'wheel' is the kart wheel's picture.")
    return bbmodel.read(path)


# ======================================================================== recipes
def shaped(name, pattern, key, result, count=1):
    write_json(os.path.join(DATA, NS, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
        "pattern": pattern, "result": {"id": result, "count": count}})


def shapeless(name, ingredients, result, count=1):
    write_json(os.path.join(DATA, NS, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [({"tag": v[1:]} if v.startswith("#") else {"item": v}) for v in ingredients], "result": {"id": result, "count": count}})


def cooking(kind, name, ingredient, result, xp, ticks):
    write_json(os.path.join(DATA, NS, "recipe", name + ".json"), {
        "type": "minecraft:" + kind, "category": "misc", "ingredient": {"item": ingredient}, "result": {"id": result}, "experience": xp, "cookingtime": ticks})


def recipes():
    # the chain
    cooking("smelting", "power_juice_from_fruit", C + "power_fruit", C + "power_juice", 0.35, 200)             # as raw beef: furnace, smoker (faster) or campfire (slow)
    cooking("smoking", "power_juice_from_fruit_smoking", C + "power_fruit", C + "power_juice", 0.35, 100)
    cooking("campfire_cooking", "power_juice_from_fruit_campfire", C + "power_fruit", C + "power_juice", 0.35, 600)
    shapeless("raw_power_ore_from_juice", [C + "power_juice", "minecraft:raw_iron"], C + "raw_power_ore", 2)
    cooking("smelting", "power_ingot_from_raw", C + "raw_power_ore", C + "power_ingot", 1.0, 200)
    cooking("blasting", "power_ingot_from_raw_blasting", C + "raw_power_ore", C + "power_ingot", 1.0, 100)
    shapeless("power_nugget_from_ingot", [C + "power_ingot"], C + "power_nugget", 9)
    shaped("power_ingot_from_nuggets", ["NNN", "NNN", "NNN"], {"N": C + "power_nugget"}, C + "power_ingot")
    shaped("power_block", ["III", "III", "III"], {"I": C + "power_ingot"}, C + "power_block")
    shapeless("power_ingot_from_block", [C + "power_block"], C + "power_ingot", 9)
    # the bag
    shaped("power_fruit_bag", ["FFF", "FFF", "FFF"], {"F": C + "power_fruit"}, C + "power_fruit_bag")
    shapeless("power_fruit_from_bag", [C + "power_fruit_bag"], C + "power_fruit", 9)
    # the palm's wood
    shapeless("power_palm_planks", ["#" + NS + ":power_palm_logs"], C + "power_palm_planks", 4)
    shaped("power_palm_wood", ["LL", "LL"], {"L": C + "power_palm_log"}, C + "power_palm_wood", 3)
    shaped("stripped_power_palm_wood", ["LL", "LL"], {"L": C + "stripped_power_palm_log"}, C + "stripped_power_palm_wood", 3)
    p = {"P": C + "power_palm_planks"}
    shaped("power_palm_stairs", ["P  ", "PP ", "PPP"], p, C + "power_palm_stairs", 4)
    shaped("power_palm_slab", ["PPP"], p, C + "power_palm_slab", 6)
    shaped("power_palm_fence", ["PSP", "PSP"], dict(p, S="minecraft:stick"), C + "power_palm_fence", 3)
    shaped("power_palm_fence_gate", ["SPS", "SPS"], dict(p, S="minecraft:stick"), C + "power_palm_fence_gate")
    shaped("power_palm_door", ["PP", "PP", "PP"], p, C + "power_palm_door", 3)
    shaped("power_palm_trapdoor", ["PPP", "PPP"], p, C + "power_palm_trapdoor", 2)
    shapeless("power_palm_button", [C + "power_palm_planks"], C + "power_palm_button")
    shaped("power_palm_pressure_plate", ["PP"], p, C + "power_palm_pressure_plate")
    # the crates: an oak frame round a spruce (item) or dark oak (fruit) panel, with a power nugget or a fruit in the middle; the planked kind and the marked kind convert both ways
    shaped("item_crate", ["OSO", "SNS", "OSO"], {"O": "minecraft:oak_planks", "S": "minecraft:spruce_planks", "N": C + "power_nugget"}, C + "item_crate", 2)
    shaped("fruit_crate", ["ODO", "DFD", "ODO"], {"O": "minecraft:oak_planks", "D": "minecraft:dark_oak_planks", "F": C + "power_fruit"}, C + "fruit_crate", 2)
    # the crate blocks look-alikes are cut from one another at the stonecutter (any of a kind into any other of it)
    for group in (("item_crate", "item_crate_planked", "item_crate_random"), ("fruit_crate", "fruit_crate_planked")):
        for src in group:
            for dst in group:
                if src != dst:
                    write_json(os.path.join(DATA, NS, "recipe", "stonecutting_%s_to_%s.json" % (src, dst)), {
                        "type": "minecraft:stonecutting", "ingredient": {"item": C + src}, "result": {"id": C + dst, "count": 1}})
    # the wheel (four wool and an iron ingot make four wheels)
    shaped("wheel", [" B ", "BIB", " B "], {"B": "minecraft:black_wool", "I": "minecraft:iron_ingot"}, C + "wheel", 4)
    # the boost blocks
    shaped("boost_pad", ["NNN", "SSS"], {"N": C + "power_nugget", "S": "minecraft:cobblestone"}, C + "boost_pad", 4)
    shaped("boost_block", ["SSS", "SPS", "SSS"], {"S": "minecraft:cobblestone", "P": C + "power_ingot"}, C + "boost_block", 8)
    shaped("boost_slab", ["BBB"], {"B": C + "boost_block"}, C + "boost_slab", 6)
    shaped("boost_stairs", ["B  ", "BB ", "BBB"], {"B": C + "boost_block"}, C + "boost_stairs", 4)
    # the Super Boost family: one lapis lazuli makes four boost pads or four boost blocks super. The slab and the stairs are cut from super blocks as the regular ones are (3 blocks, 6 slabs;
    # 6 blocks, 4 stairs), or upgraded from the regular ones with the lapis in step with the block it covers: a lapis a four blocks, so eight slabs (four blocks) or three stairs (four and a half) to a lapis
    L = "minecraft:lapis_lazuli"
    shapeless("super_boost_pad", [C + "boost_pad"] * 4 + [L], C + "super_boost_pad", 4)
    shapeless("super_boost_block", [C + "boost_block"] * 4 + [L], C + "super_boost_block", 4)
    shaped("super_boost_slab", ["BBB"], {"B": C + "super_boost_block"}, C + "super_boost_slab", 6)
    shaped("super_boost_stairs", ["B  ", "BB ", "BBB"], {"B": C + "super_boost_block"}, C + "super_boost_stairs", 4)
    shapeless("super_boost_slab_from_boost_slab", [C + "boost_slab"] * 8 + [L], C + "super_boost_slab", 8)
    shapeless("super_boost_stairs_from_boost_stairs", [C + "boost_stairs"] * 3 + [L], C + "super_boost_stairs", 3)
    # the kart and its tools
    shaped("kart", ["WLW", "PMP", "WRW"], {"W": C + "wheel", "L": "minecraft:leather", "P": C + "power_ingot", "M": "minecraft:minecart", "R": "minecraft:redstone"}, C + "kart")
    shaped("offroad_tires", ["WSW", "SIS", "WSW"], {"W": C + "wheel", "S": "minecraft:slime_ball", "I": "minecraft:iron_ingot"}, C + "offroad_tires")
    shaped("paddle", ["IPI", "PWP", "IPI"], {"I": "minecraft:iron_ingot", "P": "#minecraft:planks", "W": C + "power_ingot"}, C + "paddle")
    shaped("snow_skis", ["PIP", "PIP"], {"P": "minecraft:packed_ice", "I": "minecraft:iron_ingot"}, C + "snow_skis")
    shaped("wrench", ["IPI", " I ", " I "], {"I": "minecraft:iron_ingot", "P": C + "power_ingot"}, C + "wrench")
    shaped("track_tool", ["  P", " S ", "S  "], {"P": C + "power_ingot", "S": "minecraft:stick"}, C + "track_tool")
    shapeless("rulebook", ["minecraft:book", C + "power_nugget", "minecraft:redstone"], C + "rulebook")
    shapeless("drop_table", ["minecraft:book", C + "power_nugget", "#" + NS + ":item_crates"], C + "drop_table")
    shapeless("track_music_sheet", ["minecraft:paper", "minecraft:note_block", C + "power_nugget"], C + "track_music_sheet")
    # the track's blocks
    shaped("checkpoint_node", ["N", "R"], {"N": C + "power_nugget", "R": "minecraft:redstone"}, C + "checkpoint_node", 2)
    shapeless("start_node", [C + "checkpoint_node", C + "power_ingot"], C + "start_node")
    for place, ingot in ((1, "minecraft:gold_ingot"), (2, "minecraft:iron_ingot"), (3, "minecraft:copper_ingot")):
        shapeless("podium_%d_node" % place, [C + "checkpoint_node", ingot], C + "podium_%d_node" % place)
    shapeless("fruit_spot", [C + "power_fruit", C + "checkpoint_node"], C + "fruit_spot")
    shapeless("fruit_crate_spot", ["#" + NS + ":fruit_crates", C + "checkpoint_node"], C + "fruit_crate_spot")
    shapeless("powerup_crate_spot", ["#" + NS + ":item_crates", C + "checkpoint_node"], C + "powerup_crate_spot")
    shapeless("trigger_crate_spot", ["#" + NS + ":item_crates", C + "checkpoint_node", "minecraft:redstone_torch"], C + "trigger_crate_spot")
    shapeless("redstone_link_node", [C + "checkpoint_node", "minecraft:redstone", "minecraft:quartz"], C + "redstone_link_node")
    shaped("race_barrier", ["NNN", "N N", "NNN"], {"N": C + "power_nugget"}, C + "race_barrier", 16)


# ======================================================================== loot tables, tags, world generation
def loot(name, entries, random_sequence=True):
    body = {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}], "entries": entries}]}
    if random_sequence:
        body["random_sequence"] = "%s:blocks/%s" % (NS, name)
    write_json(os.path.join(DATA, NS, "loot_table", "blocks", name + ".json"), body)


def loot_self(name):
    loot(name, [{"type": "minecraft:item", "name": C + name}])


def counted(item, block, prop, values):
    """Entries that give {item} as many as the block's {prop} says."""
    return [{"type": "minecraft:item", "name": item, "conditions": [{"condition": "minecraft:block_state_property", "block": block, "properties": {prop: str(n)}}],
             "functions": [{"function": "minecraft:set_count", "count": float(n), "add": False}]} for n in values]


SHEARS_OR_SILK = {"condition": "minecraft:any_of", "terms": [
    {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
    {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}

SELF_DROPS = ("power_palm_log", "power_palm_wood", "stripped_power_palm_log", "stripped_power_palm_wood", "power_palm_planks", "power_palm_stairs", "power_palm_fence",
              "power_palm_fence_gate", "power_palm_trapdoor", "power_palm_button", "power_palm_pressure_plate", "power_palm_sapling", "power_block", "item_crate", "fruit_crate",
              "item_crate_planked", "fruit_crate_planked", "item_crate_random", "power_fruit_bag", "race_barrier")


def loot_tables():
    for name in SELF_DROPS:
        loot_self(name)
    loot("power_palm_slab", [{"type": "minecraft:item", "name": C + "power_palm_slab", "functions": [
        {"function": "minecraft:set_count", "count": 2.0, "add": False, "conditions": [{"condition": "minecraft:block_state_property", "block": C + "power_palm_slab", "properties": {"type": "double"}}]}]}])
    write_json(os.path.join(DATA, NS, "loot_table", "blocks", "power_palm_door.json"), {
        "type": "minecraft:block", "random_sequence": NS + ":blocks/power_palm_door",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}], "entries": [
            {"type": "minecraft:item", "name": C + "power_palm_door", "conditions": [{"condition": "minecraft:block_state_property", "block": C + "power_palm_door", "properties": {"half": "lower"}}]}]}]})
    loot("power_fruit_cluster", counted(C + "power_fruit", C + "power_fruit_cluster", "age", (1, 2, 3, 4)))
    loot("wheel", counted(C + "wheel", C + "wheel", "wheels", (1, 2, 3, 4)))
    not_shears = {"condition": "minecraft:inverted", "term": SHEARS_OR_SILK}
    write_json(os.path.join(DATA, NS, "loot_table", "blocks", "power_palm_leaves.json"), {
        "type": "minecraft:block", "random_sequence": NS + ":blocks/power_palm_leaves",
        "pools": [
            {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [SHEARS_OR_SILK], "entries": [{"type": "minecraft:item", "name": NS + ":power_palm_leaves"}]},
            {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [not_shears, {"condition": "minecraft:survives_explosion"}], "entries": [
                {"type": "minecraft:item", "name": NS + ":power_palm_sapling",
                 "conditions": [{"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": [0.06, 0.07, 0.09, 0.12, 0.2]}]}]},
            {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [not_shears, {"condition": "minecraft:survives_explosion"}], "entries": [
                {"type": "minecraft:item", "name": NS + ":power_fruit",
                 "conditions": [{"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": [0.1, 0.13, 0.17, 0.25, 0.4]}]}]},
            {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [not_shears], "entries": [
                {"type": "minecraft:item", "name": "minecraft:stick",
                 "conditions": [{"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]}],
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 2.0}, "add": False}]}]}]})


def tag(path, values):
    write_json(os.path.join(DATA, path + ".json"), {"values": values})


def tags():
    n = lambda s: NS + ":" + s
    logs = [n("power_palm_log"), n("power_palm_wood"), n("stripped_power_palm_log"), n("stripped_power_palm_wood")]
    for kind in ("block", "item"):
        tag("minecraft/tags/%s/logs" % kind, logs)
        tag("minecraft/tags/%s/logs_that_burn" % kind, logs)
        tag("minecraft/tags/%s/planks" % kind, [n("power_palm_planks")])
        tag("minecraft/tags/%s/leaves" % kind, [n("power_palm_leaves")])
        tag("minecraft/tags/%s/saplings" % kind, [n("power_palm_sapling")])
        tag("minecraft/tags/%s/stairs" % kind, [n("power_palm_stairs")])
        tag("minecraft/tags/%s/wooden_stairs" % kind, [n("power_palm_stairs")])
        tag("minecraft/tags/%s/slabs" % kind, [n("power_palm_slab")])
        tag("minecraft/tags/%s/wooden_slabs" % kind, [n("power_palm_slab")])
        tag("minecraft/tags/%s/fences" % kind, [n("power_palm_fence")])
        tag("minecraft/tags/%s/wooden_fences" % kind, [n("power_palm_fence")])
        tag("minecraft/tags/%s/fence_gates" % kind, [n("power_palm_fence_gate")])
        tag("minecraft/tags/%s/doors" % kind, [n("power_palm_door")])
        tag("minecraft/tags/%s/wooden_doors" % kind, [n("power_palm_door")])
        tag("minecraft/tags/%s/trapdoors" % kind, [n("power_palm_trapdoor")])
        tag("minecraft/tags/%s/wooden_trapdoors" % kind, [n("power_palm_trapdoor")])
        tag("minecraft/tags/%s/buttons" % kind, [n("power_palm_button")])
        tag("minecraft/tags/%s/wooden_buttons" % kind, [n("power_palm_button")])
        tag("minecraft/tags/%s/pressure_plates" % kind, [n("power_palm_pressure_plate")])
        tag("minecraft/tags/%s/wooden_pressure_plates" % kind, [n("power_palm_pressure_plate")])
        tag("c/tags/%s/fences" % kind, [n("power_palm_fence")])
        tag("c/tags/%s/fence_gates" % kind, [n("power_palm_fence_gate")])
        tag(NS + "/tags/%s/power_palm_logs" % kind, logs)
    tag("minecraft/tags/block/mineable/axe", logs + [n(s) for s in ("power_palm_planks", "power_palm_stairs", "power_palm_slab", "power_palm_fence", "power_palm_fence_gate", "power_palm_door",
                                                                    "power_palm_trapdoor", "power_palm_button", "power_palm_pressure_plate", "item_crate", "fruit_crate", "item_crate_random",
                                                                    "item_crate_planked", "fruit_crate_planked", "power_fruit_bag")])
    tag("minecraft/tags/block/mineable/hoe", [n("power_palm_leaves"), n("power_fruit_cluster")])
    tag("minecraft/tags/block/mineable/pickaxe", [n("power_block"), n("race_barrier")])
    tag("minecraft/tags/block/needs_stone_tool", [n("power_block")])
    tag(NS + "/tags/item/item_crates", [n("item_crate"), n("item_crate_planked"), n("item_crate_random")])
    tag(NS + "/tags/item/fruit_crates", [n("fruit_crate"), n("fruit_crate_planked")])
    tag("c/tags/item/ingots/power", [n("power_ingot")])
    tag("c/tags/item/nuggets/power", [n("power_nugget")])
    tag("c/tags/item/storage_blocks/power", [n("power_block")])
    tag("c/tags/block/storage_blocks/power", [n("power_block")])
    tag("c/tags/item/ingots", [n("power_ingot")])
    tag("c/tags/item/nuggets", [n("power_nugget")])
    tag("c/tags/item/raw_materials", [n("raw_power_ore")])
    tag("c/tags/item/foods/fruit", [n("power_fruit")])
    tag("minecraft/tags/item/saplings", [n("power_palm_sapling")])


def worldgen():
    write_json(os.path.join(DATA, NS, "worldgen", "configured_feature", "power_palm.json"), {"type": NS + ":power_palm", "config": {}})
    write_json(os.path.join(DATA, NS, "worldgen", "configured_feature", "power_palm_wild.json"), {"type": NS + ":power_palm_wild", "config": {}})
    write_json(os.path.join(DATA, NS, "worldgen", "placed_feature", "power_palm_beach.json"), {
        "feature": NS + ":power_palm_wild",
        "placement": [
            {"type": "minecraft:count", "count": 8},                                  # tries a chunk: the server's config says how many stand (PowerPalmWildFeature)
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
            {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_block_tag", "offset": [0, -1, 0], "tag": "minecraft:sand"}},
            {"type": "minecraft:biome"}]})
    write_json(os.path.join(DATA, NS, "neoforge", "biome_modifier", "add_power_palms.json"), {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_beach", "features": NS + ":power_palm_beach", "step": "vegetal_decoration"})


# ======================================================================== models, blockstates, language
def block_model(name, obj):
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"), obj)


def item_model(name, parent_block=None, layer=None, display=None):
    if parent_block:
        obj = {"parent": "%s:block/%s" % (NS, parent_block)}
    else:
        obj = {"parent": "minecraft:item/generated", "textures": {"layer0": layer or "%s:item/%s" % (NS, name)}}
    if display:
        obj["display"] = display
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), obj)


def state(name, obj):
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), obj)


def tex(name):
    return NS + ":block/" + name


def stairs_state(name):
    variants = {}
    tops = {"straight": ("", ), }
    for facing, ys in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = name + ("" if shape == "straight" else "_" + shape.split("_")[0])
                y = ys
                if shape.endswith("left"):
                    y = (ys + 270) % 360
                if half == "top":
                    if shape in ("straight",):
                        pass
                    elif shape.endswith("right"):
                        pass
                v = {"model": "%s:block/%s" % (NS, model)}
                x = 0
                if half == "top":
                    x = 180
                    # a flipped stair turns the other way round: the "right" shapes are what the "left" ones are from below
                    if shape != "straight":
                        y = (ys + 90) % 360 if shape.endswith("right") else ys
                    else:
                        y = ys
                if x:
                    v["x"] = x
                if y:
                    v["y"] = y
                v["uvlock"] = True
                variants["facing=%s,half=%s,shape=%s" % (facing, half, shape)] = v
    state(name, {"variants": variants})


def models():
    # ---- the fronds: plain cubes in the mod itself; the fluffy look (crossed planes round a cube, four variants) is an optional built-in pack ("fluffy_fronds") that turns it on
    leaves = ensure_leaves_bbmodel()
    block_model("power_palm_leaves", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped", "textures": {"all": tex("power_palm_leaves")}})
    state("power_palm_leaves", {"variants": {"": {"model": NS + ":block/power_palm_leaves"}}})
    item_model("power_palm_leaves", "power_palm_leaves")
    fluffy = os.path.join(RES, "resourcepacks", "fluffy_fronds")
    write_json(os.path.join(fluffy, "pack.mcmeta"), {"pack": {"pack_format": 34, "description": "CTR: Fluffy Palm Fronds (crossed planes round each leaf block)"}})
    variants = []
    for n in range(1, 5):
        write_json(os.path.join(fluffy, "assets", NS, "models", "block", "power_palm_leaves_%d.json" % n), {
            "parent": "minecraft:block/block", "render_type": "minecraft:cutout_mipped",
            "textures": {"all": tex("power_palm_leaves_fluffy"), "particle": tex("power_palm_leaves")},
            "elements": bbmodel.elements(leaves, "base", "v%da" % n, "v%db" % n)})
        for y in (0, 90, 180, 270):
            v = {"model": "%s:block/power_palm_leaves_%d" % (NS, n)}
            if y:
                v["y"] = y
            variants.append(v)
    write_json(os.path.join(fluffy, "assets", NS, "blockstates", "power_palm_leaves.json"), {"variants": {"": variants}})
    # ---- logs and wood
    for name, side, end in (("power_palm_log", "power_palm_log", "power_palm_log_top"), ("power_palm_wood", "power_palm_log", "power_palm_log"),
                            ("stripped_power_palm_log", "stripped_power_palm_log", "stripped_power_palm_log_top"),
                            ("stripped_power_palm_wood", "stripped_power_palm_log", "stripped_power_palm_log")):
        block_model(name, {"parent": "minecraft:block/cube_column", "textures": {"end": tex(end), "side": tex(side)}})
        state(name, {"variants": {"axis=y": {"model": "%s:block/%s" % (NS, name)}, "axis=z": {"model": "%s:block/%s" % (NS, name), "x": 90},
                                  "axis=x": {"model": "%s:block/%s" % (NS, name), "x": 90, "y": 90}}})
        item_model(name, name)
    # ---- planks and the rest of the wood set
    P = tex("power_palm_planks")
    block_model("power_palm_planks", {"parent": "minecraft:block/cube_all", "textures": {"all": P}})
    state("power_palm_planks", {"variants": {"": {"model": NS + ":block/power_palm_planks"}}})
    item_model("power_palm_planks", "power_palm_planks")
    st = {"bottom": P, "top": P, "side": P}
    block_model("power_palm_stairs", {"parent": "minecraft:block/stairs", "textures": st})
    block_model("power_palm_stairs_inner", {"parent": "minecraft:block/inner_stairs", "textures": st})
    block_model("power_palm_stairs_outer", {"parent": "minecraft:block/outer_stairs", "textures": st})
    stairs_state("power_palm_stairs")
    item_model("power_palm_stairs", "power_palm_stairs")
    block_model("power_palm_slab", {"parent": "minecraft:block/slab", "textures": st})
    block_model("power_palm_slab_top", {"parent": "minecraft:block/slab_top", "textures": st})
    state("power_palm_slab", {"variants": {"type=bottom": {"model": NS + ":block/power_palm_slab"}, "type=top": {"model": NS + ":block/power_palm_slab_top"},
                                           "type=double": {"model": NS + ":block/power_palm_planks"}}})
    item_model("power_palm_slab", "power_palm_slab")
    t = {"texture": P}
    block_model("power_palm_fence_post", {"parent": "minecraft:block/fence_post", "textures": t})
    block_model("power_palm_fence_side", {"parent": "minecraft:block/fence_side", "textures": t})
    block_model("power_palm_fence_inventory", {"parent": "minecraft:block/fence_inventory", "textures": t})
    state("power_palm_fence", {"multipart": [{"apply": {"model": NS + ":block/power_palm_fence_post"}}] + [
        {"when": {d: "true"}, "apply": {"model": NS + ":block/power_palm_fence_side", "uvlock": True, **({"y": y} if y else {})}} for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))]})
    item_model("power_palm_fence", "power_palm_fence_inventory")
    for suffix, parent in (("", "template_fence_gate"), ("_open", "template_fence_gate_open"), ("_wall", "template_fence_gate_wall"), ("_wall_open", "template_fence_gate_wall_open")):
        block_model("power_palm_fence_gate" + suffix, {"parent": "minecraft:block/" + parent, "textures": t})
    gate = {}
    for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        for in_wall in ("false", "true"):
            for opened in ("false", "true"):
                model = "power_palm_fence_gate" + ("_wall" if in_wall == "true" else "") + ("_open" if opened == "true" else "")
                v = {"model": "%s:block/%s" % (NS, model), "uvlock": True}
                if y:
                    v["y"] = y
                gate["facing=%s,in_wall=%s,open=%s" % (facing, in_wall, opened)] = v
    state("power_palm_fence_gate", {"variants": gate})
    item_model("power_palm_fence_gate", "power_palm_fence_gate")
    td = {"texture": tex("power_palm_trapdoor")}
    for suffix in ("bottom", "top", "open"):
        block_model("power_palm_trapdoor_" + suffix, {"parent": "minecraft:block/template_trapdoor_" + suffix, "render_type": "minecraft:cutout", "textures": td})
    trap = {}
    for facing, y in (("north", 0), ("south", 180), ("east", 90), ("west", 270)):
        for half in ("bottom", "top"):
            for opened in ("false", "true"):
                if opened == "false":
                    v = {"model": "%s:block/power_palm_trapdoor_%s" % (NS, half)}
                    if y:
                        v["y"] = y
                elif half == "bottom":
                    v = {"model": NS + ":block/power_palm_trapdoor_open"}
                    if y:
                        v["y"] = y
                else:
                    v = {"model": NS + ":block/power_palm_trapdoor_open", "x": 180, "y": (y + 180) % 360}
                    if v["y"] == 0:
                        del v["y"]
                trap["facing=%s,half=%s,open=%s" % (facing, half, opened)] = v
    state("power_palm_trapdoor", {"variants": trap})
    item_model("power_palm_trapdoor", "power_palm_trapdoor_bottom")
    block_model("power_palm_button", {"parent": "minecraft:block/button", "textures": t})
    block_model("power_palm_button_pressed", {"parent": "minecraft:block/button_pressed", "textures": t})
    block_model("power_palm_button_inventory", {"parent": "minecraft:block/button_inventory", "textures": t})
    btn = {}
    for face, x, ys in (("floor", 0, {"east": 90, "west": 270, "south": 180, "north": 0}), ("wall", 90, {"east": 90, "west": 270, "south": 180, "north": 0}),
                        ("ceiling", 180, {"east": 270, "west": 90, "south": 0, "north": 180})):
        for facing, y in ys.items():
            for powered in ("false", "true"):
                v = {"model": NS + ":block/power_palm_button" + ("_pressed" if powered == "true" else "")}
                if x:
                    v["x"] = x
                if y:
                    v["y"] = y
                if face != "wall":
                    v["uvlock"] = True
                btn["face=%s,facing=%s,powered=%s" % (face, facing, powered)] = v
    state("power_palm_button", {"variants": btn})
    item_model("power_palm_button", "power_palm_button_inventory")
    block_model("power_palm_pressure_plate", {"parent": "minecraft:block/pressure_plate_up", "textures": t})
    block_model("power_palm_pressure_plate_down", {"parent": "minecraft:block/pressure_plate_down", "textures": t})
    state("power_palm_pressure_plate", {"variants": {"powered=false": {"model": NS + ":block/power_palm_pressure_plate"}, "powered=true": {"model": NS + ":block/power_palm_pressure_plate_down"}}})
    item_model("power_palm_pressure_plate", "power_palm_pressure_plate")
    dt = {"top": tex("power_palm_door_top"), "bottom": tex("power_palm_door_bottom")}
    for half in ("bottom", "top"):
        for hinge in ("left", "right"):
            for suffix in ("", "_open"):
                block_model("power_palm_door_%s_%s%s" % (half, hinge, suffix), {"parent": "minecraft:block/door_%s_%s%s" % (half, hinge, suffix), "render_type": "minecraft:cutout", "textures": dt})
    door = {}
    closed_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    open_left = {"east": 90, "south": 180, "west": 270, "north": 0}
    open_right = {"east": 270, "south": 0, "west": 90, "north": 180}
    for facing in closed_y:
        for half, half_name in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for opened in ("false", "true"):
                    if opened == "false":
                        y, suffix = closed_y[facing], ""
                    else:
                        y, suffix = (open_left if hinge == "left" else open_right)[facing], "_open"
                    v = {"model": "%s:block/power_palm_door_%s_%s%s" % (NS, half_name, hinge, suffix)}
                    if y:
                        v["y"] = y
                    door["facing=%s,half=%s,hinge=%s,open=%s" % (facing, half, hinge, opened)] = v
    state("power_palm_door", {"variants": door})
    item_model("power_palm_door")
    # ---- the log's, the planked crates' and the metal's blocks
    block_model("power_block", {"parent": "minecraft:block/cube_all", "textures": {"all": tex("power_block")}})
    state("power_block", {"variants": {"": {"model": NS + ":block/power_block"}}})
    item_model("power_block", "power_block")
    block_model("item_crate_random", {"parent": "minecraft:block/cube_all", "textures": {"all": tex("item_crate_random")}})
    state("item_crate_random", {"variants": {"": {"model": NS + ":block/item_crate_random"}}})
    item_model("item_crate_random", "item_crate_random")
    for kind in ("item_crate", "fruit_crate"):
        block_model(kind, {"parent": "minecraft:block/cube_all", "textures": {"all": tex(kind)}})
        state(kind, {"variants": {"": {"model": NS + ":block/" + kind}}})
        item_model(kind, kind)
        planked = kind + "_planked"
        block_model(planked, {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": tex(planked + "_top"), "bottom": tex(planked + "_top"), "side": tex(planked + "_side")}})
        state(planked, {"variants": {"": {"model": NS + ":block/" + planked}}})
        item_model(planked, planked)
    # ---- the sapling
    block_model("power_palm_sapling", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": tex("power_palm_sapling")}})
    state("power_palm_sapling", {"variants": {"stage=0": {"model": NS + ":block/power_palm_sapling"}, "stage=1": {"model": NS + ":block/power_palm_sapling"}}})
    item_model("power_palm_sapling", layer=tex("power_palm_sapling"))
    # ---- the hanging fruit: one to four of the track's Power Fruit
    pods = ensure_pods_bbmodel()
    for n in range(1, 5):
        block_model("power_fruit_cluster_%d" % n, {"parent": "minecraft:block/block", "textures": {"pickups": NS + ":item/pickups", "particle": NS + ":item/pickups"},
                                                    "elements": bbmodel.elements(pods, "p%d" % n), "display": pods["display"] or {}})
    state("power_fruit_cluster", {"variants": {"age=%d,pruned=%s" % (n, pr): {"model": "%s:block/power_fruit_cluster_%d" % (NS, n)} for n in range(1, 5) for pr in ("false", "true")}})
    # ---- the wheel
    wheel = ensure_wheel_bbmodel()
    gui = {"gui": {"rotation": [30, 45, 0], "translation": [0, 11.5, 0], "scale": [2.0, 2.0, 2.0]}, "ground": {"translation": [0, 3, 0], "scale": [0.8, 0.8, 0.8]},
           "fixed": {"translation": [0, 5, 0], "scale": [1.5, 1.5, 1.5]}, "thirdperson_righthand": {"translation": [0, 3, 1], "scale": [0.8, 0.8, 0.8]}}
    for n in range(1, 5):
        block_model("wheel_%d" % n, {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": {"wheel": NS + ":item/kart_wheel", "particle": NS + ":item/kart_wheel"}, "elements": bbmodel.elements(wheel, "w%d" % n), "display": gui})
    wheel_state = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for n in range(1, 5):
            v = {"model": "%s:block/wheel_%d" % (NS, n)}
            if y:
                v["y"] = y
            wheel_state["facing=%s,wheels=%d" % (facing, n)] = v
    state("wheel", {"variants": wheel_state})
    item_model("wheel", "wheel_1")
    # ---- the bag
    bag = ensure_bag_bbmodel()
    block_model("power_fruit_bag", {"parent": "minecraft:block/block", "textures": {"side": tex("power_fruit_bag_side"), "top": tex("power_fruit_bag_top"), "bottom": tex("power_fruit_bag_bottom"),
                                                  "particle": tex("power_fruit_bag_side")}, "elements": bbmodel.elements(bag), "display": bag["display"] or {}})
    state("power_fruit_bag", {"variants": {"facing=%s" % f: ({"model": NS + ":block/power_fruit_bag", "y": y} if y else {"model": NS + ":block/power_fruit_bag"})
                                           for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    item_model("power_fruit_bag", "power_fruit_bag")
    # with Farm & Charm: its sack model and side/bottom/knot pictures under our own top picture (a built-in pack, only active when that mod is loaded)
    compat = os.path.join(RES, "resourcepacks", "farm_and_charm_compat")
    write_json(os.path.join(compat, "pack.mcmeta"), {"pack": {"pack_format": 34, "description": "CTR: Farm & Charm Sacks (the bag of power fruit in their sack)"}})
    write_json(os.path.join(compat, "assets", NS, "models", "block", "power_fruit_bag.json"), {
        "parent": "farm_and_charm:block/template_bag",
        "textures": {"3": "farm_and_charm:block/bag_side_knot", "4": "farm_and_charm:block/bag_bottom", "7": "farm_and_charm:block/bag_side", "8": tex("power_fruit_bag_top"),
                     "particle": tex("power_fruit_bag_top")}})
    # ---- the items of the chain
    for name in ("power_fruit", "power_juice", "raw_power_ore", "power_ingot", "power_nugget"):
        item_model(name)


LANG = {
    "block.craftteamracing.power_palm_log": "Power Palm Log",
    "block.craftteamracing.power_palm_wood": "Power Palm Wood",
    "block.craftteamracing.stripped_power_palm_log": "Stripped Power Palm Log",
    "block.craftteamracing.stripped_power_palm_wood": "Stripped Power Palm Wood",
    "block.craftteamracing.power_palm_planks": "Power Palm Planks",
    "block.craftteamracing.power_palm_stairs": "Power Palm Stairs",
    "block.craftteamracing.power_palm_slab": "Power Palm Slab",
    "block.craftteamracing.power_palm_fence": "Power Palm Fence",
    "block.craftteamracing.power_palm_fence_gate": "Power Palm Fence Gate",
    "block.craftteamracing.power_palm_door": "Power Palm Door",
    "block.craftteamracing.power_palm_trapdoor": "Power Palm Trapdoor",
    "block.craftteamracing.power_palm_button": "Power Palm Button",
    "block.craftteamracing.power_palm_pressure_plate": "Power Palm Pressure Plate",
    "block.craftteamracing.power_palm_leaves": "Power Palm Fronds",
    "block.craftteamracing.power_palm_sapling": "Power Palm Sapling",
    "block.craftteamracing.power_fruit_cluster": "Power Fruit Cluster",
    "block.craftteamracing.power_block": "Block of Power",
    "block.craftteamracing.item_crate": "Item Crate",
    "block.craftteamracing.fruit_crate": "Fruit Crate",
    "block.craftteamracing.item_crate_random": "Item Crate Random",
    "block.craftteamracing.trigger_crate_spot": "Trigger Crate Spot",
    "block.craftteamracing.redstone_link_node": "Redstone Linking Node",
    "screen.craftteamracing.trigger.title": "Trigger Crate",
    "screen.craftteamracing.trigger.respawn": "Respawn: %s",
    "screen.craftteamracing.trigger.duration": "Redstone signal: %s",
    "screen.craftteamracing.trigger.uses": "Uses: %s",
    "screen.craftteamracing.trigger.default": "as other crates",
    "screen.craftteamracing.trigger.always": "always",
    "screen.craftteamracing.trigger.infinite": "unlimited",
    "screen.craftteamracing.trigger.ticks": "%s ticks",
    "screen.craftteamracing.trigger.seconds": "%s s",
    "screen.craftteamracing.trigger.minutes": "%s min",
    "message.craftteamracing.link.picked": "Linking node picked: use the Wrench on a Trigger Crate Spot",
    "message.craftteamracing.link.linked": "Linked to the Trigger Crate Spot",
    "message.craftteamracing.link.unlinked": "Link removed",
    "message.craftteamracing.link.not_linked": "That spot is not linked to the picked node",
    "message.craftteamracing.link.already": "Already linked (crouch and use the Wrench to unlink)",
    "message.craftteamracing.link.dropped": "Linking node let go",
    "screen.craftteamracing.tuning.debug": "Debug (operators)",
    "screen.craftteamracing.tuning.debug_hint": "Drag the handles; Use puts these numbers on the kart now.",
    "screen.craftteamracing.tuning.export": "Export",
    "message.craftteamracing.tuning.exported": "Tuning exported to %s",
    "message.craftteamracing.tuning.export_failed": "Could not export the tuning: %s",
    "screen.craftteamracing.kart.tuning": "Tuning: %s...",
    "screen.craftteamracing.spot.hint": "Click to select several: the crate gives one of them at random. The mark on a selected one sets how many.",
    "screen.craftteamracing.spot.quantity_of": "%s: how many",
    "screen.craftteamracing.spot.quantity": "Quantity: %s",
    "screen.craftteamracing.spot.min": "Minimum: %s",
    "screen.craftteamracing.spot.max": "Maximum: %s",
    "screen.craftteamracing.spot.range_off": "Random range: off",
    "screen.craftteamracing.spot.range_on": "Range: on",
    "screen.craftteamracing.spot.range_hint": "On: the crate gives a random number from the minimum to the maximum.",
    "screen.craftteamracing.kart.mask_light_plain": "light",
    "screen.craftteamracing.kart.mask_dark_plain": "dark",
    "screen.craftteamracing.tuning.title": "Kart tuning",
    "screen.craftteamracing.tuning.use": "Use this tuning",
    "screen.craftteamracing.tuning.back": "Back",
    "screen.craftteamracing.tuning.official": "Classic class",
    "screen.craftteamracing.tuning.custom": "Custom (data pack)",
    "screen.craftteamracing.tuning.accel": "Acceleration",
    "screen.craftteamracing.tuning.speed": "Top speed",
    "screen.craftteamracing.tuning.turn": "Turning",
    "screen.craftteamracing.tuning.turn_decrease": "Speed lost steering",
    "screen.craftteamracing.tuning.response": "Steering response",
    "screen.craftteamracing.tuning.drift": "Drift turn",
    "screen.craftteamracing.tuning.weight": "Bump weight",
    "screen.craftteamracing.tuning.tick": "The white tick is what the kart has now.",
    "message.craftteamracing.tuning.set": "Tuning: %s",
    "tooltip.craftteamracing.kart.tuning": "Tuning: %s",
    "block.craftteamracing.item_crate_planked": "Planked Item Crate",
    "block.craftteamracing.fruit_crate_planked": "Planked Fruit Crate",
    "block.craftteamracing.wheel": "Wheel",
    "block.craftteamracing.power_fruit_bag": "Bag of Power Fruit",
    "item.craftteamracing.power_fruit": "Power Fruit",
    "item.craftteamracing.power_juice": "Power Juice",
    "item.craftteamracing.raw_power_ore": "Raw Power Ore",
    "item.craftteamracing.power_ingot": "Power Ingot",
    "item.craftteamracing.power_nugget": "Power Nugget",
}


def humanise(name):
    words = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", name)
    return words[0].upper() + words[1:]


def config_lang():
    """Labels for NeoForge's config screen (craftteamracing.configuration.<name>): every option and section named in the two config classes, written out from its camelCase name."""
    out = {"craftteamracing.configuration.title": "Craft Team Racing"}
    for rel in ("CtrConfig.java", "CtrClientConfig.java"):
        with open(os.path.join(ROOT, "src", "main", "java", "com", "craftteamracing", rel), encoding="utf-8") as f:
            src = f.read()
        for name in re.findall(r'\.(?:define\w*)\("([A-Za-z]+)"', src) + re.findall(r'\.push\("([A-Za-z]+)"\)', src):
            out["craftteamracing.configuration." + name] = humanise(name)
    return out


def lang():
    path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    data.update(config_lang())
    for k in [k for k in data if k.endswith("raw_power_iron") or k.endswith("power_fruit_cluster_0")]:
        del data[k]
    data.update(LANG)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


STALE = [
    "assets/craftteamracing/textures/block/power_palm_leaves_item.png", "assets/craftteamracing/models/block/power_palm_leaves_1.json", "assets/craftteamracing/models/block/power_palm_leaves_2.json",
    "assets/craftteamracing/models/block/power_palm_leaves_3.json", "assets/craftteamracing/models/block/power_palm_leaves_4.json",
    "assets/craftteamracing/textures/item/raw_power_iron.png", "assets/craftteamracing/models/item/raw_power_iron.json",
    "assets/craftteamracing/textures/block/item_crate_side.png", "assets/craftteamracing/textures/block/item_crate_top.png",
    "assets/craftteamracing/textures/block/fruit_crate_side.png", "assets/craftteamracing/textures/block/fruit_crate_top.png",
    "assets/craftteamracing/textures/block/power_block.png.mcmeta",
    "assets/craftteamracing/textures/block/power_fruit_cluster_0.png", "assets/craftteamracing/textures/block/power_fruit_cluster_1.png", "assets/craftteamracing/textures/block/power_fruit_cluster_2.png",
    "assets/craftteamracing/models/block/power_fruit_cluster_0.json", "assets/craftteamracing/models/block/power_fruit_cluster_1.json", "assets/craftteamracing/models/block/power_fruit_cluster_2.json",
    "data/craftteamracing/recipe/raw_power_iron_from_juice.json", "data/craftteamracing/loot_table/blocks/raw_power_iron.json",
    "data/craftteamracing/recipe/item_crate_planked.json", "data/craftteamracing/recipe/item_crate_from_planked.json", "data/craftteamracing/recipe/item_crate_random.json", "data/craftteamracing/recipe/item_crate_from_random.json",
    "data/craftteamracing/recipe/fruit_crate_planked.json", "data/craftteamracing/recipe/fruit_crate_from_planked.json",
]


# ======================================================================== main
def main():
    shutil.rmtree(os.path.join(RES, "resourcepacks", "plain_fronds"), ignore_errors=True)
    for rel in STALE:
        p = os.path.join(RES, rel)
        if os.path.exists(p):
            os.remove(p)
    old_pods = os.path.join(BB, "power_fruit_cluster.bbmodel")
    if os.path.exists(old_pods):
        os.remove(old_pods)
    # the pictures of this family are layered sheets (tools/survival_art.py): the layered .kra files are written here (only when this script or what it imports changed,
    # tools/kra_stamp.py) and tools/build_art.py flattens them into the PNGs. They are all drawn every run: the wood and leaf pictures share one random generator.
    inputs = kra_stamp.inputs_for(__file__)
    for name, (kind, sheet) in survival_art.build_all().items():
        kra_layers.write_if_stale("krita/%s/%s.kra" % ("items" if kind == "item" else "blocks", name), sheet, inputs)
    mcmeta("item/power_ingot.png", len(survival_art.INGOT_SPARKS), 4)
    mcmeta("item/power_nugget.png", len(survival_art.NUGGET_SPARKS), 5)
    for name, sheet in crate_sheets().items():
        kra_layers.write_if_stale("krita/blocks/%s.kra" % name, sheet, inputs)
    models()
    lang()
    recipes()
    loot_tables()
    tags()
    worldgen()
    print("survival assets ok")


if __name__ == "__main__":
    main()
