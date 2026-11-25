package com.craftteamracing.client;

import org.lwjgl.glfw.GLFW;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Client-side registrations (mod bus): the kart renderer and the extra driving keys. */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID, value = Dist.CLIENT)
public final class CraftTeamRacingClient {
    private CraftTeamRacingClient() {}

    private static final String CATEGORY = "key.categories.craftteamracing";

    /** L1: the second shoulder button (hop / powerslide / boost). R1 is the vanilla jump key. */
    public static final KeyMapping KEY_HOP2 =
            new KeyMapping("key.craftteamracing.hop2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    /** Square: brake (with the forward key a power-brake; with the back key, reverse). */
    public static final KeyMapping KEY_BRAKE =
            new KeyMapping("key.craftteamracing.brake", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    /** During a race: back to the last checkpoint you passed. */
    public static final KeyMapping KEY_RESPAWN =
            new KeyMapping("key.craftteamracing.respawn", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    /** Hold to leave a race (sneaking does not get you out of the kart in one, so it cannot happen by accident). */
    public static final KeyMapping KEY_LEAVE =
            new KeyMapping("key.craftteamracing.leave_race", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, CATEGORY);

    @SubscribeEvent
    public static void registerModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(TntRenderer.TNT);
        event.register(TntRenderer.NITRO);
        event.register(KartRenderer.MODEL);
        event.register(KartRenderer.WHEEL);
        event.register(KartRenderer.STEERING);
        for (var flame : KartRenderer.FLAMES) event.register(flame);
        for (var shell : KartRenderer.FLAME_SHELLS) event.register(shell);
        event.register(BombRenderer.MODEL);
        event.register(MissileRenderer.MODEL);
        event.register(ShieldBubble.MODEL);
        event.register(BombRenderer.MODEL_CHARGED);
        for (var layer : ShieldBubble.LAYERS) event.register(layer);
        for (int m = 0; m < ModelFx.MODELS.length; m++) {
            for (int f = 0; f < ModelFx.FRAMES[m]; f++) event.register(ModelFx.frame(ModelFx.MODELS[m], f));
        }
        event.register(MissileRenderer.FLAME);
        event.register(PotionRenderer.GREEN);
        event.register(PotionRenderer.RED);
        event.register(PickupRenderer.POWER_FRUIT);
        event.register(PickupRenderer.FRUIT_CRATE);
        event.register(PickupRenderer.POWERUP_CRATE);
        event.register(PickupRenderer.POWERUP_CRATE_BLANK);
        event.register(PickupRenderer.TRIGGER_CRATE);
        event.register(MaskHead.AKU);
        event.register(MaskHead.UKA);
        for (var frame : RainCloudFx.CLOUD) event.register(frame);
        for (var frame : WarpOrbRenderer.FRAME) event.register(frame);
        for (var frame : WarpOrbRenderer.SOLID) event.register(frame);
        for (var part : WarpOrbRenderer.CRYSTAL) event.register(part);
        for (var frame : RainCloudFx.CLOUD_SUB) event.register(frame);
        for (var frame : MaskHead.BEAM_AKU) event.register(frame);
        for (var frame : MaskHead.BEAM_UKA) event.register(frame);
    }

    /** The kart item shows its paint: the frame's faces (tint 0) and the seat's (tint 1). */
    @SubscribeEvent
    public static void registerItemColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> {
            com.craftteamracing.kart.KartPaint paint = stack.getOrDefault(com.craftteamracing.ModComponents.KART_PAINT.get(), com.craftteamracing.kart.KartPaint.DEFAULT);
            return tint == 0 ? 0xFF000000 | paint.frame() : tint == 1 ? 0xFF000000 | paint.seat() : -1;
        }, com.craftteamracing.ModItems.KART.get());
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        RiderPlayerModel.install(event);
    }

