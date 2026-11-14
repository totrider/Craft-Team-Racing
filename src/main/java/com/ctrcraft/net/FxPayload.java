package com.ctrcraft.net;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: an effect to play (see client/Fx: the look is the client's, from its resource packs' {@code fx/<id>.json}). {@code arg} is the one number a profile may use as
 * {@code $arg} or {@code $radius} (the size of a blast).
 */
public record FxPayload(String id, double x, double y, double z, float yaw, float arg) implements CustomPacketPayload {
    public static final Type<FxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FxPayload::id,
            ByteBufCodecs.DOUBLE, FxPayload::x,
            ByteBufCodecs.DOUBLE, FxPayload::y,
            ByteBufCodecs.DOUBLE, FxPayload::z,
            ByteBufCodecs.FLOAT, FxPayload::yaw,
            ByteBufCodecs.FLOAT, FxPayload::arg,
            FxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FxPayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.ctrcraft.client.Fx.play(p.id(), p.x(), p.y(), p.z(), p.yaw(), p.arg(), 0));
    }

    /** Plays the effect for every player near the place. */
    public static void send(ServerLevel level, String id, double x, double y, double z, float yaw, float arg) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, 96.0, new FxPayload(id, x, y, z, yaw, arg));
    }

    public static void send(ServerLevel level, String id, double x, double y, double z) {
        send(level, id, x, y, z, 0.0F, 0.0F);
    }
}
