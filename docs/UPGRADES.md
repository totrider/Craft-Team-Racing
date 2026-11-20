# Kart upgrades, the kart's and the crate spot's screens

Built 2026-11-15.

## The Wrench's screens
- **A kart** (right-click with the Wrench): `KartSettingsScreen`: whether the kart can start races, and the switches of its upgrades. An upgrade that is not installed has its switch greyed out (with a tooltip saying to craft it and use it on the kart). The choices go back in `KartSettingsPayload`; the server checks the player is near and ignores a switch of an upgrade the kart has not got.
- **A power-up crate spot** (right-click with the Wrench): `CrateSpotScreen`: a grid with the picture of every power-up (the HUD icons: the original's when a pack has them) and a "?" cell for the classic roulette; the current choice is outlined. The choice goes back in `SetSpotPayload` (needs `TrackPermissions.canEdit`, within 8 blocks).
- **The icon over a crate:** a spot set to one power-up draws that power-up's icon over it, facing the camera, while the player holds an editing item (a spot block, the Track Tool, a node block or the Wrench); also over the crate itself (`PickupEntity` carries the id) while a race or a redstone signal has it out.

## Upgrades
Two items, each **used on the kart** (right-click it): it is installed (the item is used up, creative keeps it) and its switch in the kart's screen unlocks. They are saved with the kart (`Upgrades`, bits in `KartEntity`) and synced to clients.
- **Offroad Tires** (`craftteamracing:offroad_tires`): with the switch on, the kart ignores rough terrain: dirt, slow dirt (grass), snow and mud drive like asphalt (`KartDriver`, the terrain under the sim). Ice, sideslip and water are left as they are (water slows, as it should).
- **Soft Bumper** (`craftteamracing:soft_bumper`): with the switch on, the kart does no collision damage to the entities it bumps into (`KartHitPayload`); it still bounces and throws them.
- Recipes: see [ITEMS.md](ITEMS.md).

## More upgrades (2026-11-16)
Used on the kart like the first two; **every upgrade starts switched on when installed** (the Low Gravity Module at 50 %). Bits in `KartEntity` (`UP_*`; the module's strength in bits 12-15).
- **Floater** (`craftteamracing:floater`): the surface is a floor for the kart: `McBlockSource` gives the top cell of each water column a slab whose top is `FloatSurface.SINK` (0.18 block) under the surface, so the kart rides IN the water; a floating kart is slowed as in water (the water terrain row: surface code `Surface.WATER`), unless it has the **Paddle** (`craftteamracing:paddle`: a switch like the Offroad Tires, and a rule "Paddle for all" in a race), which makes the surface asphalt. A kart down in the water has no slab (it would hold it down from below) and is lifted to the surface (`KartSim.setBuoyant`). An **empty** kart floats the same way (server, `KartEntity.tick`), and one without a Floater sinks slowly. On the surface the kart's stern dips 14 degrees (`client/FloatState`) and it lays no skid marks. Lava is only floated on by the Obsidian Floater.
- **Low Gravity Module** (`craftteamracing:low_gravity_module`): a slider in the kart's screen, 0 to 100 % in steps of 10. Scales gravity (and the terminal fall speed) **in the air only**, so slopes drive as before; **100 % is 37 % of gravity** (the first version's 70 %, which was the top of the useful range; `KartSim.setLowGravity`).
- **Jump Spring** (`craftteamracing:jump_spring`): a **double jump**: a second press of a jump key (R1 or L1) in the air gives the spring's high jump, once until the kart lands (`KartSim.springJump`; it is armed again only after the kart has stood on the ground for three steps, so a brush with a block's edge in the air does not give a second one; jump presses made out of the kart are dropped, so none is waiting when you get in). A slider, 0 to 100 % in steps of 10: **50 % is the spring's own high jump** (three jump forces), 100 % twice that initial speed; installed at 50 %. No key of its own.
- **Obsidian Floater** (`craftteamracing:obsidian_floater`, a Floater surrounded by obsidian): used on a kart it **replaces the Floater** (the same switch; a kart with no Floater gets it, switched on). It also floats on lava (`KartEntity.floaterMode()` 2) and the rider takes no lava, in-fire, on-fire or hot-floor damage and is put out (`RacerProtection`).
- Recipes are placeholders (`data/craftteamracing/recipe`). **In a race none of the karts' own switches count; the rules decide** (below).

### The rules for them (`RuleSet`, the Rulebook's screen)
`floater` (off / water / water + lava), `spring` (off, then 10 to 100 %) and `lowGravity` (off, then 10 to 100 %), each **for every racer** while the race lasts, whatever their karts have installed (like offroad and soft bumper). All off in the CTR rules. Lava protection for the rider follows the rule's "water + lava".

## Water and lava (all karts)
The sim had no fluid at all: a kart fell through water as through air. Now a kart whose body is in water or lava has its **vertical speed held down** each step (`KartSim.setFluid`: water settles a fall at a quarter of the terminal velocity, lava a tenth and thicker sideways too), and one on the bottom drives on the water terrain row. The Floater is the exception.

## In a race
The track's rules always override the karts' own switches: in a race a kart is offroad / soft-bumpered only if the **rule set** says so (`RuleSet.offroad`, `RuleSet.softBumper`, both off in the CTR rules; toggles "Offroad for all" and "Soft bumper for all" in the rulebook's screen), and then for every racer for the duration of the race, whether or not their karts have the upgrades. That allows an offroad track. Outside a race the kart's own switches apply.

## Copying a crate's setting (2026-11-15)
The power-up crate spot item carries the power-up it is set to (the data component `craftteamracing:crate_item`; none means the classic roulette):
- **Copy:** crouch and right-click a placed power-up crate spot while holding a power-up crate spot item: the whole held stack takes that spot's setting (action bar: "Copied: ...").
- **Tooltip:** "Gives: <power-up>" (or the classic roulette), and a hint on how to copy and clear.
- **Placing** a configured stack sets the placed spot to it. A broken configured spot drops a configured item, and a middle click (creative) picks one.
- **Clearing:** a shapeless recipe, one spot item alone in the crafting grid, gives a plain (cleared) spot item (`data/craftteamracing/recipe/powerup_crate_spot_clear.json`; the grid ignores what a stack carries, the result has none). It crafts a slot at a time.

## The kart as an item (2026-11-16)
Picking a kart up (crouch + hit with the Wrench; a creative player gets it into the inventory) gives an item that carries the kart's upgrades and switches, the mask preference and whether it starts races (custom data), and its paint (the data component `craftteamracing:kart_paint`: frame and seat RGB). Placing it restores all of it.
- **Painting in the crafting grid** (`KartDyeRecipe`, a special recipe): the kart and one or more dyes paint the **frame**; add one leather and they paint the **seat**. Dyes mix with the colour already there as on leather armour (`KartPaint.mix`); dyeing a placed kart uses the same maths.
- **Washing:** right-click the kart item on a water cauldron: the paint comes off and a level of water is used.
- **Showing it:** the item's model tints faces 0 (frame) and 1 (seat) from the component (`CraftTeamRacingClient.registerItemColours`); the tooltip lists the colours and the installed upgrades.

**Obsidian Floater and the kart item:** a kart item that carries the Obsidian Floater has the `fire_resistant` component and `kart/ObsidianProtection` makes its item entity invulnerable to fire, lava and explosions (as ancient debris is); a placed kart with it takes no reaction from lava or fire (`KartDamage`).
