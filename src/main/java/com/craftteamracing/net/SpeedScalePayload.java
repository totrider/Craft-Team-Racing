package com.craftteamracing.net;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.physics.WorldScale;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the server's speed scale in percent (50 to 100), and whether jumps shrink with it, on joining and whenever an operator or the config changes it. See {@link WorldScale}. */
public record SpeedScalePayload(int percent, boolean shrinkJumps) implements CustomPacketPayload {
    public static final Type<SpeedScalePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "speed_scale"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpeedScalePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpeedScalePayload::percent,
            ByteBufCodecs.BOOL, SpeedScalePayload::shrinkJumps,
            SpeedScalePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeedScalePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WorldScale.setGlobal(payload.percent() / 100.0);
            WorldScale.setShrinkJumps(payload.shrinkJumps());
        });
    }
}
