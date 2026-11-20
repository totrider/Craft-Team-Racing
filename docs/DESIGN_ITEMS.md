# Fruit, crates, power-ups and rule sets - design

Status: design agreed in outline 2026-11-12. Built: the pure core (`com.craftteamracing.items`: `HeldItem`, `ItemTables`, `PowerupTable`, `RuleSet`), the three
pickup spot blocks with their pickup entities, the power fruit count, the roulette, the held-item HUD and the use key (`com.craftteamracing.pickup`), and the
Turbo power-up. Since then every power-up the race roulette draws has been built (see POWERUPS.md), and the rulebook, drop table and track music sheet items. Fidelity policy: the original's behaviour is the default rule set; everything else is a rule set choice.

## What CTR does (ctr-native, read in the source)

| Thing | Behaviour | Source |
|---|---|---|
| **Power Fruit** | Picked up by touching it (players and bots); gone afterwards; +1 power fruit, the count is 0-10; a full 10 "juices" the kart (a top-speed bonus, and its items are stronger: `*_CHARGED` values for the turbo boost, mask, clock, invisibility and super engine in `Vehicle/VehPickupItem.c`). | `231/RB_Fruit.c` |
| **Fruit crate** | Breaks on touch, gives 5 + (random mod 4) power fruit (5 to 8). Comes back: a cooldown of 30 frames (1 s) that waits while something blocks the crate, then it grows back over 16 frames. | `231/RB_Crate.c` (`RB_CrateFruit_ThCollide`, `RB_CrateAny_ThTick_Grow`) |
| **Weapon crate** ("?" crate) | Breaks on touch, same respawn. It only gives something if the kart holds no item, is not already rolling one and is not firing; then it starts a 90-frame roulette and the item is drawn from the place-based set. A kart that cannot take it still breaks the crate. | `231/RB_Crate.c` (`RB_CrateWeapon_ThCollide`), `Vehicle/VehPhysProc.c:580` |
| **Time crate** | A time trial's crate: breaking it freezes the clock for 1, 2 or 3 s (0x3C0 / 0x780 / 0xB40 ms; three crate models); it never grows back (`STATIC_TIME_CRATE_01..03`). | `231/RB_Crate.c` (`RB_CrateTime_ThCollide`) |
| **Item roulette** | `VehPhysGeneral_SetHeldItem`: the item set follows the number of karts and the driver's place; an entry is drawn by `table[(roll * count) / 200]`. Last place's set is replaced by the third on lap 1. | `Vehicle/VehPhysGeneral.c:1681`, tables in `zGlobal_DATA.c:8154` |

Not in the decomp as read: how the weapons themselves behave (bomb, missile, mask ...). Each is read from its own source file when it is built.

## The placeable pieces
One placeable item per kind, so that a builder carries exactly what they need and the hotbar stays a choice:

