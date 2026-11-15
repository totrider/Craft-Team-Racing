package com.ctrcraft.pickup;

import java.util.List;

import com.ctrcraft.ModComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The power-up crate spot as an item. It carries the power-up it is set to (the data component {@code ctrcraft:crate_item}, none for CTR's roulette), shown in its tooltip and put on the
 * spot when it is placed. Crouching and right-clicking a placed power-up crate spot with this item copies that spot's setting onto the whole held stack. Crafting a spot item alone
 * (shapeless, one ingredient) gives a cleared one.
 */
public class PowerupCrateSpotItem extends BlockItem {
    public PowerupCrateSpotItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            Level level = ctx.getLevel();
            BlockPos pos = ctx.getClickedPos();
            if (level.getBlockEntity(pos) instanceof PickupSpotBlockEntity be && be.kind() == PickupKind.POWERUP_CRATE) {
                if (!level.isClientSide) {
                    ItemStack stack = ctx.getItemInHand();
                    String id = be.itemId();
                    if (id.isEmpty()) stack.remove(ModComponents.CRATE_ITEM.get());
                    else stack.set(ModComponents.CRATE_ITEM.get(), id);
                    player.displayClientMessage(Component.translatable("message.ctrcraft.spot.copied", describe(id)), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.useOn(ctx);
    }

    /** The power-up's name, the names of the list, or CTR's roulette. */
    public static Component describe(String ids) {
        java.util.List<String> list = com.ctrcraft.items.CrateItems.parse(ids);
        if (list.isEmpty()) return Component.translatable("screen.ctrcraft.spot.roulette");
        if (list.size() == 1) return ItemManager.nameOf(list.get(0));
        return Component.literal(list.stream().map(i -> ItemManager.nameOf(i).getString()).collect(java.util.stream.Collectors.joining(", ")));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String ids = stack.get(ModComponents.CRATE_ITEM.get());
        tooltip.add(Component.translatable(com.ctrcraft.items.CrateItems.parse(ids).size() > 1 ? "tooltip.ctrcraft.crate.gives_one_of" : "tooltip.ctrcraft.crate.gives", describe(ids)));
        tooltip.add(Component.translatable("tooltip.ctrcraft.crate.copy"));
    }
}
