package com.craftteamracing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a track music sheet holds (the data component {@link ModComponents#MUSIC_SHEET}) and what a track keeps: the music's id (a sound event path such as {@code music.level.crash_cove};
 * "" is the default, {@code music.race}; {@link #NONE} is no level music at all), the title to show (free text: the server never needs the sound, so a player without it still sees the
 * name) and, for a sheet, whether applying it sets the track's default or only the next race.
 */
public record MusicSheetData(String id, String title, boolean asDefault) {
    public static final String NONE = "none";
    public static final String LEVEL_PREFIX = "music.level.";
    public static final MusicSheetData DEFAULT = new MusicSheetData("", "", false);

    public static final Codec<MusicSheetData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(MusicSheetData::id),
            Codec.STRING.optionalFieldOf("title", "").forGetter(MusicSheetData::title),
            Codec.BOOL.optionalFieldOf("as_default", false).forGetter(MusicSheetData::asDefault)).apply(i, MusicSheetData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MusicSheetData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), MusicSheetData::id,
            ByteBufCodecs.stringUtf8(128), MusicSheetData::title,
            ByteBufCodecs.BOOL, MusicSheetData::asDefault,
            MusicSheetData::new);

    public boolean isDefault() {
        return id.isEmpty() && title.isEmpty();
    }
}
