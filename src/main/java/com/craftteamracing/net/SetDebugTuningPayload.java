package com.craftteamracing.net;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.kart.KartEntity;
import com.craftteamracing.physics.KartStats;
import com.craftteamracing.tuning.TuningProfile;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: an operator's debug tuning for a kart (any numbers the simulation can run on). Operators only. */
public record SetDebugTuningPayload(int kartId, int accel, int speed, int turnRate, int turnDecrease, int turnInputDelay, int driftTurnBase, int weight) implements CustomPacketPayload {
    public static final Type<SetDebugTuningPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "set_debug_tuning"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetDebugTuningPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kartId);
                buf.writeVarInt(p.accel);
                buf.writeVarInt(p.speed);
                buf.writeVarInt(p.turnRate);
                buf.writeVarInt(p.turnDecrease);
                buf.writeVarInt(p.turnInputDelay);
                buf.writeVarInt(p.driftTurnBase);
                buf.writeVarInt(p.weight);
            },
            buf -> new SetDebugTuningPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetDebugTuningPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
            Entity e = player.level().getEntity(p.kartId());
            if (!(e instanceof KartEntity kart) || kart.isRemoved() || player.distanceToSqr(kart) > 64.0) return;
            KartStats stats = new KartStats(p.accel, p.speed, p.turnRate, p.turnDecrease, p.turnInputDelay, p.driftTurnBase, p.weight);
            if (!stats.valid()) return;
            kart.setTuning(TuningProfile.debugId(stats));
            player.displayClientMessage(Component.translatable("message.craftteamracing.tuning.set", "Debug"), true);
        });
    }
}
