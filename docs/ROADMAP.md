# Craft Team Racing roadmap

Target: Minecraft 1.21.1, NeoForge 21.1.265, Java 21. Mod id `craftteamracing`, package `com.craftteamracing`.
Goal: drive karts with *Crash Team Racing*'s real physics on ordinary Minecraft block terrain, built from
blocks and crafted in survival, compatible with Sable / Create / Create Aeronautics.
Background and sources: [RESEARCH.md](RESEARCH.md).

## Architecture decisions

- **Pure physics core** (`com.craftteamracing.physics`): no Minecraft imports, integer fixed-point exactly like CTR
  (positions/velocities 1/256 units, angles 4096/turn, 8.8 terrain multipliers, arithmetic shifts). Input in
  (steer, accel, brake, drift/hop, tap latches), world queries via an interface (`GroundProbe`), pose + events out
  (boost, spin-out, hop, hang time). Unit-testable headless; replays can be checked against numbers from the decomp.
- **Fixed 30 Hz sim with an accumulator** (CTR constants assume one step per frame; Minecraft ticks at 20). The
  driving client simulates, the render thread interpolates between the last two sim poses. Input taps are latched.
- **Client-authoritative driving, server validates** (like world-of-kartcraft): the driver's client runs the sim and
  sends the kart state ~10 Hz; the server sanity-checks speed/teleport and relays. Needed for responsive feel at 30 Hz.
- **World adapter** (`com.craftteamracing.world`): turns Minecraft blocks into the `GroundProbe` the core uses (M2).
- **Kart is an entity** driven by the player riding it (M1). Sable compatibility layer is separate (`compat/sable`),
  soft dependency, never required to load.
- **Scale:** 1 sim unit length is chosen so that Balanced top speed reads right at 1 block = 1 m (tune in M1; constant
  lives in one place).
- **Models/art:** Blockbench `.bbmodel` is the source of truth for every model, `.kra` for textures, generators read
  them (see [../CLAUDE.md](../CLAUDE.md)). No CTR assets shipped.

## Milestones

### Done (the first release, 0.1.0)
- **M0 Setup and research.** Gradle/NeoForge workspace, research in [RESEARCH.md](RESEARCH.md), test-instance install script.
- **M1 Drive a kart.** The physics port (`KartSim`, tests, [PHYSICS_PORT.md](PHYSICS_PORT.md)), the kart entity and item, the 30 Hz client sim, the original's HUD meters, exhaust, sounds (own placeholders; the original's only as a local pack).
- **M2 Smooth terrain.** Steps, slabs and stairs become ramps, surfaces from block tags, boost blocks (pad, block, slab, stairs), Fusion connected textures, copycat support, the kart leaning on the ground. See [TERRAIN.md](TERRAIN.md).
- **M2.5 Pad families, Power Fruit, tunings.** The Super Boost family (opt-in), Power Fruit and its effects, kart tuning profiles as data pack entries ([KART_TUNING.md](KART_TUNING.md)).
- **M2.6 Tracks and races.** Nodes and the Track Tool, test drive and validation, branches, gathering, grid, lamps, start rev, laps, positions, HUD, respawn, wrong-way and skipped-checkpoint warnings, the track bar, podium nodes and the ceremony, ethereal finished karts, race music. See the README's track building steps; the design record is [DESIGN_TRACKS.md](DESIGN_TRACKS.md).
- **Crates and power-ups.** Power Fruit, fruit crates, power-up crates with the place-based roulette, trigger crates and the redstone linking node, every power-up the race roulette draws, the Rulebook, Drop Table and Track Music Sheet. See [ITEMS.md](ITEMS.md), [POWERUPS.md](POWERUPS.md).
- **M3 Survival.** The Power Palm and its wood set, Power Fruit, Juice, Ingot, wheels, crates, recipes for every item, the kart as an item with paint, upgrades (Offroad Tires, Soft Bumper, Floater family, Paddle, Jump Spring, Low Gravity Module), damage handling ([DAMAGE.md](DAMAGE.md)). See [SURVIVAL.md](SURVIVAL.md), [UPGRADES.md](UPGRADES.md).

### Next
- **Guide for new players.** Advancements from "find a palm" to "win your first race", and an in-game guide book (Patchouli is the candidate) with the items, blocks and how to build a track. The Power Fruit and a book are the way into it.
- **Time trials and ghosts**, on the same track data; a Time Crate Spot.
- **Ready check** before a race, a nicer start.
- **Battle mode** with its Super Engine and Invisibility power-ups.
- **Kart looks and parts** (a Garage, kept separate from the stats: no stats from visuals).
- **Damage and rule data** (the damage table as a data pack file, a ruleset for the allowed damage sources).

### Later: compatibility
- **Sable / Create Aeronautics:** karts driving on sub-levels (ground probe in sub-level space through Sable's transforms), decide between "entity on a sub-level" and "kart as a sub-level". Prototype before committing. Soft dependency, never bundled.
- **Open Parties and Claims:** find the claims a track passes through and set up a claim per track with the ruleset's needs; standard NeoForge events keep claims working without a hard dependency.
- **Xaero's Minimap / World Map:** the track and the racers on the map, instead of a map of the mod's own (decided 2026-11-12; how is still to be researched, including licences and APIs).
- **Every Compat** works (the Power Palm is picked up as a wood type); Handcrafted and Chipped through it are untested.

### Ideas for more survival depth, unscheduled
- **Kart parts:** a Garage where the kart is built from chassis, engine, tires and body, so the three classes become something to build towards (no stats from looks).
- **Power Cells:** a nugget-and-redstone battery that a boost pad takes for a bigger, longer boost.
- **Palm country:** palm signs and boats, a hammock, beach villages with a fruit merchant.
- **Juice by ripeness:** a cluster picked before it is full gives sour juice and less ore.
- **Racing garages:** small generated tracks near beaches.

### Ideas, unscheduled
AI karts, custom character skins, replays, more kart classes, a ground-tilt and chase-camera pass, interpolation for other players' karts (20 Hz now), a long-arrow boost pad look.

## Open decisions
- **Sable integration style:** an entity on a sub-level, or the kart as a sub-level. Prototype before committing. (Licence and controls are settled: GPL-3.0-or-later, and vanilla movement keys plus a few of the mod's own.)
