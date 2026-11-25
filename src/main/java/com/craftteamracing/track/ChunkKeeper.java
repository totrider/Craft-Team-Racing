package com.craftteamracing.track;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.craftteamracing.CtrConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/**
 * Keeps the chunks a race needs loaded and running, with vanilla's own region tickets (what a thrown ender pearl or a nether portal uses), so the mod does not need a chunk-claiming
 * mod for its basics. Tickets are never saved: after a stop or a crash there is nothing left to clean up.
 * <ul>
 * <li><b>A weapon in flight or lying on the track</b> ({@link #keep}) holds the chunk it is in, and a few around it, for a short while; it renews that as long as it exists. Without it a missile or a warp orb
 * would freeze in the air at the edge of the loaded world, and a trap that nobody is near would vanish with its chunk (the weapons are not saved with it).</li>
 * <li><b>The course of a race</b> ({@link #holdCourse}): every chunk the nodes and the links between them pass through (and the chunks round them, running at block level) is held from the moment the
 * race gathers until it ends, so the pickup spots along the whole track keep their crates and the redstone of the track keeps working, whoever is near.</li>
 * </ul>
 */
public final class ChunkKeeper {
    private ChunkKeeper() {}

    /** A weapon's chunk is held for two seconds after the last renewal. */
    private static final TicketType<ChunkPos> WEAPON = TicketType.create("craftteamracing_weapon", Comparator.comparingLong(ChunkPos::toLong), 40);
    /** The course of a race, held until it is let go (the value is the race's id). */
    private static final TicketType<String> COURSE = TicketType.create("craftteamracing_course", String::compareTo);

    /** How far apart the points are that a link is sampled at, to find the chunks it passes through (blocks). */
    private static final double STEP = 8.0;

    private record Held(ServerLevel level, List<ChunkPos> chunks) {}

    private static final Map<String, Held> HELD = new HashMap<>();

    /**
     * Called from a weapon's tick: holds the chunk it is in. A radius of 3 runs the chunks round it (the ones it flies into) too, 2 only its own; a ticket is renewed every few ticks, not every one.
     */
    public static void keep(Entity entity, int radius) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 4 != 0 || !CtrConfig.KEEP_WEAPONS_LOADED.get()) return;
        ChunkPos at = entity.chunkPosition();
        level.getChunkSource().addRegionTicket(WEAPON, at, radius, at, true);
    }

    /** Holds the course of a race (see the class comment) under the race's id. */
    public static void holdCourse(ServerLevel level, String raceId, TrackGraph graph, long start) {
        if (!CtrConfig.KEEP_COURSE_LOADED.get()) return;
        releaseCourse(raceId);
        List<Long> podiums = new ArrayList<>();
        for (int place = 1; place <= 3; place++) {
            Long pos = graph.podiumPos(start, place);
            if (pos != null) podiums.add(pos);
        }
        Set<Long> chunks = courseChunks(graph.component(start), node -> {
            TrackGraph.Node n = graph.get(node);
            return n == null ? new long[0] : n.links.stream().mapToLong(Long::longValue).toArray();
        }, podiums);
        List<ChunkPos> held = new ArrayList<>();
        for (long c : chunks) {
            ChunkPos cp = new ChunkPos(c);
            level.getChunkSource().addRegionTicket(COURSE, cp, 2, raceId, true);
            held.add(cp);
        }
        HELD.put(raceId, new Held(level, held));
    }

    /**
     * The chunks (as {@link ChunkPos#asLong}) a course passes through: every node's, and every chunk a link between two nodes crosses (sampled along it), and the podium nodes'. The same on the server,
     * which holds them, and on a client (the debug view of what would be held).
     */
    public static Set<Long> courseChunks(java.util.Collection<Long> nodes, java.util.function.LongFunction<long[]> linksOf, java.util.Collection<Long> podiums) {
        Set<Long> chunks = new LinkedHashSet<>();
        for (long node : nodes) {
            chunks.add(ChunkPos.asLong(NodeGeometry.x(node) >> 4, NodeGeometry.z(node) >> 4));
            for (long other : linksOf.apply(node)) {
                double x0 = NodeGeometry.x(node) + 0.5;
                double z0 = NodeGeometry.z(node) + 0.5;
                double dx = NodeGeometry.x(other) + 0.5 - x0;
                double dz = NodeGeometry.z(other) + 0.5 - z0;
                int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / STEP));
                for (int i = 0; i <= steps; i++) chunks.add(ChunkPos.asLong(Mth.floor(x0 + dx * i / steps) >> 4, Mth.floor(z0 + dz * i / steps) >> 4));
            }
        }
        for (long pos : podiums) chunks.add(ChunkPos.asLong(NodeGeometry.x(pos) >> 4, NodeGeometry.z(pos) >> 4));
        return chunks;
    }

    /** The server stopped: its tickets went with it, so only the bookkeeping is left to drop. */
    static void forgetAll() {
        HELD.clear();
    }

    /** Lets the course of a race go. */
    public static void releaseCourse(String raceId) {
        Held h = HELD.remove(raceId);
        if (h == null) return;
        for (ChunkPos cp : h.chunks()) h.level().getChunkSource().removeRegionTicket(COURSE, cp, 2, raceId, true);
    }
}
