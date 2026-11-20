package com.craftteamracing.items;

import java.util.Arrays;
import java.util.List;

/** The power-ups that can be given out, in the order the screens show them: the one place that grows when power-ups from data packs arrive. No Minecraft imports. */
public final class PowerupRegistry {
    private PowerupRegistry() {}

    /** The power-ups a roulette can give (the cut and battle-only ones never show). */
    public static List<String> rouletteIds() {
        return Arrays.stream(HeldItem.values()).filter(PowerupRegistry::implemented).map(HeldItem::id).toList();
    }

    /** False for the CTR items that exist as ids only (Spring, Invisibility, Super Engine): they are never offered anywhere. */
    public static boolean implemented(HeldItem i) {
        return i != HeldItem.SPRING && i != HeldItem.INVISIBILITY && i != HeldItem.SUPER_ENGINE;
    }

    /** How many uses a power-up comes with when it is more than one (the three bombs, the three missiles); 1 for the rest. The count is drawn by the game's own font over the icon, never baked into a picture. */
    public static int stackCount(String id) {
        return id.equals(HeldItem.BOMB_3X.id()) || id.equals(HeldItem.MISSILE_3X.id()) ? 3 : 1;
    }

    /** Every power-up a crate spot can be set to (the implemented ones). */
    public static List<String> allIds() {
        return rouletteIds();
    }

    /** The power-ups of a crate spot's screen: the plain ones; the three-bomb and three-missile stacks of the roulette are a quantity of three of the bomb and the missile there. */
    public static List<String> crateIds() {
        return allIds().stream().filter(id -> !id.equals(HeldItem.BOMB_3X.id()) && !id.equals(HeldItem.MISSILE_3X.id())).toList();
    }
}
