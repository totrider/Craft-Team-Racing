package com.craftteamracing.net;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.ModComponents;
import com.craftteamracing.ModItems;
import com.craftteamracing.RulebookData;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the rulebook screen was saved; the rulebook in that hand takes these rules. */
public record RulebookSavePayload(int hand, RulebookData data) implements CustomPacketPayload {
    public static final Type<RulebookSavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "rulebook_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RulebookSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RulebookSavePayload::hand,
            RulebookData.STREAM_CODEC, RulebookSavePayload::data,
            RulebookSavePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RulebookSavePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || p.hand() < 0 || p.hand() >= InteractionHand.values().length) return;
            ItemStack stack = player.getItemInHand(InteractionHand.values()[p.hand()]);
            if (stack.is(ModItems.RULEBOOK.get())) stack.set(ModComponents.RULEBOOK.get(), p.data());
        });
    }
}
