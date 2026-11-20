# Survival: the Power Palm, the power ingot, the wood set and the crates

Second pass of the survival side. Everything here is data or generated data (`tools/make_survival_assets.py` writes textures, models, recipes, loot, tags and world generation; the Java is
`com.craftteamracing.survival`). Nothing changes how racing works: the race mechanics stay CTR's, survival only decides how you get the things.

## The chain

```
Power Palm  --fronds / clusters-->  Power Fruit  --furnace-->  Power Juice
Power Juice + Raw Iron (crafting)  -->  2 Raw Power Ore  --furnace or blast furnace-->  Power Ingot
Power Ingot  <-->  9 Power Nuggets            9 Power Ingots  <-->  Block of Power            9 Power Fruit  <-->  Bag of Power Fruit
```

- **Power Palm** grows on beaches (`#minecraft:is_beach`, on sand) and from its **sapling** (which also takes root in dirt). The trunk is four or five logs tall, never taller than an oak's.
  The crown is four drooping fronds with tufts between them, nowhere farther than five blocks along the leaves from a log, so it never decays. With the optional fluffy pack the fronds are **fluffy**: a cube and crossed
  planes that stick out of it, four variants turned at random (`blockbench/survival/power_palm_leaves.bbmodel`; in the manner of the Better Leaves resource pack, MIT licence).
- **Fruit**: fronds drop a Power Fruit (10 %, up to 40 % with Fortune III) and a sapling (6 %); shears and silk touch take the fronds themselves. Power Fruit is **food** (3 hunger, no effects).
- **Fruit clusters**: use a Power Fruit on the *underside* of a frond and it hangs there as one fruit. It grows to two, three and four over time (random ticks), and more fruit can be pushed
  onto a cluster that has room (as petals onto pink petals). A full cluster of four is picked by hand (right click: four fruit) and goes back to one. Trees come with two full ones. The model is
  the race track's Power Fruit, copied and spread (`power_fruit_pods.bbmodel`).
- **Power Juice**: a furnace turns the fruit into it. It is a drink (4 hunger, no effects) and comes back as a glass bottle, also when used in a recipe.
- **Raw Power Ore**: Power Juice and a Raw Iron, shapeless, make **two**. The juice is the multiplier.
- **Power Ingot**: smelted or blasted. It has an iron ingot's shape and shading in the fruit's colours (red, orange, yellow, lime), an outline made of the dark of those colours, and a faint
  yellow spark that wanders across it (an animated picture). The **Power Nugget** (a little star) and the **Block of Power** (a plain, still plate; light level 3) are its small and big forms.

## Pruning, food, world generation, mod compatibility

- **Shears** on a fruit cluster stop its growth (shears again start it): the `pruned` state. New trees come with two clusters of one or two fruit.
- **Food**: a Power Fruit feeds as an apple does (4, saturation 2.4); Power Juice 5 (saturation 3.0), between an apple and bread. Juice is cooked from fruit as raw beef is: furnace, smoker
  (twice as fast) or campfire.
- **World generation** (server config, `[worldgen]`): `powerPalms` turns palms off in newly generated chunks, `powerPalmsPerChunk` (0 to 8, default 1) is how many are tried per beach chunk.
  Saplings are not affected. (The placed feature tries eight times a chunk; `PowerPalmWildFeature` keeps each try with the chance that gives the average.)
- **Handcrafted and Chipped**: not coded here. The wood set follows the naming convention Moonlight Lib (which Every Compat is built on) looks for (`power_palm_planks`, `power_palm_log`,
  `stripped_power_palm_log`, ...), so Every Compat should pick up the Power Palm as a wood type and generate those mods' furniture and chipped blocks from it. Tested with Every Compat: it works.

## The wood set

Logs, **wood** (bark on all sides), stripped logs and stripped wood (an axe strips them), planks, stairs, slabs, fence, fence gate, door, trapdoor, button and pressure plate. Planks burn
as planks do. Recipes are the vanilla ones (a log gives four planks; four logs three wood).

## The wheel, the bag, the crates

- **Wheel**: the kart's wheel (`wheel_block.bbmodel`), placed on the ground. Right click with another wheel on it to add one, as with pink petals: **1** lies in the middle, **2** are stacked,
  **3** are two stacked with one leaning against them (towards the way the block faces), **4** lie in a two by two. Four black wool and an iron ingot make four wheels; the kart and the offroad
  tires are built from wheels (not wool).
