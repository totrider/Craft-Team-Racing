package com.craftteamracing.spectate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.kart.KartEntity;
import com.craftteamracing.pickup.ItemManager;
import com.craftteamracing.pickup.ItemStatePayload;
import com.craftteamracing.track.NodeGeometry;
import com.craftteamracing.track.PodiumScenePayload;
import com.craftteamracing.track.RaceManager;
import com.craftteamracing.track.RacePayload;
import com.craftteamracing.track.TrackData;
import com.craftteamracing.track.TrackGraph;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Viewing consoles, server side. A player who right-clicks a console that is hooked up to a Start node tunes in to that track, at any time: while its race is on (the lights or the race) they follow
 * a racer; with no race on the camera sits up and in front of the Start node and they are shown what somebody waiting in the starting area is shown (the race starting soon, the cool-down).
 * They are put in spectator mode (nothing can hurt or push them, they cannot be seen, and they take no part in anything) and kept within reach of what they watch, so the chunks and the karts
 * round it are sent to them; the camera itself is the client's ({@link com.craftteamracing.client.SpectateCamera}). Leaving puts them back in the game mode and the place they started from.
 * That is also written to the player's own data, so a player who logs out (or whose game crashes) while watching is put back when they next log in.
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID)
public final class SpectateManager {
    private SpectateManager() {}

    /** The viewer is sent the racer's screen every this many ticks. */
    private static final int FEED_TICKS = 3;
    private static final String TAG = "craftteamracing_spectate";
    /** The viewer is brought to what they watch when they are further from it than this: far enough apart not to be sent a position every tick, near enough for the kart to be in range. */
    private static final double FOLLOW_DISTANCE = 6.0;

    private static final class Session {
        final ResourceKey<Level> dimension;
        final BlockPos console;
        final long start;
        boolean idle;
        /** The podium scene has been sent (the ceremony is on): the group shot has the camera, and the viewer is at the podiums. */
        boolean scene;
        UUID target;
        int index;
        int lastKart = Integer.MIN_VALUE;
        int lastRank = -1;
        int lastField = -1;
        int sinceSent;

        Session(ResourceKey<Level> dimension, BlockPos console, long start) {
            this.dimension = dimension;
            this.console = console;
            this.start = start;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static boolean isViewing(UUID id) {
        return SESSIONS.containsKey(id);
    }

    /** Tunes the player in to the console's track; returns the reason (a message) if they cannot be. */
    public static Component begin(ServerPlayer player, BlockPos consolePos, ViewingConsoleBlockEntity console) {
        if (SESSIONS.containsKey(player.getUUID())) return null;
        ServerLevel level = player.serverLevel();
        if (!console.linked()) return Component.translatable("message.craftteamracing.console.not_linked");
        TrackGraph.Node node = TrackData.get(level).graph().get(console.start());
        if (node == null || node.kind != TrackGraph.Kind.START) return Component.translatable("message.craftteamracing.console.not_linked");
        if (RaceManager.locked(player.getUUID())) return Component.translatable("message.craftteamracing.console.racing");
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) return Component.translatable("message.craftteamracing.console.spectator");

        CompoundTag saved = new CompoundTag();
        saved.putInt("mode", player.gameMode.getGameModeForPlayer().getId());
        saved.putString("dim", level.dimension().location().toString());
        saved.putDouble("x", player.getX());
        saved.putDouble("y", player.getY());
        saved.putDouble("z", player.getZ());
        saved.putFloat("yaw", player.getYRot());
        saved.putFloat("pitch", player.getXRot());
        player.getPersistentData().put(TAG, saved);

        player.stopRiding();
        Session s = new Session(level.dimension(), consolePos, console.start());
        SESSIONS.put(player.getUUID(), s);
        player.setGameMode(GameType.SPECTATOR);
        List<ServerPlayer> racers = RaceManager.racersAt(level, s.start);
        if (racers != null && !racers.isEmpty()) follow(player, s, racers, 0);
        else goIdle(player, s, node);
        return null;
    }

