"""Exports the PNG textures from the layered Krita files listed in krita/manifest.json.

The pipeline is  art script (tools/make_*.py)  ->  layered .kra  ->  PNG (this script).
The Python scripts are the source of truth. A .kra is a generated template (a modder's resource): a script rewrites it
whole when its inputs change (see tools/kra_stamp.py) and this script only flattens its layers into the shipped PNGs.
Do not paint in a .kra expecting it to last: tell Claude what you changed and it goes into the script. A .kra that
`python tools/kra_stamp.py --status` lists as "not yet scripted" still holds hand-painted art that its script has not
taken over, so it is not regenerated yet.

  python tools/build_art.py            write any textures that changed
  python tools/build_art.py --check    write nothing; exit 1 if any texture is out of date
  python tools/build_art.py --out DIR  write into DIR instead of the project (for testing)
  python tools/build_art.py --manifest FILE   use another manifest (kra/output paths stay relative to the project)
  python tools/build_art.py --only TEXT       only the .kra files whose path contains TEXT, e.g. --only ball_pit
                                              (combine with --out DIR to get just those PNGs in a folder)

Layer naming (flat .kra files, RGBA8 paint layers only):
  "<group>: <anything>"   every layer belongs to the group before the colon
  mask group (e.g. shape) its alpha limits what is exported; the layer itself is not drawn
  base groups             included in every output
  ignore groups           never exported (guides, notes)
  any other group         a variant (e.g. "red: body", "red: stripe"); composited over the base
Layer visibility is ignored, so your working view never changes the export. Opacity is respected.

Manifest entry (all paths relative to the project folder):
  kra, output_dir, mask (optional), base [..], ignore [..], outputs [
    { "file": "kart.png" }                                      base layers only
    { "file": "kart_{group}.png", "for_each_group": true }       one file per variant group
    { "file": "x.png", "plus": ["red", "glow"]     }              base + the named groups
  ]
  Any output can also set "dir" (its own output folder) and "crop": [x, y, w, h] to cut one tile
  out of a bigger canvas (e.g. several 16x16 textures laid out side by side in one .kra).
"""
import json
import os
import sys

from PIL import Image, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from kra_reader import read_kra, layer_on_canvas  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, ".."))
MANIFEST = os.path.join(ROOT, "krita", "manifest.json")


def with_opacity(img, opacity):
    if opacity >= 255:
        return img
    img = img.copy()
    img.putalpha(img.getchannel("A").point(lambda v: v * opacity // 255))
    return img


def shown(path):
    try:
        return os.path.relpath(path, ROOT)
    except ValueError:                      # another drive (e.g. a test folder): show the full path
        return path


def visible(img):
    """The picture as it shows: the colour of a fully see-through pixel does not count."""
    img = img.convert("RGBA")
    img.paste((0, 0, 0, 0), mask=img.getchannel("A").point(lambda v: 255 if v == 0 else 0))
    return img


def same_pixels(path, img):
    if not os.path.exists(path):
        return False
    old = visible(Image.open(path))
    new = visible(img)
    return old.size == new.size and old.tobytes() == new.tobytes()


def build_asset(asset, out_override, check):
    kra = os.path.join(ROOT, asset["kra"])
    if not os.path.exists(kra):
        raise SystemExit(f"error: {asset['kra']} not found")
    out_dir = out_override or os.path.join(ROOT, asset["output_dir"])
    if not check:
        os.makedirs(out_dir, exist_ok=True)

    mask_g = asset.get("mask", "").lower()
    base_g = {g.lower() for g in asset.get("base", [])}
    ignore_g = {g.lower() for g in asset.get("ignore", [])}

    doc = read_kra(kra)
    w, h = doc["width"], doc["height"]
    for name, kind in doc["skipped"]:
        print(f"  warning: {asset['kra']}: '{name}' ({kind}) is not a plain paint layer and is ignored; "
              f"keep the file flat", file=sys.stderr)

    mask = None
    tagged = []                                   # (group, image), bottom to top
    for layer in reversed(doc["layers"]):
        name = layer["name"]
        if ":" not in name:
            print(f"  warning: layer '{name}' has no 'group: name' prefix and is ignored", file=sys.stderr)
            continue
        group = name.split(":", 1)[0].strip().lower()
        img = with_opacity(layer_on_canvas(layer, w, h), layer["opacity"])
        if group == mask_g:
            mask = img.getchannel("A") if mask is None else ImageChops.lighter(mask, img.getchannel("A"))
        elif group in ignore_g:
            continue
        else:
            tagged.append((group, img))

    variants = []
    for g, _ in tagged:
        if g not in base_g and g not in variants:
            variants.append(g)

    def render(plus):
        canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        for g, img in tagged:
            if g in base_g or g in plus:
                canvas.alpha_composite(img)
        if mask is not None:
            canvas.putalpha(ImageChops.multiply(canvas.getchannel("A"), mask))
        return canvas

    jobs = []   # (file, variant groups, dir override, crop)
    for entry in asset["outputs"]:
        d, crop = entry.get("dir"), entry.get("crop")
        if entry.get("for_each_group"):
            jobs += [(entry["file"].format(group=g), [g], d, crop) for g in variants]
        else:
            plus = [p.lower() for p in entry.get("plus", [])]
            for p in plus:
                if p not in variants:
                    raise SystemExit(f"error: {entry['file']}: no layers in group '{p}' in {asset['kra']}")
            jobs.append((entry["file"], plus, d, crop))

    written = unchanged = 0
    for fname, plus, dir_override, crop in jobs:
        img = render(plus)
        if crop:
            x, y, cw, ch = crop
            img = img.crop((x, y, x + cw, y + ch))
        target_dir = out_override or (os.path.join(ROOT, dir_override) if dir_override else out_dir)
        os.makedirs(target_dir, exist_ok=True) if not check else None
        path = os.path.join(target_dir, fname)
        if same_pixels(path, img):
            unchanged += 1
        else:
            written += 1
            if not check:
                img.save(path)
            print(f"  {'out of date' if check else 'wrote'}: {shown(path)}")
    return written, unchanged


def build_for(kra_rel):
    """Writes the PNGs of one .kra (a manifest entry whose kra is {kra_rel}) now: for a script that needs a PNG at once (a Blockbench model that embeds it)."""
    manifest = json.load(open(MANIFEST))
    for asset in manifest["assets"]:
        if asset["kra"] == kra_rel.replace("\\", "/"):
            build_asset(asset, None, False)


def main():
    check = "--check" in sys.argv
    out = sys.argv[sys.argv.index("--out") + 1] if "--out" in sys.argv else None
    manifest_path = sys.argv[sys.argv.index("--manifest") + 1] if "--manifest" in sys.argv else MANIFEST
    manifest = json.load(open(manifest_path))
    only = sys.argv[sys.argv.index("--only") + 1].lower() if "--only" in sys.argv else None
    if only:
        manifest["assets"] = [a for a in manifest["assets"] if only in a["kra"].lower()]
        if not manifest["assets"]:
            raise SystemExit(f"error: no .kra in the manifest matches '{only}'")
    total_w = total_u = 0
    for asset in manifest["assets"]:
        print(f"{asset['kra']}")
        wr, un = build_asset(asset, out, check)
        total_w += wr; total_u += un
    print(f"art: {total_w} {'out of date' if check else 'written'}, {total_u} unchanged")
    sys.exit(1 if check and total_w else 0)


if __name__ == "__main__":
    main()
