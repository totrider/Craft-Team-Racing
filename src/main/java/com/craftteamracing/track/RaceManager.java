package com.craftteamracing.track;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.craftteamracing.CtrConfig;
import com.craftteamracing.ModSounds;
import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.kart.KartEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The race runtime (server). A race belongs to the Start node of a recorded, valid track and runs through four phases:
 * GATHER (a countdown while karts drive into the area round the start node), LIGHTS (the entrants are put on the grid in a random
 * order and held, revving; the three lights; the decomp's 0xF00 = 3840 ms), RACING (laps counted by passing the rings of the recorded
 * main path, see {@link RaceProgress}) and DONE. The drivers' clients run the physics; this follows their reported positions.
 * See docs/DESIGN_TRACKS.md. Everything here is a default behaviour: nothing changes how a kart drives.
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID)
public final class RaceManager {
    private RaceManager() {}

    /** Gathering takes this long (ticks) after the race is started from the menu. */
    public static final int GATHER_TICKS = 200;
    /** A racer may respawn once in this many ticks. */
    private static final int RESPAWN_COOLDOWN_TICKS = 60;
    /** Once somebody has finished, the others have this long (ticks) to follow. */
    private static final int FINISH_GRACE_TICKS = 1200;
    /** After the podium is full: a few seconds to hear the finish jingle before everyone is let go. */
    private static final int PODIUM_END_TICKS = 160;
    private static final int TICK_MS = 50;

    private record Key(ResourceKey<Level> dimension, long start) {}

    private static final class Racer {
        final UUID id;
        final Component name;
        RaceProgress progress;
        double x;
        double z;
        int lapStartTick;
        int lastLapMs;
        int bestLapMs;
        /** The time of each lap finished so far, ms. */
        final List<Integer> lapTimes = new ArrayList<>();
        int finishPlace;
        int finishMs;
        boolean out;
        int lastRespawnTick = -1000;
        int placeSeq;
        RaceGrid.Slot slot;
        /** Where the racer started the race (their grid place): the racers who are not on a podium go back there for the ceremony. */
        RaceGrid.Slot gridSlot;
        /** Put on a podium: locked in place, out of the race's way. */
        boolean podium;
        /** 1 to 3 once put on a podium. */
        int podiumPlace;

