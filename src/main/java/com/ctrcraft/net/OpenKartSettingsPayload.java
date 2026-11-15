package com.ctrcraft.net;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the Wrench was used on this kart; open its settings screen with what it has now. */
public record OpenKartSettingsPayload(int kartId, boolean canStartRaces, int upgrades, boolean darkMask) implements CustomPacketPayload {
    public static final Type<OpenKartSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "open_kart_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenKartSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenKartSettingsPayload::kartId,
            ByteBufCodecs.BOOL, OpenKartSettingsPayload::canStartRaces,
            ByteBufCodecs.VAR_INT, OpenKartSettingsPayload::upgrades,
            ByteBufCodecs.BOOL, OpenKartSettingsPayload::darkMask,
            OpenKartSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenKartSettingsPayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.ctrcraft.client.KartSettingsScreen.open(p));
    }
}
