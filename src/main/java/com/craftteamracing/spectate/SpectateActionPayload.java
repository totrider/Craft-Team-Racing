package com.craftteamracing.spectate;

import com.craftteamracing.CraftTeamRacingMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** From a viewer: the next or the previous racer, or leave. */
public record SpectateActionPayload(int action) implements CustomPacketPayload {
    public static final int NEXT = 0;
    public static final int PREVIOUS = 1;
    public static final int EXIT = 2;

    public static final Type<SpectateActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "spectate_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpectateActionPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SpectateActionPayload::action, SpectateActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpectateActionPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) SpectateManager.action(player, p.action());
        });
    }
}
