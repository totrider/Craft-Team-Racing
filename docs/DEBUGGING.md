# Debugging and diagnostics

Tools a player, a track builder or a server operator can use to find out why something does not work as expected. Only things that are in the released mod are listed; tools that exist only for development are left out.

**Contents:** [Commands](#commands) · [What you can see without a command](#what-you-can-see-without-a-command) · [The tuning debug profile](#the-tuning-debug-profile-operators) · [Settings that matter when debugging](#settings-that-matter-when-debugging) · [Common problems](#common-problems)

## Commands
All start with `/ctr`. The `debug` views are drawn only for you, and nothing else on the server changes.

| Command | Who | What it does |
|---|---|---|
| `/ctr debug rings` | Anyone | Toggles a view of **every checkpoint box of the course**, whether or not you hold a track item, also during a race. Colours: **green** the server counts it as passed, **yellow, flashing** the last one passed, **orange** the next one that counts, **grey** the rest. Use it when a checkpoint does not count where you expect. |
| `/ctr debug path` | Anyone | Toggles a **line from your kart to the point of the course the server thinks you are on**, with that stretch of the course picked out, and a line of text about your progress (below). Use it when positions, laps or the wrong-way warning look wrong. |
| `/ctr debug nodes` | Anyone | Toggles drawing the track's **nodes, the start grid and the pickup spots** without holding an editing item, also during a race. |
| `/ctr debug chunks` | Anyone | Toggles a view of **the chunks a race on the tracks round you would keep loaded**: **green** the chunks the course runs through (its nodes and the links between them, and the podium nodes), **yellow** the ring round them (blocks and pickup spots there run, but not entities). Drawn as an overview: a flat tile for every chunk at the height of the course, so it reads the same wherever you stand or fly (up to 40 chunks away), with tall posts at the corners of the green ones, like the chunk borders of vanilla's `F3 + G`. Worked out from the nodes your client knows, so it shows what a race would hold even when none is on; whether the server really holds them is the `keepCourseLoaded` setting. |
| `/ctr debug terrain` | Anyone | Toggles a view of **the ground the kart's physics sees** around the kart you drive, rebuilt every tick: half-block cells laid on the smooth surface it rides, coloured by steepness (**green** flat or under 20 degrees, **yellow** 20 to 35, **orange** 35 to 45, the steepest ramp the surface makes), **nothing** where there is no ground the kart could stand on, and **red outlines** on the collision boxes it would hit as walls. Use it when a kart bumps into something that looks climbable, or drives up something it should not. |
| `/ctr barriers [on\|off]` | Anyone | Toggles drawing the **Kart Barriers** all the time, not only while you hold a track building item (saved in the client config as `showBarriers`). |
| `/ctr track reset` | Track editors | Clears what test drives recorded for the Start Node nearest to you, so you can record the main lap again. Needs permission to edit tracks (see [`trackEditing`](#settings-that-matter-when-debugging)). |
| `/ctr speed` | Anyone | Shows the server's speed scale and whether jumps shrink with it. |
| `/ctr speed <50-100>` | Operators | Sets the speed scale in percent while you play (saved to the config, sent to every player at once). See [`speedScale`](#settings-that-matter-when-debugging). |
| `/ctr speed shrinkjumps <true\|false>` | Operators | Whether a lower speed scale also lowers and shortens jumps (`true`) or keeps their shape (`false`). |
| `/ctr debug spawn <kind> [charged]` | Operators | Puts a power-up entity standing still a few blocks ahead of you so you can look at it: `bomb`, `missile`, `shield`, `beaker`, `tnt`. It does not move, explode, hurt anyone or expire. `charged` gives the stronger form. One per player; spawning another replaces it. |
| `/ctr debug clear` | Operators | Removes the one you spawned. |

Run each command again to switch its view off.

### Reading the `/ctr debug path` text
It looks like this: *line at ring 4 of 12 (on the main path), lap 2; nearest line: main; distance 1.482 (segment 3 + 0.55); race phase 3.*
- **line at ring N of M:** the checkpoint the server has you at, and how many the course has. **on the main path**, or **on a shortcut, K of L rings** when you are on a branch, or **out of a shortcut** just after leaving one.
- **lap:** the lap you are on, counted from 1. **behind the start line** is added when you are before the line.
- **Forks and merges:** the rings after a fork (the branch's first ring and the next main ring) and the rings before a merge (the branch's last ring and the last main ring) are interchangeable for the line, so *line at ring N* can jump back to the fork when you back out of a branch. Which end a branch enters at is decided by where its rings lie against the main path (length only breaks a tie), which matters for a branch that touches the Start Node.
- **nearest line:** the part of the course closest to you, the main path or a shortcut `a->b`. If it names a shortcut while you are on the main road (or the other way round), a box covers more than its part of the track.
- **SKIPPED / WRONG WAY:** the server has decided you skipped a checkpoint or are driving against the course. These are the same conditions the on-screen warnings show.
- **distance:** your progress along the course in segments, used to rank positions.
- **race phase:** a number: 1 gathering, 2 start lights, 3 racing, 4 done. It also says whether you are on a podium or out of the race.

## What you can see without a command
- **While holding a node, the Track Tool or the Wrench:** every node near you is drawn as a box with its direction, with links as lines. Pickup spots are drawn too, **green when they are within range of a track** and **red when they are not**, so a spot that never gives a pickup is easy to find. Markers within 7 blocks are drawn over everything, so crates standing on them do not hide them.
- **During a test drive:** the boxes you can reach next **glow cyan** and a **gold line** shows the recorded main path. A node that was never driven keeps the track invalid.
- **In a race:** the **WRONG WAY** and **CHECKPOINT SKIPPED** warnings appear on screen (each can be switched off in the client config), and the **track bar** above the timers shows every racer's place along one lap.
- **Kart screen (Wrench on a kart):** whether the kart may start races, which upgrades are installed and switched on, and its tuning profile.
- **Crate spot screen (Wrench on a power-up crate spot):** what the spot gives, with the quantity of each power-up.
- **Item tooltips:** a kart item lists its paint and upgrades; a power-up crate spot item says what it gives.

## The tuning debug profile (operators)
Karts take their stats from a tuning profile ([KART_TUNING.md](KART_TUNING.md)). Operators (permission level 2) get an extra last entry in the tuning list: **Debug**.
1. Right-click an empty kart with the **Wrench** and open **Tuning**.
2. Pick the **Debug** profile at the end of the list. Its bars become **sliders for all seven numbers** (acceleration, top speed, turn rate, turn decrease, turn input delay, drift turn base, collision weight) with wide ranges. The server accepts any values the physics can run on.
3. **Use** puts the numbers on the kart at once, so you can drive and feel the change. The kart's tuning becomes a `debug:` id that saves, syncs to other players and drives like any profile.
4. **Export** writes the numbers as a ready-to-use data pack entry to the `craftteamracing-tuning-exports` folder in the game directory. Put the file into a data pack at `data/<namespace>/craftteamracing/kart_tuning/<name>.json` to make it a real profile.

Other players never see the Debug entry. A profile can be switched off in a data pack or with `disabledTuningProfiles` in the server config.

## Settings that matter when debugging
Server config (`craftteamracing-server.toml`, in the world's `serverconfig` folder):
- `trackEditing`: who may edit tracks. Creative players and operators by default. If nodes cannot be placed, linked or applied, check this first.
- `raceMinRacers`: how many karts must be in the gathering area before a race starts (1 by default). A race that never starts with only one kart usually has this set higher.
- `keepCourseLoaded` (on by default): the chunks along the track are kept loaded and running from the moment a race gathers until it ends, so the pickup spots along the whole track keep their crates whoever is near. `keepWeaponsLoaded` (on by default): the chunks round a placed or flying weapon are kept loaded while it exists, so a trap does not vanish when nobody is near it and a Missile or Warp Orb does not freeze at the edge of the loaded world. Both use vanilla's own chunk tickets (nothing is saved); switch them off if a claims or chunk-loading mod already does this, or if a long track costs too much server time.
- `pickupTrackRadius`: how far from the track's lines pickups appear (96 blocks by default). Spots outside it are shown red and do nothing in a race.
- `entities` (`contact`, `damage`, `damagePlayers`, `damageScale`): whether karts bump and hurt mobs, boats, minecarts and players on foot.
- `speedScale` (50 to 100, default 100): how fast everything may move in percent: the kart, the flying weapons, a jump's height and length, a turn's radius, in the open world too. For a weaker server or a smaller track. The kart keeps its size. A track's Rulebook turns the same knob for its race's participants, in the same 50 to 100 range; this value is its default. `shrinkJumps` (default on) says whether jumps shrink with it: on, a lower speed gives lower, shorter ramp jumps with less air time (and so a smaller landing boost), and a plain hop stays as it is; off, a jump keeps its shape at the smaller size and the same air time. Operators can change both while playing: `/ctr speed shrinkjumps <true|false>`, and the speed: `/ctr speed <percent>` (saved to the config and sent to every player at once; `/ctr speed` alone shows it).
- `weaponSpeedScale`: scales the speed of thrown weapons.

Client config (`craftteamracing-client.toml`, in `config`; also under Mods > Craft Team Racing > Config): sounds, the wrong-way and skipped-checkpoint warnings, the podium camera, motion blur, animated boost pads and Jade hiding.

The game log (`logs/latest.log`) lists data pack tuning profiles that were skipped because their numbers cannot be simulated, and anything the mod cannot load.

## Common problems
| Problem | What to check |
|---|---|
| A checkpoint does not count | `/ctr debug rings`: is the next ring (orange) where you think? A box must be **touched**. If the road is wider than the box, or a jump takes the kart more than 8 blocks above (or 3 below) it, widen the box or add a node. |
| Laps or positions jump, or WRONG WAY shows on the road | `/ctr debug path`: if *nearest line* is a shortcut, a box or a parallel lane overlaps another part of the course. Shrink the boxes to the road they belong to. |
| The race will not start | The track must be **valid**: every node and branch test driven, the loop closed back to the Start Node. Changing nodes or links invalidates it. Then check `raceMinRacers`, that the kart is allowed to start races (Wrench), and that the kart is inside the gathering area behind the Start Node. |
| The test drive does not finish | The boxes you can reach next glow cyan: drive through every one in order, including the branches back to the main path. `/ctr track reset` starts the main lap over. |
| Pickups do not appear in a race | Spots must be within `pickupTrackRadius` of the track (green when holding an editing item), and the rules applied to the track (Rulebook) must have that kind of pickup switched on. |
| A rule item cannot be applied | It cannot be applied while a race is running or gathering on that track, and applying it as the track's **default** needs permission to edit tracks; otherwise it is used for the next race only. |
| The kart will not drive over a block | One-block steps, slabs and stairs are ramps; two-block steps, fences and walls are walls. See [TERRAIN.md](TERRAIN.md). |
| The kart is slow on some block | Surfaces come from block tags (`craftteamracing:surface/...`); dirt, grass, sand and gravel are slow off-road. Offroad Tires, or the Rulebook's offroad rule, change this. |
