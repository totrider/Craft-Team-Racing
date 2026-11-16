package com.ctrcraft;

import org.slf4j.Logger;

import com.ctrcraft.net.KartDismountPayload;
import com.ctrcraft.net.KartStatePayload;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(CtrCraftMod.MODID)
public class CtrCraftMod {
    public static final String MODID = "ctrcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CtrCraftMod(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        ModComponents.register(modEventBus);
        ModRecipes.register(modEventBus);
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(com.ctrcraft.kart.KartItem::registerCauldron));
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, CtrConfig.SPEC);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, CtrClientConfig.SPEC);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(CtrCraftMod::registerPayloads);
        ModBlockEntities.register(modEventBus);
        ModParticles.register(modEventBus);
        LOGGER.info("Craft Team Racing loaded");
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(KartStatePayload.TYPE, KartStatePayload.STREAM_CODEC, KartStatePayload::handle);
        registrar.playToServer(KartDismountPayload.TYPE, KartDismountPayload.STREAM_CODEC, KartDismountPayload::handle);
        registrar.playToClient(com.ctrcraft.pickup.ItemStatePayload.TYPE, com.ctrcraft.pickup.ItemStatePayload.STREAM_CODEC,
                com.ctrcraft.pickup.ItemStatePayload::handle);
        registrar.playToClient(com.ctrcraft.net.FxPayload.TYPE, com.ctrcraft.net.FxPayload.STREAM_CODEC, com.ctrcraft.net.FxPayload::handle);
        registrar.playToClient(com.ctrcraft.pickup.TrackerPayload.TYPE, com.ctrcraft.pickup.TrackerPayload.STREAM_CODEC,
                com.ctrcraft.pickup.TrackerPayload::handle);
        registrar.playToClient(com.ctrcraft.pickup.HitFlashPayload.TYPE, com.ctrcraft.pickup.HitFlashPayload.STREAM_CODEC,
                com.ctrcraft.pickup.HitFlashPayload::handle);
        registrar.playToServer(com.ctrcraft.pickup.UseItemPayload.TYPE, com.ctrcraft.pickup.UseItemPayload.STREAM_CODEC,
                com.ctrcraft.pickup.UseItemPayload::handle);
        registrar.playToServer(com.ctrcraft.net.KartHitPayload.TYPE, com.ctrcraft.net.KartHitPayload.STREAM_CODEC,
                com.ctrcraft.net.KartHitPayload::handle);
        registrar.playToClient(com.ctrcraft.track.RulesPayload.TYPE, com.ctrcraft.track.RulesPayload.STREAM_CODEC, com.ctrcraft.track.RulesPayload::handle);
        registrar.playToServer(com.ctrcraft.net.RulebookSavePayload.TYPE, com.ctrcraft.net.RulebookSavePayload.STREAM_CODEC, com.ctrcraft.net.RulebookSavePayload::handle);
        registrar.playToClient(com.ctrcraft.net.OpenKartSettingsPayload.TYPE, com.ctrcraft.net.OpenKartSettingsPayload.STREAM_CODEC, com.ctrcraft.net.OpenKartSettingsPayload::handle);
        registrar.playToServer(com.ctrcraft.net.KartSettingsPayload.TYPE, com.ctrcraft.net.KartSettingsPayload.STREAM_CODEC, com.ctrcraft.net.KartSettingsPayload::handle);
        registrar.playToClient(com.ctrcraft.net.OpenSpotPayload.TYPE, com.ctrcraft.net.OpenSpotPayload.STREAM_CODEC, com.ctrcraft.net.OpenSpotPayload::handle);
        registrar.playToServer(com.ctrcraft.net.SetSpotPayload.TYPE, com.ctrcraft.net.SetSpotPayload.STREAM_CODEC, com.ctrcraft.net.SetSpotPayload::handle);
        registrar.playToServer(com.ctrcraft.net.MusicSheetSavePayload.TYPE, com.ctrcraft.net.MusicSheetSavePayload.STREAM_CODEC, com.ctrcraft.net.MusicSheetSavePayload::handle);
        registrar.playToClient(com.ctrcraft.track.RaceDebugPayload.TYPE, com.ctrcraft.track.RaceDebugPayload.STREAM_CODEC, com.ctrcraft.track.RaceDebugPayload::handle);
        registrar.playToServer(com.ctrcraft.net.RaceDebugRequestPayload.TYPE, com.ctrcraft.net.RaceDebugRequestPayload.STREAM_CODEC, com.ctrcraft.net.RaceDebugRequestPayload::handle);
        registrar.playToClient(com.ctrcraft.track.PodiumScenePayload.TYPE, com.ctrcraft.track.PodiumScenePayload.STREAM_CODEC, com.ctrcraft.track.PodiumScenePayload::handle);
        registrar.playToClient(com.ctrcraft.track.MusicPayload.TYPE, com.ctrcraft.track.MusicPayload.STREAM_CODEC, com.ctrcraft.track.MusicPayload::handle);
        registrar.playToServer(com.ctrcraft.net.DropTableSavePayload.TYPE, com.ctrcraft.net.DropTableSavePayload.STREAM_CODEC, com.ctrcraft.net.DropTableSavePayload::handle);
        registrar.playToServer(com.ctrcraft.net.KartSquishPayload.TYPE, com.ctrcraft.net.KartSquishPayload.STREAM_CODEC,
                com.ctrcraft.net.KartSquishPayload::handle);
        registrar.playToClient(com.ctrcraft.track.TrackSnapshotPayload.TYPE, com.ctrcraft.track.TrackSnapshotPayload.STREAM_CODEC,
                com.ctrcraft.track.TrackSnapshotPayload::handle);
        registrar.playToClient(com.ctrcraft.track.TestDrivePayload.TYPE, com.ctrcraft.track.TestDrivePayload.STREAM_CODEC,
                com.ctrcraft.track.TestDrivePayload::handle);
        registrar.playToServer(com.ctrcraft.track.LeaveRacePayload.TYPE, com.ctrcraft.track.LeaveRacePayload.STREAM_CODEC,
                com.ctrcraft.track.LeaveRacePayload::handle);
        registrar.playToServer(com.ctrcraft.pickup.TntShakePayload.TYPE, com.ctrcraft.pickup.TntShakePayload.STREAM_CODEC,
                com.ctrcraft.pickup.TntShakePayload::handle);
        registrar.playToServer(com.ctrcraft.track.RespawnPayload.TYPE, com.ctrcraft.track.RespawnPayload.STREAM_CODEC,
                com.ctrcraft.track.RespawnPayload::handle);
        registrar.playToServer(com.ctrcraft.track.TrackBoxPayload.TYPE, com.ctrcraft.track.TrackBoxPayload.STREAM_CODEC,
                com.ctrcraft.track.TrackBoxPayload::handle);
        registrar.playToClient(com.ctrcraft.track.RacePayload.TYPE, com.ctrcraft.track.RacePayload.STREAM_CODEC,
                com.ctrcraft.track.RacePayload::handle);
        registrar.playToClient(com.ctrcraft.track.TrackMenuPayload.TYPE, com.ctrcraft.track.TrackMenuPayload.STREAM_CODEC,
                com.ctrcraft.track.TrackMenuPayload::handle);
        registrar.playToServer(com.ctrcraft.track.TrackMenuActionPayload.TYPE, com.ctrcraft.track.TrackMenuActionPayload.STREAM_CODEC,
                com.ctrcraft.track.TrackMenuActionPayload::handle);
    }
}
