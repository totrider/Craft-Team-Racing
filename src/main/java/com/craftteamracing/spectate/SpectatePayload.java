package com.craftteamracing.spectate;

import java.util.UUID;

import com.craftteamracing.CraftTeamRacingMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * To a viewer: what the camera does ({@code on} false: the viewing is over). Following a racer: {@code entityId} is the racer's kart (or the racer, with no kart). With no race on
 * ({@code idle}) the camera sits up and in front of the Start node at {@code ax, ay, az}, which faces {@code yaw}.
 */
public record SpectatePayload(boolean on, boolean idle, int entityId, String name, int rank, int field, UUID racer, double ax, double ay, double az, float yaw) implements CustomPacketPayload {
    public static final Type<SpectatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "spectate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpectatePayload> STREAM_CODEC = StreamCodec.of(SpectatePayload::write, SpectatePayload::read);

    public static SpectatePayload off() {
        return new SpectatePayload(false, false, 0, "", 0, 0, new UUID(0L, 0L), 0, 0, 0, 0);
    }

    private static void write(RegistryFriendlyByteBuf b, SpectatePayload p) {
        b.writeBoolean(p.on);
        b.writeBoolean(p.idle);
        b.writeVarInt(p.entityId);
        b.writeUtf(p.name);
        b.writeVarInt(p.rank);
        b.writeVarInt(p.field);
        b.writeUUID(p.racer);
        b.writeDouble(p.ax);
        b.writeDouble(p.ay);
        b.writeDouble(p.az);
        b.writeFloat(p.yaw);
    }

    private static SpectatePayload read(RegistryFriendlyByteBuf b) {
        return new SpectatePayload(b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readUtf(), b.readVarInt(), b.readVarInt(), b.readUUID(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpectatePayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.craftteamracing.client.SpectateCamera.set(p));
    }
}
