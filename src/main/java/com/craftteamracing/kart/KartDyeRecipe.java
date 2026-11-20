package com.craftteamracing.kart;

import com.craftteamracing.ModComponents;
import com.craftteamracing.ModItems;
import com.craftteamracing.ModRecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Painting a kart item in the crafting grid: the kart and one or more dyes paint its frame (the dyes mix, as on leather armour); add one piece of leather (the upholstery) and the dyes
 * paint its seat instead. Nothing else may be in the grid.
 */
public class KartDyeRecipe extends CustomRecipe {
    public KartDyeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int karts = 0, dyes = 0, leather = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(ModItems.KART.get())) karts++;
            else if (s.getItem() instanceof DyeItem) dyes++;
            else if (s.is(Items.LEATHER)) leather++;
            else return false;
        }
        return karts == 1 && dyes >= 1 && leather <= 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack kart = ItemStack.EMPTY;
        boolean seat = false;
        java.util.List<Integer> dyes = new java.util.ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.is(ModItems.KART.get())) kart = s;
            else if (s.getItem() instanceof DyeItem d) dyes.add(d.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF);
            else if (s.is(Items.LEATHER)) seat = true;
        }
        if (kart.isEmpty() || dyes.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = kart.copyWithCount(1);
        KartPaint paint = out.getOrDefault(ModComponents.KART_PAINT.get(), KartPaint.DEFAULT);
        int[] rgb = dyes.stream().mapToInt(Integer::intValue).toArray();
        out.set(ModComponents.KART_PAINT.get(), seat ? paint.withSeat(KartPaint.mix(paint.seat(), KartEntity.DEFAULT_SEAT_COLOUR, rgb))
                : paint.withFrame(KartPaint.mix(paint.frame(), KartEntity.DEFAULT_COLOUR, rgb)));
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.KART_DYE.get();
    }
}
