# Tools

Python scripts that make the game files from their sources. Every script starts with a docstring that says what it does, what it reads and writes, and how to run it. Run them from the
project folder (`python tools/<script>.py`); `build-and-install.bat` runs the ones a build needs, in order. They need Python 3 with Pillow and numpy.

The generators stay in this folder (they import each other); two groups that stand apart have their own folders.

## Libraries (imported by the scripts)
| Module | What it is |
|---|---|
| `bbmodel.py` | Reads and writes Blockbench models; the generators read their geometry from `blockbench/` through it |
| `kra_writer.py`, `kra_reader.py` | Layered Krita (`.kra`) files, written and read without Krita |
| `kra_layers.py`, `kra_stage.py` | Helpers for the art scripts: draw onto named layers (`Sheet`, `Painter`), or turn a numpy painter into layers step by step (`Stages`) |
| `kra_stamp.py` | Decides whether a script has to rewrite its `.kra` (a hash of the script and what it imports and reads, in `krita/.stamps.json`); `--status`, `--force` |
| `build_art.py` | Flattens the `.kra` files into the shipped PNGs, by `krita/manifest.json`; `--check` fails if one is out of date |
| `sprite_outline.py` | The black outline and the 96 x 64 shape of the power-up HUD pictures |
| `survival_art.py` | The pictures of the survival side (the Power Palm and its wood set, the Power Fruit's chain, the bag), as layers |
| `oggenc.py` | Ogg Vorbis encoding for the generated sounds (uses Krita's libogg / libvorbis DLLs) |
| `export_blender_models.py` | Opens every `.blend` in Blender in the background and exports its collections (see `blender/README.md`) |

## Art and model generators
The texture scripts write layered `.kra` files into `krita/` and `build_art.py` flattens them; see `krita/README.md`. The model scripts read `blockbench/` and write model JSON.

| Family | Scripts |
|---|---|
| Kart | `make_kart.py`, `make_kart_ctr.py`, `make_kart_texture.py`, `make_kart_wheel.py`, `make_kart_shadow.py`, `make_kart_fx.py`, `make_boost_flame.py` |
| Boost pads | `make_boost_pad.py` (blocks, arrows, glow maps, Fusion sheets, the still-arrow pack) |
| Pickups and crates | `make_pickup_models.py`, `make_pickup_spots.py`, `make_tnt_models.py`, `make_crate_sprites.py`, `make_crate_break.py` |
| Power-ups | `make_power_sprites.py`, `make_power_icons.py`, `make_evil_totem.py`, `make_mask_models.py`, `make_mask_beam.py`, `make_slime_shield.py`, `make_shield_model.py`, `make_warp_orb_model.py`, `make_bomb_model.py`, `make_beaker_models.py`, `make_missile_model.py`, `make_rocket_flame.py`, `make_rain_cloud_model.py`, `make_burst_sprites.py` |
| Survival | `make_survival_assets.py` (textures, models, recipes, loot, tags, lang) |
| Track and items | `make_track_items.py`, `make_track_overlays.py`, `make_barrier_assets.py`, `make_viewing_console.py`, `make_drop_table_icon.py`, `make_music_sheet_icon.py`, `make_rulebook_icon.py`, `make_podium_icons.py`, `make_upgrade_icons.py`, `make_confetti.py` |
| Sounds | `make_sounds.py`, `make_jingles.py` (synthesised placeholders) |

## Pictures for the documentation
| Script | What it draws |
|---|---|
| `make_recipe_grids.py` | The crafting-grid pictures in `docs/img/recipes/`, from the recipe files and the models; rerun after a recipe or an icon changes |
| `make_powerup_doc_images.py` | `docs/img/powerups/` |
| `make_mod_icon.py` | The 512 x 512 mod icon variants in `docs/img/icon/` |

## One-off helpers
`fix_bbmodel_angles.py` (snaps a Blockbench model's cube rotations to the angles Minecraft takes), `refine_kart_texture.py` (the kart's seat and exhaust model changes; its picture work now
lives in `make_kart_texture.py`).

## `blender/`
Scripts that run **inside Blender**: they build a starting `.blend` once (`blender_create_*.py`) and export every collection of a `.blend` to an OBJ and a model JSON
(`blender_export_models.py`). The host side is `export_blender_models.py` and the `make_*` script of each model.

## `ctr_disc/`
Everything that reads **your own copy of the original game's disc** and writes local, git-ignored resource packs (sounds, music, textures, models). Nothing from the disc is ever committed or
shipped. The launchers are in `scripts/`; `package_asset_extractor.py` copies the extractors into `asset-extractor/ctr/`. See the README section "Original game assets".
