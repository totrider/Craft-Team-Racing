package com.craftteamracing.net;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.kart.KartEntity;
import com.craftteamracing.kart.KartScale;
import com.craftteamracing.physics.KartBump;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: this kart, under a turbo item boost, ran into that kart hard enough (an impact over 0xa00, VehPhysCrash_Attack) to squash it. The server checks that the
 * sender drives the kart, that the other is close and has a driver, and that the sender's turbo item is on (see ItemManager.squish).
 */
public record KartSquishPayload(int kartId, int victimKartId) implements CustomPacketPayload {
    public static final Type<KartSquishPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "kart_squish"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KartSquishPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KartSquishPayload::kartId,
            ByteBufCodecs.VAR_INT, KartSquishPayload::victimKartId,
            KartSquishPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KartSquishPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer driver)) return;
            if (!(driver.getVehicle() instanceof KartEntity kart) || kart.getId() != p.kartId() || kart.getControllingPassenger() != driver) return;
            Entity e = driver.level().getEntity(p.victimKartId());
            if (!(e instanceof KartEntity other) || other == kart || other.isRemoved()) return;
            if (!(other.getControllingPassenger() instanceof ServerPlayer victim)) return;
            if (kart.distanceTo(other) > KartScale.toBlocks(2.0 * KartBump.hitRadius()) + 1.5) return;
            com.craftteamracing.pickup.ItemManager.contact(driver, victim, other);
        });
    }
}
