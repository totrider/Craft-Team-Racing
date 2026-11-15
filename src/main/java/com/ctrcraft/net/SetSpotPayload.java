package com.ctrcraft.net;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.items.HeldItem;
import com.ctrcraft.pickup.ItemManager;
import com.ctrcraft.pickup.PickupKind;
import com.ctrcraft.pickup.PickupSpotBlockEntity;
import com.ctrcraft.track.TrackPermissions;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the power-up crate spot screen picked this power-up (the empty id is CTR's roulette). Checked: the player may edit there and is near, and the id is a power-up. */
public record SetSpotPayload(BlockPos pos, String itemId) implements CustomPacketPayload {
    public static final Type<SetSpotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "set_spot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetSpotPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetSpotPayload::pos,
            ByteBufCodecs.STRING_UTF8, SetSpotPayload::itemId,
            SetSpotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetSpotPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.distanceToSqr(p.pos().getCenter()) > 64.0 || !TrackPermissions.canEdit(player, p.pos())) return;
            if (!(player.level().getBlockEntity(p.pos()) instanceof PickupSpotBlockEntity be) || be.kind() != PickupKind.POWERUP_CRATE) return;
            java.util.List<String> ids = com.ctrcraft.items.CrateItems.parse(p.itemId());
            if (ids.size() > 32 || !com.ctrcraft.items.PowerupRegistry.allIds().containsAll(ids)) return;
            be.setItemId(com.ctrcraft.items.CrateItems.join(ids));
            player.displayClientMessage(com.ctrcraft.pickup.PowerupCrateSpotItem.describe(be.itemId()).copy().withStyle(net.minecraft.ChatFormatting.WHITE), true);
        });
    }
}
