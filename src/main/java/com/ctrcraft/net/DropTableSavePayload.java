package com.ctrcraft.net;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.DropTableData;
import com.ctrcraft.ModComponents;
import com.ctrcraft.ModItems;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the drop table screen was saved; the drop table item in that hand takes this table. */
public record DropTableSavePayload(int hand, DropTableData data) implements CustomPacketPayload {
    public static final Type<DropTableSavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "drop_table_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DropTableSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DropTableSavePayload::hand,
            DropTableData.STREAM_CODEC, DropTableSavePayload::data,
            DropTableSavePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DropTableSavePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || p.hand() < 0 || p.hand() >= InteractionHand.values().length) return;
            ItemStack stack = player.getItemInHand(InteractionHand.values()[p.hand()]);
            if (stack.is(ModItems.DROP_TABLE.get())) stack.set(ModComponents.DROP_TABLE.get(), p.data());
        });
    }
}
