package com.craftteamracing.survival;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.ModBlocks;
import com.craftteamracing.ModItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The survival side of the mod (docs/SURVIVAL.md): the Power Palm that grows on beaches and bears the Power Fruit, the fruit's chain (juice, raw power ore, the power ingot with its nugget and
 * block), the palm's wood set, the wheel, the bag of fruit and the crate blocks. Recipes, loot, tags, world generation and models are data (src/main/resources, written by
 * tools/make_survival_assets.py).
 */
public final class ModSurvival {
    private ModSurvival() {}

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, CraftTeamRacingMod.MODID);
    public static final DeferredHolder<Feature<?>, PowerPalmFeature> POWER_PALM_FEATURE = FEATURES.register("power_palm", () -> new PowerPalmFeature(NoneFeatureConfiguration.CODEC));

    /** A desert oasis (a pond, a grass rim and a few Power Palms): world generation only, placed into desert biomes by a biome modifier. */
    public static final DeferredHolder<Feature<?>, OasisFeature> OASIS_FEATURE = FEATURES.register("oasis", () -> new OasisFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, PowerPalmWildFeature> POWER_PALM_WILD_FEATURE = FEATURES.register("power_palm_wild", () -> new PowerPalmWildFeature(NoneFeatureConfiguration.CODEC));

    /** The configured feature a sapling grows (data/craftteamracing/worldgen/configured_feature/power_palm.json). */
    public static final ResourceKey<ConfiguredFeature<?, ?>> POWER_PALM_KEY = ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "power_palm"));
    public static final TreeGrower POWER_PALM_GROWER = new TreeGrower("power_palm", Optional.empty(), Optional.of(POWER_PALM_KEY), Optional.empty());

    /** What the creative tab lists, in order. */
    private static final List<Supplier<? extends ItemLike>> TAB = new ArrayList<>();
    /** The blocks that burn (planks' odds). */
    private static final List<DeferredBlock<? extends Block>> BURNS = new ArrayList<>();

    public static List<Supplier<? extends ItemLike>> tabItems() {
        return TAB;
    }

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).ignitedByLava();
    }

    private static BlockBehaviour.Properties planks() {
        return wood().strength(2.0F, 3.0F);
    }

    private static <I extends Item> DeferredItem<I> listed(DeferredItem<I> item) {
        TAB.add(item);
        return item;
    }

    private static DeferredItem<BlockItem> blockItem(String name, DeferredBlock<? extends Block> block) {
        return listed(ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
    }

    private static <B extends Block> DeferredBlock<B> woodBlock(String name, Supplier<B> block) {
        DeferredBlock<B> registered = ModBlocks.BLOCKS.register(name, block);
        BURNS.add(registered);
        blockItem(name, registered);
        return registered;
    }

    // ---- the tree
    public static final DeferredBlock<Block> POWER_PALM_LOG = ModBlocks.BLOCKS.register("power_palm_log", () -> new FlammablePillar(wood(), 5, 5));
    public static final DeferredBlock<Block> POWER_PALM_WOOD = ModBlocks.BLOCKS.register("power_palm_wood", () -> new FlammablePillar(wood(), 5, 5));
    public static final DeferredBlock<Block> STRIPPED_POWER_PALM_LOG = ModBlocks.BLOCKS.register("stripped_power_palm_log", () -> new FlammablePillar(wood(), 5, 5));
    public static final DeferredBlock<Block> STRIPPED_POWER_PALM_WOOD = ModBlocks.BLOCKS.register("stripped_power_palm_wood", () -> new FlammablePillar(wood(), 5, 5));
    public static final DeferredBlock<LeavesBlock> POWER_PALM_LEAVES = ModBlocks.BLOCKS.register("power_palm_leaves", () -> new FlammableLeaves(
            BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()
                    .isValidSpawn((s, g, p, e) -> e == EntityType.OCELOT || e == EntityType.PARROT).isSuffocating((s, g, p) -> false).isViewBlocking((s, g, p) -> false)
                    .ignitedByLava().pushReaction(PushReaction.DESTROY).isRedstoneConductor((s, g, p) -> false)));
    public static final DeferredBlock<PowerPalmSaplingBlock> POWER_PALM_SAPLING = ModBlocks.BLOCKS.register("power_palm_sapling", () -> new PowerPalmSaplingBlock(POWER_PALM_GROWER,
            BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)));
    /** Power Fruit hanging under the palm's fronds (one to four): it grows, is picked by hand, and more fruit can be pushed onto it. */
    public static final DeferredBlock<PowerFruitClusterBlock> POWER_FRUIT_CLUSTER = ModBlocks.BLOCKS.register("power_fruit_cluster", () -> new PowerFruitClusterBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.3F).randomTicks().sound(SoundType.GRASS).noOcclusion().pushReaction(PushReaction.DESTROY)));

    // ---- the metal
    public static final DeferredBlock<Block> POWER_BLOCK = ModBlocks.BLOCKS.register("power_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(5.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).lightLevel(s -> 3)));

    // ---- the crate blocks (plank-like): marked on every side as the race's crate spots are, and "planked" with an oak cap
    public static final DeferredBlock<Block> ITEM_CRATE = ModBlocks.BLOCKS.register("item_crate", () -> new FlammableBlock(planks(), 5, 20));
    public static final DeferredBlock<Block> FRUIT_CRATE = ModBlocks.BLOCKS.register("fruit_crate", () -> new FlammableBlock(planks(), 5, 20));
    public static final DeferredBlock<Block> ITEM_CRATE_RANDOM = ModBlocks.BLOCKS.register("item_crate_random", () -> new FlammableBlock(planks(), 5, 20));
    public static final DeferredBlock<Block> ITEM_CRATE_PLANKED = ModBlocks.BLOCKS.register("item_crate_planked", () -> new FlammableBlock(planks(), 5, 20));
    public static final DeferredBlock<Block> FRUIT_CRATE_PLANKED = ModBlocks.BLOCKS.register("fruit_crate_planked", () -> new FlammableBlock(planks(), 5, 20));

    // ---- wheels and the bag
    public static final DeferredBlock<WheelBlock> WHEEL = ModBlocks.BLOCKS.register("wheel",
            () -> new WheelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0F).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<PowerFruitBagBlock> POWER_FRUIT_BAG = ModBlocks.BLOCKS.register("power_fruit_bag",
            () -> new PowerFruitBagBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.8F).sound(SoundType.WOOL).ignitedByLava()));

    // ---- the item forms of the blocks above, in the order the creative tab shows them
    public static final DeferredItem<BlockItem> POWER_PALM_SAPLING_ITEM = blockItem("power_palm_sapling", POWER_PALM_SAPLING);
    public static final DeferredItem<BlockItem> POWER_PALM_LOG_ITEM = blockItem("power_palm_log", POWER_PALM_LOG);
    public static final DeferredItem<BlockItem> POWER_PALM_WOOD_ITEM = blockItem("power_palm_wood", POWER_PALM_WOOD);
    public static final DeferredItem<BlockItem> STRIPPED_POWER_PALM_LOG_ITEM = blockItem("stripped_power_palm_log", STRIPPED_POWER_PALM_LOG);
    public static final DeferredItem<BlockItem> STRIPPED_POWER_PALM_WOOD_ITEM = blockItem("stripped_power_palm_wood", STRIPPED_POWER_PALM_WOOD);
    public static final DeferredItem<BlockItem> POWER_PALM_LEAVES_ITEM = blockItem("power_palm_leaves", POWER_PALM_LEAVES);

    // ---- the wood set
    public static final DeferredBlock<Block> POWER_PALM_PLANKS = woodBlock("power_palm_planks", () -> new FlammableBlock(planks(), 5, 20));
    public static final DeferredBlock<StairBlock> POWER_PALM_STAIRS = woodBlock("power_palm_stairs", () -> new StairBlock(POWER_PALM_PLANKS.get().defaultBlockState(), planks()));
    public static final DeferredBlock<SlabBlock> POWER_PALM_SLAB = woodBlock("power_palm_slab", () -> new SlabBlock(planks()));
    public static final DeferredBlock<FenceBlock> POWER_PALM_FENCE = woodBlock("power_palm_fence", () -> new FenceBlock(planks().forceSolidOn()));
    public static final DeferredBlock<FenceGateBlock> POWER_PALM_FENCE_GATE = woodBlock("power_palm_fence_gate", () -> new FenceGateBlock(WoodType.OAK, planks().forceSolidOn()));
    public static final DeferredBlock<TrapDoorBlock> POWER_PALM_TRAPDOOR = woodBlock("power_palm_trapdoor",
            () -> new TrapDoorBlock(BlockSetType.OAK, wood().strength(3.0F).noOcclusion().isValidSpawn((s, g, p, e) -> false)));
    public static final DeferredBlock<ButtonBlock> POWER_PALM_BUTTON = woodBlock("power_palm_button",
            () -> new ButtonBlock(BlockSetType.OAK, 30, BlockBehaviour.Properties.of().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<PressurePlateBlock> POWER_PALM_PRESSURE_PLATE = woodBlock("power_palm_pressure_plate",
            () -> new PressurePlateBlock(BlockSetType.OAK, wood().forceSolidOn().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<DoorBlock> POWER_PALM_DOOR = ModBlocks.BLOCKS.register("power_palm_door",
            () -> new DoorBlock(BlockSetType.OAK, wood().strength(3.0F).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> POWER_PALM_DOOR_ITEM = listed(ModItems.ITEMS.register("power_palm_door", () -> new DoubleHighBlockItem(POWER_PALM_DOOR.get(), new Item.Properties())));

    // ---- the crates, the wheel, the bag, the metal
    public static final DeferredItem<BlockItem> ITEM_CRATE_ITEM = blockItem("item_crate", ITEM_CRATE);
    public static final DeferredItem<BlockItem> FRUIT_CRATE_ITEM = blockItem("fruit_crate", FRUIT_CRATE);
    public static final DeferredItem<BlockItem> ITEM_CRATE_RANDOM_ITEM = blockItem("item_crate_random", ITEM_CRATE_RANDOM);
    public static final DeferredItem<BlockItem> ITEM_CRATE_PLANKED_ITEM = blockItem("item_crate_planked", ITEM_CRATE_PLANKED);
    public static final DeferredItem<BlockItem> FRUIT_CRATE_PLANKED_ITEM = blockItem("fruit_crate_planked", FRUIT_CRATE_PLANKED);
    public static final DeferredItem<BlockItem> WHEEL_ITEM = blockItem("wheel", WHEEL);
    public static final DeferredItem<BlockItem> POWER_FRUIT_BAG_ITEM = blockItem("power_fruit_bag", POWER_FRUIT_BAG);
    public static final DeferredItem<BlockItem> POWER_BLOCK_ITEM = blockItem("power_block", POWER_BLOCK);

    // ---- the items of the chain
    private static final FoodProperties FRUIT_FOOD = new FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).build();
    private static final FoodProperties JUICE_FOOD = new FoodProperties.Builder().nutrition(5).saturationModifier(0.3F).usingConvertsTo(Items.GLASS_BOTTLE).build();

    /** The fruit of the Power Palm: eaten it feeds a little, pushed onto the underside of palm fronds it grows into a cluster, in a furnace it is Power Juice. */
    public static final DeferredItem<Item> POWER_FRUIT = listed(ModItems.ITEMS.register("power_fruit", () -> new PowerFruitItem(POWER_FRUIT_CLUSTER.get(), new Item.Properties().food(FRUIT_FOOD))));
    public static final DeferredItem<Item> POWER_JUICE = listed(ModItems.ITEMS.register("power_juice",
            () -> new PowerJuiceItem(new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE).food(JUICE_FOOD))));
    public static final DeferredItem<Item> RAW_POWER_ORE = listed(ModItems.ITEMS.register("raw_power_ore", () -> new Item(new Item.Properties())));
    public static final DeferredItem<Item> POWER_NUGGET = listed(ModItems.ITEMS.register("power_nugget", () -> new Item(new Item.Properties())));
    public static final DeferredItem<Item> POWER_INGOT = listed(ModItems.ITEMS.register("power_ingot", () -> new Item(new Item.Properties())));

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
        modEventBus.addListener(ModSurvival::commonSetup);
        modEventBus.addListener(ModSurvival::addPacks);
        NeoForge.EVENT_BUS.addListener(ModSurvival::stripping);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FireBlock fire = (FireBlock) Blocks.FIRE;
            for (DeferredBlock<? extends Block> block : BURNS) {
                fire.setFlammable(block.get(), 5, 20);
            }
        });
    }

    /** With Farm &amp; Charm installed the bag of power fruit can wear its sack: an optional pack (resourcepacks/farm_and_charm_compat: a model that borrows that mod's side and bottom pictures), off until chosen in the resource packs screen. */
    private static void addPacks(AddPackFindersEvent event) {
        // optional (off until chosen in the resource packs screen): the fluffy fronds, crossed planes round each leaf block
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addPackFinders(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "resourcepacks/fluffy_fronds"), PackType.CLIENT_RESOURCES,
                    Component.literal("CTR: Fluffy Palm Fronds"), PackSource.BUILT_IN, false, Pack.Position.TOP);
        }
        if (event.getPackType() == PackType.CLIENT_RESOURCES && ModList.get().isLoaded("farm_and_charm")) {
            event.addPackFinders(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "resourcepacks/farm_and_charm_compat"), PackType.CLIENT_RESOURCES,
                    Component.literal("CTR: Farm & Charm Sacks"), PackSource.BUILT_IN, false, Pack.Position.TOP);
        }
    }

    /** An axe turns a log into a stripped log, and wood into stripped wood. */
    private static void stripping(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.AXE_STRIP) return;
        BlockState state = event.getState();
        Block to = state.is(POWER_PALM_LOG.get()) ? STRIPPED_POWER_PALM_LOG.get() : state.is(POWER_PALM_WOOD.get()) ? STRIPPED_POWER_PALM_WOOD.get() : null;
        if (to != null) event.setFinalState(to.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)));
    }
}
