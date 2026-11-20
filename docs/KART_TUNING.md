# Kart tuning profiles

A kart's stats (acceleration, top speed, turning...) come from a **tuning profile**, chosen in the Wrench's kart screen. Profiles are data pack entries, so anyone can add or switch off profiles. Tuning never depends on how the kart looks.

## How it works
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
