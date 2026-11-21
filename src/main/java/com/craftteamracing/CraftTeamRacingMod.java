package com.craftteamracing;

import org.slf4j.Logger;

import com.craftteamracing.net.KartDismountPayload;
import com.craftteamracing.net.KartStatePayload;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(CraftTeamRacingMod.MODID)
public class CraftTeamRacingMod {
    public static final String MODID = "craftteamracing";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CraftTeamRacingMod(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        com.craftteamracing.survival.ModSurvival.register(modEventBus);
        com.craftteamracing.tuning.TuningManager.register();
        ModSounds.register(modEventBus);
        ModComponents.register(modEventBus);
        ModRecipes.register(modEventBus);
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(com.craftteamracing.kart.KartItem::registerCauldron));
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, CtrConfig.SPEC);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, CtrClientConfig.SPEC);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(CraftTeamRacingMod::registerPayloads);
        ModBlockEntities.register(modEventBus);
        ModParticles.register(modEventBus);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) com.craftteamracing.client.ConfigScreens.register(modContainer);
        LOGGER.info("Craft Team Racing loaded");
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(com.craftteamracing.net.SpeedScalePayload.TYPE, com.craftteamracing.net.SpeedScalePayload.STREAM_CODEC, com.craftteamracing.net.SpeedScalePayload::handle);
        registrar.playToServer(KartStatePayload.TYPE, KartStatePayload.STREAM_CODEC, KartStatePayload::handle);
        registrar.playToServer(KartDismountPayload.TYPE, KartDismountPayload.STREAM_CODEC, KartDismountPayload::handle);
        registrar.playToClient(com.craftteamracing.pickup.ItemStatePayload.TYPE, com.craftteamracing.pickup.ItemStatePayload.STREAM_CODEC,
                com.craftteamracing.pickup.ItemStatePayload::handle);
        registrar.playToClient(com.craftteamracing.net.FxPayload.TYPE, com.craftteamracing.net.FxPayload.STREAM_CODEC, com.craftteamracing.net.FxPayload::handle);
        registrar.playToClient(com.craftteamracing.pickup.TrackerPayload.TYPE, com.craftteamracing.pickup.TrackerPayload.STREAM_CODEC,
                com.craftteamracing.pickup.TrackerPayload::handle);
        registrar.playToClient(com.craftteamracing.pickup.HitFlashPayload.TYPE, com.craftteamracing.pickup.HitFlashPayload.STREAM_CODEC,
                com.craftteamracing.pickup.HitFlashPayload::handle);
        registrar.playToServer(com.craftteamracing.pickup.UseItemPayload.TYPE, com.craftteamracing.pickup.UseItemPayload.STREAM_CODEC,
                com.craftteamracing.pickup.UseItemPayload::handle);
        registrar.playToServer(com.craftteamracing.net.KartHitPayload.TYPE, com.craftteamracing.net.KartHitPayload.STREAM_CODEC,
                com.craftteamracing.net.KartHitPayload::handle);
        registrar.playToClient(com.craftteamracing.track.RulesPayload.TYPE, com.craftteamracing.track.RulesPayload.STREAM_CODEC, com.craftteamracing.track.RulesPayload::handle);
        registrar.playToServer(com.craftteamracing.net.RulebookSavePayload.TYPE, com.craftteamracing.net.RulebookSavePayload.STREAM_CODEC, com.craftteamracing.net.RulebookSavePayload::handle);
        registrar.playToClient(com.craftteamracing.net.OpenKartSettingsPayload.TYPE, com.craftteamracing.net.OpenKartSettingsPayload.STREAM_CODEC, com.craftteamracing.net.OpenKartSettingsPayload::handle);
        registrar.playToServer(com.craftteamracing.net.KartSettingsPayload.TYPE, com.craftteamracing.net.KartSettingsPayload.STREAM_CODEC, com.craftteamracing.net.KartSettingsPayload::handle);
        registrar.playToClient(com.craftteamracing.net.OpenSpotPayload.TYPE, com.craftteamracing.net.OpenSpotPayload.STREAM_CODEC, com.craftteamracing.net.OpenSpotPayload::handle);
        registrar.playToClient(com.craftteamracing.tuning.TuningListPayload.TYPE, com.craftteamracing.tuning.TuningListPayload.STREAM_CODEC, com.craftteamracing.tuning.TuningListPayload::handle);
        registrar.playToServer(com.craftteamracing.net.SetDebugTuningPayload.TYPE, com.craftteamracing.net.SetDebugTuningPayload.STREAM_CODEC, com.craftteamracing.net.SetDebugTuningPayload::handle);
        registrar.playToServer(com.craftteamracing.net.SetTuningPayload.TYPE, com.craftteamracing.net.SetTuningPayload.STREAM_CODEC, com.craftteamracing.net.SetTuningPayload::handle);
        registrar.playToServer(com.craftteamracing.net.SetSpotPayload.TYPE, com.craftteamracing.net.SetSpotPayload.STREAM_CODEC, com.craftteamracing.net.SetSpotPayload::handle);
        registrar.playToClient(com.craftteamracing.net.OpenTriggerPayload.TYPE, com.craftteamracing.net.OpenTriggerPayload.STREAM_CODEC, com.craftteamracing.net.OpenTriggerPayload::handle);
        registrar.playToServer(com.craftteamracing.net.SetTriggerPayload.TYPE, com.craftteamracing.net.SetTriggerPayload.STREAM_CODEC, com.craftteamracing.net.SetTriggerPayload::handle);
        registrar.playToServer(com.craftteamracing.net.MusicSheetSavePayload.TYPE, com.craftteamracing.net.MusicSheetSavePayload.STREAM_CODEC, com.craftteamracing.net.MusicSheetSavePayload::handle);
        registrar.playToClient(com.craftteamracing.track.RaceDebugPayload.TYPE, com.craftteamracing.track.RaceDebugPayload.STREAM_CODEC, com.craftteamracing.track.RaceDebugPayload::handle);
        registrar.playToServer(com.craftteamracing.net.RaceDebugRequestPayload.TYPE, com.craftteamracing.net.RaceDebugRequestPayload.STREAM_CODEC, com.craftteamracing.net.RaceDebugRequestPayload::handle);
        registrar.playToClient(com.craftteamracing.track.PodiumScenePayload.TYPE, com.craftteamracing.track.PodiumScenePayload.STREAM_CODEC, com.craftteamracing.track.PodiumScenePayload::handle);
        registrar.playToClient(com.craftteamracing.track.MusicPayload.TYPE, com.craftteamracing.track.MusicPayload.STREAM_CODEC, com.craftteamracing.track.MusicPayload::handle);
        registrar.playToServer(com.craftteamracing.net.DropTableSavePayload.TYPE, com.craftteamracing.net.DropTableSavePayload.STREAM_CODEC, com.craftteamracing.net.DropTableSavePayload::handle);
        registrar.playToServer(com.craftteamracing.net.KartSquishPayload.TYPE, com.craftteamracing.net.KartSquishPayload.STREAM_CODEC,
                com.craftteamracing.net.KartSquishPayload::handle);
        registrar.playToClient(com.craftteamracing.track.TrackSnapshotPayload.TYPE, com.craftteamracing.track.TrackSnapshotPayload.STREAM_CODEC,
                com.craftteamracing.track.TrackSnapshotPayload::handle);
        registrar.playToClient(com.craftteamracing.track.TestDrivePayload.TYPE, com.craftteamracing.track.TestDrivePayload.STREAM_CODEC,
                com.craftteamracing.track.TestDrivePayload::handle);
        registrar.playToServer(com.craftteamracing.track.LeaveRacePayload.TYPE, com.craftteamracing.track.LeaveRacePayload.STREAM_CODEC,
                com.craftteamracing.track.LeaveRacePayload::handle);
        registrar.playToServer(com.craftteamracing.pickup.TntShakePayload.TYPE, com.craftteamracing.pickup.TntShakePayload.STREAM_CODEC,
                com.craftteamracing.pickup.TntShakePayload::handle);
        registrar.playToServer(com.craftteamracing.track.RespawnPayload.TYPE, com.craftteamracing.track.RespawnPayload.STREAM_CODEC,
                com.craftteamracing.track.RespawnPayload::handle);
        registrar.playToServer(com.craftteamracing.track.TrackBoxPayload.TYPE, com.craftteamracing.track.TrackBoxPayload.STREAM_CODEC,
                com.craftteamracing.track.TrackBoxPayload::handle);
        registrar.playToClient(com.craftteamracing.track.RacePayload.TYPE, com.craftteamracing.track.RacePayload.STREAM_CODEC,
                com.craftteamracing.track.RacePayload::handle);
        registrar.playToClient(com.craftteamracing.track.TrackMenuPayload.TYPE, com.craftteamracing.track.TrackMenuPayload.STREAM_CODEC,
                com.craftteamracing.track.TrackMenuPayload::handle);
        registrar.playToServer(com.craftteamracing.track.TrackMenuActionPayload.TYPE, com.craftteamracing.track.TrackMenuActionPayload.STREAM_CODEC,
                com.craftteamracing.track.TrackMenuActionPayload::handle);
    }
}
