package com.ctrcraft.kart;

import com.ctrcraft.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Right-click a block to place a kart facing the way you look. */
public class KartItem extends Item {
    public KartItem(Properties properties) {
        super(properties);
    }

    /** A kart item right-clicked on a water cauldron is washed: the paint comes off (a level of water is used), as with leather armour. */
    public static void registerCauldron() {
        net.minecraft.core.cauldron.CauldronInteraction.WATER.map().put(com.ctrcraft.ModItems.KART.get(), (state, level, pos, player, hand, stack) -> {
            if (!stack.has(com.ctrcraft.ModComponents.KART_PAINT.get())) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide) {
                stack.remove(com.ctrcraft.ModComponents.KART_PAINT.get());
                net.minecraft.world.level.block.LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        KartPaint paint = stack.get(com.ctrcraft.ModComponents.KART_PAINT.get());
        if (paint != null) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.ctrcraft.kart.frame", String.format("#%06X", paint.frame())).withStyle(s -> s.withColor(paint.frame())));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.ctrcraft.kart.seat", String.format("#%06X", paint.seat())).withStyle(s -> s.withColor(paint.seat())));
        }
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        int up = data == null ? 0 : data.copyTag().getInt("Upgrades");
        String[][] names = { { "1", "offroad_tires" }, { "4", "soft_bumper" }, { "16", "floater" }, { "64", "jump_spring" }, { "256", "low_gravity_module" } };
        for (String[] n : names) {
            if ((up & Integer.parseInt(n[0])) != 0) {
                String key = (n[1].equals("floater") && (up & KartEntity.UP_OBSIDIAN) != 0) ? "obsidian_floater" : n[1];
                tooltip.add(net.minecraft.network.chat.Component.translatable("item.ctrcraft." + key).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos();
        Vec3 at = face == Direction.UP
                ? new Vec3(context.getClickLocation().x, pos.getY() + 1, context.getClickLocation().z)
                : Vec3.atBottomCenterOf(pos.relative(face));
        KartEntity kart = ModEntities.KART.get().create(serverLevel);
        if (kart == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        float yaw = player != null ? player.getYRot() : 0.0F;
        kart.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        kart.setDrawnYawAngle(KartScale.yawToAngle(yaw));
        ItemStack stack = context.getItemInHand();
        kart.applyItem(stack);
        serverLevel.addFreshEntity(kart);
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
