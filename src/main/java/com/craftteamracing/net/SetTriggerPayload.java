package com.craftteamracing.net;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.pickup.PickupKind;
import com.craftteamracing.pickup.PickupSpotBlockEntity;
import com.craftteamracing.pickup.TriggerTime;
import com.craftteamracing.track.TrackPermissions;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the Trigger Crate Spot's settings. Checked: the player may edit there and is near, and the values are in range. */
public record SetTriggerPayload(BlockPos pos, int respawn, int duration, int uses) implements CustomPacketPayload {
    public static final Type<SetTriggerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "set_trigger"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetTriggerPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetTriggerPayload::pos,
            ByteBufCodecs.VAR_INT, SetTriggerPayload::respawn,
            ByteBufCodecs.VAR_INT, SetTriggerPayload::duration,
            ByteBufCodecs.VAR_INT, SetTriggerPayload::uses,
            SetTriggerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetTriggerPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.distanceToSqr(p.pos().getCenter()) > 64.0 || !TrackPermissions.canEdit(player, p.pos())) return;
            if (!(player.level().getBlockEntity(p.pos()) instanceof PickupSpotBlockEntity be) || be.kind() != PickupKind.TRIGGER_CRATE) return;
            be.configure(Math.max(0, Math.min(TriggerTime.MAX_STEP, p.respawn())), Math.max(0, Math.min(TriggerTime.MAX_STEP, p.duration())),
                    Math.max(0, Math.min(TriggerTime.MAX_USES, p.uses())));
        });
    }
}
