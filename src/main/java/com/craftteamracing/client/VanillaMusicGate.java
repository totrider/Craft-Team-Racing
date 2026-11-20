package com.craftteamracing.client;

import com.craftteamracing.CraftTeamRacingMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/** While the local player is in a race (from the lamps on) the game picks no music of its own: the race's music is the mod's (see RaceMusic, which also stops what was already playing). */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID, value = Dist.CLIENT)
public final class VanillaMusicGate {
    private VanillaMusicGate() {}

    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        if (RaceMusic.keepsVanillaOut()) event.setMusic(null);
    }
}
