package com.ctrcraft.net;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.track.RaceManager;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: whether this player wants the server's view of their race progress (the debug view of the checkpoints and of the line they are on); repeated while it is on. */
public record RaceDebugRequestPayload(boolean rings, boolean path) implements CustomPacketPayload {
    public static final Type<RaceDebugRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "race_debug_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RaceDebugRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RaceDebugRequestPayload::rings,
            ByteBufCodecs.BOOL, RaceDebugRequestPayload::path,
            RaceDebugRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RaceDebugRequestPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) RaceManager.setDebug(player.getUUID(), p.rings() || p.path());
        });
    }
}