- **Fruit spot**, **Fruit crate spot**, **Power-up crate spot** (the name "weapon crate" is CTR's; in this mod they give power-ups).
- Later, without changing the design: **Time crate spot** for time trials, and anything an extension adds.

A spot is a block (so it can float in the air or sit against a block) that is **only visible while a track item is in hand**, like the track
nodes, and **does nothing outside a race**. When a race starts the mod spawns the pickups at the spots as rotating, collidable entities; they are
gone when it ends. Spots are not part of the route graph: they belong to a track by being near it (within a configurable distance of one of the
track's nodes; open decision below).

Pickups are entities so they can rotate, bob, break with an effect and be collided with by any kart. Touching is the same sphere test the karts
use (`KartBump`-style: the kart's sphere against the pickup's), done on the driver's client and confirmed by the server, which owns the pickups'
state (taken, respawn timers). Crates respawn as in CTR; fruit does not come back during a race.

## Sizes
The stand-ins keep the original's proportions, normalised so a crate is exactly one block (the unit Minecraft works in; the kart, which already feels
right, is the other reference): a cube with a bevelled frame. A loose Power Fruit is 54.8 x 60.6 x 67.5 level units against the crate's 108.8, about
0.5 x 0.56 x 0.62 blocks (all measured from the models on the disc). Icons are 32 x 48, the Power Fruit sprite and the lamps 48 x 32, as the original's. The pickup's
reach for a kart is its own size plus the kart's half width.

## Original art
`tools/extract_ctr_textures.py` builds local resource packs from the player's own disc with the original crate faces, power-up icons, Power Fruit
sprite and start lamps (Minecraft size, and original size). The disc formats it reads are in `tools/ctr_disc_textures.py`. Models stay the mod's own;
the packs replace the pickup sheet (`textures/item/pickups.png`: palette, "?" face, fruit crate side) and the two crate models with plain cubes.

## Rule sets
`RuleSet` (pure) holds: the **power-up table**, fruit on/off, fruit crates on/off, power-up crates on/off, boost pads on/off, a top-speed cap in
percent (0 = none). **The default is `RuleSet.CTR`**, the original game: place-based tables, everything on, no cap.

- **The ruleset item** (a "rulebook"): configured once (a screen with a scrollable power-up list with a weight per item, and the toggles), then
  **copied** (crafting or in the creative tab: copies keep the data) and **sneak + right-click on a Start node** applies it to that track.
- **Temporary vs default**: a rule set applied to a start is *temporary* by default and the start falls back to its default after the race. The
  rulebook has a "set as default" toggle; who may apply a default is a permission (op / creative / later a claims mod, through `TrackPermissions`).
  So a track owner can let visitors try their own rules for a race without changing the track.
- A power-up table is **either CTR's** (rank-based) **or a flat weighted list** of item ids. The default list shows CTR's shares as a starting
  point when a builder switches to custom.

## Extending the power-ups with data packs
Goal: as little friction as possible, with real flexibility. Proposal, to be built after the built-in items exist:

1. A power-up is a **data pack entry** (`data/<ns>/craftteamracing_powerup/<name>.json`): id, icon, name, how many uses, and an **effect**.
2. An effect is a **list of built-in actions** (boost for N frames, shield, fire a projectile of a chosen kind, drop a hazard, hit the kart in
   front, give power fruit ...) with parameters; most power-ups are a combination of these.
3. For anything the actions cannot do, an effect may call a **function** (`"function": "pack:my_effect"`) that runs as the driver at the kart;
   command functions are the data pack world's own way to script, so no new language is needed.
4. Ids from packs go into a rule set's table exactly like the built-in ones (`mypack:banana`).

Each built-in power-up is then also expressible this way, which keeps the built-in and extension paths the same.

## Build order
1. **Pure core** - done (item ids, CTR's tables and place rules, `PowerupTable`, `RuleSet`).
2. **Fruit spot + fruit crate spot**: blocks, items, ghost display, race-time spawning, collision, the power fruit count in the HUD and its top-speed effect.
3. **Power-up crate spot**: the roulette (90 frames, the HUD slot) and a first few items (turbo and shield are the simplest, then the potion and TNT hazards).
4. **Rulebook item and its screen**, applied to a Start; temporary vs default; permissions.
5. The remaining items one at a time (bomb, missile, mask, warp orb, clock ...), then the data pack format.
6. **Time trial** with time crates, as a rule set / placeable extension.

## Open decisions
- How a spot belongs to a track: **within N blocks of one of the track's nodes** (built: `pickupTrackRadius`, default 96) or an explicit link from a node. The distance is taken to the **lines between linked nodes**, not to the nodes themselves, so a crate beside a long straight is as near as one beside a node. Spots are outlined green (within range of a track) or red (not) while a track item is in hand.
- Crate **respawn time** and whether the **fruit stays gone** are CTR's by default; both could be rule set options later.
- ~~Whether a rulebook is a new item or the Wrench with a mode.~~ Decided: **its own item**, whose settings travel with the item when it is copied, like a map or a Create schematic.

## Extensions decided along the way
- A **power-up crate spot can be set to one particular power-up** (Wrench, right-click): good for testing, and for builds that want a fixed power-up.
- Spots **react to redstone**: with a signal the pickup is there without any track or race. A fruit that was taken stays gone until the signal is off and on again; crates come back as in a race.

## Carried items, spots and leaving a race (2026-11-12)
- **The power fruit count and the held power-up belong to the kart**, not to the driver's seat: they are saved with the kart (`PowerFruit` (older saves: `Wumpa`), `HeldPowerup`), stay when the driver gets out and are shown again when anybody gets in. A race clears them for its entrants when it puts them on the grid (`ItemManager.reset`), so every race starts with nothing.
- **Pickup spot blocks are invisible** and drawn (`PickupSpotRenderer`) only while an editing item is in hand: a spot block, the Track Tool, a node block or the Wrench.
- **The power-up roulette sound** loops on the driver's client for the whole roll (`RouletteSound`; CTR starts OtherFX 0x5D and stops it when the item is drawn).
- **Redstone and fruit:** a spot with a redstone signal behaves as if a race were on near it. A fruit spot keeps at most one fruit (it holds the id of the one it spawned and spawns again only when that is gone). A fruit that is taken does not come back while the signal stays on (CTR: fruit does not respawn in a race); switching the signal off and on again resets the spot, so a clock or a lever makes it return. Crates respawn after their CTR delay while powered.
- **Leaving a race:** sneaking does not get you out of the kart between the lights and the finish; hold the *Leave race* key (Backspace by default) for two seconds. Free roam is unchanged.
- **Redstone, revised:** a redstone signal arriving at a spot **arms** it (saved with the block): its pickup appears (a fruit that was taken comes back, a crate respawns as usual) and **stays after the signal is gone**. A new pulse brings back a fruit that was taken. A race on a track near the spot works independently of this (it resets the spot when it begins), so spots with and without redstone can share a track. An armed spot has no off switch other than removing the block.

## The rulebook item (built 2026-11-15)
- **Item:** `craftteamracing:rulebook` (`RulebookItem`), in the creative tab. It holds a `RulebookData` (the data component `craftteamracing:rulebook`: a `RuleSet` and `asDefault`), so the rules travel with the item when it is copied (a middle click in creative).
- **Screen:** right-click (in the air or on any block but a Start node) opens `RulebookScreen`: toggles for power fruit, fruit crates, power-up crates, boost pads, driver damage, driver hunger, offroad for all and soft bumper for all (docs/UPGRADES.md), a cycle for the top speed cap (none, 100 to 50 %), and whether applying sets the track's default or only the next race. Saving sends `RulebookSavePayload`; the server writes it on the book in that hand.
- **Applying:** right-click a Start node (no crouch needed; the Start nodes are shown while the item is held). As the **track's default** it needs `TrackPermissions.canEdit` and is saved with the track (`TrackData`, the `rules` list); otherwise it is **for the next race only** (anybody; used up when that race ends). Refused while a race is gathering or running on the track. A race snapshots the rules (`RaceManager.Race.rules`: the temporary ones, else the default, else CTR's) when it begins to gather.
- **What reads the rules:** the power-up draw (`ItemManager.rules(player)`), which pickups spawn (`PickupSpotBlockEntity`: fruit, fruit crates, power-up crates; a redstone-armed spot ignores it), driver damage and hunger (`RacerProtection`), and, through `RulesPayload` sent to each racer, the boost pads and the top speed cap in the driver's own sim (`RaceClient.rules()`, `KartSim.speedCapPercent`).
- **To add a rule later:** a field in `RuleSet` and in `RulebookData`'s codec (give it a default so old rulebooks still load), a toggle in `RulebookScreen`, and a reader where the rule is used. Damage categories and hotbar items (docs/DAMAGE.md) are the next fields.
- **Not built:** crafting a copy of a book, a name or description on a rule set, data pack power-ups in the custom list, and rule sets per claim (OpenPAC compat).

## The drop table item (built 2026-11-15)
The power-up table moved out of the rulebook into an item of its own (`craftteamracing:drop_table`, `DropTableItem`, the data component `craftteamracing:drop_table`: a `PowerupTable` and `asDefault`), so a track's drops can be swapped without touching its rules.
- **Screen** (`DropTableScreen`): a grid of power-up icons with their weights (left click +1, right click -1, crouch for 5, the wheel scrolls when there are more power-ups than fit, hover for the name); the classic roulette is one switch away (touching a cell with it on starts a custom list from CTR's shares); applies as the track's default or for the next race only. The list comes from `PowerupRegistry` (the one place that grows with data pack power-ups).
- **Applying:** right-click a Start node (no crouch needed; the Start nodes are shown while the item is held), as the rulebook (default needs `TrackPermissions.canEdit`; refused while a race is on the track). `TrackData` keeps the default table (saved) and the one for the next race (used up), apart from the rules; a race starts with `TrackData.effectiveRules` = the track's rules with the drop table in place of their own when there is one.
- An older rulebook that holds a custom table keeps it (the screen leaves it as it is), the drop table overrides it.

## Crates locked to power-ups (built 2026-11-15)
- A power-up crate spot can be set to **several** power-ups (`CrateItems`: a comma-separated list in the spot's `Item` tag and the item's `crate_item` component, so an older single id is a list of one). A kart gets **one of them at random** per crate; none selected is the general drop table. The Wrench's screen toggles cells; the "?" cell clears.
- A crate locked to power-ups shows the **icon on each of its six faces** where the "?" is (`IconBillboard.drawFaces`: six flat quads 0.004 block off the faces, 0.6 block across; an unseen larger box would be the same six quads and need a model of its own, so planes are the cheaper and simpler). A list cycles one icon every 1.5 s. The icon over the crate (and the spot's ghost) while holding an editing item stays, for crates turned off by redstone.

## The track music sheet (built 2026-11-16)
`craftteamracing:track_music_sheet` (`MusicSheetItem`, the data component `craftteamracing:music_sheet`: a sound id, a free-text title and `asDefault`, `MusicSheetData`). Right-click opens `MusicSheetScreen`: a text field for the title and a grid of the tracks the loaded resource packs provide
(the sound events `craftteamracing:music.level.<name>` under `MusicTitles.available`), "Default" and "None" first; tracks the pack does not provide are shown grey (set by someone else, kept as they are). Right-click a Start node to apply it (as the track's default with
`TrackPermissions.canEdit`, else for the next race only; refused while a race is on the track), as the rulebook. `TrackData` keeps the default music apart from the rules and the temporary one (used up with the race). A race snapshots it when it begins to gather and sends `MusicPayload`
to every racer; the title is shown even where the sound is missing, and `RaceMusic` plays the track's event (the default `music.race` when nothing is set, nothing for "None"). See [MUSIC.md](MUSIC.md).

## The trigger crate ("!" crate) and the Redstone Linking Node

- **Trigger Crate Spot** (a spot block like the others, a `PickupKind.TRIGGER_CRATE`): its "!" crate is there all the time, in a race or not, and a signal reaching the spot does nothing (it
  gives one). A kart (or a rolling bomb) that breaks the crate switches the spot's redstone output on; the crate comes back after its respawn timer.
- **Settings** (the Wrench opens a screen): *respawn* (step 0: the other crates' delay; then 1 to 10 ticks, 1 to 60 seconds, 2 to 5 minutes), *redstone signal* (step 0: always, i.e. on for good once
  broken; then the same steps) and *uses* (0: unlimited, up to 64; when they are spent the crate no longer comes back).
- **Redstone Linking Node**: a small visible block that gives a signal while any Trigger Crate Spot linked to it does. Link: Wrench on the node (it is picked), then Wrench on the spot; with the node picked, a crouching click on the spot unlinks it. Crouching on the node lets go of it; with no node
  picked, a click on the spot opens its settings. A spot draws its links (red lines) while an editing item is in hand. Several spots on one node: the node goes off when the first of them ends its signal (not counted).
- The pictures: the jar's "!" is the blank crate with a "!" in the stand-in "?"'s colours; the local texture pack takes the original "?" crate's own colours and outline for a "!" it draws on
  the blank crate (`crate_blank.exclaim`).

- **Markers on top**: with an editing item in hand, the spot markers and track node lines within 7 blocks are drawn over everything (`OnTopTypes`), so crates standing on them do not hide them.

## Quantities on a power-up crate spot

- Each power-up of a crate spot's list has a quantity: exactly n (1 to 10), or a random number from a minimum to a maximum. Stored in the spot's list string as `id`, `id*n` or `id*min-max`; the old
  `bomb_3x` / `missile_3x` ids read as `bomb*3` / `missile*3` (the Wrench screen has no separate three-bomb and three-missile cells any more; the drop table's roulette still has them, as the original).
- The screen: click a power-up to select or deselect it (as before); a selected one has an options mark in its corner, and a power-up that has just been selected is open already. The panel under the
  grid sets its quantity: one slider, or with "random range" on a minimum and a maximum slider. The number is drawn over the icon (nothing for one, "2", "2-4") in the screen, on the crate's faces and
  over the spot while building.
- What a kart gets: the roulette ends with the power-up and the quantity rolled when the crate was broken; every power-up takes one shot of the stack per use (`consume`: a short cooldown between
  shots, the last one takes it away), so two boosts or a stack of totems work like the three bombs always did. The HUD shows the number left.
