package com.craftteamracing;

import java.util.function.Supplier;

import com.craftteamracing.kart.BoostBlock;
import com.craftteamracing.kart.BoostPadBlock;
import com.craftteamracing.kart.BoostSlabBlock;
import com.craftteamracing.kart.BoostStairsBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The boost blocks: all are in the {@code craftteamracing:turbo_pad} tag, so a kart over any of them gets CTR's pad boost. Each has a
 * direction (an arrow on top); the plate has no collision.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CraftTeamRacingMod.MODID);

    private static BlockBehaviour.Properties solid() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1.5F, 6.0F).sound(SoundType.METAL);
    }

    /** A 1 px plate on the floor, no collision. */
    public static final DeferredBlock<BoostPadBlock> BOOST_PAD = BLOCKS.register("boost_pad", () -> new BoostPadBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5F).sound(SoundType.METAL)
                    .noCollission().noOcclusion()));
    public static final DeferredBlock<BoostBlock> BOOST_BLOCK = BLOCKS.register("boost_block", () -> new BoostBlock(solid()));
    public static final DeferredBlock<BoostSlabBlock> BOOST_SLAB = BLOCKS.register("boost_slab", () -> new BoostSlabBlock(solid()));
    public static final DeferredBlock<BoostStairsBlock> BOOST_STAIRS = BLOCKS.register("boost_stairs",
            () -> new BoostStairsBlock(BOOST_BLOCK.get().defaultBlockState(), solid()));

    public static final DeferredItem<BlockItem> BOOST_PAD_ITEM = item("boost_pad", BOOST_PAD);
    public static final DeferredItem<BlockItem> BOOST_BLOCK_ITEM = item("boost_block", BOOST_BLOCK);
    public static final DeferredItem<BlockItem> BOOST_SLAB_ITEM = item("boost_slab", BOOST_SLAB);
    public static final DeferredItem<BlockItem> BOOST_STAIRS_ITEM = item("boost_stairs", BOOST_STAIRS);

    /** The Super Boost family (an extension: CTR has the super turbo pad but never draws it differently): the same four blocks, in the {@code craftteamracing:super_turbo_pad} tag. */
    public static final DeferredBlock<BoostPadBlock> SUPER_BOOST_PAD = BLOCKS.register("super_boost_pad", () -> new BoostPadBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.5F).sound(SoundType.METAL).noCollission().noOcclusion()));
    public static final DeferredBlock<BoostBlock> SUPER_BOOST_BLOCK = BLOCKS.register("super_boost_block", () -> new BoostBlock(solid().mapColor(MapColor.COLOR_CYAN)));
    public static final DeferredBlock<BoostSlabBlock> SUPER_BOOST_SLAB = BLOCKS.register("super_boost_slab", () -> new BoostSlabBlock(solid().mapColor(MapColor.COLOR_CYAN)));
    public static final DeferredBlock<BoostStairsBlock> SUPER_BOOST_STAIRS = BLOCKS.register("super_boost_stairs",
            () -> new BoostStairsBlock(SUPER_BOOST_BLOCK.get().defaultBlockState(), solid().mapColor(MapColor.COLOR_CYAN)));

    public static final DeferredItem<BlockItem> SUPER_BOOST_PAD_ITEM = item("super_boost_pad", SUPER_BOOST_PAD);
    public static final DeferredItem<BlockItem> SUPER_BOOST_BLOCK_ITEM = item("super_boost_block", SUPER_BOOST_BLOCK);
    public static final DeferredItem<BlockItem> SUPER_BOOST_SLAB_ITEM = item("super_boost_slab", SUPER_BOOST_SLAB);
    public static final DeferredItem<BlockItem> SUPER_BOOST_STAIRS_ITEM = item("super_boost_stairs", SUPER_BOOST_STAIRS);

    /** Track nodes: invisible, no collision; only seen with the Track Tool in hand. */
    public static final DeferredBlock<com.craftteamracing.track.TrackNodeBlock> START_NODE = BLOCKS.register("start_node",
            () -> new com.craftteamracing.track.TrackNodeBlock(nodeProps(), com.craftteamracing.track.TrackGraph.Kind.START));
    public static final DeferredBlock<com.craftteamracing.track.TrackNodeBlock> CHECKPOINT_NODE = BLOCKS.register("checkpoint_node",
            () -> new com.craftteamracing.track.TrackNodeBlock(nodeProps(), com.craftteamracing.track.TrackGraph.Kind.CHECKPOINT));
    public static final DeferredItem<BlockItem> START_NODE_ITEM = ModItems.ITEMS.register("start_node",
            () -> new com.craftteamracing.track.TrackNodeItem(START_NODE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> CHECKPOINT_NODE_ITEM = ModItems.ITEMS.register("checkpoint_node",
            () -> new com.craftteamracing.track.TrackNodeItem(CHECKPOINT_NODE.get(), new Item.Properties()));

    /** The Kart Barrier: invisible and not solid for anything on foot; the driven kart treats it as a wall, but only while it takes part in a race. Seen only with a track building item in hand. */
    public static final DeferredBlock<com.craftteamracing.track.RaceBarrierBlock> RACE_BARRIER = BLOCKS.register("race_barrier",
            () -> new com.craftteamracing.track.RaceBarrierBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.5F).noCollission().noOcclusion()));
    public static final DeferredItem<BlockItem> RACE_BARRIER_ITEM = item("race_barrier", RACE_BARRIER);

    /** Pickup spots: a fruit, a fruit crate or a power-up crate appears here while a race is on nearby (or the spot has a redstone signal). */
    public static final DeferredBlock<com.craftteamracing.pickup.PickupSpotBlock> FRUIT_SPOT = BLOCKS.register("fruit_spot",
            () -> new com.craftteamracing.pickup.PickupSpotBlock(spotProps(), com.craftteamracing.pickup.PickupKind.FRUIT));
    public static final DeferredBlock<com.craftteamracing.pickup.PickupSpotBlock> FRUIT_CRATE_SPOT = BLOCKS.register("fruit_crate_spot",
            () -> new com.craftteamracing.pickup.PickupSpotBlock(spotProps(), com.craftteamracing.pickup.PickupKind.FRUIT_CRATE));
    public static final DeferredBlock<com.craftteamracing.pickup.PickupSpotBlock> POWERUP_CRATE_SPOT = BLOCKS.register("powerup_crate_spot",
            () -> new com.craftteamracing.pickup.PickupSpotBlock(spotProps(), com.craftteamracing.pickup.PickupKind.POWERUP_CRATE));
    public static final DeferredItem<BlockItem> FRUIT_SPOT_ITEM = item("fruit_spot", FRUIT_SPOT);
    public static final DeferredItem<BlockItem> FRUIT_CRATE_SPOT_ITEM = item("fruit_crate_spot", FRUIT_CRATE_SPOT);
    public static final DeferredItem<BlockItem> POWERUP_CRATE_SPOT_ITEM = ModItems.ITEMS.register("powerup_crate_spot",
            () -> new com.craftteamracing.pickup.PowerupCrateSpotItem(POWERUP_CRATE_SPOT.get(), new Item.Properties()));

    /** The "!" crate's spot: breaking its crate gives a redstone signal (settings by Wrench), anywhere, race or not. */
    public static final DeferredBlock<com.craftteamracing.pickup.PickupSpotBlock> TRIGGER_CRATE_SPOT = BLOCKS.register("trigger_crate_spot",
            () -> new com.craftteamracing.pickup.PickupSpotBlock(spotProps(), com.craftteamracing.pickup.PickupKind.TRIGGER_CRATE));
    public static final DeferredItem<BlockItem> TRIGGER_CRATE_SPOT_ITEM = item("trigger_crate_spot", TRIGGER_CRATE_SPOT);
    /** The far end of a wireless redstone line from Trigger Crate Spots. */
    public static final DeferredBlock<com.craftteamracing.pickup.RedstoneLinkNodeBlock> REDSTONE_LINK_NODE = BLOCKS.register("redstone_link_node",
            () -> new com.craftteamracing.pickup.RedstoneLinkNodeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5F).sound(SoundType.METAL)
                    .lightLevel(s -> s.getValue(com.craftteamracing.pickup.RedstoneLinkNodeBlock.POWERED) ? 7 : 0)));
    public static final DeferredItem<BlockItem> REDSTONE_LINK_NODE_ITEM = item("redstone_link_node", REDSTONE_LINK_NODE);

    private static BlockBehaviour.Properties spotProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.METAL).noCollission().noOcclusion();
    }

    /** Podium nodes (1st to 3rd): where the racers who finish are put, once a Track Tool has hooked them up to the Start node. */
    public static final DeferredBlock<com.craftteamracing.track.TrackNodeBlock> PODIUM_1_NODE = BLOCKS.register("podium_1_node",
            () -> new com.craftteamracing.track.TrackNodeBlock(nodeProps(), com.craftteamracing.track.TrackGraph.Kind.PODIUM_1));
    public static final DeferredBlock<com.craftteamracing.track.TrackNodeBlock> PODIUM_2_NODE = BLOCKS.register("podium_2_node",
            () -> new com.craftteamracing.track.TrackNodeBlock(nodeProps(), com.craftteamracing.track.TrackGraph.Kind.PODIUM_2));
    public static final DeferredBlock<com.craftteamracing.track.TrackNodeBlock> PODIUM_3_NODE = BLOCKS.register("podium_3_node",
            () -> new com.craftteamracing.track.TrackNodeBlock(nodeProps(), com.craftteamracing.track.TrackGraph.Kind.PODIUM_3));
    public static final DeferredItem<BlockItem> PODIUM_1_NODE_ITEM = ModItems.ITEMS.register("podium_1_node",
            () -> new com.craftteamracing.track.TrackNodeItem(PODIUM_1_NODE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> PODIUM_2_NODE_ITEM = ModItems.ITEMS.register("podium_2_node",
            () -> new com.craftteamracing.track.TrackNodeItem(PODIUM_2_NODE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> PODIUM_3_NODE_ITEM = ModItems.ITEMS.register("podium_3_node",
            () -> new com.craftteamracing.track.TrackNodeItem(PODIUM_3_NODE.get(), new Item.Properties()));

    private static BlockBehaviour.Properties nodeProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.5F).sound(SoundType.METAL)
                .noCollission().noOcclusion();
    }

    private ModBlocks() {}

    private static DeferredItem<BlockItem> item(String name, Supplier<? extends Block> block) {
        return ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
