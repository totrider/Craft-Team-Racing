package com.craftteamracing.track;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One racer's progress round a recorded track (the main path of {@link TrackRoutes}), pure Java. The racer has to pass the rings of the
 * main path in order, or take a recorded branch instead of the stretch of main path it replaces (rings of the branch in order, then it
 * rejoins the main path at its far end, and the skipped main nodes count as passed). Passing the start ring again closes a lap.
 *
 * <p>Which way round a branch runs is decided by the main path: it is entered at the end from which the stretch of main path it replaces
 * (compared by length) lies forwards along the course, the way it was recorded breaking ties. A branch that runs past the start line (enters before it, rejoins after it) counts the lap when it rejoins.
 */
public final class RaceProgress {
    public enum Kind { NONE, NODE, LAP }

    public record Result(Kind kind, long node) {
        static final Result NONE = new Result(Kind.NONE, 0);
    }

    /** A branch seen from the direction of the course. */
    private record Fwd(int entryIdx, int exitIdx, long[] chain, long exit) {}

    private final TrackGraph graph;
    private final List<Long> main;
    private final int n;
    private final List<Fwd> branches = new ArrayList<>();

    private int at;                  // index in main of the last main node passed (0: the start line)
    private int laps;                // laps completed
    private Fwd current;             // the branch being driven, or null
    private int branchPos;           // next ring in current.chain; == chain.length means the exit is next

    public RaceProgress(TrackGraph graph, TrackRoutes routes) {
        this.graph = graph;
        this.main = routes.main();
        this.n = main.size() - 1;
        Map<Long, Integer> idx = new HashMap<>();
        for (int i = 0; i < n; i++) idx.putIfAbsent(main.get(i), i);
        for (TrackRoutes.Branch b : routes.branches()) {
            Integer from = idx.get(b.from());
            Integer to = idx.get(b.to());
            if (from == null || to == null) continue;
            // Which way round the branch runs: it is entered at the one of its two main nodes that is closer to the start along the main path, and rejoins at the other (a branch replaces a stretch of
            // the main path between them, and a stretch does not contain the start line), whichever way it was recorded. When one of them is the start node itself the branch either leaves
            // it or rejoins at it, and that is told by the length of the stretch it matches.
            boolean forwardWay;
            if (from.equals(to)) {
                forwardWay = true;
            } else if (from == 0 || to == 0) {
                double len = branchLength(b);
                int other = from == 0 ? to : from;
                // the stretch it replaces is the one it runs along: the rings' mean distance to the stretch from the start to the other node, against the one from it to the start
                // (a branch that runs beside the main path is told by where it lies); the lengths only decide when they lie about as near to both
                double toFirst = meanDistToStretch(b.nodes(), 0, other);
                double toSecond = meanDistToStretch(b.nodes(), other, n);
                boolean leavesStart;
                if (Math.abs(toFirst - toSecond) > 1.5) leavesStart = toFirst < toSecond;
                else leavesStart = Math.abs(arc(0, other) - len) <= Math.abs(arc(other, 0) - len);
                forwardWay = leavesStart == (from == 0);
            } else {
                forwardWay = from < to;
            }
            if (forwardWay) {
                branches.add(new Fwd(from, to, b.nodes(), b.to()));
            } else {
                long[] rev = new long[b.nodes().length];
                for (int i = 0; i < rev.length; i++) rev[i] = b.nodes()[rev.length - 1 - i];
                branches.add(new Fwd(to, from, rev, b.from()));
            }
        }
    }

