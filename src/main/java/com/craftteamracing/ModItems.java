package com.craftteamracing;

import com.craftteamracing.kart.KartItem;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CraftTeamRacingMod.MODID);

    /** Places a kart. Milestone 1: creative-tab only (no recipe yet; survival comes in milestone 3). */
    public static final DeferredItem<KartItem> KART = ITEMS.register("kart", () -> new KartItem(new Item.Properties().stacksTo(1)));

    /** Links track nodes (see docs/DESIGN_TRACKS.md). */
    public static final DeferredItem<com.craftteamracing.track.TrackToolItem> TRACK_TOOL = ITEMS.register("track_tool",
            () -> new com.craftteamracing.track.TrackToolItem(new Item.Properties().stacksTo(1)));

    /** Changes a kart's settings (right-click the kart): whether it can start races, later its tuning profile. */
    public static final DeferredItem<com.craftteamracing.kart.WrenchItem> WRENCH = ITEMS.register("wrench",
            () -> new com.craftteamracing.kart.WrenchItem(new Item.Properties().stacksTo(1)));

    /** Holds a rule set (see docs/DESIGN_ITEMS.md): right-click to set it, crouch and right-click a Start node to apply it. */
    public static final DeferredItem<com.craftteamracing.track.RulebookItem> RULEBOOK = ITEMS.register("rulebook",
            () -> new com.craftteamracing.track.RulebookItem(new Item.Properties().stacksTo(1)));

    /** Used on a kart it unlocks the offroad switch in the kart's settings: the kart can ignore rough terrain (grass, dirt, sand, snow, mud). */
    public static final DeferredItem<Item> OFFROAD_TIRES = ITEMS.register("offroad_tires", () -> new Item(new Item.Properties().stacksTo(16)));

    /** Used on a kart it unlocks the soft bumper switch: the kart does no collision damage to the entities it bumps into. */
    public static final DeferredItem<Item> SOFT_BUMPER = ITEMS.register("soft_bumper", () -> new Item(new Item.Properties().stacksTo(16)));

    /** Used on a kart: it floats on water like a boat, with no water slowdown. */
    public static final DeferredItem<Item> FLOATER = ITEMS.register("floater", () -> new Item(new Item.Properties().stacksTo(16)));

    /** Used on a kart: with the Floater it drives at full speed on the water instead of the water's slowdown. */
    public static final DeferredItem<Item> PADDLE = ITEMS.register("paddle", () -> new Item(new Item.Properties().stacksTo(16)));

    /** Used on a kart: replaces its Floater with one that floats on lava too and keeps the rider from lava's damage and fire. */
    public static final DeferredItem<Item> OBSIDIAN_FLOATER = ITEMS.register("obsidian_floater", () -> new Item(new Item.Properties().stacksTo(16)));

    /** Used on a kart: its falls are slower (a strength of 0 to 100 % in the kart's screen). */
    public static final DeferredItem<Item> LOW_GRAVITY_MODULE = ITEMS.register("low_gravity_module", () -> new Item(new Item.Properties().stacksTo(16)));

    /** Used on a kart: a jump of its own on a key of its own (the spring's high jump). */
    public static final DeferredItem<Item> JUMP_SPRING = ITEMS.register("jump_spring", () -> new Item(new Item.Properties().stacksTo(16)));

    /** Holds a drop table (see docs/DESIGN_ITEMS.md): right-click to set it, crouch and right-click a Start node to apply it. */
    public static final DeferredItem<com.craftteamracing.track.DropTableItem> DROP_TABLE = ITEMS.register("drop_table",
            () -> new com.craftteamracing.track.DropTableItem(new Item.Properties().stacksTo(1)));

    /** Holds the music of a track (see docs/MUSIC.md): right-click to pick it, right-click a Start node to apply it. */
    public static final DeferredItem<com.craftteamracing.track.MusicSheetItem> MUSIC_SHEET = ITEMS.register("track_music_sheet",
            () -> new com.craftteamracing.track.MusicSheetItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
