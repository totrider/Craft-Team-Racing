# Craft Team Racing - project rules

NeoForge 1.21.1 (21.1.265) mod, Java 21, Parchment mappings. Mod id `craftteamracing`, package `com.craftteamracing`.
Roadmap: `docs/ROADMAP.md`. Research: `docs/RESEARCH.md`.

## Workflow
- Test instances: `F:\ATLauncher\instances\CraftTeamRacing` (NeoForge 21.1.265) and `F:\ATLauncher\instances\CraftTeamRacingMP` (multiplayer tests;
  every build goes to both). `build-and-install.bat` builds and copies the
  jar into its `mods` folder. Minecraft locks the jar while it runs: close the game first.
- Models: **Blockbench first**. Every block/item/entity model's geometry lives in `blockbench/<family>/*.bbmodel` and is
  the source of truth; generators in `tools/` read them. Never put geometry only in Java or hand-edit generated model
  JSON. Patch existing bbmodels in place so the user's manual edits survive. New model = new bbmodel first.
- Mesh models (anything that is a mesh rather than boxes, e.g. the Slime Shield's layers): `blender/<family>/*.blend` is the source, next to `blockbench/`; `tools/export_blender_models.py` exports each collection to
  OBJ + model JSON (conventions in `blender/README.md`). Same rules: never hand-edit the exported OBJ/JSON.
- Power-up sprites: drawn 16 x 16 WITHOUT an outline, then outlined in black (18 x 18 at most) and put into the original's 3 : 2 icon shape (96 x 64, the sprite stretched 4 x 3 so it shows square: a
  reference pixel is 0.7361 as wide as tall); `tools/sprite_outline.py`, `tools/make_power_sprites.py`. Minecraft's own sprites the mod borrows (totem, clock) are outlined at run time (`client/IconSprites`), never copied.
- Textures: layered Krita `.kra` in `art/` (not .ora); a build script exports PNGs. Never hand-edit generated PNGs.
- When handing over an editable source file (bbmodel, kra, config), say up front anything non-obvious about its structure.
- `.mcp.json` is local only (gitignored). The Blockbench MCP server is configured there (local path).
- Datagen output in `src/generated/resources`; data-driven things (terrain table, recipes, tags) as data packs where possible.

## Code rules
- `com.craftteamracing.physics` must not import Minecraft or NeoForge classes. It uses CTR's integer fixed-point math exactly
  (see RESEARCH.md conventions). Anything Minecraft-specific goes through an interface in that package.
- Port from ctr-native / world-of-kartcraft's spec function by function; note the C source function in a comment and in
  `docs/PHYSICS_PORT.md`. Verify constants against the raw files, never from memory.
- Sable/Create are soft dependencies (compile-only + `compat/` package + optional mod dependency). The mod must load
  without them. Never bundle Sable (Polyform Shield).
- Do not ship CTR game assets (models, textures, audio). License is GPL-3.0-or-later because the physics is a port.
  Original CTR sounds and textures only ever exist as LOCAL resource packs made from the user's own disc by `tools/extract_ctr_sounds.py` and
  `tools/extract_ctr_textures.py` (`extract-ctr-sounds.bat`, `extract-ctr-textures.bat`; output in git-ignored `local-assets/`, installed into the instance's `resourcepacks/`, never in the jar,
  never uploaded). The disc image in the project folder is git-ignored too. The mod ships synthesised placeholders (`tools/make_sounds.py`).
- Client-side sim at fixed 30 Hz with interpolation; server validates, does not simulate.

## Naming (decided 2026-11-20)
- The mod is **Craft Team Racing** (**CTR** in short); mod id `craftteamracing`, package `com.craftteamracing`. Never "CTRCraft" in anything that ships, in docs or in text a player sees.
- Player-visible text (lang, tooltips, config comments, pack names, advancements) uses Minecraft-themed or generic names (Redstone Surge, Rocket, Potion, Slime Shield, Totem, End Crystal, Power Fruit...).
  The original's names (Aku Aku, Wumpa, level names...) live only in the local resource packs' lang files and in notes that are about the original's mechanics (`docs/POWERUPS.md` has the mapping).

## Fidelity policy (decided 2026-11-12)
- **Default behaviour is the PS1 game's**, as read from the decomp (ctr-native), not from later games (Nitro-Fueled etc.) and not from
  memory. A constant or rule that is not in the decomp is not "CTR": say so, and make it an extension.
- **Extensions are opt-in, by what you build or choose**: a different block (e.g. a Super Boost Pad family), a different item, a datapack
  entry or a config flag; never a silent change to the default. Because tracks are built from blocks, an extension is just another block.
- **Exception, decided by the author: interaction with Minecraft entities** (mobs, boats, minecarts, players on foot; damage, knockback,
  power-ups hitting them) is core to the survival gameplay and is **on by default**. It is a separate system from the race mechanics and
  never changes the CTR kart-vs-kart physics; tune it with config flags rather than switching it off by default.
- When unsure what PS1 does, read the decomp before answering or coding: the full ctr-native source is in `reference/ctr-native-src/` (git-ignored;
  `game/Vehicle/*.c`, `game/DotLights.c`, `game/zGlobal_DATA.c` for the stat tables...); `reference/ctr-native/` is an older partial copy.

## Privacy and scope (this repository is public)
- **Never mention, link or name any other project, repository, folder or work of the author's** (private or public, finished or not), or any
  personal detail (real name, handles, e-mail, user names, personal interests, machine paths beyond the generic test-instance path), anywhere in
  the repository: code, comments, docs, README, lang files, tool scripts, configs, commit messages, tags or release notes.
- If a rule, idea or setting was carried over from elsewhere, state the rule on its own, never where it came from ("same as <other project>" is not allowed).
- Public third-party references are fine and expected (ctr-native, world-of-kartcraft, Sable, Create, Fusion, Copycats+, NeoForge ...).
- Before every commit: search the tree (`git grep -i`) and the commit message for names of other projects and personal details, and
  check that `git config user.name` / `user.email` is an identity the author wants public.
