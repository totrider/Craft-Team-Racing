package com.ctrcraft;

import java.util.function.Supplier;

import com.ctrcraft.kart.KartDyeRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Recipe types of the mod's own. */
public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, CtrCraftMod.MODID);

    /** Painting a kart item with dyes (frame) and dyes plus leather (seat). */
    public static final Supplier<RecipeSerializer<KartDyeRecipe>> KART_DYE = SERIALIZERS.register("kart_dye", () -> new SimpleCraftingRecipeSerializer<>(KartDyeRecipe::new));

    private ModRecipes() {}

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
