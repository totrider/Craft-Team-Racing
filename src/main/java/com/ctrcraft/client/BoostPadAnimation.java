package com.ctrcraft.client;

import java.util.Optional;

import com.ctrcraft.CtrClientConfig;
import com.ctrcraft.CtrCraftMod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * The boost pads' arrows are an animated texture. With the client config's {@code animatedBoostPads} off, a built-in pack (resources/static-pads, made by
 * tools/make_boost_pad.py) is switched on above the mod's own assets: the same pictures with a metadata that shows frame 0 only. The pack is offered to the
 * repository only while the setting is off, and changing the setting reloads the resources so the change shows at once.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BoostPadAnimation {
    private BoostPadAnimation() {}

    /** The setting the loaded resources were made for. */
    private static boolean applied = true;

    private static boolean animated() {
        try {
            return CtrClientConfig.ANIMATED_BOOST_PADS.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }

    @SubscribeEvent
    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        var info = ModList.get().getModContainerById(CtrCraftMod.MODID).orElseThrow().getModInfo();
        var path = info.getOwningFile().getFile().findResource("static-pads");
        event.addRepositorySource(consumer -> {
            applied = animated();
            if (applied) return;
            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo("mod/" + CtrCraftMod.MODID + ":static_pads", Component.literal("Craft Team Racing: still boost pad arrows"), PackSource.BUILT_IN,
                            Optional.of(new KnownPack("neoforge", "mod/" + CtrCraftMod.MODID + ":static_pads", info.getVersion().toString()))),
                    BuiltInPackSource.fromName(p -> new PathPackResources(p, path)),
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack != null) consumer.accept(pack);
        });
    }

    /** The setting was changed (the config screen, or the file): reload the resources when it changes what is shown. */
    @SubscribeEvent
    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != CtrClientConfig.SPEC) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (animated() != applied) mc.reloadResourcePacks();
        });
    }
}