    @SubscribeEvent
    public static void registerReloadListener(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> GuiTextures.clear());
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) BeakerBubbles::load);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) CrateBreak::load);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) BombFuse::load);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) Fx::load);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) com.craftteamracing.kart.KartWheels::load);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) com.craftteamracing.kart.KartShadow::load);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) com.craftteamracing.kart.KartSteering::load);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) com.craftteamracing.kart.KartExhausts::load);
    }

    @SubscribeEvent
    public static void registerParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.craftteamracing.ModParticles.DUST_PUFF.get(), DustPuffParticle.Provider::new);
        event.registerSpriteSet(com.craftteamracing.ModParticles.CONFETTI.get(), sprites -> new ConfettiParticle.Provider(sprites, false));
        event.registerSpriteSet(com.craftteamracing.ModParticles.STREAMER.get(), sprites -> new ConfettiParticle.Provider(sprites, true));
        event.registerSpriteSet(com.craftteamracing.ModParticles.SKID_MARK.get(), SkidMarkParticle.Provider::new);
        event.registerSpriteSet(com.craftteamracing.ModParticles.EXHAUST_SMOKE.get(), sprites -> new ExhaustSmokeParticle.Provider(sprites, false));
        event.registerSpriteSet(com.craftteamracing.ModParticles.EXHAUST_GLOW.get(), sprites -> new ExhaustSmokeParticle.Provider(sprites, true));
        event.registerSpriteSet(com.craftteamracing.ModParticles.BOOST_FIRE.get(), BoostFireParticle.Provider::new);
        event.registerSpriteSet(com.craftteamracing.ModParticles.BOOST_FIRE_BLUE.get(), BoostFireParticle.Provider::new);
        event.registerSpriteSet(com.craftteamracing.ModParticles.CLOD.get(), ClodParticle.Provider::new);
        event.registerSpriteSet(com.craftteamracing.ModParticles.SMOKE_RING.get(), SmokeRingParticle.Provider::new);
        event.registerSpriteSet(com.craftteamracing.ModParticles.BURST.get(), BurstParticle.Provider::new);
        event.registerSpecial(com.craftteamracing.ModParticles.CRATE_BREAK.get(), new CrateBreakParticle.Provider());
    }

    /** Active only while riding a kart, and in conflict with nothing: the alternate key defaults to Q like the drop key, which does not drop anything in a kart. */
    public static final net.neoforged.neoforge.client.settings.IKeyConflictContext IN_KART = new net.neoforged.neoforge.client.settings.IKeyConflictContext() {
        @Override
        public boolean isActive() {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return mc.screen == null && mc.player != null && mc.player.getVehicle() instanceof com.craftteamracing.kart.KartEntity;
        }

        @Override
        public boolean conflicts(net.neoforged.neoforge.client.settings.IKeyConflictContext other) {
            return other == this;
        }
    };

    /** The held power-up's other way of firing (a Potion thrown forward, a Bomb rolled backwards). */
    public static final KeyMapping KEY_ITEM_ALT =
            new KeyMapping("key.craftteamracing.item_alt", IN_KART, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, CATEGORY);

    /** Use the held power-up. */
    public static final KeyMapping KEY_ITEM =
            new KeyMapping("key.craftteamracing.item", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.KART.get(), KartRenderer::new);
        event.registerEntityRenderer(ModEntities.PICKUP.get(), PickupRenderer::new);
        event.registerEntityRenderer(ModEntities.TNT.get(), TntRenderer::new);
        event.registerEntityRenderer(ModEntities.POTION.get(), PotionRenderer::new);
        event.registerEntityRenderer(ModEntities.BOMB.get(), BombRenderer::new);
        event.registerEntityRenderer(ModEntities.MISSILE.get(), MissileRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIELD_BOMB.get(), ShieldBombRenderer::new);
        event.registerEntityRenderer(ModEntities.WARP_ORB.get(), WarpOrbRenderer::new);
        event.registerBlockEntityRenderer(com.craftteamracing.ModBlockEntities.PICKUP_SPOT.get(), PickupSpotRenderer::new);
    }

    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "kart_hud"), KartHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "race_hud"), RaceHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "item_hud"), ItemHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "screen_flash"), ScreenFlash::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "spectate_hud"), SpectateCamera::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "leave_race_bar"), HoldBar::renderLeaveRace);
    }

    @SubscribeEvent
    public static void clientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        RaceMusic.tick();
        RouletteSound.update();
    }

    @SubscribeEvent
    public static void captureCamera(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        PowerFruitFly.capture(event);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY_HOP2);
        event.register(KEY_BRAKE);
        event.register(KEY_RESPAWN);
        event.register(KEY_LEAVE);
        event.register(KEY_ITEM);
        event.register(KEY_ITEM_ALT);
    }
}
