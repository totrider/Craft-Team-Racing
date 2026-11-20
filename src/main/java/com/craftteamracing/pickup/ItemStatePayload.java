package com.craftteamracing.pickup;

import com.craftteamracing.CraftTeamRacingMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: what the driver carries. {@code roll} is the game ticks left of the roulette (0 when none), {@code effect} a power-up the
 * driver's kart has just used, applied once per change of {@code effectSeq} (the client sim is the one that boosts). {@code count} is the shots a stack (3 bombs, 3 missiles) has left.
 */
public record ItemStatePayload(int powerFruit, String held, int roll, int effect, int effectSeq, int count) implements CustomPacketPayload {
    public static final Type<ItemStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "item_state"));
    public static final ItemStatePayload EMPTY = new ItemStatePayload(0, "", 0, 0, 0, 0);

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ItemStatePayload::powerFruit,
            ByteBufCodecs.STRING_UTF8, ItemStatePayload::held,
            ByteBufCodecs.VAR_INT, ItemStatePayload::roll,
            ByteBufCodecs.VAR_INT, ItemStatePayload::effect,
            ByteBufCodecs.VAR_INT, ItemStatePayload::effectSeq,
            ByteBufCodecs.VAR_INT, ItemStatePayload::count,
            ItemStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ItemStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ItemClient.update(payload));
    }
}
