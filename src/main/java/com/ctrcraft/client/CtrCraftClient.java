package com.ctrcraft.client;

import org.lwjgl.glfw.GLFW;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModEntities;
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
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT)
public final class CtrCraftClient {
    private CtrCraftClient() {}

    private static final String CATEGORY = "key.categories.ctrcraft";

    /** L1: the second shoulder button (hop / powerslide / boost). R1 is the vanilla jump key. */
    public static final KeyMapping KEY_HOP2 =
            new KeyMapping("key.ctrcraft.hop2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    /** Square: brake (with the forward key a power-brake; with the back key, reverse). */
    public static final KeyMapping KEY_BRAKE =
            new KeyMapping("key.ctrcraft.brake", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    /** During a race: back to the last checkpoint you passed. */
    public static final KeyMapping KEY_RESPAWN =
            new KeyMapping("key.ctrcraft.respawn", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    /** Hold to leave a race (sneaking does not get you out of the kart in one, so it cannot happen by accident). */
    public static final KeyMapping KEY_LEAVE =
            new KeyMapping("key.ctrcraft.leave_race", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, CATEGORY);

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
        event.register(MaskHead.AKU);
        event.register(MaskHead.UKA);
        for (var frame : RainCloudFx.CLOUD) event.register(frame);
        for (var frame : WarpOrbRenderer.FRAME) event.register(frame);
        for (var frame : WarpOrbRenderer.SOLID) event.register(frame);
        for (var frame : RainCloudFx.CLOUD_SUB) event.register(frame);
        for (var frame : MaskHead.BEAM_AKU) event.register(frame);
        for (var frame : MaskHead.BEAM_UKA) event.register(frame);
    }

    /** The kart item shows its paint: the frame's faces (tint 0) and the seat's (tint 1). */
    @SubscribeEvent
    public static void registerItemColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> {
            com.ctrcraft.kart.KartPaint paint = stack.getOrDefault(com.ctrcraft.ModComponents.KART_PAINT.get(), com.ctrcraft.kart.KartPaint.DEFAULT);
            return tint == 0 ? 0xFF000000 | paint.frame() : tint == 1 ? 0xFF000000 | paint.seat() : -1;
        }, com.ctrcraft.ModItems.KART.get());
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
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) com.ctrcraft.kart.KartWheels::load);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) com.ctrcraft.kart.KartShadow::load);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) com.ctrcraft.kart.KartSteering::load);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) com.ctrcraft.kart.KartExhausts::load);
    }

    @SubscribeEvent
    public static void registerParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.ctrcraft.ModParticles.DUST_PUFF.get(), DustPuffParticle.Provider::new);
        event.registerSpriteSet(com.ctrcraft.ModParticles.CONFETTI.get(), sprites -> new ConfettiParticle.Provider(sprites, false));
        event.registerSpriteSet(com.ctrcraft.ModParticles.STREAMER.get(), sprites -> new ConfettiParticle.Provider(sprites, true));
        event.registerSpriteSet(com.ctrcraft.ModParticles.SKID_MARK.get(), SkidMarkParticle.Provider::new);
        event.registerSpriteSet(com.ctrcraft.ModParticles.EXHAUST_SMOKE.get(), sprites -> new ExhaustSmokeParticle.Provider(sprites, false));
        event.registerSpriteSet(com.ctrcraft.ModParticles.EXHAUST_GLOW.get(), sprites -> new ExhaustSmokeParticle.Provider(sprites, true));
        event.registerSpriteSet(com.ctrcraft.ModParticles.CLOD.get(), ClodParticle.Provider::new);
        event.registerSpriteSet(com.ctrcraft.ModParticles.SMOKE_RING.get(), SmokeRingParticle.Provider::new);
        event.registerSpriteSet(com.ctrcraft.ModParticles.BURST.get(), BurstParticle.Provider::new);
        event.registerSpecial(com.ctrcraft.ModParticles.CRATE_BREAK.get(), new CrateBreakParticle.Provider());
    }

    /** Use the held power-up. */
    public static final KeyMapping KEY_ITEM =
            new KeyMapping("key.ctrcraft.item", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

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
        event.registerBlockEntityRenderer(com.ctrcraft.ModBlockEntities.PICKUP_SPOT.get(), PickupSpotRenderer::new);
    }

    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "kart_hud"), KartHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "race_hud"), RaceHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item_hud"), ItemHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "screen_flash"), ScreenFlash::render);
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
    }
}
