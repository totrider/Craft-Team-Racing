package com.craftteamracing.client;

import com.craftteamracing.pickup.ShieldBombEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** A thrown Power Shield (RB_ShieldDark_ThTick_Grow, SHIELD_FLAG_SHOOT): a single model, the colour layer at three quarters (alphaScale 0x400), with no dark layer and no sheen, at a quarter of the scale it has on the kart (the game's 0x400 against 0x1000), a little above the ground, green or blue. */
public class ShieldBombRenderer extends EntityRenderer<ShieldBombEntity> {
    public ShieldBombRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(ShieldBombEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        // the bubble is 1.7 blocks wide at its resting scale; a quarter of 0x1000 against its 0x700 is a bit over half of it
        float k = 0.571F;                          // 0x400 against the resting 0x700
        boolean original = ShieldBubble.drawOriginal(pose, buffers, this.entityRenderDispatcher.camera, entity.getPosition(partialTick), entity.getYRot(), k, k, entity.blue(), 0.75F, Float.NaN, true);                 // alphaScale 0x400: the colour layer at three quarters; no dark layer, no sheen
        if (!original) {
            ShieldBubble.draw(pose, buffers, 0.0F, 0.4F, 0.0F, 0.56F, 0.56F, entity.blue(), 0.5F);
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ShieldBombEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
