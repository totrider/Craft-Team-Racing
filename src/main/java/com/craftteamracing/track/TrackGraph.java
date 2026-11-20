package com.craftteamracing.track;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The track node graph: start and checkpoint nodes, and the links between them. Pure Java (no Minecraft imports): nodes are keyed by
 * a long (a packed block position), so the rules can be unit-tested. See docs/DESIGN_TRACKS.md.
 *
 * <p>Links are undirected: you link nodes without choosing a direction, the direction of travel comes from the test drive later.
 * A connected group of nodes is one track; a track has at most one start node.
 */
public final class TrackGraph {
    /** PODIUM_1 to PODIUM_3: where the racers who finish first, second and third are put (see {@link Node#podiumStart}); they are not part of the course. */
    public enum Kind {
        START, CHECKPOINT, PODIUM_1, PODIUM_2, PODIUM_3;

        public boolean isPodium() {
            return this == PODIUM_1 || this == PODIUM_2 || this == PODIUM_3;
        }

        /** 1 to 3 for a podium node, else 0. */
        public int place() {
            return this == PODIUM_1 ? 1 : this == PODIUM_2 ? 2 : this == PODIUM_3 ? 3 : 0;
        }
    }

    public static final class Node {
        public final Kind kind;
        /** 0-15 in 22.5 degree steps (the standing sign rotation); the direction the node faces. */
        public final int rotation;
        /** The box a kart has to touch to pass this node, see {@link NodeGeometry}: six extents in blocks, adjusted with the Track Tool. */
        public final float[] ext = NodeGeometry.DEFAULT_EXTENTS.clone();
        final Set<Long> links = new LinkedHashSet<>();
        /** For a podium node: the Start node it is hooked up to (a podium node has no links to checkpoints, and nothing links to it), or null. */
        Long podiumStart;

        Node(Kind kind, int rotation) {
            this.kind = kind;
            this.rotation = rotation & 15;
            if (kind.isPodium()) {
                java.util.Arrays.fill(ext, PODIUM_EXTENT);
                ext[NodeGeometry.UP] = 2.5F;          // the box stands on the floor of the node's block: 0.5 below the node's middle, 2.5 above it
                ext[NodeGeometry.DOWN] = 0.5F;
            }
        }

        public Set<Long> links() {
            if (kind.isPodium()) return podiumStart == null ? Collections.emptySet() : Collections.singleton(podiumStart);
            return Collections.unmodifiableSet(links);
        }
    }

    public enum LinkResult { LINKED, UNLINKED, SAME_NODE, MISSING, TWO_STARTS }

    /** What a node's track looks like right now: shown to the player when they link. */
    public record Summary(int nodes, int starts, int checkpoints, boolean loopClosed) {}

    private final Map<Long, Node> nodes = new LinkedHashMap<>();
    private int version;

    /** Bumped on every change: clients holding the tool are re-sent the graph when it differs. */
    public int version() {
        return version;
    }

    public Map<Long, Node> nodes() {
        return Collections.unmodifiableMap(nodes);
    }

    /**
     * The squared distance from a point to the track the node {@code any} belongs to, taken as the lines that link its nodes (a node with no
     * link counts as a point): a rough racing line, so a place beside a long straight is as near as one beside a node.
     */
    public double distSqToTrack(long any, double x, double y, double z) {
        double best = Double.MAX_VALUE;
        for (long p : component(any)) {
            Node n = nodes.get(p);
            if (n == null) continue;
            double ax = NodeGeometry.x(p) + 0.5;
            double ay = NodeGeometry.y(p) + 0.5;
            double az = NodeGeometry.z(p) + 0.5;
            best = Math.min(best, NodeGeometry.segmentDistSq(x, y, z, ax, ay, az, ax, ay, az));
            for (long q : n.links()) {
                best = Math.min(best, NodeGeometry.segmentDistSq(x, y, z, ax, ay, az,
                        NodeGeometry.x(q) + 0.5, NodeGeometry.y(q) + 0.5, NodeGeometry.z(q) + 0.5));
            }
        }
        return best;
    }

    public Node get(long pos) {
        return nodes.get(pos);
    }

