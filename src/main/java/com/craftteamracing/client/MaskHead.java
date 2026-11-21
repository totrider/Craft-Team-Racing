package com.craftteamracing.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.kart.KartEntity;
import com.craftteamracing.kart.KartScale;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * The mask that circles a kart under the Mask power-up (RB_MaskWeapon_ThTick): the head orbits the kart at 64 units, a sixteenth of a turn a frame (30 frames a second), a little above it,
 * its face looking outward; it shrinks away over the fade at the end. A beam of light, added to the screen, turns round the kart with it. The head is a flat sprite: Minecraft's totem of undying for the good mask, an evil totem of the mod's own for the evil one
 * (tools/make_mask_models.py, tools/make_evil_totem.py); the beam is a stand-in (a resource pack brings the original's head and beam).
 */
public final class MaskHead {
    private MaskHead() {}

    public static final ModelResourceLocation AKU = model("pickup_mask_aku");
    public static final ModelResourceLocation UKA = model("pickup_mask_uka");
    /** The beam of light's frames (akubeam / ukabeam have 24, played 30 a second as the original does: animFrame++ each frame). */
    public static final int BEAM_FRAMES = 24;
    public static final ModelResourceLocation[] BEAM_AKU = beamFrames("aku");
    public static final ModelResourceLocation[] BEAM_UKA = beamFrames("uka");

    private static ModelResourceLocation[] beamFrames(String who) {
        ModelResourceLocation[] out = new ModelResourceLocation[BEAM_FRAMES];
        for (int i = 0; i < BEAM_FRAMES; i++) out[i] = model("pickup_mask_beam_" + who + "_" + i);
        return out;
    }
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };
    private static final RandomSource RANDOM = RandomSource.create();
    /** When each kart's mask began to fade (the kart's tick count), to shrink it over the fade. */
    private static final Map<KartEntity, Integer> FADE_START = new WeakHashMap<>();
    private static final int FADE_TICKS = 10;
    /** The head is a flat totem sprite a block across; this brings it to the size of a mask. */
    private static final float HEAD_SCALE = 0.7F;
    /** The head turns this much of the original's speed (a very slight reduction reads better), and orbits a little outside the beam's ring so the light is behind it, not over it. */
    private static final double HEAD_TURN = 0.85;
    private static final double HEAD_RADIUS = 1.2;
    /** R231.maskPosArr (reference/ctr-native-src/game/231/R231.c): the head's height offset in units for each of the beam's 24 frames. */
    private static final int[] HEAD_DIP = { 0, 0, -2, -4, -8, -12, -16, -19, -23, -27, -29, -31, -32, -31, -29, -27, -23, -19, -16, -12, -8, -4, -2, 0 };

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "item/" + name));
    }

    public static void draw(KartEntity kart, PoseStack pose, MultiBufferSource buffers, float partialTick, int light) {
        int state = kart.maskState();
        if (state == 0) {
            FADE_START.remove(kart);
            return;
        }
        float scale = 1.0F;
        if ((state & 4) != 0) {
            int start = FADE_START.computeIfAbsent(kart, k -> k.tickCount);
            scale = Math.max(0.0F, 1.0F - (kart.tickCount + partialTick - start) / FADE_TICKS);
        } else {
            FADE_START.remove(kart);
        }
        if (scale <= 0.0F) return;
        BakedModel model = Minecraft.getInstance().getModelManager().getModel((state & 3) == 2 ? UKA : AKU);
        double t = kart.tickCount + partialTick;
        double angle = -t * 1.5 * HEAD_TURN * (2.0 * Math.PI / 16.0);          // rot.y -= 0x100 of 0x1000 a game frame: 30 frames a second is 1.5 frames a tick, so a turn takes 16 frames, about half a second
        double radius = KartScale.visualBlocks(64.0) * HEAD_RADIUS;
        // RB_MaskWeapon_ThTick: the head's height is 0x40 plus RB_MASK_POSITION[the beam's frame], so it dips and rises with the ribbon (24 entries, down to -32 at frame 12)
        int frame = (int) (t * 1.5) % BEAM_FRAMES;
        double height = KartScale.visualBlocks(64.0 + HEAD_DIP[frame]);
        // the beam: round the kart at the head's height, turning with the head
        BakedModel beamModel = Minecraft.getInstance().getModelManager().getModel(((state & 3) == 2 ? BEAM_UKA : BEAM_AKU)[frame]);
        pose.pushPose();
        pose.translate(0.0, KartScale.visualBlocks(64.0), 0.0);
        pose.mulPose(Axis.YP.rotation((float) angle));
        pose.scale(scale, scale, scale);
        pose.translate(-0.5, -0.5, -0.5);
        // Aku Aku's beam is added to the screen, Uka Uka's taken from it (the blend modes of their textures)
        VertexConsumer add = buffers.getBuffer((state & 3) == 2 ? AdditiveTypes.BLOCK_ATLAS_SUBTRACT : AdditiveTypes.BLOCK_ATLAS);
        for (Direction side : SIDES) {
            RANDOM.setSeed(42L);
            for (var quad : beamModel.getQuads(null, side, RANDOM)) {
                add.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, 0xF000F0, OverlayTexture.NO_OVERLAY);
            }
        }
        pose.popPose();
        pose.pushPose();
        pose.translate(Math.sin(angle) * radius, height, Math.cos(angle) * radius);
        pose.mulPose(Axis.YP.rotation((float) angle));              // the face (+z) looks outward
        pose.scale(scale * HEAD_SCALE, scale * HEAD_SCALE, scale * HEAD_SCALE);
        pose.translate(-0.5, -0.5, -0.5);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        for (Direction side : SIDES) {
            RANDOM.setSeed(42L);
            for (var quad : model.getQuads(null, side, RANDOM)) {
                consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, 0xF000F0, OverlayTexture.NO_OVERLAY);
            }
        }
        pose.popPose();
    }
}
