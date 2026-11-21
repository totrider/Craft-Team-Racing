package com.craftteamracing.client;

import com.craftteamracing.ModBlocks;
import com.craftteamracing.ModItems;
import com.craftteamracing.pickup.PickupSpotBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The pickup spot markers (fruit, fruit crate, power-up crate) are invisible blocks: this draws their model only while the player holds something to edit them
 * with (a spot block, the Track Tool, a node block or the Wrench). With anything else in hand the spots are not there to see; the pickups they hold are.
 */
public class PickupSpotRenderer implements BlockEntityRenderer<PickupSpotBlockEntity> {
    public PickupSpotRenderer(BlockEntityRendererProvider.Context context) {}

    /** Whether the item is one for building or editing a track. */
    public static boolean isEditingItem(ItemStack stack) {
        return stack.is(ModBlocks.RACE_BARRIER_ITEM.get()) || stack.is(ModItems.TRACK_TOOL.get()) || stack.is(ModItems.WRENCH.get()) || stack.is(ModBlocks.START_NODE_ITEM.get())
                || stack.is(ModBlocks.CHECKPOINT_NODE_ITEM.get()) || stack.is(ModBlocks.FRUIT_SPOT_ITEM.get()) || stack.is(ModBlocks.FRUIT_CRATE_SPOT_ITEM.get())
                || stack.is(ModBlocks.POWERUP_CRATE_SPOT_ITEM.get()) || stack.is(ModBlocks.TRIGGER_CRATE_SPOT_ITEM.get()) || stack.is(ModBlocks.REDSTONE_LINK_NODE_ITEM.get());
    }

    public static boolean holdingEditingItem(Player player) {
        return isEditingItem(player.getMainHandItem()) || isEditingItem(player.getOffhandItem());
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(PickupSpotBlockEntity be) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(be.getBlockPos());
        if (be.kind() == com.craftteamracing.pickup.PickupKind.TRIGGER_CRATE) {
            for (net.minecraft.core.BlockPos node : be.links()) box = box.minmax(new net.minecraft.world.phys.AABB(node));
        }
        return box;
    }

    @Override
    public void render(PickupSpotBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(holdingEditingItem(mc.player) || RaceDebugClient.nodes())) return;
        BlockState state = be.getBlockState();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        // within a few blocks the marker is drawn over everything, so a crate standing on it does not hide it
        boolean near = mc.player.distanceToSqr(be.getBlockPos().getCenter()) <= OnTopTypes.RADIUS * OnTopTypes.RADIUS;
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(near ? OnTopTypes.BLOCKS : RenderType.cutout()), state, model, 1.0F, 1.0F, 1.0F, light,
                OverlayTexture.NO_OVERLAY);
        // a trigger crate spot's links to its Redstone Linking Nodes
        if (be.kind() == com.craftteamracing.pickup.PickupKind.TRIGGER_CRATE) {
            com.mojang.blaze3d.vertex.VertexConsumer lines = buffers.getBuffer(OnTopTypes.LINES);
            PoseStack.Pose p = pose.last();
            for (net.minecraft.core.BlockPos node : be.links()) {
                float dx = node.getX() - be.getBlockPos().getX();
                float dy = node.getY() - be.getBlockPos().getY();
                float dz = node.getZ() - be.getBlockPos().getZ();
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1e-3F || len > 96.0F) continue;
                float nx = dx / len, ny = dy / len, nz = dz / len;
                lines.addVertex(p, 0.5F, 0.5F, 0.5F).setColor(1.0F, 0.35F, 0.3F, 0.95F).setNormal(p, nx, ny, nz);
                lines.addVertex(p, dx + 0.5F, dy + 0.5F, dz + 0.5F).setColor(1.0F, 0.35F, 0.3F, 0.95F).setNormal(p, nx, ny, nz);
            }
        }
        // a power-up crate spot that always gives one power-up shows its icon above it (so it can be read from a distance while building)
        if (be.kind() == com.craftteamracing.pickup.PickupKind.POWERUP_CRATE && !be.itemId().isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 0.0, 0.5);
            IconBillboard.draw(pose, buffers, be.itemId(), 1.4, 0.8F);
            pose.popPose();
        }
    }
}