    public boolean contains(long pos) {
        return nodes.containsKey(pos);
    }

    /** Adds a node (a placed node block). Replaces one already there, keeping its links. */
    public Node put(long pos, Kind kind, int rotation) {
        Node old = nodes.get(pos);
        Node n = new Node(kind, rotation);
        if (old != null) {
            if (!kind.isPodium()) System.arraycopy(old.ext, 0, n.ext, 0, n.ext.length);
            if (!kind.isPodium()) n.links.addAll(old.links);
            else n.podiumStart = old.podiumStart;
        }
        nodes.put(pos, n);
        version++;
        return n;
    }

    /** Loads a saved node (no version bump); its links may point to nodes loaded later, see {@link #pruneLinks()}. */
    public void restore(long pos, Kind kind, int rotation, float[] ext, long[] links) {
        Node n = new Node(kind, rotation);
        if (ext != null && ext.length == n.ext.length && !kind.isPodium()) {          // a podium node keeps its fixed size
            for (int i = 0; i < ext.length; i++) {
                n.ext[i] = Math.max(NodeGeometry.MIN_EXTENT, Math.min(NodeGeometry.MAX_EXTENT, ext[i]));
            }
        }
        if (kind.isPodium()) {
            if (links.length > 0) n.podiumStart = links[0];
        } else {
            for (long l : links) n.links.add(l);
        }
        nodes.put(pos, n);
    }

    /** After loading: drops links to nodes that are gone, and makes every link two-way. */
    public void pruneLinks() {
        for (Map.Entry<Long, Node> e : nodes.entrySet()) {
            Node n = e.getValue();
            n.links.removeIf(l -> !nodes.containsKey(l) || l.equals(e.getKey()) || nodes.get(l).kind.isPodium());
            if (n.podiumStart != null && (!nodes.containsKey(n.podiumStart) || nodes.get(n.podiumStart).kind != Kind.START)) n.podiumStart = null;
        }
        for (Map.Entry<Long, Node> e : nodes.entrySet()) {
            for (long l : new ArrayList<>(e.getValue().links)) nodes.get(l).links.add(e.getKey());
        }
    }

    /** Moves one side of a node's box by {@code delta} blocks (clamped); returns the new extent, or -1 if there is no such node. */
    public float resize(long pos, int side, float delta) {
        Node n = nodes.get(pos);
        if (n == null || n.kind.isPodium() || side < 0 || side >= n.ext.length) return -1;
        n.ext[side] = Math.max(NodeGeometry.MIN_EXTENT, Math.min(NodeGeometry.MAX_EXTENT, n.ext[side] + delta));
        version++;
        return n.ext[side];
    }

    /** Removes a node (a broken node block) and every link to it. */
    public void remove(long pos) {
        Node n = nodes.remove(pos);
        if (n == null) return;
        for (Node o : nodes.values()) if (o.podiumStart != null && o.podiumStart == pos) o.podiumStart = null;
        for (long other : n.links) {
            Node o = nodes.get(other);
            if (o != null) o.links.remove(pos);
        }
        version++;
    }

    /** Links two nodes, or unlinks them if they already are. A track may not hold two start nodes. */
    public LinkResult toggleLink(long a, long b) {
        if (a == b) return LinkResult.SAME_NODE;
        Node na = nodes.get(a);
        Node nb = nodes.get(b);
        if (na == null || nb == null) return LinkResult.MISSING;
        if (na.links.contains(b)) {
            na.links.remove(b);
            nb.links.remove(a);
            version++;
            return LinkResult.UNLINKED;
        }
        if (countStarts(component(a)) > 0 && countStarts(component(b)) > 0 && !component(a).contains(b)) {
            return LinkResult.TWO_STARTS;
        }
        na.links.add(b);
        nb.links.add(a);
        version++;
        return LinkResult.LINKED;
    }

    /** A podium node's box is 3 x 3 x 3 blocks, standing on the floor of the node's block (it only marks a place, and is where the confetti is thrown from) and is not adjustable. */
    public static final float PODIUM_EXTENT = 1.5F;

