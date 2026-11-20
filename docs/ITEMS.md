# Items and recipes

Every item of the mod, what it does and how to craft it, grouped as in the creative tab. The pictures are drawn from the recipe files by `tools/make_recipe_grids.py`, so they match the game. Every recipe also shows in a recipe viewer such as JEI or REI.
Shapeless recipes (no fixed pattern) have their ingredients laid out from the top left. For the survival progression as a whole see [SURVIVAL.md](SURVIVAL.md); for kart upgrades in depth, [UPGRADES.md](UPGRADES.md).

**Contents:** [Basics](#basics) · [Power Fruit chain](#the-power-fruit-chain) · [Boost blocks](#boost-blocks) · [Track building](#track-building) · [Crates and spots](#crates-and-spots) · [Rule items](#rule-items) · [Kart upgrades](#kart-upgrades) · [Power Palm wood set](#power-palm-wood-set)

## Basics
| Item | Recipe | What it does |
|---|---|---|
| **Wheel** | <img src="img/recipes/wheel.png" width="300" alt="wheel recipe"> | Four wheels from one craft. They are the building block of the Kart and the Offroad Tires. Placed as a block on the ground; right-click with another wheel to add up to four. |
| **Kart** | <img src="img/recipes/kart.png" width="300" alt="kart recipe"> | The rideable kart. Right-click the ground to place it, right-click it to get in, sneak to get out (not in a race). Dyes paint it, see below. |
| **Wrench** | <img src="img/recipes/wrench.png" width="300" alt="wrench recipe"> | Right-click a kart for its screen (can it start races, its upgrades, its tuning and Totem look); right-click a power-up crate spot to choose what it gives; crouch and hit a kart to pick it up with its upgrades and paint. |
| **Track Tool** | <img src="img/recipes/track_tool.png" width="300" alt="track tool recipe"> | Links nodes, resizes their boxes, and opens the Start Node menu with the test drive. See [Building a race track](../README.md#building-a-race-track). |

**Painting a kart.** Put a Kart and one or more dyes in the crafting grid to paint its **frame**; add one leather as well to paint the **seat**. Dyes mix with the colour already there, as with leather armour. A kart item is washed clean by right-clicking a water cauldron with it. (A special recipe, so there is no fixed picture.)

## The Power Fruit chain
Power Palms grow on beaches (see [SURVIVAL.md](SURVIVAL.md)); their fronds drop **Power Fruit** and **saplings**, which are not crafted. Fruit also hangs in clusters under the fronds.

| Item | Recipe | What it does |
|---|---|---|
| **Power Juice** | <img src="img/recipes/power_juice_from_fruit.png" width="300" alt="power juice from fruit recipe"> | **Cook** a Power Fruit in a furnace. Juice is a drink and returns the glass bottle. |
| **Power Juice (smoker)** | <img src="img/recipes/power_juice_from_fruit_smoking.png" width="300" alt="power juice from fruit smoking recipe"> | The same in a smoker, twice as fast. A blast furnace does not cook food. |
| **Power Juice (campfire)** | <img src="img/recipes/power_juice_from_fruit_campfire.png" width="300" alt="power juice from fruit campfire recipe"> | The same on a campfire. |
| **Raw Power Ore** | <img src="img/recipes/raw_power_ore_from_juice.png" width="300" alt="raw power ore from juice recipe"> | Shapeless. One Power Juice and a Raw Iron make **two**. |
| **Power Ingot (smelted)** | <img src="img/recipes/power_ingot_from_raw.png" width="300" alt="power ingot from raw recipe"> | Smelt Raw Power Ore in a furnace. |
| **Power Ingot (blasted)** | <img src="img/recipes/power_ingot_from_raw_blasting.png" width="300" alt="power ingot from raw blasting recipe"> | Or in a blast furnace, faster. |
| **Power Ingot (nuggets)** | <img src="img/recipes/power_ingot_from_nuggets.png" width="300" alt="power ingot from nuggets recipe"> | Nine Power Nuggets. |
| **Power Nugget** | <img src="img/recipes/power_nugget_from_ingot.png" width="300" alt="power nugget from ingot recipe"> | Nine from one ingot. |
| **Block of Power** | <img src="img/recipes/power_block.png" width="300" alt="power block recipe"> | Nine ingots. A decorative block that gives off a little light. |
| **Power Ingot (from a block)** | <img src="img/recipes/power_ingot_from_block.png" width="300" alt="power ingot from block recipe"> | Nine ingots back from one Block of Power. |
| **Bag of Power Fruit** | <img src="img/recipes/power_fruit_bag.png" width="300" alt="power fruit bag recipe"> | Nine fruit in a sack that stands on the ground. With Farm & Charm installed it uses that mod's sack look. |
| **Power Fruit (from a bag)** | <img src="img/recipes/power_fruit_from_bag.png" width="300" alt="power fruit from bag recipe"> | Nine fruit back from the bag. |

## Boost blocks
A turbo boost whichever way you drive over them. The pad has no collision, and an empty-hand click turns its arrow. The **Super Boost** family is an opt-in blue variant with a longer, stronger boost.

| Item | Recipe | What it does |
|---|---|---|
| **Boost Pad** | <img src="img/recipes/boost_pad.png" width="300" alt="boost pad recipe"> | Four pads from three nuggets over three iron ingots. |
| **Boost Block** | <img src="img/recipes/boost_block.png" width="300" alt="boost block recipe"> | Eight blocks from cobblestone round an ingot. |
| **Boost Slab** | <img src="img/recipes/boost_slab.png" width="300" alt="boost slab recipe"> | Six slabs. |
| **Boost Stairs** | <img src="img/recipes/boost_stairs.png" width="300" alt="boost stairs recipe"> | Four stairs. |
| **Super Boost Pad** | <img src="img/recipes/super_boost_pad.png" width="300" alt="super boost pad recipe"> | Shapeless: a Boost Pad and Lapis Lazuli. |
| **Super Boost Block** | <img src="img/recipes/super_boost_block.png" width="300" alt="super boost block recipe"> | A Boost Block and Lapis Lazuli. |
| **Super Boost Slab** | <img src="img/recipes/super_boost_slab.png" width="300" alt="super boost slab recipe"> | A Boost Slab and Lapis Lazuli. |
| **Super Boost Stairs** | <img src="img/recipes/super_boost_stairs.png" width="300" alt="super boost stairs recipe"> | Boost Stairs and Lapis Lazuli. |

## Track building
Invisible markers that make a track; you see them while you hold a node, the Track Tool or the Wrench.

| Item | Recipe | What it does |
|---|---|---|
| **Checkpoint Node** | <img src="img/recipes/checkpoint_node.png" width="300" alt="checkpoint node recipe"> | Two nodes from a nugget over redstone. A kart passes it by touching its box, from any direction. |
| **Start Node** | <img src="img/recipes/start_node.png" width="300" alt="start node recipe"> | Shapeless: a Checkpoint Node and a Power Ingot. One per track; the grid and gathering area are laid out behind it. Right-click it with a rule item to apply that. |
| **Podium 1 Node** | <img src="img/recipes/podium_1_node.png" width="300" alt="podium 1 node recipe"> | Optional. A Checkpoint Node and a gold ingot. Where the winner stands in the podium ceremony. |
| **Podium 2 Node** | <img src="img/recipes/podium_2_node.png" width="300" alt="podium 2 node recipe"> | Optional. With an iron ingot: second place. |
| **Podium 3 Node** | <img src="img/recipes/podium_3_node.png" width="300" alt="podium 3 node recipe"> | Optional. With a copper ingot: third place. |
| **Redstone Linking Node** | <img src="img/recipes/redstone_link_node.png" width="300" alt="redstone link node recipe"> | A small visible block that gives a redstone signal while any Trigger Crate Spot linked to it does. Link with the Wrench. |

## Crates and spots
Spots are small markers: a pickup appears there while a race is on a track near it, or when a redstone signal arms it. The crates are also plain building blocks.

| Item | Recipe | What it does |
|---|---|---|
| **Item Crate** | <img src="img/recipes/item_crate.png" width="300" alt="item crate recipe"> | Two crates, marked on every side as a power-up crate is. An ingredient, and a block. |
| **Fruit Crate** | <img src="img/recipes/fruit_crate.png" width="300" alt="fruit crate recipe"> | Two crates; an ingredient of the fruit crate spot. |
| **Planked crates** | <img src="img/recipes/stonecutting_item_crate_to_item_crate_planked.png" width="300" alt="stonecutting item crate to item crate planked recipe"> | Cut an Item or Fruit Crate at the **stonecutter** to get its planked look, and back. The Item Crate also has a random-faced variant. |
| **Power Fruit Spot** | <img src="img/recipes/fruit_spot.png" width="300" alt="fruit spot recipe"> | Shapeless: a Power Fruit and a Checkpoint Node. A fruit appears here in a race. |
| **Power Fruit Crate Spot** | <img src="img/recipes/fruit_crate_spot.png" width="300" alt="fruit crate spot recipe"> | A Fruit Crate and a Checkpoint Node. A crate of 5 to 8 fruit. |
| **Power-up Crate Spot** | <img src="img/recipes/powerup_crate_spot.png" width="300" alt="powerup crate spot recipe"> | An Item Crate and a Checkpoint Node. Gives a power-up by the original's place-based roulette, or what you set with the Wrench (one or several power-ups, each with a quantity). Crouch and right-click a placed spot with another spot item to copy its setting onto the whole stack. |
| **Power-up Crate Spot (cleared)** | <img src="img/recipes/powerup_crate_spot_clear.png" width="300" alt="powerup crate spot clear recipe"> | A spot item alone in the grid clears the setting it carries. |
| **Trigger Crate Spot** | <img src="img/recipes/trigger_crate_spot.png" width="300" alt="trigger crate spot recipe"> | An Item Crate, a Checkpoint Node and a redstone torch. A "!" crate that gives a redstone signal when it is broken; set its respawn, signal length and uses with the Wrench. |

## Rule items
Right-click in the air to set one up in its screen, then right-click a Start Node to apply it: as the track's default (needs permission to edit tracks), or for the next race only. A rule item cannot be applied while a race is on the track.

| Item | Recipe | What it does |
|---|---|---|
| **Rulebook** | <img src="img/recipes/rulebook.png" width="300" alt="rulebook recipe"> | Shapeless: a book, a nugget and redstone. Toggles for power fruit, fruit crates, power-up crates, boost pads, driver damage and hunger, offroad for all, soft bumper for all and the floater, spring and low-gravity rules; a top-speed cap. |
| **Drop Table** | <img src="img/recipes/drop_table.png" width="300" alt="drop table recipe"> | A book, a nugget and an Item Crate. A grid of power-ups with a weight each, or the classic roulette. |
| **Track Music Sheet** | <img src="img/recipes/track_music_sheet.png" width="300" alt="track music sheet recipe"> | Paper, a note block and a nugget. Picks the music a track plays and the title shown. |

## Kart upgrades
Use one on a kart (right-click it) to install it; its switch then unlocks in the kart's screen. In a race the track's rules decide, not the kart's switches. See [UPGRADES.md](UPGRADES.md).

| Item | Recipe | What it does |
|---|---|---|
| **Offroad Tires** | <img src="img/recipes/offroad_tires.png" width="300" alt="offroad tires recipe"> | Dirt, grass, snow and mud drive like asphalt. Ice and water are left as they are. |
| **Soft Bumper** | <img src="img/recipes/soft_bumper.png" width="300" alt="soft bumper recipe"> | The kart does no collision damage to the entities it bumps, but still bounces and throws them. |
| **Floater** | <img src="img/recipes/floater.png" width="300" alt="floater recipe"> | The kart floats on water, which slows it as water does. |
| **Obsidian Floater** | <img src="img/recipes/obsidian_floater.png" width="300" alt="obsidian floater recipe"> | Replaces the Floater, floats on lava too and protects the rider from lava, fire and hot floors. |
| **Paddle** | <img src="img/recipes/paddle.png" width="300" alt="paddle recipe"> | With a Floater, the kart drives on the water's surface at full speed. |
| **Jump Spring** | <img src="img/recipes/jump_spring.png" width="300" alt="jump spring recipe"> | A double jump: a second press of the jump key in the air gives a high jump. Strength is a slider in the kart's screen. |
| **Low Gravity Module** | <img src="img/recipes/low_gravity_module.png" width="300" alt="low gravity module recipe"> | Reduces gravity in the air only. A slider in the kart's screen. |

## Power Palm wood set
The Power Palm's logs, wood, stripped variants, planks, stairs, slabs, fence, gate, door, trapdoor, button and pressure plate, with the vanilla recipes, plus fronds and a sapling (found, not crafted). Planks burn as planks do.

| Item | Recipe | What it does |
|---|---|---|
| **Power Palm Planks** | <img src="img/recipes/power_palm_planks.png" width="300" alt="power palm planks recipe"> | Four planks from a log. |
| **Power Palm Wood** | <img src="img/recipes/power_palm_wood.png" width="300" alt="power palm wood recipe"> | Three wood (bark on all sides) from four logs. Stripped wood the same, from stripped logs. |
| **Stairs** | <img src="img/recipes/power_palm_stairs.png" width="300" alt="power palm stairs recipe"> | Four. |
| **Slab** | <img src="img/recipes/power_palm_slab.png" width="300" alt="power palm slab recipe"> | Six. |
| **Fence** | <img src="img/recipes/power_palm_fence.png" width="300" alt="power palm fence recipe"> | Three. |
| **Fence Gate** | <img src="img/recipes/power_palm_fence_gate.png" width="300" alt="power palm fence gate recipe"> | One. |
| **Door** | <img src="img/recipes/power_palm_door.png" width="300" alt="power palm door recipe"> | Three. |
| **Trapdoor** | <img src="img/recipes/power_palm_trapdoor.png" width="300" alt="power palm trapdoor recipe"> | Two. |
| **Button** | <img src="img/recipes/power_palm_button.png" width="300" alt="power palm button recipe"> | One. |
| **Pressure Plate** | <img src="img/recipes/power_palm_pressure_plate.png" width="300" alt="power palm pressure plate recipe"> | One. |
