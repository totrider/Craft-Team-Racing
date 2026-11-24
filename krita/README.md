# Krita files (modder templates)

Layered `.kra` files that show how the mod's textures are built. They are a **resource for modders**: copy them into your own folder, edit them there, and use the result as a hand-made
asset, a resource pack override or the start of your own variant. **They are not edited inside this project.**

## How they are made
The pipeline is  **art script (`tools/make_*.py`)  ->  layered `.kra`  ->  PNG**.

- The Python scripts are the source of truth. A script that owns a `.kra` writes the whole file from its drawing code, with the layers kept intact (`tools/kra_writer.py`).
- It does so only when something it depends on changed: each `.kra` has a stamp (a hash of the script, its helper modules and any model or data file it reads) in `.stamps.json`.
  A small change therefore does not rebuild every file. `--force` (or the environment variable `FORCE_KRA=1`) rebuilds all.
- `python tools/build_art.py` flattens the `.kra` files into the PNGs the mod ships. `manifest.json` says which `.kra` becomes which PNG, and which layers or tiles go into it.
  `--check` writes nothing and fails if a PNG is out of date.
- `python tools/kra_stamp.py --status` lists every `.kra` in the manifest as scripted or **not yet scripted**. A file that is not yet scripted may still hold hand-made art that no
  script produces, so nothing overwrites it until its script has taken that art over.

Derived pictures have no `.kra` of their own and stay computed by their scripts from the ones that do: the outlined 96 x 64 HUD pictures (from the sprites), the glow maps, Fusion's
pieced sheets and the still-arrow pack of the boost pads, the shine and particle copies of the burst, and the kart's ground shadow (projected from the kart's model).

## Painting something by hand
Paint it in your copy, then tell Claude what you changed. It is carried into the script as faithfully as the script's rules allow (approximately: the scripts are rule-based, not
pixel-by-pixel), and the `.kra` is regenerated from there. Changes made in the files in this folder are overwritten the next time their script runs.

## Layer rules (what `build_art.py` reads)
- Flat files: plain RGBA 8-bit paint layers, no groups, masks, filter, vector or text layers (they are ignored, with a warning).
- Name every layer **`group: name`**. A layer without the colon is ignored, with a warning.
- Layer visibility does not matter for the export; opacity does.
- Groups get a role in `manifest.json`: a **mask** group (its alpha limits what is exported), **base** groups (in every output), **ignore** groups (guides, never exported), and
  any other group is a **variant**, drawn over the base.
- A canvas can hold several textures side by side; the manifest cuts each out with a `crop`.

## Folders
Sorted by what the picture is for: `blocks/` (block textures), `items/` (item pictures and model textures), `entity/` (entity textures), `gui/` (HUD pictures and the burst), `particles/`
and `sprites/` (the 16 x 16 power-up sprites, no outline). `manifest.json` lists every file and the PNGs it becomes.
