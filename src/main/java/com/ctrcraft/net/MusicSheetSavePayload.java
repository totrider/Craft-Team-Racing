package com.ctrcraft.net;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModComponents;
import com.ctrcraft.ModItems;
import com.ctrcraft.MusicSheetData;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the track music screen was saved; the music sheet in that hand takes this music. */
public record MusicSheetSavePayload(int hand, MusicSheetData data) implements CustomPacketPayload {
    public static final Type<MusicSheetSavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "music_sheet_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicSheetSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MusicSheetSavePayload::hand,
            MusicSheetData.STREAM_CODEC, MusicSheetSavePayload::data,
            MusicSheetSavePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MusicSheetSavePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || p.hand() < 0 || p.hand() >= InteractionHand.values().length) return;
            ItemStack stack = player.getItemInHand(InteractionHand.values()[p.hand()]);
            if (stack.is(ModItems.MUSIC_SHEET.get())) stack.set(ModComponents.MUSIC_SHEET.get(), p.data());
        });
    }
}