        Racer(UUID id, Component name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class Race {
        final Key key;
        final int laps;
        final UUID starter;
        final int graphVersion;
        /** Started from the menu (entrants are whoever is in the area) rather than by karts driving in. */
        final boolean manual;
        int phase = RacePayload.GATHER;
        int ticks = GATHER_TICKS;
        int lastLights;
        int goTick;
        int firstFinishTick = -1;
        int finished;
        /** The tick the race ends at once the podium is full or the time is up (-1: not set). */
        int endTick = -1;
        boolean podiumFilled;
        final Map<UUID, Racer> racers = new LinkedHashMap<>();
        /** Players near the start who were sent the gathering countdown without being in the area: they are told when it is over (the text would stay on their screen otherwise). */
        final java.util.Set<UUID> bystanders = new java.util.HashSet<>();
        /** The rules the race runs by, fixed when it begins to gather (the track's temporary or default ones). */
        final com.craftteamracing.items.RuleSet rules;
        /** The track's music, fixed with the rules when the race begins to gather. */
        com.craftteamracing.MusicSheetData music = com.craftteamracing.MusicSheetData.DEFAULT;

        Race(Key key, int laps, UUID starter, int graphVersion, boolean manual, com.craftteamracing.items.RuleSet rules) {
            this.manual = manual;
            this.rules = rules;
            this.key = key;
            this.laps = laps;
            this.starter = starter;
            this.graphVersion = graphVersion;
        }
    }

    /** A racer's place for the power-up roulette: the field size, the place (0 is first) and the lap (0 is the first). */
    public record Standing(int field, int rank, int lapIndex) {}

    public static Standing standing(ServerPlayer player) {
        Race r = BY_PLAYER.get(player.getUUID());
        if (r == null || r.phase != RacePayload.RACING) return null;
        Racer rc = r.racers.get(player.getUUID());
        if (rc == null) return null;
        return new Standing(r.racers.size(), Math.max(0, ranking(r).indexOf(rc)), rc.progress.laps());
    }

    /** The racers still racing in the order of the race (first place first), the player included, or null when the player is not racing (the Warp Orb goes by this). */
    public static List<ServerPlayer> rankedRacers(ServerPlayer player) {
        Race r = BY_PLAYER.get(player.getUUID());
        if (r == null || r.phase != RacePayload.RACING) return null;
        List<ServerPlayer> out = new ArrayList<>();
        for (Racer rc : ranking(r)) {
            if (rc.finishPlace != 0 || rc.out || rc.podium) continue;
            ServerPlayer p = player.server.getPlayerList().getPlayer(rc.id);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** The centres of the rings of the main path of the race this player races in (the course the Warp Orb follows), or null when the player is not racing. */
    public static List<net.minecraft.world.phys.Vec3> courseNodes(ServerPlayer player) {
        Race r = BY_PLAYER.get(player.getUUID());
        if (r == null || r.phase != RacePayload.RACING) return null;
        List<Long> main = TrackData.get(player.serverLevel()).routes(r.key.start()).main();
        List<net.minecraft.world.phys.Vec3> out = new ArrayList<>();
        for (int i = 0; i < main.size() - 1; i++) {          // the last entry is the start again
            BlockPos p = BlockPos.of(main.get(i));
            out.add(new net.minecraft.world.phys.Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
        }
        return out;
    }

    /** The rings of the branch a racer is on that it has passed (empty when it is on the main path): the orb follows them to the racer. */
    public static List<net.minecraft.world.phys.Vec3> courseBranch(ServerPlayer player) {
        Race r = BY_PLAYER.get(player.getUUID());
        Racer rc = r == null ? null : r.racers.get(player.getUUID());
        List<net.minecraft.world.phys.Vec3> out = new ArrayList<>();
        if (rc == null) return out;
        for (long ring : rc.progress.branchRings()) {
            BlockPos p = BlockPos.of(ring);
            out.add(new net.minecraft.world.phys.Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
        }
        return out;
    }

    /** The branches of the course this player races on (empty when not racing). */
    public static List<RaceProgress.BranchView> courseBranches(ServerPlayer player) {
        Race r = BY_PLAYER.get(player.getUUID());
        Racer rc = r == null ? null : r.racers.get(player.getUUID());
        return rc == null ? List.of() : rc.progress.branchViews();
    }

    /** Where the branch a racer is on rejoins the main path (an index in it), or -1 when the racer is on the main path. */
    public static int courseBranchExit(ServerPlayer player) {
        Race r = BY_PLAYER.get(player.getUUID());
        Racer rc = r == null ? null : r.racers.get(player.getUUID());
        return rc == null ? -1 : rc.progress.branchExitIdx();
    }

    /** Where on the course (index in the main path) a racer is, or -1. */
    public static int courseIndex(ServerPlayer player) {
        Race r = BY_PLAYER.get(player.getUUID());
        Racer rc = r == null ? null : r.racers.get(player.getUUID());
        return rc == null ? -1 : rc.progress.courseIdx();
    }

    /** The other racers of the race this player is racing in (the lights are out; those who have finished are not in it any more), or null when the player is not racing. */
    public static List<ServerPlayer> opponents(ServerPlayer player) {
        Race r = BY_PLAYER.get(player.getUUID());
        if (r == null || r.phase != RacePayload.RACING) return null;
        List<ServerPlayer> out = new ArrayList<>();
        for (Racer rc : r.racers.values()) {
            if (rc.id.equals(player.getUUID()) || rc.finishPlace != 0 || rc.out || rc.podium) continue;
            ServerPlayer p = player.server.getPlayerList().getPlayer(rc.id);
            if (p != null) out.add(p);
        }
        return out;
    }

    /**
     * Whether a race that has reached the lights is on a track whose line (the links between its nodes) passes within {@code radius} blocks of
     * {@code pos}: the spots near a track belong to it (docs/DESIGN_ITEMS.md).
     */
    public static boolean activeNear(ServerLevel level, BlockPos pos, double radius) {
        double sq = radius * radius;
        for (Race r : RACES.values()) {
            if (r.phase != RacePayload.LIGHTS && r.phase != RacePayload.RACING) continue;
            if (!r.key.dimension().equals(level.dimension())) continue;
            if (TrackData.get(level).graph().distSqToTrack(r.key.start(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= sq) return true;
        }
        return false;
    }

    /** The rules of the race this player is in (gathering, on the grid or racing), or null when the player is not in one. */
    public static com.craftteamracing.items.RuleSet rulesOf(UUID id) {
        Race r = BY_PLAYER.get(id);
        return r == null ? null : r.rules;
    }

    /** The rules of a race that has reached the lights on a track whose line passes within {@code radius} blocks of {@code pos}, or null when there is none. */
    public static com.craftteamracing.items.RuleSet rulesNear(ServerLevel level, BlockPos pos, double radius) {
        double sq = radius * radius;
        for (Race r : RACES.values()) {
            if (r.phase != RacePayload.LIGHTS && r.phase != RacePayload.RACING) continue;
            if (!r.key.dimension().equals(level.dimension())) continue;
            if (TrackData.get(level).graph().distSqToTrack(r.key.start(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= sq) return r.rules;
        }
        return null;
    }

    /** The racers of the race on this Start node's track who are still on the course, in the order of the race (null when no race is racing there): what a viewing console can follow. */
    public static List<ServerPlayer> racersAt(ServerLevel level, long start) {
        Race r = RACES.get(new Key(level.dimension(), start));
        if (r == null || (r.phase != RacePayload.RACING && r.phase != RacePayload.LIGHTS)) return null;
        List<ServerPlayer> out = new ArrayList<>();
        for (Racer rc : ranking(r)) {
            if (rc.finishPlace != 0 || rc.out || rc.podium) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(rc.id);
            if (p != null && p.getVehicle() instanceof com.craftteamracing.kart.KartEntity) out.add(p);
        }
        return out;
    }

    /** Whether a race on the track of this Start node has started (the lights are out): until then the grid and the gathering area stay shown. */
    public static boolean raceRunning(ServerLevel level, long start) {
        Race r = RACES.get(new Key(level.dimension(), start));
        return r != null && r.phase == RacePayload.RACING;
    }

    private static final Map<Key, Race> RACES = new HashMap<>();
    private static final Map<UUID, Race> BY_PLAYER = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static int placeCounter;

    public static boolean isRunning(ServerLevel level, long start) {
        return RACES.containsKey(new Key(level.dimension(), start));
    }

    /** Starts the gathering for the race on the track of the Start node at {@code startPos}. Returns an error message key, or null if it started. */
    public static String start(ServerPlayer starter, ServerLevel level, BlockPos startPos) {
        TrackData data = TrackData.get(level);
        long start = startPos.asLong();
        TrackRoutes routes = data.routes(start);
        if (!routes.valid(data.graph(), start)) return "message.craftteamracing.race.not_valid";
        Key key = new Key(level.dimension(), start);
        if (RACES.containsKey(key)) return "message.craftteamracing.race.running";
        if (coolingDown(level, key)) return "message.craftteamracing.race.cooldown";
        Race created = new Race(key, routes.laps(), starter.getUUID(), data.graph().version(), true, data.effectiveRules(start));
        created.music = data.music(start);
        RACES.put(key, created);
        ChunkKeeper.holdCourse(level, key.dimension().location() + "@" + key.start(), data.graph(), start);
        broadcastGather(level, RACES.get(key), startPos);
        return null;
    }

    public static void cancel(ServerLevel level, long start) {
        Race r = RACES.get(new Key(level.dimension(), start));
        if (r != null) end(level.getServer(), r, "message.craftteamracing.race.cancelled", false);
    }

    /**
     * Whether the player is in a race that has reached the lights and has not finished it: such a driver cannot get out of the kart by accident (sneak does nothing);
     * the "leave race" key ({@link LeaveRacePayload}) is the way out.
     */
    public static boolean locked(UUID id) {
        Race r = BY_PLAYER.get(id);
        if (r == null || (r.phase != RacePayload.LIGHTS && r.phase != RacePayload.RACING)) return false;
        Racer rc = r.racers.get(id);
        return rc != null && !rc.out;
    }

    /** The driver chose to leave the race: out of it (the kart is theirs again). */
    public static void forfeit(ServerPlayer p) {
        if (!locked(p.getUUID())) return;
        Race r = BY_PLAYER.get(p.getUUID());
        Racer rc = r == null ? null : r.racers.get(p.getUUID());
        if (rc != null && (rc.finishPlace != 0 || rc.podium)) {
            // a racer who has finished keeps their place; leaving only lets them go
            BY_PLAYER.remove(p.getUUID());
            releaseKart(p);
        } else {
            leave(p.getUUID());
        }
        PacketDistributor.sendToPlayer(p, RacePayload.off());
        p.displayClientMessage(Component.translatable("message.craftteamracing.race.left"), true);
    }

    /** In a race a dismount (sneaking) is refused: leaving is a deliberate act, the held leave-race key. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onDismount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof KartEntity && event.getEntityMounting() instanceof ServerPlayer p
                && !p.isRemoved() && p.isAlive() && locked(p.getUUID())) {
            event.setCanceled(true);
            if (p.tickCount % 40 == 0) p.displayClientMessage(Component.translatable("message.craftteamracing.race.leave_hint"), true);
        }
    }

    /** A racer logged out, left the dimension or the kart: out of the race. */
    public static void leave(UUID id) {
        Race r = BY_PLAYER.remove(id);
        if (r != null) {
            Racer rc = r.racers.get(id);
            if (rc != null) rc.out = true;
        }
    }

    private static boolean inArea(ServerPlayer p, BlockPos start, boolean manual) {
        if (!(p.getVehicle() instanceof KartEntity kart) || kart.getControllingPassenger() != p || (!manual && !kart.canStartRaces())) return false;
        TrackGraph.Node node = TrackData.get(p.serverLevel()).graph().get(start.asLong());
        return node != null && GatherZone.contains(start.asLong(), node.rotation, p.getX(), p.getY(), p.getZ());
    }

    private static List<ServerPlayer> inArea(ServerLevel level, BlockPos start, boolean manual) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (inArea(p, start, manual)) out.add(p);
        return out;
    }

    private static void broadcastGather(ServerLevel level, Race r, BlockPos start) {
        List<ServerPlayer> in = inArea(level, start, r.manual);
        RacePayload payload = new RacePayload(RacePayload.GATHER, r.ticks, 0, r.laps, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, in.size(), List.of(), List.of(), List.of(), List.of(), false, false, false, false);
        for (ServerPlayer p : level.players()) {
            // everybody near sees the countdown, so drivers can find the area; those in it are counted
            boolean near = inArea(p, start, r.manual) || p.distanceToSqr(start.getX(), start.getY(), start.getZ()) < 64 * 64;
            if (near) {
                PacketDistributor.sendToPlayer(p, payload);
                r.bystanders.add(p.getUUID());
            } else if (r.bystanders.remove(p.getUUID())) {
                PacketDistributor.sendToPlayer(p, RacePayload.off());          // walked away: the countdown is not theirs to see any more
            }
        }
    }

    /**
     * The track is cleared of weapons before the lights: every bomb, missile, potion, TNT crate and warp orb lying or flying within the pickup radius of the track's line goes, whoever
     * threw it (a driver who is not in the race included), so the race starts on a clean track. A kart's shield stays with its driver.
     */
    private static void clearTrack(ServerLevel level, Race r) {
        double radius = CtrConfig.PICKUP_TRACK_RADIUS.get();
        double sq = radius * radius;
        TrackGraph graph = TrackData.get(level).graph();
        List<net.minecraft.world.entity.Entity> found = new ArrayList<>();
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            boolean weapon = (e instanceof com.craftteamracing.pickup.BombEntity && !(e instanceof com.craftteamracing.pickup.ShieldBombEntity))
                    || e instanceof com.craftteamracing.pickup.PotionEntity || e instanceof com.craftteamracing.pickup.TntEntity || e instanceof com.craftteamracing.pickup.WarpOrbEntity;
            if (weapon && graph.distSqToTrack(r.key.start(), e.getX(), e.getY(), e.getZ()) <= sq) found.add(e);
        }
        for (net.minecraft.world.entity.Entity e : found) {
            if (e instanceof com.craftteamracing.pickup.MinePool.Mine m) {
                if (!m.isGone()) m.vanish(level);
            } else {
                e.discard();
            }
        }
    }

    /** The gathering is over: everybody who was shown its countdown and is not racing is told it is gone (the racers get the race's own state). */
    private static void clearBystanders(ServerLevel level, Race r) {
        for (UUID id : r.bystanders) {
            if (r.racers.containsKey(id)) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) PacketDistributor.sendToPlayer(p, RacePayload.off());
        }
        r.bystanders.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCooldowns(event.getServer());
        if (RACES.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (Race r : new ArrayList<>(RACES.values())) {
            ServerLevel level = server.getLevel(r.key.dimension());
            if (level == null) {
                end(server, r, null, false);
                continue;
            }
            TrackGraph graph = TrackData.get(level).graph();
            BlockPos start = BlockPos.of(r.key.start());
            if (graph.version() != r.graphVersion && (!graph.contains(r.key.start()) || !TrackData.get(level).routes(r.key.start()).valid(graph, r.key.start()))) {
                end(server, r, "message.craftteamracing.race.track_changed", false);
                continue;
            }
            switch (r.phase) {
                case RacePayload.GATHER -> tickGather(level, r, start);
                case RacePayload.LIGHTS -> tickLights(level, r);
                case RacePayload.RACING -> tickRacing(level, r);
                default -> { }
            }
        }
    }

    private static void tickGather(ServerLevel level, Race r, BlockPos start) {
        r.ticks--;
        if (r.ticks % 20 == 0 && r.ticks > 0) {
            broadcastGather(level, r, start);
            if (r.ticks <= 100) level.playSound(null, start, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if (r.ticks > 0) return;
        List<ServerPlayer> entrants = inArea(level, start, r.manual);
        if (entrants.isEmpty() || (!r.manual && entrants.size() < CtrConfig.RACE_MIN_RACERS.get())) {
            end(level.getServer(), r, "message.craftteamracing.race.nobody", false);
            return;
        }
        TrackData data = TrackData.get(level);
        TrackRoutes routes = data.routes(r.key.start());
        TrackGraph.Node startNode = data.graph().get(r.key.start());
        List<ServerPlayer> order = RaceGrid.shuffled(entrants, RANDOM);
        for (int i = 0; i < order.size(); i++) {
            ServerPlayer p = order.get(i);
            Racer rc = new Racer(p.getUUID(), p.getName());
            rc.progress = new RaceProgress(data.graph(), routes);
            rc.slot = RaceGrid.slot(r.key.start(), startNode.rotation, i);
            rc.gridSlot = rc.slot;
            rc.placeSeq = ++placeCounter;
            r.racers.put(p.getUUID(), rc);
            com.craftteamracing.physics.WorldScale.setTrack(r.rules.speedPercent());          // the race's pace, for the weapons the server moves (one race at a time sets it)
            BY_PLAYER.put(p.getUUID(), r);
            com.craftteamracing.pickup.ItemManager.reset(p);
            if (p.getVehicle() instanceof KartEntity ek) ek.setEthereal(false);          // a new race starts with nothing in the kart
            PacketDistributor.sendToPlayer(p, new RulesPayload(r.rules));        // the rules the client's own sim needs (boost pads, the pace)
            PacketDistributor.sendToPlayer(p, new MusicPayload(r.music));        // the track's music (its name shows even where the sound is missing)
            put(level, p, rc);
        }
        clearBystanders(level, r);
        clearTrack(level, r);
        r.phase = RacePayload.LIGHTS;
        setPower(level, r.key.start(), true);
        r.ticks = RaceGrid.INTRO_TICKS + RaceGrid.LIGHTS_TICKS + 20;       // the intro (jingle and camera pan), then a second to settle on the grid before the first light
        r.lastLights = 0;
        sendAll(level, r, 0);
    }

    private static void tickLights(ServerLevel level, Race r) {
        r.ticks--;
        int lit = RaceGrid.lightsLit(r.ticks);
        BlockPos start = BlockPos.of(r.key.start());
        if (lit != r.lastLights) {
            r.lastLights = lit;
            if (lit > 0) level.playSound(null, start, ModSounds.LIGHT_COUNT.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
            sendAll(level, r, 0);
        }
        if (r.ticks > 0) return;
        r.phase = RacePayload.RACING;
        r.goTick = level.getServer().getTickCount();
        for (Racer rc : r.racers.values()) rc.lapStartTick = r.goTick;
        level.playSound(null, start, ModSounds.LIGHT_GREEN.get(), SoundSource.PLAYERS, 1.5F, 1.0F);
        sendAll(level, r, 0);
    }

    private static void tickRacing(ServerLevel level, Race r) {
        int now = level.getServer().getTickCount();
        boolean anyActive = false;
        for (Racer rc : r.racers.values()) {
            if (rc.out || rc.finishPlace != 0) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(rc.id);
            if (p == null || !(p.getVehicle() instanceof KartEntity) || !p.level().dimension().equals(r.key.dimension())) {
                rc.out = true;
                BY_PLAYER.remove(rc.id);
                if (p != null) PacketDistributor.sendToPlayer(p, RacePayload.off());
                continue;
            }
            anyActive = true;
        }
        boolean timeUp = r.firstFinishTick >= 0 && now - r.firstFinishTick > FINISH_GRACE_TICKS;
        boolean podiums = podiumsReady(level, r);
        if (podiums && !r.podiumFilled && (r.finished >= 3 || timeUp || !anyActive)) {
            // the podium is full, or the time is up: whoever is next in the race takes the places that are left, and the race ends a moment later
            fillPodium(level, r);
            sendScene(level, r);
            parkOthers(level, r);
            r.podiumFilled = true;
            r.endTick = now + PODIUM_END_TICKS;
        }
        if (podiums && now % 3 == 0) celebrate(level, r);
        if (r.endTick >= 0 && now >= r.endTick) {
            end(level.getServer(), r, null, true);
            return;
        }
        if (!podiums && (!anyActive || timeUp)) {
            end(level.getServer(), r, null, true);
            return;
        }
        if (now % 4 == 0) sendAll(level, r, now);
    }

    /** Players who asked for the debug view of their race progress (see RaceDebugClient). */
    private static final java.util.Set<UUID> DEBUG = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void setDebug(UUID id, boolean on) {
        if (on) DEBUG.add(id);
        else DEBUG.remove(id);
    }

    private static void sendDebug(ServerLevel level, Race r, ServerPlayer p, Racer rc) {
        TrackGraph graph = TrackData.get(level).graph();
        RaceProgress.Debug d = rc.progress.debug();
        List<RaceDebugPayload.Ring> rings = new ArrayList<>();
        for (long pos : d.rings()) {
            TrackGraph.Node node = graph.get(pos);
            if (node == null) continue;
            int state = pos == d.head() ? RaceDebugPayload.HEAD : d.next().contains(pos) ? RaceDebugPayload.NEXT : d.passed().contains(pos) ? RaceDebugPayload.PASSED : RaceDebugPayload.OTHER;
            rings.add(new RaceDebugPayload.Ring(pos, node.rotation, node.ext, state));
        }
        PacketDistributor.sendToPlayer(p, new RaceDebugPayload(rings, d.seg(), d.point(), d.info() + "; race phase " + r.phase + (rc.podium ? ", on a podium" : "") + (rc.out ? ", out" : "")));
    }

    /** After a race with a podium ceremony the track is not free for a while: a new race cannot start until the ceremony is over and this many ticks more (15 s). */
    private static final int COOLDOWN_TICKS = 300;
    private static final Map<Key, Integer> COOLDOWN = new HashMap<>();
    /** Players who were shown a cool-down count (so it can be taken off their screen). */
    private static final Set<UUID> COOLDOWN_SEEN = new HashSet<>();

    private static boolean coolingDown(ServerLevel level, Key key) {
        Integer end = COOLDOWN.get(key);
        return end != null && level.getServer().getTickCount() < end;
    }

    private static RacePayload cooldownPayload(int left) {
        return new RacePayload(RacePayload.GATHER, left, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), false, false, false, true);
    }

    /** The count of the cool-down is shown in the usual race-starting place to the racers waiting in the staging area (nobody outside it); when it ends a race starts at once if enough karts are waiting in the area. */
    private static void tickCooldowns(MinecraftServer server) {
        if (COOLDOWN.isEmpty()) return;
        int now = server.getTickCount();
        for (Map.Entry<Key, Integer> e : new ArrayList<>(COOLDOWN.entrySet())) {
            Key key = e.getKey();
            ServerLevel level = server.getLevel(key.dimension());
            int left = e.getValue() - now;
            if (level == null) {
                COOLDOWN.remove(key);
                continue;
            }
            BlockPos start = BlockPos.of(key.start());
            if (left <= 0) {
                COOLDOWN.remove(key);
                for (UUID id : new ArrayList<>(COOLDOWN_SEEN)) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null && !BY_PLAYER.containsKey(id)) PacketDistributor.sendToPlayer(p, RacePayload.off());
                }
                COOLDOWN_SEEN.clear();
                Set<UUID> in = INSIDE.get(key);
                if (in != null && !in.isEmpty()) entered(level, key, in.size());
                continue;
            }
            if (now % 10 != 0) continue;
            RacePayload payload = cooldownPayload(left);
            Set<UUID> staging = INSIDE.getOrDefault(key, Set.of());          // only the karts standing in the staging area see it
            for (ServerPlayer p : level.players()) {
                if (BY_PLAYER.containsKey(p.getUUID())) continue;
                if (staging.contains(p.getUUID())) {
                    PacketDistributor.sendToPlayer(p, payload);
                    COOLDOWN_SEEN.add(p.getUUID());
                } else if (COOLDOWN_SEEN.remove(p.getUUID())) {
                    PacketDistributor.sendToPlayer(p, RacePayload.off());
                }
            }
        }
    }

    /** A Start node gives a redstone signal while its race is running, a podium node while a racer stands on it. */
    private static void setPower(ServerLevel level, long node, boolean on) {
        TrackNodeBlock.setPowered(level, BlockPos.of(node), on);
    }

    private static void powerAll(ServerLevel level, Race r, boolean on) {
        setPower(level, r.key.start(), on);
        if (on) return;
        TrackGraph graph = TrackData.get(level).graph();
        for (int place = 1; place <= 3; place++) {
            Long pos = graph.podiumPos(r.key.start(), place);
            if (pos != null) setPower(level, pos, false);
        }
    }

    /** Tells every racer the podium ceremony has begun and where the places are (the cameras of those not on a podium look over them). */
    private static void sendScene(ServerLevel level, Race r) {
        List<PodiumScenePayload.Spot> spots = new ArrayList<>();
        for (int place = 1; place <= 3; place++) {
            RaceGrid.Slot s = podiumSlot(level, r, place);
            if (s != null) spots.add(new PodiumScenePayload.Spot(s.x(), s.y(), s.z(), s.yaw(), place));
        }
        PodiumScenePayload payload = new PodiumScenePayload(spots);
        for (Racer rc : r.racers.values()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(rc.id);
            if (p != null) PacketDistributor.sendToPlayer(p, payload);
        }
    }

    /** Confetti and streamers over each podium node that has a racer on it, while they are on display. */
    private static void celebrate(ServerLevel level, Race r) {
        TrackGraph graph = TrackData.get(level).graph();
        for (Racer rc : r.racers.values()) {
            if (!rc.podium || rc.podiumPlace == 0) continue;
            Long pos = graph.podiumPos(r.key.start(), rc.podiumPlace);
            if (pos == null) continue;
            // at random places inside the node's 3 x 3 x 3 box (it stands on the floor of the node's block)
            for (int i = 0; i < 8; i++) {
                double x = NodeGeometry.x(pos) + level.random.nextDouble() * 3.0 - 1.0;
                double y = NodeGeometry.y(pos) + level.random.nextDouble() * 3.0;
                double z = NodeGeometry.z(pos) + level.random.nextDouble() * 3.0 - 1.0;
                level.sendParticles(i < 6 ? com.craftteamracing.ModParticles.CONFETTI.get() : com.craftteamracing.ModParticles.STREAMER.get(), x, y, z, 1, 0.0, 0.0, 0.0, 0.1);
            }
        }
    }

    /** Whether the track has a podium node hooked up for each of the three places (without all three, the podium does nothing). */
    private static boolean podiumsReady(ServerLevel level, Race r) {
        TrackGraph graph = TrackData.get(level).graph();
        for (int place = 1; place <= 3; place++) if (graph.podiumPos(r.key.start(), place) == null) return false;
        return true;
    }

    private static RaceGrid.Slot podiumSlot(ServerLevel level, Race r, int place) {
        TrackGraph graph = TrackData.get(level).graph();
        Long pos = graph.podiumPos(r.key.start(), place);
        if (pos == null) return null;
        TrackGraph.Node node = graph.get(pos);
        double yaw = Math.toDegrees(Math.atan2(-NodeGeometry.facingX(node.rotation), NodeGeometry.facingZ(node.rotation)));
        return new RaceGrid.Slot(NodeGeometry.x(pos) + 0.5, NodeGeometry.y(pos), NodeGeometry.z(pos) + 0.5, (float) yaw);
    }

    /** Puts the racer on the podium for {@code place}: the kart goes there, is locked in place and from now on touches nothing (see KartEntity#ethereal). */
    private static void toPodium(ServerLevel level, Race r, Racer rc, int place) {
        RaceGrid.Slot slot = podiumSlot(level, r, place);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(rc.id);
        if (slot == null || p == null) return;
        rc.slot = slot;
        rc.placeSeq = ++placeCounter;
        rc.podium = true;
        rc.podiumPlace = place;
        Long node = TrackData.get(level).graph().podiumPos(r.key.start(), place);
        if (node != null) setPower(level, node, true);
        put(level, p, rc);
        sendOne(level, r, rc, level.getServer().getTickCount());
    }

    /** The places of the podium that nobody has: filled by the racers who are next in the race. */
    private static void fillPodium(ServerLevel level, Race r) {
        List<Racer> rank = ranking(r);
        boolean[] taken = new boolean[4];
        for (Racer rc : rank) if (rc.podium && rc.finishPlace >= 1 && rc.finishPlace <= 3) taken[rc.finishPlace] = true;
        int next = 0;
        for (int place = 1; place <= 3; place++) {
            if (taken[place]) continue;
            while (next < rank.size() && (rank.get(next).podium || rank.get(next).out)) next++;
            if (next >= rank.size()) return;
            toPodium(level, r, rank.get(next++), place);
        }
    }

    /**
     * The ceremony starts: every racer who is not on a podium goes back to the grid place they started the race in (that is near the start, so the podium is inside what their client has loaded),
     * locked there and out of the race's way, to watch the podium. They are held like the winners are (the same "podium" lock) but are on no place, so no confetti falls on them.
     */
    private static void parkOthers(ServerLevel level, Race r) {
        for (Racer rc : r.racers.values()) {
            if (rc.podium || rc.out || rc.gridSlot == null) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(rc.id);
            if (p == null) continue;
            rc.slot = rc.gridSlot;
            rc.placeSeq = ++placeCounter;
            rc.podium = true;
            rc.podiumPlace = 0;
            if (p.getVehicle() instanceof KartEntity kart) kart.setEthereal(true);
            put(level, p, rc);
            sendOne(level, r, rc, level.getServer().getTickCount());
        }
    }

    /** The kart is the driver's again: no more locked, no more ethereal. */
    private static void releaseKart(ServerPlayer p) {
        if (p.getVehicle() instanceof KartEntity kart) kart.setEthereal(false);
        PacketDistributor.sendToPlayer(p, RacePayload.off());
    }

    /** Moves the racer's kart and rider to the slot the racer holds (grid or respawn). The driver's client then places its sim there. */
    private static void put(ServerLevel level, ServerPlayer p, Racer rc) {
        if (p.getVehicle() instanceof KartEntity kart) {
            kart.absMoveTo(rc.slot.x(), rc.slot.y(), rc.slot.z(), rc.slot.yaw(), 0.0F);
            kart.setDeltaMovement(Vec3.ZERO);
            kart.positionRider(p);
            level.getChunkSource().move(p);
        }
        rc.x = rc.slot.x();
        rc.z = rc.slot.z();
    }

    /** Respawn: back to the last checkpoint you passed, facing the next one. */
    public static void respawn(ServerPlayer player) {
        Race r = BY_PLAYER.get(player.getUUID());
        Racer rc = r == null ? null : r.racers.get(player.getUUID());
        if (r == null || r.phase != RacePayload.RACING || rc == null || rc.out || rc.finishPlace != 0) {
            player.displayClientMessage(Component.translatable("message.craftteamracing.race.no_respawn"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        int now = level.getServer().getTickCount();
        if (now - rc.lastRespawnTick < RESPAWN_COOLDOWN_TICKS) return;
        rc.lastRespawnTick = now;
        long node = rc.progress.lastPassed();
        long next = rc.progress.expected().iterator().next();
        double dx = NodeGeometry.x(next) - NodeGeometry.x(node);
        double dz = NodeGeometry.z(next) - NodeGeometry.z(node);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        rc.slot = new RaceGrid.Slot(NodeGeometry.x(node) + 0.5, NodeGeometry.y(node), NodeGeometry.z(node) + 0.5, yaw);
        rc.placeSeq = ++placeCounter;
        put(level, player, rc);
        sendOne(level, r, rc, now);
        player.displayClientMessage(Component.translatable("message.craftteamracing.race.respawned"), true);
    }

    // ---- starting by driving in: enter and exit tracking ----

    /** Who is inside each Start node's gathering box (a kart that may start races): entering is what can begin a race, so the sets are kept apart from any race. */
    private static final Map<Key, Set<UUID>> INSIDE = new HashMap<>();

    /** Updates the areas a kart's new position is in or out of. Runs on every report, but only compares against the Start nodes. */
    private static void trackAreas(ServerPlayer player, double x, double y, double z) {
        ServerLevel level = player.serverLevel();
        boolean can = player.getVehicle() instanceof KartEntity k && k.canStartRaces();
        for (long start : TrackData.get(level).starts()) {
            TrackGraph.Node node = TrackData.get(level).graph().get(start);
            boolean in = can && node != null && GatherZone.contains(start, node.rotation, x, y, z);
            Key key = new Key(level.dimension(), start);
            Set<UUID> set = INSIDE.computeIfAbsent(key, k -> new HashSet<>());
            if (in) {
                if (set.add(player.getUUID())) entered(level, key, set.size());
            } else if (set.remove(player.getUUID())) {
                exited(level.getServer(), key, set.size());
            }
        }
    }

    private static void entered(ServerLevel level, Key key, int count) {
        if (RACES.containsKey(key) || count < CtrConfig.RACE_MIN_RACERS.get() || coolingDown(level, key)) return;
        TrackData data = TrackData.get(level);
        TrackRoutes routes = data.routes(key.start());
        if (!routes.valid(data.graph(), key.start())) return;
        Race race = new Race(key, routes.laps(), null, data.graph().version(), false, data.effectiveRules(key.start()));
        race.music = data.music(key.start());
        RACES.put(key, race);
        ChunkKeeper.holdCourse(level, key.dimension().location() + "@" + key.start(), data.graph(), key.start());
        broadcastGather(level, race, BlockPos.of(key.start()));
    }

    private static void exited(MinecraftServer server, Key key, int count) {
        Race r = RACES.get(key);
        if (r != null && r.phase == RacePayload.GATHER && !r.manual && count < CtrConfig.RACE_MIN_RACERS.get()) {
            end(server, r, "message.craftteamracing.race.not_enough", false);
        }
    }

    /** A player left the game, the dimension or their kart: out of every area. */
    public static void exitAll(MinecraftServer server, UUID id) {
        for (Map.Entry<Key, Set<UUID>> e : new ArrayList<>(INSIDE.entrySet())) {
            if (e.getValue().remove(id)) exited(server, e.getKey(), e.getValue().size());
        }
    }

    /** Called for each position a driver's kart reports. */
    public static void onKartMoved(ServerPlayer player, double x0, double y0, double z0, double x1, double y1, double z1) {
        trackAreas(player, x1, y1, z1);
        Race r = BY_PLAYER.get(player.getUUID());
        if (r == null || r.phase != RacePayload.RACING) return;
        Racer rc = r.racers.get(player.getUUID());
        if (rc == null || rc.out || rc.finishPlace != 0) return;
        rc.x = x1;
        rc.z = z1;
        rc.progress.track(x1, y1, z1);
        rc.progress.noteDirection();
        RaceProgress.Result res = rc.progress.move(x0, y0, z0, x1, y1, z1);
        if (res.kind() != RaceProgress.Kind.LAP) return;
        ServerLevel level = player.serverLevel();
        int now = level.getServer().getTickCount();
        int lapMs = (now - rc.lapStartTick) * TICK_MS;
        rc.lapStartTick = now;
        rc.lastLapMs = lapMs;
        rc.lapTimes.add(lapMs);
        rc.bestLapMs = rc.bestLapMs == 0 ? lapMs : Math.min(rc.bestLapMs, lapMs);
        if (rc.progress.laps() >= r.laps) {
            rc.finishPlace = ++r.finished;
            rc.finishMs = (now - r.goTick) * TICK_MS;
            if (r.firstFinishTick < 0) r.firstFinishTick = now;
            com.craftteamracing.pickup.ItemManager.onRaceFinished(player);
            if (player.getVehicle() instanceof KartEntity fk) fk.setEthereal(true);          // the finished kart touches nothing and nothing touches it
            if (rc.finishPlace <= 3 && podiumsReady(level, r)) toPodium(level, r, rc, rc.finishPlace);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
            for (Racer other : r.racers.values()) {
                ServerPlayer op = level.getServer().getPlayerList().getPlayer(other.id);
                if (op != null) op.sendSystemMessage(Component.translatable("message.craftteamracing.race.finished", rc.name, rc.finishPlace, fmt(rc.finishMs)));
            }
            sendOne(level, r, rc, now);
        } else {
            player.displayClientMessage(Component.translatable("message.craftteamracing.race.lap", rc.progress.laps() + 1, r.laps, fmt(lapMs)), true);
            // PlayLevel.c: OtherFX_Play(0x66) when the farthest-ahead human crosses the line without finishing (every lap); a racer who is not the first across this lap hears nothing
            boolean first = true;
            for (Racer other : r.racers.values()) if (other != rc && other.progress.laps() >= rc.progress.laps()) first = false;
            if (first) level.playSound(null, player.blockPosition(), com.craftteamracing.ModSounds.LAP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /** Racers in order: finishers by finish place, then the rest by progress round the course, ties by distance to the next ring. */
    private static List<Racer> ranking(Race r) {
        List<Racer> list = new ArrayList<>(r.racers.values());
        list.sort(Comparator
                .comparingInt((Racer a) -> a.out ? 1 : 0)
                .thenComparingInt(a -> a.finishPlace != 0 ? a.finishPlace : Integer.MAX_VALUE)
                .thenComparing(Comparator.comparingDouble((Racer a) -> -a.progress.continuous()))
                .thenComparingDouble(a -> a.progress.distanceToNextSq(a.x, a.z)));
        return list;
    }

    private static void sendAll(ServerLevel level, Race r, int now) {
        List<Racer> rank = ranking(r);
        for (Racer rc : r.racers.values()) {
            sendOne(level, r, rc, now, rank);
        }
    }

    private static void sendOne(ServerLevel level, Race r, Racer rc, int now) {
        sendOne(level, r, rc, now, ranking(r));
    }

    /** Where the podium places are while the ceremony of the race on this Start node's track is on (the group shot's scene, for a viewer at a console), or null when it is not. */
    public static PodiumScenePayload podiumView(ServerLevel level, long start) {
        Race r = RACES.get(new Key(level.dimension(), start));
        if (r == null || !r.podiumFilled) return null;
        List<PodiumScenePayload.Spot> spots = new ArrayList<>();
        for (int place = 1; place <= 3; place++) {
            RaceGrid.Slot s = podiumSlot(level, r, place);
            if (s != null) spots.add(new PodiumScenePayload.Spot(s.x(), s.y(), s.z(), s.yaw(), place));
        }
        return spots.isEmpty() ? null : new PodiumScenePayload(spots);
    }

    /**
     * What a viewer at a console with no racer to follow is shown: the countdown of a race that is gathering ("starting soon", with the karts counted in the starting area), the track's
     * cool-down after a race, or nothing (the screen is cleared).
     */
    public static RacePayload idleView(ServerLevel level, long start) {
        Key key = new Key(level.dimension(), start);
        Race r = RACES.get(key);
        if (r != null && r.phase == RacePayload.GATHER) {
            int in = inArea(level, BlockPos.of(start), r.manual).size();
            return new RacePayload(RacePayload.GATHER, r.ticks, 0, r.laps, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, in, List.of(), List.of(), List.of(), List.of(), false, false, false, false);
        }
        Integer end = COOLDOWN.get(key);
        int left = end == null ? 0 : end - level.getServer().getTickCount();
        return left > 0 ? cooldownPayload(left) : RacePayload.off();
    }

    /** What the racer's own screen is told, for a viewer who watches the race through that racer (a viewing console): null when there is no such racer. */
    public static RacePayload viewOf(ServerLevel level, long start, UUID racer) {
        Race r = RACES.get(new Key(level.dimension(), start));
        Racer rc = r == null ? null : r.racers.get(racer);
        if (rc == null || rc.out) return null;
        return payloadFor(r, rc, level.getServer().getTickCount(), ranking(r));
    }

    private static void sendOne(ServerLevel level, Race r, Racer rc, int now, List<Racer> rank) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(rc.id);
        if (p == null || rc.out) return;
        if (DEBUG.contains(rc.id) && (r.phase == RacePayload.RACING || r.phase == RacePayload.LIGHTS)) sendDebug(level, r, p, rc);
        PacketDistributor.sendToPlayer(p, payloadFor(r, rc, now, rank));
    }

    private static RacePayload payloadFor(Race r, Racer rc, int now, List<Racer> rank) {
        int place = rank.indexOf(rc) + 1;
        int timeMs = r.phase == RacePayload.RACING ? (now - r.goTick) * TICK_MS : 0;
        if (rc.finishPlace != 0) timeMs = rc.finishMs;
        boolean hold = r.phase == RacePayload.LIGHTS;
        int phase = rc.finishPlace != 0 ? RacePayload.DONE : r.phase;
        boolean racing = phase == RacePayload.RACING && !rc.podium;
        int lap = Math.min(r.laps, rc.progress.laps() + 1);
        return new RacePayload(phase, r.ticks, r.lastLights, r.laps, lap, place, r.racers.size(), timeMs, rc.lastLapMs,
                rc.bestLapMs, hold, rc.placeSeq, rc.slot.x(), rc.slot.y(), rc.slot.z(), rc.slot.yaw(), rc.finishPlace, 0,
                rank.stream().limit(4).map(o -> o.id).toList(), List.copyOf(rc.lapTimes),
                rank.stream().filter(o -> o.finishPlace == 0 && !o.podium).map(o -> o.id).toList(),          // a racer who has finished is not on the track bar
                rank.stream().filter(o -> o.finishPlace == 0 && !o.podium).map(o -> o.progress.barFraction()).toList(), rc.podium,
                racing && rc.progress.wrongWay(), racing && rc.progress.skipped(), false);
    }

    /** An id for the race the player is in (its track), or null. */
    public static String raceIdOf(UUID id) {
        Race r = BY_PLAYER.get(id);
        return r == null ? null : r.key.dimension().location() + "@" + r.key.start();
    }

    private static void end(MinecraftServer server, Race r, String reasonKey, boolean results) {
        RACES.remove(r.key);
        ChunkKeeper.releaseCourse(r.key.dimension().location() + "@" + r.key.start());
        if (RACES.isEmpty()) com.craftteamracing.physics.WorldScale.setTrack(0);
        ServerLevel level = server.getLevel(r.key.dimension());
        if (level != null) {
            powerAll(level, r, false);
            if (r.podiumFilled) COOLDOWN.put(r.key, server.getTickCount() + COOLDOWN_TICKS);
        }
        if (level != null) TrackData.get(level).clearTemporaryAll(r.key.start());          // a rule set set for one race is used up by it
        if (level != null) com.craftteamracing.pickup.MinePool.clearRace(r.key.dimension().location() + "@" + r.key.start(), level);
        List<Racer> rank = ranking(r);
        for (Racer rc : r.racers.values()) {
            BY_PLAYER.remove(rc.id);
            ServerPlayer p = server.getPlayerList().getPlayer(rc.id);
            if (p == null) continue;
            if (p.getVehicle() instanceof KartEntity ek) ek.setEthereal(false);
            PacketDistributor.sendToPlayer(p, RacePayload.off());
            PacketDistributor.sendToPlayer(p, new RulesPayload(com.craftteamracing.items.RuleSet.CTR));
            PacketDistributor.sendToPlayer(p, new MusicPayload(com.craftteamracing.MusicSheetData.DEFAULT));
            if (reasonKey != null) p.displayClientMessage(Component.translatable(reasonKey), true);
            if (results) {
                p.sendSystemMessage(Component.translatable("message.craftteamracing.race.results"));
                for (int i = 0; i < rank.size(); i++) {
                    Racer o = rank.get(i);
                    Component line = o.finishPlace != 0 ? Component.literal(i + 1 + ". ").append(o.name).append("  " + fmt(o.finishMs))
                            : Component.literal(i + 1 + ". ").append(o.name).append("  ").append(Component.translatable("message.craftteamracing.race.dnf"));
                    p.sendSystemMessage(line);
                }
            }
        }
        if (level != null) clearBystanders(level, r);
        if (level != null && r.racers.isEmpty()) {
            // gathering that was cancelled: tell the people who were watching the countdown it is off
            BlockPos start = BlockPos.of(r.key.start());
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(start.getX(), start.getY(), start.getZ()) < 64 * 64) {
                    PacketDistributor.sendToPlayer(p, RacePayload.off());
                    if (reasonKey != null) p.displayClientMessage(Component.translatable(reasonKey), true);
                }
            }
        }
    }

    public static String fmt(int ms) {
        return String.format("%d:%02d.%03d", ms / 60000, ms / 1000 % 60, ms % 1000);
    }

    /** Drop everything (the server stopped; the statics would outlive a singleplayer world). */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (Iterator<Race> it = RACES.values().iterator(); it.hasNext(); ) {
            it.next();
            it.remove();
        }
        BY_PLAYER.clear();
        INSIDE.clear();
        com.craftteamracing.pickup.MinePool.clearAll();
        ChunkKeeper.forgetAll();
    }
}
