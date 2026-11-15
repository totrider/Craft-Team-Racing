package com.ctrcraft.net;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the Wrench was used on a power-up crate spot; open the screen to pick its power-up. */
public record OpenSpotPayload(BlockPos pos, String itemId) implements CustomPacketPayload {
    public static final Type<OpenSpotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "open_spot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSpotPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenSpotPayload::pos,
            ByteBufCodecs.STRING_UTF8, OpenSpotPayload::itemId,
            OpenSpotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSpotPayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.ctrcraft.client.CrateSpotScreen.open(p));
    }
}
