package com.ctrcraft.items;

import java.util.Arrays;
import java.util.List;

/** The power-ups that can be given out, in the order the screens show them: the one place that grows when power-ups from data packs arrive. No Minecraft imports. */
public final class PowerupRegistry {
    private PowerupRegistry() {}

    /** The power-ups a roulette can give (the cut and battle-only ones never show). */
    public static List<String> rouletteIds() {
        return Arrays.stream(HeldItem.values()).filter(i -> i != HeldItem.SPRING && i != HeldItem.INVISIBILITY && i != HeldItem.SUPER_ENGINE).map(HeldItem::id).toList();
    }

    /** How many uses a power-up comes with when it is more than one (the three bombs, the three missiles); 1 for the rest. The count is drawn by the game's own font over the icon, never baked into a picture. */
    public static int stackCount(String id) {
        return id.equals(HeldItem.BOMB_3X.id()) || id.equals(HeldItem.MISSILE_3X.id()) ? 3 : 1;
    }

    /** Every power-up a crate spot can be set to. */
    public static List<String> allIds() {
        return Arrays.stream(HeldItem.values()).map(HeldItem::id).toList();
    }
}
