# Blockbench models

The source of every block, item and entity model that is made of boxes. One folder per family; open the `.bbmodel` files in Blockbench.

| Folder | Models | Generator that reads them |
|---|---|---|
| `boost_pad/` | The boost block family: plate, block, slabs and stairs (straight, inner, outer; bottom and top) | `tools/make_boost_pad.py` |
| `kart/` | The baseline kart (`kart_ctr`), the old placeholder kart, the wheel and the boost flame | `tools/make_kart_ctr.py`, `make_kart.py`, `make_kart_wheel.py`, `make_boost_flame.py` |
| `pickups/` | The Power Fruit, the fruit crate, the power-up crates (`powerup_crate`, `_blank`, `trigger_crate`), the TNT and creeper crates, the rocket and the shield bubble | `tools/make_pickup_models.py`, `make_tnt_models.py`, `make_missile_model.py`, `make_shield_model.py` |
| `rain_cloud/` | The red beaker's rain cloud | `tools/make_rain_cloud_model.py` |
| `spectate/` | The viewing console: a full block, the monitor on its north face (the blockstate turns it), buttons and a joystick standing out by a pixel or two | `tools/make_viewing_console.py` |
| `survival/` | The fluffy palm leaves, the hanging fruit pods, the wheel block and the bag of power fruit | `tools/make_survival_assets.py` |
| `warp_orb/` | The Warp Orb's crystal | `tools/make_warp_orb_model.py` |

## Rules
- **The `.bbmodel` is the source.** The generators read the geometry from it and write the model JSON under `src/main/resources/`. Never hand-edit that JSON, and never put geometry only in
  Java or a script.
- **A model is written once, then it is yours.** Each generator writes a starting `.bbmodel` from its own shapes only when the file is missing; after that it only reads it, so your edits
  survive. A generator that has to change a model patches it in place.
- **A new model starts as a `.bbmodel`**, with a generator that reads it.
- **If you change a model by hand, tell Claude.** The surrounding files (the generator's expectations, the item model, the docs) are updated from what you changed.
- Meshes (anything that is not boxes) are Blender models, in `blender/`.

## Non-obvious structure
The details are in the docstring at the top of each generator; the ones that bite:
- **Boost blocks:** the texture slots are `top` (the chevron arrow, pointing north in the file) and `side`. The arrow faces' uv and rotation are **generated** for each direction, so change an
  arrow face's size by changing its element and do not touch its uv. The stairs are authored for facing east; the `_top` models are the bottom ones turned upside down and edited so the visible
  top is one big arrow. The glow overlay is generated and is not in the models.
- **Kart:** the faces of `kart_ctr` are mapped by their **names** onto the 128 x 128 texture (`tub_*`, `strut_*`, `seat_*`, `engine_*`, `exhaust_*`, `steering_*`; see `tools/make_kart_texture.py`);
  keep the names. Rotations must be 0, 22.5 or 45 degrees (`tools/fix_bbmodel_angles.py` snaps the others).
- **Pickups:** the Power Fruit and the two crates that use the "?" and plank faces share the 32 x 32 sheet `textures/item/pickups.png` (slot `pickups`); the blank and the trigger crate
  have their own face pictures (`crate_blank.png`, `crate_exclaim.png`), and the TNT, creeper, rocket and shield have theirs. The models carry a copy of their picture; the generators refresh it.
- **The textures** the models use are made by scripts: layered templates in `krita/`, flattened into `src/`. Do not paint them in place.