- **Bag of Power Fruit**: nine fruit, and back. It stands facing the way it was placed. With **Farm & Charm** installed, a built-in resource pack (`resourcepacks/farm_and_charm_compat`, active only
  when that mod is loaded) gives it that mod's sack model with its side, bottom and knot pictures (referenced, not copied) under our own top picture, which has the same two pixel edge. Without
  it the bag has its own sack pictures (`power_fruit_bag.bbmodel`).
- **Item Crate** and **Fruit Crate**: marked on every side as the race's crate spots are. The **Planked** kinds (oak caps, the first pass's pictures) are cut from one another at the stonecutter. They are ingredients of the crate spots, and are in `mineable/axe`.

## Recipes (a starting point: tune them in play)

| Result | Recipe |
| --- | --- |
| Power Juice | smelt a Power Fruit |
| 2 Raw Power Ore | Power Juice + Raw Iron (shapeless) |
| Power Ingot | smelt or blast Raw Power Ore; or 9 Power Nuggets |
| 9 Power Nuggets / 9 Power Ingots | from an ingot / a Block of Power |
| Block of Power | 9 Power Ingots |
| Bag of Power Fruit | 9 Power Fruit (and 9 fruit back from the bag) |
| 4 Wheels | black wool N, E, S, W round an Iron Ingot |
| 2 Item Crates | oak planks on the corners, spruce planks on the edges, a Power Nugget in the middle |
| 2 Fruit Crates | oak planks on the corners, dark oak planks on the edges, a Power Fruit in the middle |
| 4 Boost Pads | 3 Power Nuggets over 3 Iron Ingots |
| 8 Boost Blocks | cobblestone round a Power Ingot; 6 slabs from 3 blocks, 4 stairs from 6 |
| Super Boost (pad, block, slab, stairs) | the boost block + Lapis Lazuli (shapeless) |
| Kart | wheels on the corners, leather on top (seat), Power Ingots on the sides, a Minecart in the middle, Redstone below |
| Offroad Tires | wheels on the corners, slime balls on the edges, an Iron Ingot in the middle |
| Paddle | planks N, E, S, W (the spokes), iron ingots on the corners, a Power Ingot in the middle (a paddle wheel) |
| Wrench | iron, Power Ingot, iron over a two block iron handle |
| Track Tool | a Power Ingot on a stick, on a stick |
| Rulebook | Book + Power Nugget + Redstone |
| Drop Table | Book + Power Nugget + Item Crate |
| Track Music Sheet | Paper + Note Block + Power Nugget |
| 2 Checkpoint Nodes | a Power Nugget over Redstone |
| Start Node | Checkpoint Node + Power Ingot |
| Podium 1 / 2 / 3 Node | Checkpoint Node + Gold / Iron / Copper Ingot |
| Fruit Spot | Power Fruit + Checkpoint Node |
| Fruit Crate Spot / Power-up Crate Spot | Fruit Crate / Item Crate + Checkpoint Node |

The other kart upgrades (floater, spring...) keep the recipes they had.

## Ideas for more survival depth

- **Kart parts, not just a kart**: a Garage block where the kart is built from chassis, engine, tires and body, the parts made from the power ingot and vanilla metals, so the three CTR classes
  (speed, balanced, acceleration) become something you build towards, and a better ingot tier (power ingot plus netherite scrap) lifts the top speed a little.
- **Power Cells**: a nugget-and-redstone battery that a boost pad takes to give a bigger, longer boost for a while.
- **Fluffy fronds on**: the fronds are plain cubes by default; the built-in optional pack "CTR: fluffy Power Palm fronds" (resource packs screen, `resourcepacks/fluffy_fronds`, written by the generator) turns the fluffy look on. It does not depend on any other leaves pack.
- **Palm country**: palm signs and boats, a hammock, villages on beaches with a fruit merchant who buys fruit for emeralds and sells saplings.
- **Juice by ripeness**: a cluster picked before it is full gives sour juice that makes less raw power ore.
- **Racing garages** as generated structures near beaches: a small track with a start node, two crate spots and a chest with a rulebook.
- **Advancements**: a short line from "find a palm" through "first power ingot" to "win your first race".
