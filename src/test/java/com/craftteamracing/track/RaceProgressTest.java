package com.craftteamracing.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.craftteamracing.track.RaceProgress.Kind;

/** Laps, branches and the grid. Uses the same square course as the test drive tests. */
class RaceProgressTest {
    static final long S = TestDriveTest.S;
    static final long A = TestDriveTest.A;
    static final long B = TestDriveTest.B;
    static final long C = TestDriveTest.C;

    /** A recorded square, S A B C S, and a shortcut X between A and C that was driven too. */
    static RaceProgress course(boolean withShortcut, TrackRoutes[] out) {
        TrackGraph g = TestDriveTest.square();
        if (withShortcut) {
            g.put(TestDriveTest.X, TrackGraph.Kind.CHECKPOINT, 0);
            g.toggleLink(A, TestDriveTest.X);
            g.toggleLink(TestDriveTest.X, C);
        }
        TrackRoutes r = new TrackRoutes();
        TestDrive d = new TestDrive(g, r, S);
        d.reach(A);
        d.reach(B);
        d.reach(C);
        d.reach(S);
        if (withShortcut) {
            d.reach(TestDriveTest.X);
            d.reach(C);
        }
        out[0] = r;
        return new RaceProgress(g, r);
    }

    @Test
    void ringsInOrderCloseALap() {
        RaceProgress p = course(false, new TrackRoutes[1]);
        assertEquals(Set.of(A), p.expected());
        assertEquals(Kind.NODE, p.reach(A).kind());
        assertEquals(Set.of(B), p.expected());
        p.reach(B);
        p.reach(C);
        assertEquals(Kind.LAP, p.reach(S).kind());
        assertEquals(1, p.laps());
        assertEquals(Set.of(A), p.expected(), "round again");
    }

    @Test
    void aRingOutOfOrderDoesNothing() {
        RaceProgress p = course(false, new TrackRoutes[1]);
        assertEquals(Kind.NONE, p.reach(C).kind(), "skipping A and B");
        assertEquals(0.0, p.progress());
    }

    @Test
    void theShortcutCountsInsteadOfTheMainStretch() {
        RaceProgress p = course(true, new TrackRoutes[1]);
        p.reach(A);
        assertEquals(Set.of(B, TestDriveTest.X), p.expected(), "at A the main road or the shortcut");
        assertEquals(Kind.NODE, p.reach(TestDriveTest.X).kind());
        assertEquals(Set.of(C), p.expected(), "the shortcut rejoins at C");
        assertEquals(Kind.NODE, p.reach(C).kind());
        assertEquals(Kind.LAP, p.reach(S).kind(), "B was skipped by the shortcut");
        assertEquals(1, p.laps());
    }

    @Test
    void progressGrowsAndTheShortcutIsPaidPro() {
        RaceProgress p = course(true, new TrackRoutes[1]);
        double last = p.progress();
        for (long n : new long[] { A, TestDriveTest.X, C, S }) {
            p.reach(n);
            assertTrue(p.progress() > last, "after " + n);
            last = p.progress();
        }
    }

    @Test
    void aKartDrivingThroughTheRingsFinishesALap() {
        RaceProgress p = course(false, new TrackRoutes[1]);
        double[][] route = { { 2, 64.5, 0.5 }, { 38, 64.5, 0.5 }, { 43, 64.5, 0.5 }, { 40.5, 64.5, 38 }, { 40.5, 64.5, 43 },
                { 3, 64.5, 40.5 }, { -3, 64.5, 40.5 }, { 0.5, 64.5, 3 }, { 0.5, 64.5, -3 } };
        int laps = 0;
        p.track(route[0][0], route[0][1], route[0][2]);
        p.noteDirection();
        for (int i = 1; i < route.length; i++) {
            double[] a = route[i - 1];
            double[] b = route[i];
            p.track(b[0], b[1], b[2]);          // as the race manager does with each report: where the racer is on the course, and which way they are going on it
            p.noteDirection();
            if (p.move(a[0], a[1], a[2], b[0], b[1], b[2]).kind() == Kind.LAP) laps++;
        }
        assertEquals(1, laps);
    }

