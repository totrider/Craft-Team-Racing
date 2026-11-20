package com.craftteamracing.pickup;

import com.craftteamracing.CraftTeamRacingMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client, every tick while a missile is homing in on the driver: how far it is, in the unit the original's warning uses (UI_RaceHud: {@code sqrt(distanceToTarget) / 0x32}, lu
 * over 50). The client draws the warning (client/TrackerHud) for as long as these keep coming.
 */
public record TrackerPayload(int distance) implements CustomPacketPayload {
    public static final Type<TrackerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "tracker"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrackerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TrackerPayload::distance,
            TrackerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrackerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.craftteamracing.client.TrackerHud.update(payload.distance()));
    }
}
