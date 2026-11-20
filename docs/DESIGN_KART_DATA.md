# Kart data: tuning (stats) separate from look (visuals)

> **Design record.** This note was written while planning and building, and is kept for the reasoning. It is **not the manual**: for what is implemented and how to use it, see the [README](../README.md) and [KART_TUNING.md](KART_TUNING.md). Where it disagrees with the game, the game and those guides are right.

Tuning profiles are built (see [KART_TUNING.md](KART_TUNING.md)); looks and parts are not. Designed 2026-11-12. Follows the fidelity policy in [../CLAUDE.md](../CLAUDE.md): PS1 behaviour by default,
extensions opt-in.

## The split
A kart has two independent pieces of data. Neither one ever changes the other.

| | Tuning profile | Look (visual parts) |
|---|---|---|
| What | the physics stats | chassis, wheels, seat, steering, exhaust... (the model's groups) |
| Decides | accel, top speed, turn rate, turn decrease, turn input delay, drift turn base, collision weight | how the kart is drawn |
| Data component | `craftteamracing:tuning` | `craftteamracing:look` (later) |
| Synced | to the driver (physics runs on their client) and to everyone for display (name) | to everyone (the renderer) |
| Edited with | the Wrench's kart screen | a customisation screen / parts items (later) |
| Default | the four PS1 engine classes | the current placeholder kart |

Both live on the **kart item** and on the **kart entity**: spawning copies the item's components to the entity, picking up (or pick-block)
writes them back to the item, so a tuned kart stays tuned when you carry it. (Components, not NBT: NeoForge 1.21.1 `DataComponentType`s.)

## Tuning profile
- `KartClass` (an enum of the four PS1 classes) becomes `KartStats` (a record in `com.craftteamracing.physics`, still no Minecraft imports).
  `KartSim` takes a `KartStats`; the four enum constants remain as the built-in defaults, so nothing about driving changes.
- A profile is a **datapack entry**: `data/<ns>/craftteamracing/kart_tuning/<name>.json` with a display name and the stats. Shipped: `balanced`, `acceleration`,
  `speed`, `turning` with the exact PS1 numbers (spec section 10), flagged `"official": true`. Anyone can add more (clearly not PS1).
- The component stores the profile id (`craftteamracing:balanced`), not copies of numbers, so datapack tweaks apply and old karts stay valid; an
  unknown id falls back to `balanced`.
- Racers are presets by name: "Crash" shows as Balanced etc. (cosmetic label, spec section 10 table).

## Tuning profile (as built)
A profile is a data pack entry holding the physics stats, stored on the kart by id. The format, sync and screen are documented in [KART_TUNING.md](KART_TUNING.md).

## Look (later)
- Parts: the model already has one group per part (`chassis`, `nose`, `body`, `seat`, `steering`, four wheels, `exhaust`). A look = one choice
  per slot (a part id), each part a small Blockbench model + texture (same rules as everything else: bbmodel is the source).
- Rendering composes the chosen parts (a baked model per part, drawn in order), still with the wheelie/tilt/flame effects, which attach to anchors, not to the part.
- **No stats from visuals, ever** (the user's rule). If survival crafting (M3) wants "better wheels = better handling", that is a separate opt-in
  rule that *sets a tuning profile*, it is not baked into the part.