    @Test
    void aRespawnGoesToTheLastRingPassed() {
        RaceProgress p = course(true, new TrackRoutes[1]);
        assertEquals(S, p.lastPassed(), "before any ring: the start");
        p.reach(A);
        assertEquals(A, p.lastPassed());
        p.reach(TestDriveTest.X);
        assertEquals(TestDriveTest.X, p.lastPassed(), "on the shortcut: its last ring");
        p.reach(C);
        assertEquals(C, p.lastPassed());
    }

    @Test
    void theLightsComeOnOneByOneAndStayUntilGo() {
        // DotLights.c: red lamps at 2880, 1920 and 960 ms left, the green (GO) at 0
        assertEquals(0, RaceGrid.lightsLit(RaceGrid.LIGHTS_TICKS + 5));
        assertEquals(0, RaceGrid.lightsLit(58));
        assertEquals(1, RaceGrid.lightsLit(57));
        assertEquals(1, RaceGrid.lightsLit(39));
        assertEquals(2, RaceGrid.lightsLit(38));
        assertEquals(2, RaceGrid.lightsLit(20));
        assertEquals(3, RaceGrid.lightsLit(19));
        assertEquals(3, RaceGrid.lightsLit(1));
    }

    @Test
    void theGridIsBehindTheStartNodeAndFacesTheWayItDoes() {
        long start = NodeGeometry.pack(100, 64, 100);
        RaceGrid.Slot pole = RaceGrid.slot(start, 12, 0);      // the node faces east: the grid is to its west
        assertTrue(pole.x() < 100.5, "behind: west of the node");
        assertEquals(270.0F, pole.yaw());
        RaceGrid.Slot second = RaceGrid.slot(start, 12, 1);
        assertEquals(pole.x(), second.x(), 0.3, "the second kart is beside the first, in the same row");
        assertEquals(3.77, Math.abs(second.z() - pole.z()), 0.02, "3.8 blocks along the row");
        RaceGrid.Slot fifth = RaceGrid.slot(start, 12, 4);
        assertEquals(5.0, pole.x() - fifth.x(), 0.2, "the second row is about 5 blocks further back");
    }

    @Test
    void theGridIsTheOriginalTwoRowsOfFourAndRepeatsBehind() {
        long start = NodeGeometry.pack(100, 64, 100);
        RaceGrid.Slot pole = RaceGrid.slot(start, 12, 0);
        RaceGrid.Slot last = RaceGrid.slot(start, 12, 3);
        assertEquals(11.31, Math.abs(pole.z() - last.z()), 0.02, "four karts a row, 3.8 blocks apart");
        // the 9th racer starts the pattern again behind the 8th
        RaceGrid.Slot ninth = RaceGrid.slot(start, 12, 8);
        assertTrue(ninth.x() < RaceGrid.slot(start, 12, 7).x(), "behind the last of the first eight");
        assertEquals(pole.z(), ninth.z(), 1e-9);
        // the whole pattern fits the gathering box
        for (int i = 0; i < 8; i++) {
            RaceGrid.Slot s = RaceGrid.slot(start, 12, i);
            assertTrue(GatherZone.contains(start, 12, s.x(), s.y() + 0.5, s.z()), "slot " + i + " is inside the gathering area");
        }
    }

    /** A branch is entered at the one of its two main nodes that is closer to the start along the main path, whichever way it was recorded. */
    @Test
    void aBranchIsEnteredFromTheEndCloserToTheStart() {
        long s = TestDriveTest.at(0, 0);
        long a = TestDriveTest.at(100, 0);
        long b = TestDriveTest.at(100, 40);
        long c = TestDriveTest.at(0, 20);
        long y = TestDriveTest.at(50, -10);
        TrackGraph g = new TrackGraph();
        g.put(s, TrackGraph.Kind.START, 12);
        g.put(a, TrackGraph.Kind.CHECKPOINT, 0);
        g.put(b, TrackGraph.Kind.CHECKPOINT, 4);
        g.put(c, TrackGraph.Kind.CHECKPOINT, 8);
        g.put(y, TrackGraph.Kind.CHECKPOINT, 12);
        g.toggleLink(s, a);
        g.toggleLink(a, b);
        g.toggleLink(b, c);
        g.toggleLink(c, s);
        g.toggleLink(a, y);
        g.toggleLink(y, c);
        for (boolean recordedBackwards : new boolean[] { false, true }) {
            TrackRoutes r = new TrackRoutes();
            r.setMain(java.util.List.of(s, a, b, c, s));
            r.addBranch(recordedBackwards ? new TrackRoutes.Branch(c, a, new long[] { y }) : new TrackRoutes.Branch(a, c, new long[] { y }));
            RaceProgress p = new RaceProgress(g, r);
            p.reach(a);
            assertTrue(p.expected().contains(y), "at A (the end nearer the start) the branch is one of the ways on");
            assertEquals(Kind.NODE, p.reach(y).kind());
            assertEquals(Kind.NODE, p.reach(c).kind(), "it rejoins at C");
            assertEquals(0, p.laps());
            assertEquals(Kind.LAP, p.reach(s).kind());
        }
    }

