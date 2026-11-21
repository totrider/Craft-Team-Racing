package com.craftteamracing.net;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.kart.KartEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the kart settings screen was saved. {@code switches} holds the UP_*_ON bits the player chose; one of an upgrade the kart has not got is ignored. */
public record KartSettingsPayload(int kartId, boolean canStartRaces, boolean darkMask, int switches, int gravity, int spring) implements CustomPacketPayload {
    public static final Type<KartSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "kart_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KartSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KartSettingsPayload::kartId,
            ByteBufCodecs.BOOL, KartSettingsPayload::canStartRaces,
            ByteBufCodecs.BOOL, KartSettingsPayload::darkMask,
            ByteBufCodecs.VAR_INT, KartSettingsPayload::switches,
            ByteBufCodecs.VAR_INT, KartSettingsPayload::gravity,
            ByteBufCodecs.VAR_INT, KartSettingsPayload::spring,
            KartSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KartSettingsPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Entity e = player.level().getEntity(p.kartId());
            if (!(e instanceof KartEntity kart) || kart.isRemoved() || player.distanceToSqr(kart) > 64.0) return;
            kart.setCanStartRaces(p.canStartRaces());
            kart.setDarkMask(p.darkMask());
            int bits = kart.upgrades();
            int[][] pairs = { { KartEntity.UP_OFFROAD_UNLOCKED, KartEntity.UP_OFFROAD_ON }, { KartEntity.UP_BUMPER_UNLOCKED, KartEntity.UP_BUMPER_ON },
                    { KartEntity.UP_FLOATER_UNLOCKED, KartEntity.UP_FLOATER_ON }, { KartEntity.UP_PADDLE_UNLOCKED, KartEntity.UP_PADDLE_ON }, { KartEntity.UP_SKIS_UNLOCKED, KartEntity.UP_SKIS_ON } };
            for (int[] pair : pairs) {
                if ((bits & pair[0]) != 0) bits = (p.switches() & pair[1]) != 0 ? bits | pair[1] : bits & ~pair[1];
            }
            if ((bits & KartEntity.UP_GRAVITY_UNLOCKED) != 0) bits = (bits & ~(15 << KartEntity.UP_GRAVITY_SHIFT)) | (Math.max(0, Math.min(10, p.gravity())) << KartEntity.UP_GRAVITY_SHIFT);
            if ((bits & KartEntity.UP_SPRING_UNLOCKED) != 0) bits = (bits & ~(15 << KartEntity.UP_SPRING_SHIFT) & ~KartEntity.UP_SPRING_ON) | (Math.max(0, Math.min(10, p.spring())) << KartEntity.UP_SPRING_SHIFT);
            kart.setUpgrades(bits);
        });
    }
}
