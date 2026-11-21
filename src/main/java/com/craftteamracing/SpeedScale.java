package com.craftteamracing;

import com.craftteamracing.net.SpeedScalePayload;
import com.craftteamracing.physics.WorldScale;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The server side of the speed scale (see {@link WorldScale}): the config's {@code speedScale} is applied when the server starts and when the file changes, sent to every player
 * on joining, and an operator can change it while playing with {@code /ctr speed <50-100>} (saved to the config, sent to everyone at once). {@code /ctr speed} alone shows it.
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID)
public final class SpeedScale {
    private SpeedScale() {}

    public static final int MIN_PERCENT = 50;

    /** Takes the value as the server's scale and tells everyone. */
    public static void apply(int percent) {
        WorldScale.setGlobal(percent / 100.0);
        WorldScale.setShrinkJumps(CtrConfig.SHRINK_JUMPS.get());
        if (ServerLifecycleHooks.getCurrentServer() != null) PacketDistributor.sendToAllPlayers(new SpeedScalePayload(percent, CtrConfig.SHRINK_JUMPS.get()));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        WorldScale.setGlobal(CtrConfig.SPEED_SCALE.get() / 100.0);
        WorldScale.setShrinkJumps(CtrConfig.SHRINK_JUMPS.get());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WorldScale.reset();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new SpeedScalePayload((int) Math.round(WorldScale.global() * 100.0), WorldScale.shrinkJumps()));
        }
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ctr").then(Commands.literal("speed")
                .executes(ctx -> {
                    int now = (int) Math.round(WorldScale.global() * 100.0);
                    ctx.getSource().sendSuccess(() -> Component.literal("Speed scale: " + now + "%, jumps " + (WorldScale.shrinkJumps() ? "shrink with it" : "keep their shape") + " (50 to 100; operators: /ctr speed <percent>, /ctr speed shrinkjumps <true|false>)"), false);
                    return now;
                })
                .then(Commands.argument("percent", IntegerArgumentType.integer(MIN_PERCENT, 100)).requires(s -> s.hasPermission(2)).executes(ctx -> {
                    int percent = IntegerArgumentType.getInteger(ctx, "percent");
                    CtrConfig.SPEED_SCALE.set(percent);
                    try {
                        CtrConfig.SPEC.save();
                    } catch (RuntimeException e) {
                        CraftTeamRacingMod.LOGGER.warn("Could not save the speed scale to the config", e);
                    }
                    apply(percent);
                    ctx.getSource().sendSuccess(() -> Component.literal("Speed scale set to " + percent + "%"), true);
                    return percent;
                }))
                .then(Commands.literal("shrinkjumps").requires(s -> s.hasPermission(2)).then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                    boolean on = BoolArgumentType.getBool(ctx, "on");
                    CtrConfig.SHRINK_JUMPS.set(on);
                    try {
                        CtrConfig.SPEC.save();
                    } catch (RuntimeException e) {
                        CraftTeamRacingMod.LOGGER.warn("Could not save the jump setting to the config", e);
                    }
                    apply((int) Math.round(WorldScale.global() * 100.0));
                    ctx.getSource().sendSuccess(() -> Component.literal("Jumps " + (on ? "shrink with the speed scale" : "keep their shape at any speed scale")), true);
                    return on ? 1 : 0;
                })))));
    }

    /** The config file was edited while the server runs. */
    @EventBusSubscriber(modid = CraftTeamRacingMod.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        private Reload() {}

        @SubscribeEvent
        public static void onReload(ModConfigEvent.Reloading event) {
            if (event.getConfig().getSpec() == CtrConfig.SPEC && ServerLifecycleHooks.getCurrentServer() != null) apply(CtrConfig.SPEED_SCALE.get());
        }
    }
}
