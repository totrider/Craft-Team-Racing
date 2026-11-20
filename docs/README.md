# Documentation

Start with the [project README](../README.md) for what the mod does and how to play it.

The documents here come in two kinds, and the difference matters:
- **Guides** describe what is **implemented** and how to use it. They are kept in step with the game.
- **Design records** are notes written while planning and building: the reasoning, the dead ends, the original game's rules as read from the decompilation. They are kept for people who are curious about the project's direction and history, and they are **not** a promise of what the game does. Where one disagrees with a guide, the guide is right.

## Guides (current)
| Document | What it covers |
|---|---|
| [ITEMS.md](ITEMS.md) | Every item and block with its crafting recipe drawn as a crafting grid, grouped by purpose |
| [SURVIVAL.md](SURVIVAL.md) | The Power Palm, the Power Fruit chain, the wood set, wheels and crates |
| [POWERUPS.md](POWERUPS.md) | What each power-up does, its charged form, and the mod's differences from the original |
| [UPGRADES.md](UPGRADES.md) | Kart upgrades, the kart's and the crate spot's screens, the kart as an item and its paint |
| [KART_TUNING.md](KART_TUNING.md) | Tuning profiles as data pack entries |
| [DAMAGE.md](DAMAGE.md) | How Minecraft's damage reaches a kart and its driver, in the open world and in a race |
| [TERRAIN.md](TERRAIN.md) | How blocks become smooth ground, and the surface tags |
| [MUSIC.md](MUSIC.md) | Race music events and the local music pack |
| [PHYSICS_PORT.md](PHYSICS_PORT.md) | Function-by-function map of the physics port and what is verified |
| [ROADMAP.md](ROADMAP.md) | What is built, what comes next, and the architecture decisions |

## Design records and logs (history)
| Document | What it is |
|---|---|
| [DESIGN_TRACKS.md](DESIGN_TRACKS.md) | How nodes, the test drive, the race runtime and the podium were designed. The "Race progress model" section is the current one where sections disagree |
| [DESIGN_ITEMS.md](DESIGN_ITEMS.md) | The design of Power Fruit, crates, the roulette, rule sets and the rule items, with the original's rules |
| [DESIGN_KART_DATA.md](DESIGN_KART_DATA.md) | Why tuning and looks are separate, and the plan for kart looks (not built) |
| [POWERUPS_DEVLOG.md](POWERUPS_DEVLOG.md) | Chronological log of porting and tuning each power-up, with the decomp functions read |
| [RESEARCH.md](RESEARCH.md) | The first investigation of the sources. Some numbers were summarised, so re-verify before porting |

## Naming
The mod is **Craft Team Racing**, or **CTR** for short; its id is `craftteamracing`. In-game text uses Minecraft-themed or generic words. The original game's names appear only where a document is about the original's mechanics, and in the optional local resource packs.
