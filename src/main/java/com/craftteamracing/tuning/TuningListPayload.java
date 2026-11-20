package com.craftteamracing.tuning;

import java.util.List;

import com.craftteamracing.CraftTeamRacingMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the tuning profiles the server offers (on joining, and when the data packs are reloaded). The client's own driving and the tuning screen read them. */
public record TuningListPayload(List<TuningProfile> profiles) implements CustomPacketPayload {
    public static final Type<TuningListPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "tuning_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TuningListPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.profiles.size());
                for (TuningProfile t : p.profiles) TuningProfile.STREAM_CODEC.encode(buf, t);
            },
            buf -> {
                int n = buf.readVarInt();
                List<TuningProfile> list = new java.util.ArrayList<>();
                for (int i = 0; i < n; i++) list.add(TuningProfile.STREAM_CODEC.decode(buf));
                return new TuningListPayload(list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TuningListPayload p, IPayloadContext context) {
        context.enqueueWork(() -> TuningClient.set(p.profiles));
    }
}
