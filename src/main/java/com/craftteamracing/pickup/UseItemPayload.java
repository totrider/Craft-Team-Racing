package com.craftteamracing.pickup;

import com.craftteamracing.CraftTeamRacingMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: a power-up key was pressed. The use key sends neither flag (a flask is dropped behind, a bomb rolls forward); the alternate key (Q by default) sends both: {@code forward} throws a flask forward (the original's d-pad up), {@code back} rolls a bomb backwards. */
public record UseItemPayload(boolean forward, boolean back) implements CustomPacketPayload {
    public static final Type<UseItemPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "use_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UseItemPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.BOOL, UseItemPayload::forward,
            net.minecraft.network.codec.ByteBufCodecs.BOOL, UseItemPayload::back, UseItemPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UseItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer p) ItemManager.use(p, payload.forward(), payload.back());
        });
    }
}