    private static double dist(long a, long b) {
        double dx = NodeGeometry.x(a) - NodeGeometry.x(b);
        double dy = NodeGeometry.y(a) - NodeGeometry.y(b);
        double dz = NodeGeometry.z(a) - NodeGeometry.z(b);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The length of the main path from main node {@code i} forwards (round the lap) to main node {@code j}. */
    private double arc(int i, int j) {
        double sum = 0;
        for (int k = i; k != j; k = (k + 1) % n) sum += dist(main.get(k), main.get(k + 1));
        return sum;
    }

    /** The mean distance of some rings to the main path from main node {@code i} to main node {@code j} (j may be n, the closing start). */
    private double meanDistToStretch(long[] rings, int i, int j) {
        double sum = 0;
        for (long r : rings) {
            double px = NodeGeometry.x(r);
            double py = NodeGeometry.y(r);
            double pz = NodeGeometry.z(r);
            double best = Double.MAX_VALUE;
            for (int k = i; k < j; k++) {
                double ax = NodeGeometry.x(main.get(k));
                double ay = NodeGeometry.y(main.get(k));
                double az = NodeGeometry.z(main.get(k));
                double dx = NodeGeometry.x(main.get(k + 1)) - ax;
                double dy = NodeGeometry.y(main.get(k + 1)) - ay;
                double dz = NodeGeometry.z(main.get(k + 1)) - az;
                double len2 = dx * dx + dy * dy + dz * dz;
                double t = len2 > 0 ? Math.max(0.0, Math.min(1.0, ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / len2)) : 0.0;
                double ex = ax + dx * t - px;
                double ey = ay + dy * t - py;
                double ez = az + dz * t - pz;
                best = Math.min(best, Math.sqrt(ex * ex + ey * ey + ez * ez));
            }
            sum += best;
        }
        return rings.length == 0 ? 0 : sum / rings.length;
    }

    private static double branchLength(TrackRoutes.Branch b) {
        long[] nodes = b.nodes();
        double sum = dist(b.from(), nodes[0]) + dist(nodes[nodes.length - 1], b.to());
        for (int i = 1; i < nodes.length; i++) sum += dist(nodes[i - 1], nodes[i]);
        return sum;
    }

    public int laps() {
        return laps;
    }

    /** A branch as the course runs it: where it leaves the main path, where it rejoins, and its own rings in order. */
    public record BranchView(int entryIdx, int exitIdx, long[] chain) {}

    public List<BranchView> branchViews() {
        List<BranchView> out = new ArrayList<>();
        for (Fwd f : branches) out.add(new BranchView(f.entryIdx(), f.exitIdx(), f.chain()));
        return out;
    }

    /** The index in the main path where the branch being driven rejoins it, or -1 on the main path. */
    public int branchExitIdx() {
        return onBranch == null ? -1 : onBranch.exitIdx();
    }

    /** The rings of the branch being driven that have been passed (empty on the main path). */
    public long[] branchRings() {
        if (current != null) return java.util.Arrays.copyOf(current.chain(), Math.min(branchPos, current.chain().length));
        if (onBranch == null) return new long[0];
        // the rings of the branch the racer has got past, from where it leaves the main path (the racer may be driving it the wrong way round)
        long[] chain = onBranch.chain();
        double totalLen = dist(main.get(onBranch.entryIdx()), chain.length > 0 ? chain[0] : main.get(onBranch.exitIdx()));
        double[] at = new double[chain.length];
        for (int i = 0; i < chain.length; i++) {
            if (i > 0) totalLen += dist(chain[i - 1], chain[i]);
            at[i] = totalLen;
        }
        if (chain.length > 0) totalLen += dist(chain[chain.length - 1], main.get(onBranch.exitIdx()));
        int count = 0;
        for (int i = 0; i < chain.length; i++) if (totalLen > 0 && at[i] / totalLen <= branchT) count++;
        return java.util.Arrays.copyOf(chain, count);
    }

    /** The index in the main path of the last main ring passed (0: the start line). */
    public int mainIndex() {
        return at;
    }

    private double[] cum;
    private double total;
    /** Where the racer really is on the main path, whichever way they drive: the segment (main node trackIdx to the next) and how far along it (0 to 1). Unlike the rings passed, it goes backwards too. */
    private int trackIdx;
    private double trackT;

    /**
     * Follows the racer along the main path: the nearest point of the path, looked for round where they were last (the full path when they are far from it, after a respawn or a shortcut).
     * The count of rings passed only goes forwards (a ring counts when it is passed in order); this goes both ways, as the original's distance to the finish does.
     */
    public void track(double x, double y, double z) {
        double best = Double.MAX_VALUE;
        int bi = trackIdx;
        double bt = trackT;
        for (int pass = 0; pass < 2 && best > 30.0 * 30.0; pass++) {
            int from = pass == 0 ? -3 : 0;
            int to = pass == 0 ? 3 : n - 1;
            for (int d = from; d <= to; d++) {
                int i = Math.floorMod(pass == 0 ? trackIdx + d : d, n);
                double ax = NodeGeometry.x(main.get(i)) + 0.5;
                double ay = NodeGeometry.y(main.get(i)) + 0.5;
                double az = NodeGeometry.z(main.get(i)) + 0.5;
                double bx = NodeGeometry.x(main.get(i + 1)) + 0.5;
                double by = NodeGeometry.y(main.get(i + 1)) + 0.5;
                double bz = NodeGeometry.z(main.get(i + 1)) + 0.5;
                double dx = bx - ax;
                double dy = by - ay;
                double dz = bz - az;
                double len2 = dx * dx + dy * dy + dz * dz;
                double t = len2 > 0 ? Math.max(0.0, Math.min(1.0, ((x - ax) * dx + (y - ay) * dy + (z - az) * dz) / len2)) : 0.0;
                double px = ax + dx * t - x;
                double py = ay + dy * t - y;
                double pz = az + dz * t - z;
                double d2 = px * px + py * py + pz * pz;
                if (d2 < best) {
                    best = d2;
                    bi = i;
                    bt = t;
                }
            }
        }
        trackIdx = bi;
        trackT = bt;
        // the branch the racer is on, by where they are (not only by the rings passed: driving a shortcut backwards, from its far end, counts too): the nearest line of the course wins,
        // main path or branch, with a block of give so it does not flicker where they meet
        if (current != null && branchPos >= current.chain().length) {
            // all the rings of the shortcut are passed and the racer is back on the main path it replaces, nearer that than the shortcut: either way round covers the same ground, so the line
            // is the main path up to where they are (and goes back along it from there)
            boolean onStretch = Math.floorMod(trackIdx - current.entryIdx(), n) < Math.floorMod(current.exitIdx() - current.entryIdx(), n);
            if (onStretch && best < measure(current, x, y, z)[0]) {
                // a merge is a closed case: the shortcut's last ring and the main ring before its exit both lead to the exit. Whichever of the two lanes the racer is nearest, the line is at that
                // main ring (not at whichever main segment happens to be nearest where the lanes run side by side), and the exit is next.
                at = Math.floorMod(current.exitIdx() - 1, n);
                viaStretch = current;
                current = null;
                branchPos = 0;
                branchT = -1.0;
                via = null;
            }
        }
        Fwd pick = null;
        double pickShare = 0.0;
        if (current != null) {
            pick = current;
            pickShare = measure(current, x, y, z)[1];
        } else {
            double bestB = Double.MAX_VALUE;
            for (Fwd f : branches) {
                double[] m = measure(f, x, y, z);
                if (m[0] < bestB) {
                    bestB = m[0];
                    pick = f;
                    pickShare = m[1];
                }
            }
            if (pick != null) {
                // the nearest point is an end of the branch: that is a main node, where the racer is just as much on the main path (past the exit of a branch that runs beside the main path, say)
                if (pickShare <= 0.001 || pickShare >= 0.999) pick = null;
            }
            if (pick != null) {
                // a block of give for the line they are already on, so it does not flicker where they run side by side; the main path wins a tie
                double give = pick == onBranch ? -1.0 : 1.0;
                if (!(Math.sqrt(bestB) + give < Math.sqrt(best))) pick = null;
            }
        }
        onBranch = pick;
        branchT = pick == null ? -1.0 : pickShare;
        computePathPoint(x, y, z);
        // the distance round the race: each report adds the (shortest) change in the share of the lap, so crossing the line either way, or cutting past rings, never mixes up the laps
        double f = lapFraction(0, 0);
        if (!unwrappedSet) {
            unwrapped = f > 0.5 ? f - 1.0 : f;          // the grid is behind the start line
            unwrappedSet = true;
        } else {
            double d = f - lastFraction;
            if (d > 0.5) d -= 1.0;
            else if (d < -0.5) d += 1.0;
            unwrapped += d;
        }
        lastFraction = f;
        syncLine(x, y, z);
    }

    private double unwrapped;
    private double lastFraction;
    private boolean unwrappedSet;

    /** Where on its lap the racer is for the HUD's track bar, 0 to 1: never behind the start of the bar before the first lap, and the end of the lap, not the start, just behind the line afterwards. */
    public float barFraction() {
        double f = unwrapped - laps;
        if (laps == 0 && f < 0) return 0.0F;
        f = f - Math.floor(f);
        return (float) f;
    }

    private double[] pathSeg = new double[6];
    private double[] pathPoint = new double[3];

    /** The line of the course the racer is nearest to (a segment between two rings) and the point on it: the debug view draws them. */
    private void computePathPoint(double x, double y, double z) {
        long a;
        long b;
        double t;
        if (onBranch != null && branchT >= 0) {
            long[] chain = onBranch.chain();
            long[] pts = new long[chain.length + 2];
            pts[0] = main.get(onBranch.entryIdx());
            System.arraycopy(chain, 0, pts, 1, chain.length);
            pts[pts.length - 1] = main.get(onBranch.exitIdx());
            double[] cl = new double[pts.length];
            for (int i = 1; i < pts.length; i++) cl[i] = cl[i - 1] + dist(pts[i - 1], pts[i]);
            double target = branchT * cl[pts.length - 1];
            int k = 0;
            while (k + 2 < pts.length && cl[k + 1] < target) k++;
            a = pts[k];
            b = pts[k + 1];
            t = cl[k + 1] > cl[k] ? Math.max(0.0, Math.min(1.0, (target - cl[k]) / (cl[k + 1] - cl[k]))) : 0.0;
        } else {
            a = main.get(trackIdx);
            b = main.get(trackIdx + 1);
            t = trackT;
        }
        pathSeg = new double[] { NodeGeometry.x(a) + 0.5, NodeGeometry.y(a) + 0.5, NodeGeometry.z(a) + 0.5, NodeGeometry.x(b) + 0.5, NodeGeometry.y(b) + 0.5, NodeGeometry.z(b) + 0.5 };
        pathPoint = new double[] { pathSeg[0] + (pathSeg[3] - pathSeg[0]) * t, pathSeg[1] + (pathSeg[4] - pathSeg[1]) * t, pathSeg[2] + (pathSeg[5] - pathSeg[2]) * t };
    }

    /** What the debug view shows of this racer's progress: the rings of the line (passed), the last one, the ones that count next, the line the racer is nearest to, and a line of text. */
    public record Debug(java.util.List<Long> rings, java.util.Set<Long> passed, long head, java.util.Set<Long> next, double[] seg, double[] point, String info) {}

    public Debug debug() {
        java.util.LinkedHashSet<Long> rings = new java.util.LinkedHashSet<>();
        for (int i = 0; i < n; i++) rings.add(main.get(i));
        for (Fwd f : branches) for (long c : f.chain()) rings.add(c);
        java.util.LinkedHashSet<Long> passed = new java.util.LinkedHashSet<>();
        long head;
        if (current != null) {
            for (int i = 0; i <= at; i++) passed.add(main.get(i));
            for (int i = 0; i < branchPos; i++) passed.add(current.chain()[i]);
            head = current.chain()[branchPos - 1];
        } else {
            for (int i = 0; i <= at; i++) {
                if (via != null && i > via.entryIdx() && i < via.exitIdx()) continue;          // the stretch the shortcut replaced was not driven
                passed.add(main.get(i));
            }
            if (via != null) for (long c : via.chain()) passed.add(c);
            head = main.get(at);
        }
        if (behind && at == 0 && current == null) passed.remove(main.get(0));
        String where = current != null ? "on a shortcut, " + branchPos + " of " + current.chain().length + " rings" : via != null ? "out of a shortcut" : "on the main path";
        String info = "line at ring " + (current != null ? current.entryIdx() : at) + " of " + n + " (" + where + "), lap " + (laps + 1) + (behind ? ", behind the start line" : "")
                + "; nearest line: " + (onBranch != null ? "shortcut " + onBranch.entryIdx() + "->" + onBranch.exitIdx() : "main") + (skipped() ? "; SKIPPED" : "") + (wrongWay() ? "; WRONG WAY" : "") + String.format("; distance %.3f (segment %d + %.2f)", unwrapped, trackIdx, trackT);
        return new Debug(new java.util.ArrayList<>(rings), passed, head, expected(), pathSeg, pathPoint, info);
    }

    /** The branch the racer is on by position (see {@link #track}), or null on the main path. */
    private Fwd onBranch;

    /** How far along the branch being driven the racer is, 0 (where it leaves the main path) to 1 (where it rejoins), by the length of the branch's own line. */
    private double branchT = -1.0;

    /** The squared distance from a point to a branch's line (entry node, its rings, exit node) and how far along that line (by length, 0 to 1) the nearest point is. */
    private double[] measure(Fwd branch, double x, double y, double z) {
        long[] chain = branch.chain();
        long[] pts = new long[chain.length + 2];
        pts[0] = main.get(branch.entryIdx());
        System.arraycopy(chain, 0, pts, 1, chain.length);
        pts[pts.length - 1] = main.get(branch.exitIdx());
        double[] cumLen = new double[pts.length];
        for (int i = 1; i < pts.length; i++) cumLen[i] = cumLen[i - 1] + dist(pts[i - 1], pts[i]);
        double totalLen = cumLen[pts.length - 1];
        if (totalLen <= 0) return new double[] { Double.MAX_VALUE, 0.0 };
        double best = Double.MAX_VALUE;
        double along = 0.0;
        for (int i = 0; i + 1 < pts.length; i++) {
            double ax = NodeGeometry.x(pts[i]) + 0.5;
            double ay = NodeGeometry.y(pts[i]) + 0.5;
            double az = NodeGeometry.z(pts[i]) + 0.5;
            double dx = NodeGeometry.x(pts[i + 1]) + 0.5 - ax;
            double dy = NodeGeometry.y(pts[i + 1]) + 0.5 - ay;
            double dz = NodeGeometry.z(pts[i + 1]) + 0.5 - az;
            double len2 = dx * dx + dy * dy + dz * dz;
            double t = len2 > 0 ? Math.max(0.0, Math.min(1.0, ((x - ax) * dx + (y - ay) * dy + (z - az) * dz) / len2)) : 0.0;
            double px = ax + dx * t - x;
            double py = ay + dy * t - y;
            double pz = az + dz * t - z;
            double d2 = px * px + py * py + pz * pz;
            if (d2 < best) {
                best = d2;
                along = cumLen[i] + (cumLen[i + 1] - cumLen[i]) * t;
            }
        }
        return new double[] { best, along / totalLen };
    }

    /** The index in the main path the racer is at: where they really are on it when on the main path (see {@link #track}), else the ring they left it at. */
    public int courseIdx() {
        return onBranch == null ? trackIdx : onBranch.entryIdx();
    }

    /**
     * Where the racer is along one lap, 0 (the start line) to 1, by the length of the main path (a branch, by how many of its rings are passed, over the stretch of main path it replaces). For the
     * HUD's track bar. {@code x, z} is the racer's place (the share of the way to the next ring).
     */
    public float lapFraction(double x, double z) {
        if (cum == null) {
            cum = new double[n + 1];
            for (int i = 0; i < n; i++) cum[i + 1] = cum[i] + dist(main.get(i), main.get(i + 1));
            total = cum[n];
        }
        if (total <= 0) return 0.0F;
        double pos;
        if (onBranch != null) {
            int e = onBranch.entryIdx();
            int xi = onBranch.exitIdx();
            double a = cum[e];
            double b = xi > e ? cum[xi] : total + cum[xi];
            double share = branchT >= 0 ? branchT : current != null ? branchPos / (double) (current.chain().length + 1) : 0.0;          // the branch's own length, remapped onto the stretch of main path it replaces
            pos = a + (b - a) * share;
        } else {
            pos = cum[trackIdx] + dist(main.get(trackIdx), main.get(trackIdx + 1)) * trackT;
        }
        return (float) ((pos / total) % 1.0);
    }

    /**
     * How far round the race the racer is, laps plus the share of the lap, from where they really are on the course (so driving backwards loses ground, and a racer who has passed another one on the
     * way is ahead of them). The lap count only changes at the start line, so a racer just behind the line before it has been crossed (the grid, or one who drove back over it) is a little
     * under a whole lap, not nearly one more.
     */
    public double continuous() {
        return unwrappedSet ? unwrapped : laps;
    }

    /** Rings passed so far, as a number that only grows: laps times the nodes of a lap, plus where on this lap, branches counted pro rata. */
    public double progress() {
        double p = (double) laps * n + at;
        if (current != null) {
            int span = Math.max(1, Math.floorMod(current.exitIdx() - current.entryIdx(), n));
            p += (double) branchPos / (current.chain().length + 1) * span;
        }
        return p;
    }

    /** The last ring passed: where a respawn puts you (the main node you were last at, or the last ring of the branch you are on). */
    public long lastPassed() {
        return current != null && branchPos >= 1 ? current.chain()[branchPos - 1] : main.get(at);
    }

    /** The rings that count next. */
    public Set<Long> expected() {
        Set<Long> out = new LinkedHashSet<>();
        if (current != null) {
            out.add(branchPos < current.chain().length ? current.chain()[branchPos] : current.exit());
            // the rings after the ring the shortcut leaves at are all one fork: backed out of the shortcut, the next main ring counts too (the line goes back to the fork)
            if (branchPos < current.chain().length && at + 1 < n) out.add(main.get(at + 1));
            return out;
        }
        out.add(main.get(at + 1));
        for (Fwd f : branches) {
            if (f.entryIdx() == at) out.add(f.chain()[0]);
            // the main ring right after a fork was passed (not by coming out of a shortcut): the first ring of the shortcut counts too
            else if (f.entryIdx() + 1 == at && via == null) out.add(f.chain()[0]);
        }
        return out;
    }

    /** The squared distance (blocks, in the ground plane) to the nearest ring that counts next: breaks ties between racers on the same ring. */
    public double distanceToNextSq(double x, double z) {
        double best = Double.MAX_VALUE;
        for (long e : expected()) {
            double dx = NodeGeometry.x(e) + 0.5 - x;
            double dz = NodeGeometry.z(e) + 0.5 - z;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    /** Behind the start line: the grid, or the line was crossed backwards. Crossing it forwards then only clears this (the original's ACTION_BEHIND_START_LINE): no lap, and none to take back. */
    private boolean behind = true;
    /** Set when the main ring the line ends at was reached by the exit of this branch: backwards, the line goes back through the branch's rings. */
    private Fwd via;

    // ---- warnings: going the wrong way, and a checkpoint that was skipped ----
    private double lastUnwrapped;
    private boolean lastUnwrappedSet;
    private int backwardsReports;
    /** The change in the distance round the course in the last position report: positive going the way the course runs, negative the other way. */
    private double lastDelta;

    /** Called with each position report after {@link #track}: counts the reports in a row in which the racer got further back along the course. */
    void noteDirection() {
        if (!unwrappedSet) return;
        if (lastUnwrappedSet) {
            double d = unwrapped - lastUnwrapped;
            lastDelta = d;
            if (d < -0.0002) backwardsReports = Math.min(backwardsReports + 1, 60);
            else if (d > 0.0002) backwardsReports = 0;
            else backwardsReports = Math.max(0, backwardsReports - 1);
        }
        lastUnwrapped = unwrapped;
        lastUnwrappedSet = true;
        skippedReports = skippedNow() ? Math.min(skippedReports + 1, 100) : 0;
        boolean w = wrongWay();
        boolean k = skipped();
        if ((w && !loggedWrong) || (k && !loggedSkipped)) {
            com.craftteamracing.CraftTeamRacingMod.LOGGER.info("Race tracker: wrong way {}, skipped {}: line ends at main ring {} (branch {} at {}), racer at segment {} + {}, on branch {}, distance {}",
                    w, k, at, current != null, branchPos, trackIdx, String.format("%.2f", trackT), onBranch == null ? "none" : onBranch.entryIdx() + "->" + onBranch.exitIdx(), String.format("%.3f", unwrapped));
        }
        loggedWrong = w;
        loggedSkipped = k;
    }

    private boolean loggedWrong;
    private boolean loggedSkipped;

    /** Driving the wrong way round the course (further back along it for a second or so). */
    public boolean wrongWay() {
        return backwardsReports >= 20;
    }

    /**
     * A checkpoint was skipped: the racer is further along the course than the rings they have passed in order (they are past the next ring, or on a shortcut they did not enter at its
     * start), so nothing counts until they go back to the ring they missed. Not while driving the wrong way, which is the other warning.
     */
    public boolean skipped() {
        return skippedReports >= 10;
    }

    private int skippedReports;

    /** The condition behind {@link #skipped}, for this report; the warning only shows once it has held for half a second, so a momentary change of the line the racer is nearest to does not flash it. */
    private boolean skippedNow() {
        if (!unwrappedSet || wrongWay() || backwardsReports > 0 || current != null) return false;
        if (onBranch != null) {
            // on a shortcut the line did not enter at its start: not when the line came out of this very shortcut (the racer is still nearest its end), or is at either end of it
            if (onBranch == via) return false;
            // (the ring after the fork it leaves at is the same fork, and so is the ring before the merge it rejoins at)
            return onBranch.entryIdx() != at && onBranch.exitIdx() != at && onBranch.entryIdx() + 1 != at && Math.floorMod(onBranch.exitIdx() - 1, n) != at;
        }
        int ahead = Math.floorMod(trackIdx - at, n);
        return ahead < n / 2 && (ahead >= 2 || (ahead == 1 && trackT > 0.15));
    }

    /** Whether a ring is on the line of the course the racer is really on (the main path, or the branch they are on): the rings of the other one at a fork do not count. */
    private boolean onLineOf(long c) {
        if (current != null) return true;
        boolean fork = false;
        for (Fwd f : branches) {
            if (f.entryIdx() != at) continue;
            fork = true;
            if (f.chain()[0] == c) return onBranch == f;
        }
        return !fork || onBranch == null;          // the next main ring: preferred unless the racer is on a shortcut that leaves at the ring they are at
    }

    /** Whether passing this expected ring closes a lap (the start ring after the last one, or the exit of a branch that runs over the start line). */
    private boolean closesLap(long c) {
        if (current != null) return c == current.exit() && branchPos >= current.chain().length && current.exitIdx() < current.entryIdx();
        return c == main.get(at + 1) && at + 1 == n;
    }

    private static final int FORWARD = 1;
    private static final int START_LINE = 3;

    /**
     * One kart move. The racer's checkpoint line is one string of rings from the start. The next ring of it counts when the racer touches it, whichever way they go through it, as long as it is
     * on the line of the course they are really on (so two rings whose boxes overlap at a fork cannot be mixed up); a ring that is not next does nothing, so one skipped stays skipped. Only the
     * start line has a direction: a lap counts when it is crossed the way the course runs (the distance round the course going up), and crossing it while behind it only clears that. The line
     * goes back by where the racer is, not by what they touch, see {@link #syncLine}. At most one thing happens per move (the first ring touched).
     */
    public Result move(double x0, double y0, double z0, double x1, double y1, double z1) {
        boolean forwards = lastDelta > 1.0E-6;
        long hit = 0;
        int role = 0;
        double hitT = Double.MAX_VALUE;
        boolean hitPreferred = false;
        for (long c : expected()) {
            TrackGraph.Node node = graph.get(c);
            if (node == null) continue;
            double t = NodeGeometry.touch(c, node.rotation, node.ext, x0, y0, z0, x1, y1, z1);
            if (t < 0 || (closesLap(c) && !forwards)) continue;
            // touching a ring counts whatever line the racer is nearest to (a shortcut leaving the ring on the side they came in at does not make it miss); only when the boxes of two rings
            // that could count next are both touched in the one move does the line they are on tell which it was
            boolean preferred = onLineOf(c);
            if (role == FORWARD && ((hitPreferred && !preferred) || (preferred == hitPreferred && t >= hitT))) continue;
            hitT = t;
            hit = c;
            role = FORWARD;
            hitPreferred = preferred;
        }
        // the start line: crossing it forwards while behind it only clears that
        if (behind && at == 0 && current == null) {
            long start = main.get(0);
            TrackGraph.Node node = graph.get(start);
            if (node != null) {
                double t = NodeGeometry.touch(start, node.rotation, node.ext, x0, y0, z0, x1, y1, z1);
                if (t >= 0 && t < hitT && forwards) {
                    hitT = t;
                    hit = start;
                    role = START_LINE;
                }
            }
        }
        if (role == 0) return Result.NONE;
        if (role == START_LINE) {
            behind = false;
            return new Result(Kind.NODE, hit);
        }
        return reach(hit);
    }

    /** How far from a ring's centre a racer can still be inside its box, plus a little (blocks): behind a ring by more than this, the racer has not passed it. */
    private double reachOf(long ring) {
        TrackGraph.Node node = graph.get(ring);
        double e = 0;
        if (node != null) for (float f : node.ext) e = Math.max(e, f);
        return e * 1.5 + 1.5;
    }

    private static double distTo(long ring, double x, double y, double z) {
        double dx = NodeGeometry.x(ring) + 0.5 - x;
        double dy = NodeGeometry.y(ring) + 0.5 - y;
        double dz = NodeGeometry.z(ring) + 0.5 - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The length of a branch's own line (entry node, its rings, exit node), and how far along it each ring is (the length to it). */
    private double[] branchRingLengths(Fwd f) {
        long[] chain = f.chain();
        double[] along = new double[chain.length + 1];
        long prev = main.get(f.entryIdx());
        double sum = 0;
        for (int i = 0; i < chain.length; i++) {
            sum += dist(prev, chain[i]);
            along[i] = sum;
            prev = chain[i];
        }
        sum += dist(prev, main.get(f.exitIdx()));
        along[chain.length] = sum;
        return along;
    }

    /**
     * The line of rings goes back with the racer: when they are behind the last ring of it by more than the ring's box (driving the course the wrong way), that ring is unmarked, and so on back,
     * through a shortcut's rings if the line came out of one and out of its start. Called with each report, after {@link #track}. Nothing here depends on which way a ring was touched.
     */
    /**
     * The shortcut whose rings were all passed and whose merge the racer is now past, on the main path (see {@link #track}): the line is at the main ring before the shortcut's exit, and while the
     * racer is on the stretch the shortcut replaces it is not pulled back by where they are (the lane they took there may run beside the main rings: nothing was skipped).
     */
    private Fwd viaStretch;

    private void syncLine(double x, double y, double z) {
        if (viaStretch != null) {
            boolean within = Math.floorMod(trackIdx - viaStretch.entryIdx(), n) < Math.floorMod(viaStretch.exitIdx() - viaStretch.entryIdx(), n);
            if (current != null || !within) viaStretch = null;
        }
        if (current == null && via != null && onBranch == via && branchT >= 0) {
            // the line ends at the exit of a shortcut and the racer is back inside it
            double[] along = branchRingLengths(via);
            double total = along[along.length - 1];
            if (total > 0 && (1.0 - branchT) * total > reachOf(main.get(via.exitIdx()))) {
                current = via;
                branchPos = via.chain().length;
                at = via.entryIdx();
                via = null;
            }
        }
        if (current == null && via != null && onBranch != via && unwrappedSet && total > 0) {
            // the line came out of a shortcut at its merge and the racer is now on the main path, behind the merge, on the stretch the shortcut replaces: either way round covers the same ground,
            // so the line is the main path's up to the merge, and goes back along it from there
            boolean wrapped = via.exitIdx() < via.entryIdx();
            double exitShare = wrapped ? 0.0 : cum[via.exitIdx()] / total;
            boolean onStretch = onBranch == null && Math.floorMod(trackIdx - via.entryIdx(), n) < Math.floorMod(via.exitIdx() - via.entryIdx(), n);
            if (onStretch && unwrapped - laps < exitShare - reachOf(main.get(via.exitIdx())) / total) via = null;
        }
        if (current != null) {
            double[] along = branchRingLengths(current);
            double total = along[along.length - 1];
            while (branchPos > 0 && branchT >= 0 && total > 0 && branchT * total < along[branchPos - 1] - reachOf(current.chain()[branchPos - 1])) branchPos--;
            if (branchPos <= 0) {
                current = null;                          // back out of the start of the shortcut: the line ends at the ring it leaves the main path at
                branchPos = 0;
                branchT = -1.0;
            }
            return;
        }
        if (!unwrappedSet || total <= 0) return;
        // the racer's share of the lap (a shortcut's own length mapped onto the stretch it replaces) against the share of the ring the line ends at: behind it by more than its box, the line goes
        // back, whether the racer is on the main path or on a shortcut (driving a shortcut backwards from its far end, then forwards again, must find the line where they now are)
        double f = unwrapped - laps;
        if (via != null && f < cum[via.entryIdx()] / total - reachOf(main.get(via.entryIdx())) / total) {
            at = via.entryIdx();                     // back before the start of the shortcut the line came out of: those rings are unmarked
            via = null;
        }
        if (via == null && viaStretch == null) {
            // the share of the lap alone can be wrong (it is a running sum): the line only goes back when the nearest segment of the course says the racer is behind the ring as well (or they are on a shortcut)
            while (at > 0 && f < cum[at] / total - reachOf(main.get(at)) / total && (onBranch != null || (Math.floorMod(at - trackIdx, n) >= 1 && Math.floorMod(at - trackIdx, n) < n / 2))) at--;
            if (at == 0 && !behind && f < -reachOf(main.get(0)) / total) behind = true;          // back before the start line
        }
    }

    /** Applies "the racer passed the ring of {@code node}" (one of {@link #expected()}). */
    public Result reach(long node) {
        viaStretch = null;
        // a lap only counts near the start line: the share of the lap the racer is really at (see track()) has to be close to a whole one, whatever the rings say
        boolean closes = current != null ? node == current.exit() && branchPos >= current.chain().length && current.exitIdx() < current.entryIdx() : node == main.get(at + 1) && at + 1 == n;
        if (closes && unwrappedSet) {
            double frac = lastFraction;
            if (frac > 0.25 && frac < 0.75) {
                com.craftteamracing.CraftTeamRacingMod.LOGGER.info("Race: lap not counted, the racer is at {} of the lap (ring at {}, branch {})", frac, at, current != null);
                return Result.NONE;
            }
        }
        if (current != null && branchPos < current.chain().length && at + 1 < n && node == main.get(at + 1) && node != current.chain()[branchPos]) {
            // backed out of the shortcut and on along the main path: the line goes back to the fork, then takes the main ring
            current = null;
            branchPos = 0;
            branchT = -1.0;
        }
        if (current != null) {
            if (branchPos < current.chain().length) {
                if (node != current.chain()[branchPos]) return Result.NONE;
                branchPos++;
                return new Result(Kind.NODE, node);
            }
            if (node != current.exit()) return Result.NONE;
            boolean wrapped = current.exitIdx() < current.entryIdx();
            at = current.exitIdx();
            via = current;
            current = null;
            behind = false;
            if (wrapped) {
                laps++;
                return new Result(Kind.LAP, node);
            }
            return new Result(Kind.NODE, node);
        }
        if (node == main.get(at + 1)) {
            at++;
            via = null;
            behind = false;
            if (at == n) {
                at = 0;
                laps++;
                return new Result(Kind.LAP, node);
            }
            return new Result(Kind.NODE, node);
        }
        for (Fwd f : branches) {
            if ((f.entryIdx() == at || (f.entryIdx() + 1 == at && via == null)) && f.chain()[0] == node) {
                at = f.entryIdx();                       // the line goes back to the fork
                current = f;
                via = null;
                branchT = -1.0;
                branchPos = 1;
                return new Result(Kind.NODE, node);
            }
        }
        return Result.NONE;
    }
}
