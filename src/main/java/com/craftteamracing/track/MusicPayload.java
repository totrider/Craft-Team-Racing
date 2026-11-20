package com.craftteamracing.track;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.MusicSheetData;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the music of the race this player is in (the default music, {@link MusicSheetData#DEFAULT}, when the race has ended). */
public record MusicPayload(MusicSheetData music) implements CustomPacketPayload {
    public static final Type<MusicPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "track_music"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicPayload> STREAM_CODEC = MusicSheetData.STREAM_CODEC.map(MusicPayload::new, MusicPayload::music);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MusicPayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.craftteamracing.client.MusicTitles.setRaceMusic(p.music()));
    }
}
