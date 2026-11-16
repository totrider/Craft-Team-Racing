package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.pickup.WarpOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * The Warp Orb (DYNAMIC_WARPBALL): the pack's model, a glowing star standing on the ground, with a gem and lightning in its middle, 16 animation frames, one a game frame, drawn added to the
 * screen and fixed in the world (as the original's instance, which is never turned). The jar has a stand-in star.
 */
public class WarpOrbRenderer extends EntityRenderer<WarpOrbEntity> {
    public static final int FRAMES = 16;
    public static final ModelResourceLocation[] FRAME = new ModelResourceLocation[FRAMES];
    /** The texels drawn as they are (the others, in FRAME, are added to the screen). */
    public static final ModelResourceLocation[] SOLID = new ModelResourceLocation[FRAMES];

    static {
        for (int i = 0; i < FRAMES; i++) {
            FRAME[i] = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/pickup_warp_orb_" + i));
            SOLID[i] = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/pickup_warp_orb_solid_" + i));
        }
    }

    private static final java.util.Map<Integer, Float> YAW = new java.util.HashMap<>();
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };
    private final RandomSource random = RandomSource.create();

    public WarpOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(WarpOrbEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        double t = entity.tickCount + partialTick;
        int frame = (int) (t * 1.5) % FRAMES;
        pose.pushPose();
        // the star is a flat picture standing across the way the orb goes (the original's instance is never turned: it is seen from behind along the course): turned to its heading, which is
        // the way it has moved lately
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        Float last = YAW.get(entity.getId());
        float heading = last == null ? 0.0F : last;
        if (dx * dx + dz * dz > 1.0E-4) heading = (float) Math.toDegrees(Math.atan2(dx, dz));
        if (YAW.size() > 64) YAW.clear();
        YAW.put(entity.getId(), heading);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(heading));
        pose.translate(-0.5, -0.5, -0.5);
        draw(net.minecraft.client.Minecraft.getInstance().getModelManager().getModel(SOLID[frame]), pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)));
        draw(net.minecraft.client.Minecraft.getInstance().getModelManager().getModel(FRAME[frame]), pose, buffers.getBuffer(AdditiveTypes.BLOCK_ATLAS));
        pose.popPose();
    }

    private void draw(BakedModel model, PoseStack pose, VertexConsumer vc) {
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (var quad : model.getQuads(null, side, random)) {
                vc.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(WarpOrbEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
