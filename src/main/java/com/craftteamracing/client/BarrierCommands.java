package com.craftteamracing.client;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.CtrClientConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /ctr barriers} toggles drawing the Kart Barriers all the time (as the cyan ghost of {@link BarrierGhostRenderer}), not only while a track building item is in hand;
 * {@code /ctr barriers on} and {@code off} set it. It only changes what this client draws, so it needs no operator rights and works in multiplayer. The choice is saved in the client config
 * ({@code showBarriers}), so it stays across sessions.
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID, value = Dist.CLIENT)
public final class BarrierCommands {
    private BarrierCommands() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ctr").then(Commands.literal("barriers")
                .executes(ctx -> set(ctx.getSource(), !CtrClientConfig.SHOW_BARRIERS.get()))
                .then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), false)))));
    }

    private static int set(CommandSourceStack source, boolean on) {
        CtrClientConfig.SHOW_BARRIERS.set(on);
        try {
            CtrClientConfig.SPEC.save();
        } catch (RuntimeException e) {
            CraftTeamRacingMod.LOGGER.warn("Could not save the barrier view to the client config", e);
        }
        source.sendSuccess(() -> Component.literal(on ? "Kart Barriers: always shown (/ctr barriers again to switch it off)"
                : "Kart Barriers: shown only while you hold a track building item"), false);
        return on ? 1 : 0;
    }
}
