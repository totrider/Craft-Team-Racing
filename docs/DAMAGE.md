# Vanilla damage and karts

How Minecraft's own damage treats a kart and its driver, in the open world and in a race. *Last checked against the code 2026-11-20.*

**Implemented:** the sturdy kart, the router (`KartDamage`), the burn state, and driver protection with its two Rulebook switches (driver damage, hunger). **Not implemented:** choosing which damage sources count in a race, a data-driven table, and non-racer rules; they are listed at the end.

## How a kart takes damage
- **A kart is not killed by damage.** It used to be removed by anything a player caused, an arrow included (`KartEntity.hurt` took any player-caused damage for a punch). Now:
  - **Picking a kart up:** crouch and hit it by hand with the Wrench (an empty kart; a creative player may pick up a driven one). Any other punch does nothing, and a shot arrow is not a punch.
  - **Critical damage out of a race:** an explosion (not one of the mod's own weapons) or lava breaks the kart into its item. In a race nothing breaks it.
  - `/kill` and the void remove it.
- **The router** (`kart/KartDamage.java`). The damage source is sorted by the vanilla damage type tags (so a modded source carrying the right tag, or derived from one, lands in the right bucket), by a few types by name, and else by the fallback. Both a hit on the kart and a hit on its driver (`LivingIncomingDamageEvent`) go through it; the driver's own vanilla damage is untouched.

| Source | In free roam | In a race |
| --- | --- | --- |
| projectile (arrows, tridents, fireballs without fire ...) | spin out | spin out |
| explosion (vanilla) | breaks the kart (critical) | blast tumble |
| lava | breaks the kart (critical) | burn |
| fire, on fire, campfire, hot floor | burn | burn |
| lightning | blast tumble | blast tumble |
| fall, drowning, freezing, elytra, starving, suffocation, cramming, drying out | driver only | driver only |
| anything else (melee, magic, thorns, cactus, berry bushes ...) | spin out (the fallback) | spin out |
| the mod's own weapons' explosions | nothing (their effects do it) | nothing |

  Water is not damage: it is meant to slow the kart by the sim's rules. A reaction goes through `ItemManager.applyEffect`: a shield takes it and pops; a kart reacts at most once every 1.5 s so a dispenser cannot chain it; a spin or burn costs a power fruit as in the original.
- **Burn** (VehPickState_NewState, VEH_PICK_DAMAGE_BURN): a spin out with no input for 0x780 ms, a burn of 0xf00 ms in which the full damaged speed is taken off the top speed, sound 0x69. Flames and dark smoke are drawn on the kart (vanilla flame and smoke particles as a stand-in for the original's emitter, which the pack could replace). In the original only environmental fire causes it.

## Not implemented (ideas, in no order)
- **Data-driven mapping:** the table above as a data pack file (damage type or tag to a reaction, per free roam and race), like the power-up tables.
- **Rulebook rules for damage:** the allowed damage sources per race (which categories may affect karts and drivers), so a race can switch the reactions in the table above on or off. Today a kart in a race always reacts as the table says; only the driver's health and hunger are rules. Also whether hotbar items may be used while racing. The latter is a set of cancellable vanilla events, checked for racers.
- **Race and not race:** nothing keeps a non-racer from interfering with a race (other mods, mounts, ranged weapons make it unrealistic): that is for server moderation. Optional rules, off by default: damage and bumps from a non-racer do not affect racers; the router can log source and attacker.
- **OpenPAC compat** (a soft dependency, a `compat/` package): from a track's path find the claims it passes through; if it is in a claim, check whether that claim is already a race track, and which ruleset the track has; then offer the player to configure that claim, or a new claim made for the track, and set the claim's rules to what the ruleset needs. Every track gets a claim of its own so different tracks can run different rulesets at once on one server. The mod hooks standard NeoForge events and respects cancellation, so claims protect without a hard dependency; how OpenPAC treats power-up keys (not an item use) still has to be checked.

## Drivers in a race
By default a racing driver (from the gathering until the race is over) **cannot lose health** and **does not get hungry**, whatever the damage: vanilla sources and our own weapons' explosions alike. The kart still reacts to the hit (`KartDamage`: a spin, a burn, a blast ...); only the driver's health is spared (`RacerProtection`, a cancelled `LivingIncomingDamageEvent` after the kart's reaction; a hit another mod (a claim) has cancelled never reaches either). The food bar holds still: the driver's exhaustion is reset every tick. `/kill` and the void still work. Both are rules (`RuleSet.driverDamage`, `RuleSet.hunger`, off in the CTR rules) that a track's ruleset can turn on: the rulebook item sets them (docs/DESIGN_ITEMS.md). Out of a race everything is vanilla. A driver who dies (when damage is allowed) leaves the race by the usual respawn / leave handling.