    public enum PodiumResult { HOOKED, UNHOOKED, MISSING, NOT_START }

    /** Hooks a podium node up to a Start node (or unhooks it, if it already is): a Start node has at most one node for each place, a new one takes the place of the old. */
    public PodiumResult togglePodium(long podium, long start) {
        Node p = nodes.get(podium);
        Node s = nodes.get(start);
        if (p == null || s == null || !p.kind.isPodium()) return PodiumResult.MISSING;
        if (s.kind != Kind.START) return PodiumResult.NOT_START;
        version++;
        if (p.podiumStart != null && p.podiumStart == start) {
            p.podiumStart = null;
            return PodiumResult.UNHOOKED;
        }
        for (Node o : nodes.values()) if (o.kind == p.kind && o.podiumStart != null && o.podiumStart == start) o.podiumStart = null;
        p.podiumStart = start;
        return PodiumResult.HOOKED;
    }

    /** The position of the podium node for {@code place} (1 to 3) that is hooked up to the Start node, or null. */
    public Long podiumPos(long start, int place) {
        for (Map.Entry<Long, Node> e : nodes.entrySet()) {
            Node n = e.getValue();
            if (n.kind.place() == place && n.podiumStart != null && n.podiumStart == start) return e.getKey();
        }
        return null;
    }

    /** Whether the node at {@code pos} may become {@code kind}: a track may only have one start, so a checkpoint cannot turn into a second one. */
    public boolean canBecome(long pos, Kind kind) {
        if (kind != Kind.START) return true;
        for (long p : component(pos)) {
            if (p != pos && nodes.get(p).kind == Kind.START) return false;
        }
        return true;
    }

    /** Every node reachable from {@code pos} through links (including it). */
    public Set<Long> component(long pos) {
        Set<Long> seen = new LinkedHashSet<>();
        if (!nodes.containsKey(pos)) return seen;
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(pos);
        seen.add(pos);
        while (!queue.isEmpty()) {
            Node n = nodes.get(queue.poll());
            if (n == null) continue;
            for (long l : n.links) {
                if (seen.add(l)) queue.add(l);
            }
        }
        return seen;
    }

    private int countStarts(Set<Long> comp) {
        int c = 0;
        for (long p : comp) {
            Node n = nodes.get(p);
            if (n != null && n.kind == Kind.START) c++;
        }
        return c;
    }

    /** The start node of the track {@code pos} belongs to, or null if it has none. */
    public Long startOf(long pos) {
        for (long p : component(pos)) {
            if (nodes.get(p).kind == Kind.START) return p;
        }
        return null;
    }

    /** True if the start node lies on a loop: some link of it leads back to it without using that same link. */
    public boolean loopClosed(long start) {
        Node s = nodes.get(start);
        if (s == null || s.links.size() < 2) return false;
        for (long first : s.links) {
            // can we get from `first` back to `start` without crossing the link start-first?
            Set<Long> seen = new HashSet<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(first);
            seen.add(first);
            while (!queue.isEmpty()) {
                long cur = queue.poll();
                Node n = nodes.get(cur);
                if (n == null) continue;
                for (long l : n.links) {
                    if (cur == first && l == start) continue;       // not back along the same link
                    if (l == start) return true;
                    if (seen.add(l)) queue.add(l);
                }
            }
        }
        return false;
    }

    public Summary summary(long pos) {
        Set<Long> comp = component(pos);
        int starts = 0;
        int checkpoints = 0;
        Long start = null;
        for (long p : comp) {
            if (nodes.get(p).kind == Kind.START) {
                starts++;
                start = p;
            } else {
                checkpoints++;
            }
        }
        return new Summary(comp.size(), starts, checkpoints, start != null && loopClosed(start));
    }

    /** The nodes of every track, for listing: each connected group once, as a list. */
    public List<Set<Long>> components() {
        Set<Long> done = new HashSet<>();
        List<Set<Long>> out = new ArrayList<>();
        for (long p : nodes.keySet()) {
            if (done.contains(p)) continue;
            Set<Long> comp = component(p);
            done.addAll(comp);
            out.add(comp);
        }
        return out;
    }
}