    /** What the viewer's client asked for. */
    public static void action(ServerPlayer player, int action) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        if (action == SpectateActionPayload.EXIT) {
            end(player, null);
            return;
        }
        List<ServerPlayer> racers = RaceManager.racersAt(player.serverLevel(), s.start);
        if (s.idle || racers == null || racers.isEmpty()) return;
        int n = racers.size();
        follow(player, s, racers, action == SpectateActionPayload.NEXT ? (s.index + 1) % n : (s.index - 1 + n) % n);
    }

    /** A console was broken: everybody who watches through it is put back. */
    public static void consoleGone(ServerLevel level, BlockPos pos) {
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session s = SESSIONS.get(id);
            if (s != null && s.console.equals(pos) && s.dimension.equals(level.dimension())) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null) end(p, Component.translatable("message.craftteamracing.console.destroyed"));
                else SESSIONS.remove(id);
            }
        }
    }

    private static void follow(ServerPlayer viewer, Session s, List<ServerPlayer> racers, int index) {
        s.idle = false;
        clearScene(viewer, s);
        s.index = Math.max(0, Math.min(racers.size() - 1, index));
        ServerPlayer target = racers.get(s.index);
        s.target = target.getUUID();
        s.lastKart = Integer.MIN_VALUE;          // sent again below
        send(viewer, s, target, racers);
        feed(viewer, s, target);
        moveTo(viewer, racerAt(target), true);
    }

    /** No race to follow: the camera goes to the Start node, and the viewer is shown what somebody waiting in the starting area is shown. */
    private static void goIdle(ServerPlayer viewer, Session s, TrackGraph.Node node) {
        s.idle = true;
        s.target = null;
        Vec3 at = anchorOf(s);
        PacketDistributor.sendToPlayer(viewer, new SpectatePayload(true, true, -1, "", 0, 0, new UUID(0L, 0L), at.x, at.y, at.z, yawOf(node)));
        moveTo(viewer, at.add(0.0, 2.0, 0.0), true);
        sendIdleView(viewer, s);
    }

    private static void clearScene(ServerPlayer viewer, Session s) {
        if (!s.scene) return;
        s.scene = false;
        PacketDistributor.sendToPlayer(viewer, new PodiumScenePayload(List.of()));
    }

    /** With no racers: the podium ceremony's group shot while it is on (the viewer is brought to the podiums so they are in range), else the view from the Start node. */
    private static void idleTick(ServerPlayer viewer, Session s) {
        PodiumScenePayload scene = RaceManager.podiumView(viewer.serverLevel(), s.start);
        if (scene != null) {
            if (!s.scene) {
                s.scene = true;
                PacketDistributor.sendToPlayer(viewer, scene);
            }
            Vec3 centre = Vec3.ZERO;
            for (PodiumScenePayload.Spot p : scene.spots()) centre = centre.add(p.x(), p.y(), p.z());
            moveTo(viewer, centre.scale(1.0 / scene.spots().size()).add(0.0, 2.0, 0.0), false);
        } else {
            clearScene(viewer, s);
            moveTo(viewer, anchorOf(s).add(0.0, 2.0, 0.0), false);
        }
        if (viewer.tickCount % FEED_TICKS == 0) sendIdleView(viewer, s);
    }

    private static Vec3 anchorOf(Session s) {
        BlockPos p = BlockPos.of(s.start);
        return new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    private static float yawOf(TrackGraph.Node node) {
        return (float) Math.toDegrees(Math.atan2(-NodeGeometry.facingX(node.rotation), NodeGeometry.facingZ(node.rotation)));
    }

    private static void sendIdleView(ServerPlayer viewer, Session s) {
        RacePayload view = RaceManager.idleView(viewer.serverLevel(), s.start);
        PacketDistributor.sendToPlayer(viewer, view);
        PacketDistributor.sendToPlayer(viewer, ItemStatePayload.EMPTY);
    }

    /** The racer's own screen, read only: their race state (clock, places, track bar, lamps) and what their item box shows, as the racer is sent them. */
    private static void feed(ServerPlayer viewer, Session s, ServerPlayer target) {
        RacePayload race = RaceManager.viewOf(viewer.serverLevel(), s.start, target.getUUID());
        if (race != null) PacketDistributor.sendToPlayer(viewer, race);
        ItemStatePayload items = ItemManager.viewOf(target);
        if (items != null) PacketDistributor.sendToPlayer(viewer, items);
    }

    private static void send(ServerPlayer viewer, Session s, ServerPlayer target, List<ServerPlayer> racers) {
        Entity kart = target.getVehicle() instanceof KartEntity k ? k : target;
        s.lastKart = kart.getId();
        s.lastRank = s.index + 1;
        s.lastField = racers.size();
        s.sinceSent = 0;
        PacketDistributor.sendToPlayer(viewer, new SpectatePayload(true, false, kart.getId(), target.getGameProfile().getName(), s.lastRank, s.lastField, target.getUUID(), 0, 0, 0, 0.0F));
    }

    private static Vec3 racerAt(ServerPlayer target) {
        Entity at = target.getVehicle() != null ? target.getVehicle() : target;
        return at.position().add(0.0, 1.0, 0.0);
    }

    private static void moveTo(ServerPlayer viewer, Vec3 at, boolean force) {
        if (!force && viewer.position().distanceToSqr(at) < FOLLOW_DISTANCE * FOLLOW_DISTANCE) return;
        // relative rotation of 0: the viewer keeps looking where they look
        viewer.teleportTo(viewer.serverLevel(), at.x, at.y, at.z, Set.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT), 0.0F, 0.0F);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session s = SESSIONS.get(id);
            ServerPlayer viewer = event.getServer().getPlayerList().getPlayer(id);
            if (s == null) continue;
            if (viewer == null) {
                SESSIONS.remove(id);
                continue;
            }
            ServerLevel level = event.getServer().getLevel(s.dimension);
            if (level == null || !(level.getBlockEntity(s.console) instanceof ViewingConsoleBlockEntity c) || !c.linked() || c.start() != s.start) {
                end(viewer, Component.translatable("message.craftteamracing.console.destroyed"));
                continue;
            }
            TrackGraph.Node node = TrackData.get(level).graph().get(s.start);
            if (node == null || node.kind != TrackGraph.Kind.START) {
                end(viewer, Component.translatable("message.craftteamracing.console.not_linked"));
                continue;
            }
            List<ServerPlayer> racers = RaceManager.racersAt(level, s.start);
            if (racers == null || racers.isEmpty()) {
                if (!s.idle) goIdle(viewer, s, node);
                else idleTick(viewer, s);
                continue;
            }
            if (s.idle) {
                follow(viewer, s, racers, 0);          // a race has started: the leader
                continue;
            }
            int at = -1;
            for (int i = 0; i < racers.size(); i++) if (racers.get(i).getUUID().equals(s.target)) at = i;
            if (at < 0) {
                follow(viewer, s, racers, s.index);          // the racer finished or left: whoever is now where they were
                continue;
            }
            s.index = at;
            ServerPlayer target = racers.get(at);
            moveTo(viewer, racerAt(target), false);
            if (viewer.tickCount % FEED_TICKS == 0) feed(viewer, s, target);
            Entity kart = target.getVehicle() instanceof KartEntity k ? k : target;
            if (kart.getId() != s.lastKart || at + 1 != s.lastRank || racers.size() != s.lastField || ++s.sinceSent >= 40) send(viewer, s, target, racers);
        }
    }

    /** Ends the viewing: back to the game mode and the place the player came from. */
    public static void end(ServerPlayer player, Component message) {
        SESSIONS.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, SpectatePayload.off());
        restore(player);
        if (message != null) player.displayClientMessage(message, true);
    }

    private static void restore(ServerPlayer player) {
        if (!player.getPersistentData().contains(TAG)) return;
        CompoundTag saved = player.getPersistentData().getCompound(TAG);
        player.getPersistentData().remove(TAG);
        GameType mode = GameType.byId(saved.getInt("mode"));
        player.setGameMode(mode == GameType.SPECTATOR ? GameType.SURVIVAL : mode);
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(saved.getString("dim")));
        ServerLevel level = player.server.getLevel(dim);
        if (level != null) player.teleportTo(level, saved.getDouble("x"), saved.getDouble("y"), saved.getDouble("z"), saved.getFloat("yaw"), saved.getFloat("pitch"));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SESSIONS.remove(player.getUUID());
        restore(player);
    }

    /** A player who was watching when the game went away (a crash, or a server that stopped) is put back now. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !SESSIONS.containsKey(player.getUUID())) restore(player);
    }
}
