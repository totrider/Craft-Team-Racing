package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.pickup.BombEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** The Tracking Missile: {@code item/pickup_missile} (the jar's stand-in from blockbench/pickups/missile.bbmodel), nose first along its heading, a little above the ground. */
public class MissileRenderer extends EntityRenderer<BombEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/pickup_missile"));
    /** The flame fans of the pack's rocket (added to the screen, as the boost flame is); the jar's is empty. */
    public static final ModelResourceLocation FLAME = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/pickup_missile_flame"));
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };
    private final RandomSource random = RandomSource.create();

    public MissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(BombEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);
        float theta = 180.0F - Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float gb = entity.powered() ? 0.55F : 1.0F;            // the charged missile is tinted red
        pose.pushPose();
        TiltRender.apply(pose, entity.tilt());                 // up is the ground's normal, then it turns about that (VehPhysForce_RotAxisAngle)
        pose.translate(0.0, 0.32, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(theta));
        pose.translate(-0.5, -0.5, -0.5);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (var quad : model.getQuads(null, side, random)) {
                consumer.putBulkData(pose.last(), quad, 1.0F, gb, gb, 1.0F, light, OverlayTexture.NO_OVERLAY);
            }
        }
        BakedModel flame = Minecraft.getInstance().getModelManager().getModel(FLAME);
        VertexConsumer add = buffers.getBuffer(AdditiveTypes.BLOCK_ATLAS);
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (var quad : flame.getQuads(null, side, random)) {
                add.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(BombEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
