package com.craftteamracing.track;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.RulebookData;
import com.craftteamracing.items.RuleSet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the rules of the race the player is in (CTR's again when it is over), for the parts of the rules the client's own sim applies (boost pads, the pace). */
public record RulesPayload(RuleSet rules) implements CustomPacketPayload {
    public static final Type<RulesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RulesPayload> STREAM_CODEC = RulebookData.RULES_STREAM_CODEC.map(RulesPayload::new, RulesPayload::rules);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RulesPayload p, IPayloadContext context) {
        context.enqueueWork(() -> RaceClient.setRules(p.rules()));
    }
}
