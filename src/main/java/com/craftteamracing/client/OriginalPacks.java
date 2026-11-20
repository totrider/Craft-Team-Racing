package com.craftteamracing.client;

import net.minecraft.client.Minecraft;

/** Which of the local packs made from the player's own disc are switched on (their ids are "file/CTR-Original..."). */
public final class OriginalPacks {
    private OriginalPacks() {}

    /** The original textures (either size) are in use: the game's own pictures and names for what they show. */
    public static boolean textures() {
        return Minecraft.getInstance().getResourcePackRepository().getSelectedIds().stream().anyMatch(id -> id.contains("CTR-OriginalTextures"));
    }
}
