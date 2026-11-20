package com.craftteamracing;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Item data components. */
public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CraftTeamRacingMod.MODID);

    /** The track node the Track Tool has selected (the next click links to it). */
    public static final Supplier<DataComponentType<GlobalPos>> SELECTED_NODE = COMPONENTS.registerComponentType("selected_node",
            builder -> builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /** The rules a rulebook item holds (docs/DESIGN_ITEMS.md). */
    public static final Supplier<DataComponentType<RulebookData>> RULEBOOK = COMPONENTS.registerComponentType("rulebook",
            builder -> builder.persistent(RulebookData.CODEC).networkSynchronized(RulebookData.STREAM_CODEC));

    /** The power-up a power-up crate spot item is set to (absent: CTR's roulette): copied from a placed spot, put on the spot when the item is placed. */
    public static final Supplier<DataComponentType<String>> CRATE_ITEM = COMPONENTS.registerComponentType("crate_item",
            builder -> builder.persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    /** The drop table a drop table item holds (docs/DESIGN_ITEMS.md). */
    public static final Supplier<DataComponentType<DropTableData>> DROP_TABLE = COMPONENTS.registerComponentType("drop_table",
            builder -> builder.persistent(DropTableData.CODEC).networkSynchronized(DropTableData.STREAM_CODEC));

    /** The music a track music sheet holds (docs/MUSIC.md). */
    public static final Supplier<DataComponentType<MusicSheetData>> MUSIC_SHEET = COMPONENTS.registerComponentType("music_sheet",
            builder -> builder.persistent(MusicSheetData.CODEC).networkSynchronized(MusicSheetData.STREAM_CODEC));

    /** What a kart item is painted with: the frame's and the seat's colour (none: the kart's own). */
    public static final Supplier<DataComponentType<com.craftteamracing.kart.KartPaint>> KART_PAINT = COMPONENTS.registerComponentType("kart_paint",
            builder -> builder.persistent(com.craftteamracing.kart.KartPaint.CODEC).networkSynchronized(com.craftteamracing.kart.KartPaint.STREAM_CODEC));

    private ModComponents() {}

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
