package com.craftteamracing.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.MusicSheetData;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The tracks a player can pick for a track and what they are called. The sound events are the mod's own ({@code music.level.<name>}); which of them have a sound is the player's resource
 * packs' business (the original's music is a local pack), so the list is read from the loaded sounds on this client. A track set by another player whose sound is missing here is still
 * named (the title travels with the track), it just is not heard.
 */
public final class MusicTitles {
    private static volatile MusicSheetData raceMusic = MusicSheetData.DEFAULT;

    private MusicTitles() {}

    /** The music of the race this client is in (what {@link RaceMusic} plays), the default when there is none. */
    public static MusicSheetData raceMusic() {
        return raceMusic;
    }

    public static void setRaceMusic(MusicSheetData data) {
        raceMusic = data;
        Minecraft mc = Minecraft.getInstance();
        // the title is shown only to a player who has the music: without its sound there is nothing to announce
        if (mc.player != null && !data.isDefault() && hasSound(data.id())) mc.player.displayClientMessage(Component.translatable("message.craftteamracing.music.track", display(data)), true);
    }

    /** A track's own name: the lang entry of its id ("Crash Cove"), else the id's last word made readable. */
    public static Component nameOf(String id) {
        if (id.isEmpty()) return Component.translatable("music.craftteamracing.default");
        if (id.equals(MusicSheetData.NONE)) return Component.translatable("music.craftteamracing.none");
        return Component.translatable("music.craftteamracing." + id.replace(MusicSheetData.LEVEL_PREFIX, "level."));
    }

    /** What to show for a track: its title when it has one, else the name of its sound. */
    public static Component display(MusicSheetData data) {
        return data.title().isEmpty() ? nameOf(data.id()) : Component.literal(data.title());
    }

    /** The ids of the tracks this client has a sound for, in name order. */
    public static List<String> available() {
        List<String> out = new ArrayList<>();
        for (ResourceLocation rl : Minecraft.getInstance().getSoundManager().getAvailableSounds()) {
            if (rl.getNamespace().equals(CraftTeamRacingMod.MODID) && rl.getPath().startsWith(MusicSheetData.LEVEL_PREFIX)) out.add(rl.getPath());
        }
        out.sort((a, b) -> nameOf(a).getString().toLowerCase(Locale.ROOT).compareTo(nameOf(b).getString().toLowerCase(Locale.ROOT)));
        return out;
    }

    /** True when the loaded resource packs put at least one sound into the track's event (a track set to none counts: it is silent on purpose). */
    public static boolean hasSound(String id) {
        if (id.isEmpty() || id.equals(MusicSheetData.NONE)) return true;
        var sounds = Minecraft.getInstance().getSoundManager().getSoundEvent(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, id));
        return sounds != null && sounds.getSound(net.minecraft.util.RandomSource.create()) != net.minecraft.client.sounds.SoundManager.EMPTY_SOUND;
    }

    /** True when this client has a sound for the track (always true for the default and for none). */
    public static boolean playable(String id) {
        return id.isEmpty() || id.equals(MusicSheetData.NONE) || available().contains(id);
    }
}
