"""
The track items: the Track Tool and the two node blocks (Start Node, Checkpoint Node).

  python tools/make_track_items.py

These are flat items and invisible blocks, so there is no geometry to model: the icons are layered sheets written as krita/items/<name>.kra (a modder's
templates; tools/build_art.py flattens them into the PNGs), and this writes the item models, block states, loot tables and the names and messages in the lang file. Run from the project folder.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kra_layers  # noqa: E402
import kra_stamp  # noqa: E402

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "craftteamracing")
DATA = os.path.join(RES, "data", "craftteamracing")
NS = "craftteamracing"
TEX = os.path.join(ASSETS, "textures", "item")


def px(im, pts, colour):
    for x, y in pts:
        im[x, y] = colour


def ring(im, cx, cy, r, colour):
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if r - 0.7 <= d <= r + 0.5:
                im[x, y] = colour


CLEAR = (0, 0, 0, 0)


def draw_tool():
    """A wand: a brown handle running up to the right and a green node on its tip."""
    sheet = kra_layers.Sheet(16, 16)
    dark, light, node, glint = (sheet.layer(n) for n in ("handle", "handle highlight", "node", "node highlight"))
    for i in range(10):
        px(dark, [(2 + i, 13 - i)], (120, 80, 40, 255))
        px(light, [(3 + i, 13 - i)], (150, 105, 55, 255))
    for y in range(2, 7):
        for x in range(9, 14):
            node[x, y] = (60, 230, 90, 255)
    corners = [(9, 2), (13, 2), (9, 6), (13, 6)]
    px(node, corners, CLEAR)
    px(dark, corners, CLEAR)                                  # the node's rounded corners are see-through, also where the handle runs under them
    px(light, corners, CLEAR)
    px(glint, [(10, 3), (11, 3)], (170, 255, 190, 255))
    return sheet


def draw_wrench():
    """A spanner: a grey handle running up to the right, an open jaw on its tip."""
    sheet = kra_layers.Sheet(16, 16)
    handle, shine, jaw, jaw_shine = (sheet.layer(n) for n in ("handle", "handle highlight", "jaw", "jaw highlight"))
    for i in range(9):
        px(handle, [(2 + i, 13 - i), (3 + i, 13 - i)], (150, 156, 168, 255))
        px(shine, [(2 + i, 12 - i)], (200, 206, 216, 255))
    for x, y in [(10, 2), (11, 2), (12, 2), (13, 3), (13, 4), (12, 5), (11, 5), (10, 4), (9, 3)]:
        jaw[x, y] = (150, 156, 168, 255)
    opening = [(11, 3), (12, 3), (12, 4)]
    for layer in (handle, shine, jaw):
        px(layer, opening, CLEAR)                             # the jaw's opening is see-through
    px(jaw_shine, [(10, 3), (11, 4)], (200, 206, 216, 255))
    return sheet


def draw_node(colour, arrow):
    """A ring with an arrow through it: green for the start, orange for a checkpoint."""
    sheet = kra_layers.Sheet(16, 16)
    rim, shaft, head = (sheet.layer(n) for n in ("ring", "arrow shaft", "arrow head"))
    ring(rim, 7.5, 7.5, 5.8, colour)
    # an arrow pointing up through the middle
    px(shaft, [(7, y) for y in range(4, 13)] + [(8, y) for y in range(4, 13)], arrow)
    px(head, [(5, 6), (6, 5), (9, 5), (10, 6), (4, 7), (11, 7)], arrow)
    return sheet


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


def main():
    icons = {"track_tool": draw_tool(),
             "wrench": draw_wrench(),
             "start_node": draw_node((60, 230, 90, 255), (255, 255, 255, 255)),
             "checkpoint_node": draw_node((255, 165, 30, 255), (255, 255, 255, 255))}
    inputs = kra_stamp.inputs_for(__file__)
    for name, sheet in icons.items():
        kra_layers.write_if_stale("krita/items/%s.kra" % name, sheet, inputs)

    dump(os.path.join(ASSETS, "models", "item", "track_tool.json"),
         {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/track_tool"}})
    dump(os.path.join(ASSETS, "models", "item", "wrench.json"),
         {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/wrench"}})
    for node in ("start_node", "checkpoint_node"):
        dump(os.path.join(ASSETS, "models", "item", node + ".json"),
             {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{node}"}})
        dump(os.path.join(ASSETS, "blockstates", node + ".json"), {"variants": {"": {"model": f"{NS}:block/track_node"}}})
        dump(os.path.join(DATA, "loot_table", "blocks", node + ".json"), {
            "type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                                                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{node}"}],
                                                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # the blocks are invisible: the model only gives the break particles a texture
    dump(os.path.join(ASSETS, "models", "block", "track_node.json"), {"textures": {"particle": f"{NS}:item/start_node"}})

    lp = os.path.join(ASSETS, "lang", "en_us.json")
    lang = json.load(open(lp, encoding="utf-8")) if os.path.exists(lp) else {}
    lang.update({
        f"item.{NS}.track_tool": "Track Tool",
        f"block.{NS}.start_node": "Start Node",
        f"block.{NS}.checkpoint_node": "Checkpoint Node",
        f"message.{NS}.track.selected": "Selected %s. Right-click another node to link it.",
        f"message.{NS}.track.cleared": "Selection cleared.",
        f"message.{NS}.track.linked": "Linked %s to %s.",
        f"message.{NS}.track.unlinked": "Unlinked %s from %s.",
        f"message.{NS}.track.summary": "Track: %s start, %s checkpoints, %s.",
        f"message.{NS}.track.loop_closed": "loop closed",
        f"message.{NS}.track.loop_open": "loop open",
        f"message.{NS}.track.two_starts": "A track can only have one start node.",
        f"message.{NS}.track.too_far": "Nodes can be linked up to %s blocks apart.",
        f"message.{NS}.track.no_permission": "You can't edit tracks here.",
    })
    dump(lp, lang)
    print("track items written")


if __name__ == "__main__":
    main()