    private static long at(int x, int y, int z) {
        return NodeGeometry.pack(x, y, z);
    }

    /**
     * A track from a real world: the main path S, 1 ... 7 and back to S, and a shortcut that leaves at ring 3, passes two rings and runs into S beside main ring 7 to S. Driving the shortcut
     * to its end and then (a jump sideways) on to the main path beside it, the line must end at main ring 7 (the one before the exit) without a skipped checkpoint, wherever the main path's nearest segment is along the way.
     */
    @Test
    void aShortcutMergingIntoTheStartLeavesTheLineAtTheRingBeforeIt() {
        long[] m = { at(-729, -56, 624), at(-733, -59, 557), at(-795, -57, 534), at(-805, -59, 580), at(-804, -60, 635), at(-812, -60, 697), at(-754, -60, 708), at(-722, -57, 676) };
        long b1 = at(-779, -56, 627);
        long b2 = at(-751, -52, 669);
        TrackGraph g = new TrackGraph();
        g.put(m[0], TrackGraph.Kind.START, 0);
        for (int i = 1; i < 8; i++) g.put(m[i], TrackGraph.Kind.CHECKPOINT, 0);
        g.put(b1, TrackGraph.Kind.CHECKPOINT, 0);
        g.put(b2, TrackGraph.Kind.CHECKPOINT, 0);
        for (int i = 0; i < 8; i++) g.toggleLink(m[i], m[(i + 1) % 8]);
        g.toggleLink(m[3], b1);
        g.toggleLink(b1, b2);
        g.toggleLink(b2, m[0]);
        TrackRoutes r = new TrackRoutes();
        TestDrive d = new TestDrive(g, r, m[0]);
        for (int i = 1; i < 8; i++) d.reach(m[i]);
        d.reach(m[0]);
        d.reach(b1);
        d.reach(b2);
        d.reach(m[0]);
        RaceProgress p = new RaceProgress(g, r);
        p.reach(m[1]);
        p.reach(m[2]);
        p.reach(m[3]);
        double[][] way = { { -805, -59, 580 }, { -779, -56, 627 }, { -751, -52, 669 }, { -735, -57, 672 }, { -727, -57, 643 }, { -729, -56, 624 } };
        double px = way[0][0] + 0.5;
        double py = way[0][1] + 0.5;
        double pz = way[0][2] + 0.5;
        for (int leg = 1; leg < way.length; leg++) {
            double len = Math.sqrt(Math.pow(way[leg][0] - way[leg - 1][0], 2) + Math.pow(way[leg][2] - way[leg - 1][2], 2));
            int steps = (int) Math.ceil(len);
            for (int s = 1; s <= steps; s++) {
                double u = s / (double) steps;
                double x = way[leg - 1][0] + (way[leg][0] - way[leg - 1][0]) * u + 0.5;
                double y = way[leg - 1][1] + (way[leg][1] - way[leg - 1][1]) * u + 0.5;
                double z = way[leg - 1][2] + (way[leg][2] - way[leg - 1][2]) * u + 0.5;
                p.track(x, y, z);
                p.move(px, py, pz, x, y, z);
                p.noteDirection();
                px = x;
                py = y;
                pz = z;
                assertTrue(!p.skipped(), "skipped at leg " + leg + " step " + s + ": " + p.debug().info());
            }
        }
        assertEquals(1, p.laps(), p.debug().info());
    }
}
