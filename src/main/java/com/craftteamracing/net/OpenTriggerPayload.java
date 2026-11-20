package com.craftteamracing.net;

import com.craftteamracing.CraftTeamRacingMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the Wrench was used on a Trigger Crate Spot; open its settings (respawn step, redstone duration step, uses). */
public record OpenTriggerPayload(BlockPos pos, int respawn, int duration, int uses) implements CustomPacketPayload {
    public static final Type<OpenTriggerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "open_trigger"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTriggerPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenTriggerPayload::pos,
            ByteBufCodecs.VAR_INT, OpenTriggerPayload::respawn,
            ByteBufCodecs.VAR_INT, OpenTriggerPayload::duration,
            ByteBufCodecs.VAR_INT, OpenTriggerPayload::uses,
            OpenTriggerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenTriggerPayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.craftteamracing.client.TriggerSpotScreen.open(p));
    }
}
