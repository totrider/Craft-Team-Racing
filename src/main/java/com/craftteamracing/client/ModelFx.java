package com.craftteamracing.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.craftteamracing.CraftTeamRacingMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Effects made of baked model frames (the original's explosion: {@code warpedburst}, 30 frames, and the red {@code shockwave}, 17), played by {@link Fx} from a profile's {@code model}
 * emitters. Each frame is a standalone model {@code item/pickup_<model>_<n>} (the resource pack bakes them from the game's own animation; the jar has empty ones), drawn bright and
 * added to the screen after the particles, facing the camera or lying flat, one frame every two thirds of a tick (30 a second). A model with no frame in any pack draws nothing.
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID, value = Dist.CLIENT)
public final class ModelFx {
    private ModelFx() {}

    private record Active(Fx.ModelSpec spec, double x, double y, double z, double start) {}

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };
    private static final RandomSource RANDOM = RandomSource.create();
    /** The frame counts that are registered as models (CraftTeamRacingClient). */
    public static final String[] MODELS = { "burst", "shockwave" };
    public static final int[] FRAMES = { 30, 17 };

    public static ModelResourceLocation frame(String model, int n) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "item/pickup_" + model + "_" + n));
    }

    public static void spawn(Fx.ModelSpec spec, double x, double y, double z, long gameTime) {
        synchronized (ACTIVE) {
            if (ACTIVE.size() < 64) ACTIVE.add(new Active(spec, x, y, z, gameTime));
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        synchronized (ACTIVE) {
            if (ACTIVE.isEmpty()) return;
            double now = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buffers.getBuffer(AdditiveTypes.BLOCK_ATLAS);
            Iterator<Active> it = ACTIVE.iterator();
            while (it.hasNext()) {
                Active a = it.next();
                int frame = (int) ((now - a.start()) * 1.5 * a.spec().fps() / 30.0);
                if (frame < 0) continue;
                if (frame >= a.spec().frames()) {
                    it.remove();
                    continue;
                }
                BakedModel model = mc.getModelManager().getModel(frame(a.spec().model(), frame));
                pose.pushPose();
                pose.translate(a.x() - cam.x, a.y() - cam.y, a.z() - cam.z);
                if (a.spec().billboard()) pose.mulPose(event.getCamera().rotation());
                if (a.spec().roll() != 0.0) pose.mulPose(Axis.ZP.rotationDegrees((float) a.spec().roll()));
                float k = (float) a.spec().scale();
                pose.scale(k, k, k);
                pose.translate(-0.5, -0.5, -0.5);
                for (Direction side : SIDES) {
                    RANDOM.setSeed(42L);
                    for (var quad : model.getQuads(null, side, RANDOM)) {
                        vc.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    }
                }
                pose.popPose();
            }
            buffers.endBatch(AdditiveTypes.BLOCK_ATLAS);
        }
    }
}
