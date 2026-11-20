package com.craftteamracing.client;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.pickup.WarpOrbEntity;
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
            FRAME[i] = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "item/pickup_warp_orb_" + i));
            SOLID[i] = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "item/pickup_warp_orb_solid_" + i));
        }
    }

    /** The jar's stand-in, in the end crystal's spirit: three nested cubes (glass shell, gem, core), turned separately. Drawn only when no pack brings the original's star. */
    public static final ModelResourceLocation CRYSTAL_OUTER = crystal("outer");
    public static final ModelResourceLocation CRYSTAL_INNER = crystal("inner");
    public static final ModelResourceLocation CRYSTAL_CORE = crystal("core");
    public static final ModelResourceLocation[] CRYSTAL = { CRYSTAL_OUTER, CRYSTAL_INNER, CRYSTAL_CORE };
    /** The crystal's size, times the model's (its outer cube is 10 of 16 pixels): the original's star is large, about two and a half blocks across. */
    private static final float CRYSTAL_SCALE = 2.4F;
    /** How high the crystal's middle floats over the orb's place: measured on the original's 16 frames, the star's middle is about 4 blocks up (its model runs from 0.2 to 7.8) and it is about 7 across, its lowest tips at the ground. */
    private static final float CRYSTAL_HEIGHT = 3.6F;
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "item/orb_glow");

    private static float bob(double t) {
        float b = (float) (Math.sin(t * 0.2) * 0.5 + 0.5);
        return (b * b + b) * 0.2F;
    }

    /** The height of the crystal's middle over the entity's feet (also used to put the lens on it). */
    public static float centreHeight(WarpOrbEntity entity, float partialTick) {
        return CRYSTAL_HEIGHT + bob(entity.tickCount + partialTick);
    }

    private static ModelResourceLocation crystal(String part) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "item/pickup_warp_crystal_" + part));
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
        var models = net.minecraft.client.Minecraft.getInstance().getModelManager();
        if (!hasQuads(models.getModel(SOLID[frame])) && !hasQuads(models.getModel(FRAME[frame]))) {
            renderCrystal(entity, t, pose, buffers);
            return;
        }
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

    private static final RandomSource PROBE = RandomSource.create();

    /** True when no pack brings the original's star: the jar's crystal is the one drawn. */
    public static boolean jarOrb(WarpOrbEntity entity) {
        var models = net.minecraft.client.Minecraft.getInstance().getModelManager();
        int frame = (int) (entity.tickCount * 1.5) % FRAMES;
        for (BakedModel m : new BakedModel[] { models.getModel(SOLID[frame]), models.getModel(FRAME[frame]) }) {
            for (Direction side : SIDES) {
                PROBE.setSeed(42L);
                if (!m.getQuads(null, side, PROBE).isEmpty()) return false;
            }
        }
        return true;
    }

    private boolean hasQuads(BakedModel model) {
        for (Direction side : SIDES) {
            random.setSeed(42L);
            if (!model.getQuads(null, side, random).isEmpty()) return true;
        }
        return false;
    }

    /** The end crystal's way: spins, bobs, and its cubes sit corner-on (turned 60 degrees about the diagonal), each inner cube a step smaller and turned again. */
    private void renderCrystal(WarpOrbEntity entity, double t, PoseStack pose, MultiBufferSource buffers) {
        var models = net.minecraft.client.Minecraft.getInstance().getModelManager();
        float spin = (float) (t * 3.0);
        float bob = bob(t);
        VertexConsumer glass = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose();
        pose.translate(0.0, CRYSTAL_HEIGHT + bob, 0.0);
        OrbLightning.draw(pose, buffers, (entity.tickCount / 2) * 7919L + entity.getId() * 104729L, CRYSTAL_HEIGHT + bob - 0.25F);
        glow(pose, buffers, t);
        pose.scale(CRYSTAL_SCALE, CRYSTAL_SCALE, CRYSTAL_SCALE);
        org.joml.Quaternionf corner = new org.joml.Quaternionf().setAngleAxis((float) Math.toRadians(60.0), 0.7071F, 0.0F, 0.7071F);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
        pose.pushPose();
        pose.mulPose(corner);
        pose.translate(-0.5, -0.5, -0.5);
        draw(models.getModel(CRYSTAL_OUTER), pose, glass);
        pose.popPose();
        pose.scale(0.875F, 0.875F, 0.875F);
        pose.pushPose();
        pose.mulPose(corner);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
        pose.translate(-0.5, -0.5, -0.5);
        draw(models.getModel(CRYSTAL_INNER), pose, glass);
        pose.popPose();
        pose.scale(0.875F, 0.875F, 0.875F);
        pose.mulPose(corner);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
        pose.translate(-0.5, -0.5, -0.5);
        draw(models.getModel(CRYSTAL_CORE), pose, glass);
        pose.popPose();
    }

    /** The halo: a camera-facing glow, added to the screen, breathing. */
    private void glow(PoseStack pose, MultiBufferSource buffers, double t) {
        var sprite = net.minecraft.client.Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(GLOW);
        float h = 3.4F * (1.0F + 0.09F * (float) Math.sin(t * 0.35) + 0.04F * (float) Math.sin(t * 1.1));
        pose.pushPose();
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        org.joml.Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(AdditiveTypes.BLOCK_ATLAS);
        float[][] c = { { -h, -h, sprite.getU0(), sprite.getV1() }, { h, -h, sprite.getU1(), sprite.getV1() }, { h, h, sprite.getU1(), sprite.getV0() }, { -h, h, sprite.getU0(), sprite.getV0() } };
        for (float[] v : c) {
            vc.addVertex(m, v[0], v[1], 0.0F).setColor(255, 255, 255, 255).setUv(v[2], v[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0.0F, 1.0F, 0.0F);
        }
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
