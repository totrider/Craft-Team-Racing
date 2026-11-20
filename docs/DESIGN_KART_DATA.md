# Kart data: tuning (stats) separate from look (visuals)

Status: **tuning profiles built (see "Built" at the end); looks and parts not built.** Designed 2026-11-12. Follows the fidelity policy in [../CLAUDE.md](../CLAUDE.md): PS1 behaviour by default,
extensions opt-in.

## The split
A kart has two independent pieces of data. Neither one ever changes the other.

| | Tuning profile | Look (visual parts) |
|---|---|---|
| What | the physics stats | chassis, wheels, seat, steering, exhaust... (the model's groups) |
| Decides | accel, top speed, turn rate, turn decrease, turn input delay, drift turn base, collision weight | how the kart is drawn |
| Data component | `craftteamracing:tuning` | `craftteamracing:look` (later) |
| Synced | to the driver (physics runs on their client) and to everyone for display (name) | to everyone (the renderer) |
| Edited with | the Tuning Wrench | a customisation screen / parts items (later) |
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

## Tuning Wrench
- Item `craftteamracing:tuning_wrench`. Right-click a kart (nobody driving it) opens a screen: the available profiles as a list, bars for the stats, an
  "official PS1" badge. Picking one writes the component on the entity. Pick the kart up and the item carries it (tooltip shows the profile).
- **Open:** who may use it (anyone / creative+ops), and whether the screen may offer free numeric stats (default: no; only profiles; a server
  config flag could enable sliders, clearly marked as not PS1).

## Look (later)
- Parts: the model already has one group per part (`chassis`, `nose`, `body`, `seat`, `steering`, four wheels, `exhaust`). A look = one choice
  per slot (a part id), each part a small Blockbench model + texture (same rules as everything else: bbmodel is the source).
- Rendering composes the chosen parts (a baked model per part, drawn in order), still with the wheelie/tilt/flame effects, which attach to anchors, not to the part.
- **No stats from visuals, ever** (the user's rule). If survival crafting (M3) wants "better wheels = better handling", that is a separate opt-in
  rule that *sets a tuning profile*, it is not baked into the part.

## Build order
1. `KartStats` + datapack tuning entries + `craftteamracing:tuning` component on item and entity + tests (physics unchanged for the four defaults).
2. Tuning Wrench + screen.
3. Look component + parts (later, own milestone).


## Built: tuning profiles (the Wrench's first option)
- `KartStats` (physics, no Minecraft imports) holds the numbers (accel, speed, turn rate, turn decrease, turn input delay, drift turn base, collision weight); `KartClass` keeps the four official
  classes and `KartSim` runs on a `KartStats`. Nothing changes for the four defaults.
- **Profiles are data pack entries**: `data/<namespace>/craftteamracing/kart_tuning/<name>.json`
  ```json
  { "name": "Heavy", "description": "Shown under the name.", "official": false, "enabled": true,
    "stats": { "accel": 480, "speed": 13140, "turn_rate": 28, "turn_decrease": 4080, "turn_input_delay": 5000, "drift_turn_base": 14, "weight": 256 } }
  ```
  Numbers left out are Balanced's; entries with numbers the simulation cannot run on are logged and skipped. The mod ships `craftteamracing:balanced`, `acceleration`, `speed` and `turning` (the PS1 numbers, `official`).
- **Turning one off**: a data pack replaces its file with `{ "enabled": false }`, or the server config lists the id in `disabledTuningProfiles` (`"speed"` or `"craftteamracing:speed"`).
- **Fallback**: a kart whose profile is unknown or off is tuned as Balanced; if Balanced is off, as the first profile left; if none is left, as the original Balanced numbers.
- **Sync**: the server sends its enabled profiles to every player on joining and after a data pack reload (`TuningListPayload`); the driver's client runs its simulation on the profile of the kart
  it drives (re-read every tick, so a change applies at once), and other karts' bump weight comes from theirs.
- **Storage**: the kart entity's `Tuning` id (synced), saved with the entity and in the kart item's data, so a picked-up kart keeps it; the item's tooltip names it.
- **UI**: the Wrench's kart screen has "Tuning: <name>..." as its first button. It opens a list of the profiles (scrolls) with the picked one's description, a "Classic class" or "Custom
  (data pack)" badge and a bar and number for each stat, with a tick for what the kart has now; "Use this tuning" sends `SetTuningPayload`, which the server checks against its own list.
- **Debug profile** (operators only, permission level 2): the list's last entry. Its bars are sliders for all seven numbers (wide ranges; the server accepts anything `KartStats.valid()` allows);
  "Use" puts them on the kart at once (the kart's tuning id becomes `debug:accel,speed,...`, so it saves, syncs and drives like any profile) and "Export" writes the numbers as a data pack entry to
  `craftteamracing-tuning-exports/` in the game folder.
