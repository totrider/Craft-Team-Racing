package com.ctrcraft.pickup;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModEntities;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator tools for looking at a weapon in the world: {@code /ctr debug spawn <kind> [charged]} puts a weapon of that kind a few blocks ahead of you, standing still for ever
 * (it does not move, explode, hurt anyone or expire, but its animation and effects play as they do in flight: the roll, the exhaust, the flame), and {@code /ctr debug clear}
 * removes it. One per player: spawning another replaces the first. Kinds: bomb, missile, beaker, tnt (add {@code charged} for the red beaker, the Nitro crate, the charged bomb and
 * missile). New weapons add themselves in {@link #spawn}.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID)
public final class DebugWeaponCommands {
    private DebugWeaponCommands() {}

    /** Player to the weapon they have standing. */
    private static final Map<UUID, UUID> SPAWNED = new HashMap<>();
    private static final String[] KINDS = { "bomb", "missile", "shield", "beaker", "tnt" };
    /** How far ahead of the player it stands, blocks. */
    private static final double AHEAD = 2.5;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        SuggestionProvider<CommandSourceStack> kinds = (ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(KINDS, builder);
        LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn").then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word()).suggests(kinds)
                .executes(ctx -> spawn(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind"), false))
                .then(Commands.argument("charged", BoolArgumentType.bool())
                        .executes(ctx -> spawn(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind"), BoolArgumentType.getBool(ctx, "charged")))));
        LiteralArgumentBuilder<CommandSourceStack> clear = Commands.literal("clear").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            boolean had = clear(p);
            ctx.getSource().sendSuccess(() -> Component.literal(had ? "Cleared the debug weapon" : "No debug weapon to clear"), false);
            return had ? 1 : 0;
        });
        event.getDispatcher().register(Commands.literal("ctr").requires(s -> s.hasPermission(2)).then(Commands.literal("debug").then(spawn).then(clear)));
    }

    /** Removes the player's standing weapon, if any. */
    public static boolean clear(ServerPlayer p) {
        UUID id = SPAWNED.remove(p.getUUID());
        if (id == null) return false;
        Entity e = p.serverLevel().getEntity(id);
        if (e == null) return false;
        e.discard();
        return true;
    }

    private static int spawn(CommandSourceStack source, String kind, boolean charged) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        ServerLevel level = p.serverLevel();
        clear(p);
        double yaw = Math.toRadians(p.getYRot());
        double x = p.getX() - Math.sin(yaw) * AHEAD;
        double z = p.getZ() + Math.cos(yaw) * AHEAD;
        double y = p.getY();
        Entity made;
        switch (kind) {
            case "bomb" -> {
                BombEntity e = ModEntities.BOMB.get().create(level);
                if (e == null) return 0;
                e.debug = true;
                e.setPowered(charged);
                made = e;
            }
            case "missile" -> {
                MissileEntity e = ModEntities.MISSILE.get().create(level);
                if (e == null) return 0;
                e.debug = true;
                e.setPowered(charged);
                made = e;
            }
            case "shield" -> {
                ShieldBombEntity e = ModEntities.SHIELD_BOMB.get().create(level);
                if (e == null) return 0;
                e.debug = true;
                e.setBlue(charged);
                made = e;
            }
            case "beaker" -> {
                PotionEntity e = ModEntities.POTION.get().create(level);
                if (e == null) return 0;
                e.debug = true;
                e.setRed(charged);
                made = e;
            }
            case "tnt" -> {
                TntEntity e = ModEntities.TNT.get().create(level);
                if (e == null) return 0;
                e.debug = true;
                e.setNitro(charged);
                made = e;
            }
            default -> {
                source.sendFailure(Component.literal("Unknown weapon '" + kind + "': " + String.join(", ", KINDS)));
                return 0;
            }
        }
        made.setPos(x, y, z);
        made.setYRot(p.getYRot());
        made.yRotO = p.getYRot();
        level.addFreshEntity(made);
        SPAWNED.put(p.getUUID(), made.getUUID());
        source.sendSuccess(() -> Component.literal("Spawned a " + (charged ? "charged " : "") + kind + " 2.5 blocks ahead of you (/ctr debug clear removes it, a new spawn replaces it)"), false);
        return 1;
    }
}
