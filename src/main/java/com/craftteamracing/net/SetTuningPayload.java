package com.craftteamracing.net;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.kart.KartEntity;
import com.craftteamracing.tuning.TuningManager;
import com.craftteamracing.tuning.TuningProfile;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the tuning screen picked this profile for the kart. Checked: the kart is near and the profile is one the server offers. */
public record SetTuningPayload(int kartId, String profile) implements CustomPacketPayload {
    public static final Type<SetTuningPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "set_tuning"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetTuningPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetTuningPayload::kartId,
            ByteBufCodecs.STRING_UTF8, SetTuningPayload::profile,
            SetTuningPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetTuningPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Entity e = player.level().getEntity(p.kartId());
            if (!(e instanceof KartEntity kart) || kart.isRemoved() || player.distanceToSqr(kart) > 64.0) return;
            TuningProfile chosen = TuningManager.enabled().stream().filter(t -> t.id().equals(p.profile())).findFirst().orElse(null);
            if (chosen == null) return;
            kart.setTuning(chosen.id());
            player.displayClientMessage(Component.translatable("message.craftteamracing.tuning.set", chosen.name()), true);
        });
    }
}
