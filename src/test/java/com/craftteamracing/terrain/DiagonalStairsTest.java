package com.craftteamracing.terrain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.craftteamracing.physics.KartClass;
import com.craftteamracing.physics.KartInput;
import com.craftteamracing.physics.KartSim;
import com.craftteamracing.physics.V3;

/**
 * A corner staircase of full blocks (every block one higher than the one before it along x and along z) climbs at 45 degrees along an axis but at 54.7 degrees along its diagonal
 * (two blocks up per 1.41 across). The kart must be able to drive up it from any heading.
 */
class DiagonalStairsTest {
    private static final double LU = TerrainFieldTest.LU;

    private static TerrainCollider corner(double rise) {
        return new TerrainCollider(new TerrainField(TerrainFieldTest.world((x, z) -> Math.max(0.0, Math.floor((x + z - 39) * rise)))), LU);
    }

    private static KartSim climb(int heading, double startX, double startZ, int frames) {
        TerrainCollider c = corner(1.0);
        KartSim k = new KartSim(KartClass.BALANCED, new V3(startX * LU, 0, startZ * LU), heading);
        TerrainFieldTest.drive(k, c, frames, new KartInput(0, true, false, false, false, false));
        return k;
    }

    @Test
    void alongAnAxisTheKartClimbsTheCornerStaircase() {
        KartSim k = climb(1024, 12, 20.5, 260);                          // +x: one block up per block
        assertTrue(k.position().y > 6 * LU, "x " + k.position().x / LU + " y " + k.position().y / LU);
    }

    @Test
    void alongTheDiagonalTheKartClimbsTheCornerStaircase() {
        KartSim k = climb(512, 18, 18, 260);                             // +x and +z: two blocks up per 1.41 across
        assertTrue(k.position().y > 6 * LU, "x " + k.position().x / LU + " z " + k.position().z / LU + " y " + k.position().y / LU + " speed " + k.speed());
    }

    @Test
    void theKartClimbsTheCornerStaircaseFromAnyHeading() {
        StringBuilder out = new StringBuilder();
        int fails = 0;
        for (int heading : new int[] { 512, 600, 700, 800, 900, 1024, 1500, 1600, 2560, 3000, 3584 }) {
            for (double off : new double[] { 0.0, 0.3, 0.6 }) {
                double dx = Math.sin(heading * Math.PI * 2 / 4096.0);
                double dz = Math.cos(heading * Math.PI * 2 / 4096.0);
                // start 14 blocks before the foot of the wedge (the line x + z = 39), on the way the kart faces
                double sx = 20 + off, sz = 20 + off * 0.7;
                double t = 14.0 / Math.max(0.2, dx + dz);
                if (dx + dz < 0.2) continue;                               // heading away from the rise
                KartSim k = climb(heading, sx - dx * t, sz - dz * t, 240);
                boolean up = k.position().y > 4 * LU;
                if (!up) {
                    fails++;
                    out.append(String.format("heading %d off %.1f: x %.1f z %.1f y %.1f speed %d%n", heading, off, k.position().x / LU, k.position().z / LU, k.position().y / LU, (int) k.speed()));
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(0, fails, "headings that did not get up: " + out);
    }

    private static KartSim driveOver(java.util.function.BiFunction<Integer, Integer, Double> height, int heading, double sx, double sz, int frames) {
        TerrainCollider c = new TerrainCollider(new TerrainField(TerrainFieldTest.world(height)), LU);
        KartSim k = new KartSim(KartClass.BALANCED, new V3(sx * LU, 0, sz * LU), heading);
        TerrainFieldTest.drive(k, c, frames, new KartInput(0, true, false, false, false, false));
        return k;
    }

    @Test
    void aStaircaseWithOneBlockTreadsAndOneBlockRisersIsClimbed() {
        KartSim k = driveOver((x, z) -> Math.max(0.0, x - 19.0), 1024, 8, 5.5, 300);               // 45 degrees, no tread to rest on
        assertTrue(k.position().y > 5 * LU, "x " + k.position().x / LU + " y " + k.position().y / LU);
    }

    @Test
    void anInnerCornerOfTwoStaircasesIsClimbedOnTheDiagonal() {
        KartSim k = driveOver((x, z) -> Math.max(0.0, Math.max(x, z) - 19.0), 512, 10, 10, 320);   // two staircases meeting at an inner corner
        assertTrue(k.position().y > 5 * LU, "x " + k.position().x / LU + " z " + k.position().z / LU + " y " + k.position().y / LU);
    }

    @Test
    void anInnerCornerOfTwoStaircasesIsClimbedHeadOn() {
        KartSim k = driveOver((x, z) -> Math.max(0.0, Math.max(x, z) - 19.0), 1024, 8, 12.5, 320);
        assertTrue(k.position().y > 5 * LU, "x " + k.position().x / LU + " z " + k.position().z / LU + " y " + k.position().y / LU);
    }

    @Test
    void aStaircaseIsClimbedFromAStandstillAtItsFoot() {
        KartSim k = driveOver((x, z) -> Math.max(0.0, x - 19.0), 1024, 17.6, 5.5, 240);
        assertTrue(k.position().y > 5 * LU, "x " + k.position().x / LU + " y " + k.position().y / LU);
    }

    @Test
    void anInnerCornerIsClimbedFromAStandstillAtItsFoot() {
        KartSim k = driveOver((x, z) -> Math.max(0.0, Math.max(x, z) - 19.0), 512, 17.4, 17.4, 260);
        assertTrue(k.position().y > 5 * LU, "x " + k.position().x / LU + " z " + k.position().z / LU + " y " + k.position().y / LU);
    }

    @Test
    void anInnerCornerIsClimbedFromAStandstillAtItsFootAtAnyAngle() {
        StringBuilder out = new StringBuilder();
        int fails = 0;
        for (int heading : new int[] { 300, 512, 700, 900, 1024, 1250 }) {
            KartSim k = driveOver((x, z) -> Math.max(0.0, Math.max(x, z) - 19.0), heading, 17.4, 17.4, 260);
            if (k.position().y <= 5 * LU) {
                fails++;
                out.append(String.format("heading %d: x %.1f z %.1f y %.1f; ", heading, k.position().x / LU, k.position().z / LU, k.position().y / LU));
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(0, fails, out.toString());
    }

    @Test
    void aStaircaseIsSeenFurtherUpThanOneStepFromItsFoot() {
        TerrainField f = new TerrainField(TerrainFieldTest.world((x, z) -> Math.max(0.0, x - 19.0)));
        f.prepare(18.5, 0.0, 5.5, 0.0);                                         // standing at the foot: only the first tread is within a step of the kart
        assertTrue(!Double.isNaN(f.surface(20.5, 5.5)), "the second tread is ground, reached by a step from the first");
        assertTrue(!Double.isNaN(f.surface(21.5, 5.5)), "and the third");
        assertTrue(f.forEachWallBoxCount(18.5, 5.5, 8.0) == 0, "no tread of it is a wall");
    }

    @Test
    void aTwoBlockWallIsStillAWallFromItsFoot() {
        TerrainField f = new TerrainField(TerrainFieldTest.world((x, z) -> x >= 20 ? 2.0 : 0.0));
        f.prepare(18.5, 0.0, 5.5, 0.0);
        assertTrue(Double.isNaN(f.surface(21.5, 5.5)), "no step reaches the top of a two block wall");
        assertTrue(f.forEachWallBoxCount(19.5, 5.5, 8.0) > 0, "it is a wall");
    }

    /** The build from the test world: heights above the ground by block, rows z = 160 to 176, columns x = -1306 to -1294. */
    private static final String[] WEDGE = {
            "0000000000000",
            "0000000000000",
            "0000000000000",
            "0000000000000",
            "0000001000000",
            "0000002100000",
            "0000543210000",
            "0000554321000",
            "0000555432100",
            "0000555543210",
            "0000555554000",
            "0000555555000",
            "0000555555000",
            "0000000000000",
            "0000000000000",
            "0000000000000",
            "0000000000000" };

    private static double wedge(int x, int z) {
        int r = z - 160;
        int c = x - (-1306);
        if (r < 0 || r >= WEDGE.length || c < 0 || c >= WEDGE[r].length()) return 0.0;
        return WEDGE[r].charAt(c) - '0';
    }

    @Test
    void theRealWedgeIsClimbedOnTheDiagonalFromTheFront() {
        // the build from the test world: a slope that rises along the diagonal, climbed from the front across a spread of angles and aims
        StringBuilder out = new StringBuilder();
        int fails = 0;
        for (int heading : new int[] { 3300, 3400, 3500, 3584, 3700 }) {
            double dx = Math.sin(heading * Math.PI * 2 / 4096.0);
            double dz = Math.cos(heading * Math.PI * 2 / 4096.0);
            for (double aim : new double[] { -1300.5, -1300.0, -1299.5, -1299.0, -1298.5 }) {
                TerrainCollider c = new TerrainCollider(new TerrainField(TerrainFieldTest.world(DiagonalStairsTest::wedge)), LU);
                KartSim k = new KartSim(KartClass.BALANCED, new V3((aim - dx * 7) * LU, 0, (168.5 - dz * 7) * LU), heading);
                double top = 0;
                for (int i = 0; i < 140; i++) {
                    c.prepare(k.position(), k.velocity().y / 256.0);
                    k.tick(c, new KartInput(0, true, false, false, false, false));
                    top = Math.max(top, k.position().y / LU);
                }
                if (top < 2.0) {
                    fails++;
                    out.append(String.format("heading %d aim %.1f highest %.1f; ", heading, aim, top));
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(0, fails, out.toString());
    }

    /** Every polished andesite block of the build in the test world, as {x, y, z} (the ground is flat with its top at y = 64). */
    private static final int[][] BUILD = { {-1309,64,166},{-1309,65,166},{-1309,66,166},{-1309,67,166},{-1309,67,167},{-1309,67,168},{-1309,67,169},{-1309,67,170},{-1309,68,166},{-1309,68,167},{-1309,68,168},{-1309,68,169},{-1309,68,170},{-1308,64,166},{-1308,65,166},{-1308,66,166},{-1308,67,166},{-1308,67,167},{-1308,67,168},{-1308,67,169},{-1308,67,170},{-1308,68,166},{-1308,68,167},{-1308,68,168},{-1308,68,169},{-1308,68,170},{-1307,64,166},{-1307,65,166},{-1307,66,166},{-1307,67,166},{-1307,67,167},{-1307,67,168},{-1307,67,169},{-1307,67,170},{-1307,68,166},{-1307,68,167},{-1307,68,168},{-1307,68,169},{-1307,68,170},{-1306,64,166},{-1306,65,166},{-1306,66,166},{-1306,67,166},{-1306,67,167},{-1306,67,168},{-1306,67,169},{-1306,67,170},{-1306,68,166},{-1306,68,167},{-1306,68,168},{-1306,68,169},{-1306,68,170},{-1305,64,166},{-1305,65,166},{-1305,66,166},{-1305,67,166},{-1305,67,167},{-1305,67,168},{-1305,67,169},{-1305,67,170},{-1305,68,166},{-1305,68,167},{-1305,68,168},{-1305,68,169},{-1305,68,170},{-1304,64,166},{-1304,65,166},{-1304,66,166},{-1304,67,166},{-1304,67,167},{-1304,67,168},{-1304,67,169},{-1304,67,170},{-1304,68,166},{-1304,68,167},{-1304,68,168},{-1304,68,169},{-1304,68,170},{-1303,64,166},{-1303,65,166},{-1303,66,166},{-1303,67,166},{-1303,67,167},{-1303,67,168},{-1303,67,169},{-1303,67,170},{-1303,68,166},{-1303,68,167},{-1303,68,168},{-1303,68,169},{-1303,68,170},{-1302,64,166},{-1302,64,167},{-1302,64,168},{-1302,64,169},{-1302,64,170},{-1302,64,171},{-1302,64,172},{-1302,65,166},{-1302,65,167},{-1302,65,168},{-1302,65,169},{-1302,65,170},{-1302,65,171},{-1302,65,172},{-1302,66,166},{-1302,66,167},{-1302,66,168},{-1302,66,169},{-1302,66,170},{-1302,66,171},{-1302,66,172},{-1302,67,166},{-1302,67,167},{-1302,67,168},{-1302,67,169},{-1302,67,170},{-1302,67,171},{-1302,67,172},{-1302,68,166},{-1302,68,167},{-1302,68,168},{-1302,68,169},{-1302,68,170},{-1302,68,171},{-1302,68,172},{-1301,64,166},{-1301,64,167},{-1301,64,168},{-1301,64,169},{-1301,64,170},{-1301,64,171},{-1301,64,172},{-1301,65,166},{-1301,65,167},{-1301,65,168},{-1301,65,169},{-1301,65,170},{-1301,65,171},{-1301,65,172},{-1301,66,166},{-1301,66,167},{-1301,66,168},{-1301,66,169},{-1301,66,170},{-1301,66,171},{-1301,66,172},{-1301,67,166},{-1301,67,167},{-1301,67,168},{-1301,67,169},{-1301,67,170},{-1301,67,171},{-1301,67,172},{-1301,68,167},{-1301,68,168},{-1301,68,169},{-1301,68,170},{-1301,68,171},{-1301,68,172},{-1300,64,164},{-1300,64,165},{-1300,64,166},{-1300,64,167},{-1300,64,168},{-1300,64,169},{-1300,64,170},{-1300,64,171},{-1300,64,172},{-1300,65,165},{-1300,65,166},{-1300,65,167},{-1300,65,168},{-1300,65,169},{-1300,65,170},{-1300,65,171},{-1300,65,172},{-1300,66,166},{-1300,66,167},{-1300,66,168},{-1300,66,169},{-1300,66,170},{-1300,66,171},{-1300,66,172},{-1300,67,167},{-1300,67,168},{-1300,67,169},{-1300,67,170},{-1300,67,171},{-1300,67,172},{-1300,68,168},{-1300,68,169},{-1300,68,170},{-1300,68,171},{-1300,68,172},{-1299,64,165},{-1299,64,166},{-1299,64,167},{-1299,64,168},{-1299,64,169},{-1299,64,170},{-1299,64,171},{-1299,64,172},{-1299,65,166},{-1299,65,167},{-1299,65,168},{-1299,65,169},{-1299,65,170},{-1299,65,171},{-1299,65,172},{-1299,66,167},{-1299,66,168},{-1299,66,169},{-1299,66,170},{-1299,66,171},{-1299,66,172},{-1299,67,168},{-1299,67,169},{-1299,67,170},{-1299,67,171},{-1299,67,172},{-1299,68,169},{-1299,68,170},{-1299,68,171},{-1299,68,172},{-1298,64,166},{-1298,64,167},{-1298,64,168},{-1298,64,169},{-1298,64,170},{-1298,64,171},{-1298,64,172},{-1298,65,167},{-1298,65,168},{-1298,65,169},{-1298,65,170},{-1298,65,171},{-1298,65,172},{-1298,66,168},{-1298,66,169},{-1298,66,170},{-1298,66,171},{-1298,66,172},{-1298,67,169},{-1298,67,170},{-1298,67,171},{-1298,67,172},{-1298,68,170},{-1298,68,171},{-1298,68,172},{-1297,64,167},{-1297,64,168},{-1297,64,169},{-1297,64,170},{-1297,64,171},{-1297,64,172},{-1297,65,168},{-1297,65,169},{-1297,65,170},{-1297,65,171},{-1297,65,172},{-1297,66,169},{-1297,66,170},{-1297,66,171},{-1297,66,172},{-1297,67,170},{-1297,67,171},{-1297,67,172},{-1297,68,171},{-1297,68,172},{-1296,64,168},{-1296,64,169},{-1296,65,169},{-1295,64,169} };

    /** The build as the game's block source gives it: a full-cube box for every block over flat ground. */
    private static BlockSource realBuild() {
        java.util.Set<Long> set = new java.util.HashSet<>();
        for (int[] b : BUILD) set.add(((long) b[0] << 40) ^ ((long) b[1] << 20) ^ (b[2] & 0xFFFFFL));
        return new BlockSource() {
            @Override
            public void boxes(double x0, double y0, double z0, double x1, double y1, double z1, BoxVisitor v) {
                for (int bx = (int) Math.floor(x0); bx < Math.ceil(x1); bx++) {
                    for (int bz = (int) Math.floor(z0); bz < Math.ceil(z1); bz++) {
                        for (int gy = Math.max(40, (int) Math.floor(y0)); gy < 64; gy++) v.box(bx, gy, bz, bx + 1, gy + 1, bz + 1);        // the ground, one box a block as the game gives it
                        for (int y = 64; y < 72; y++) {
                            if (set.contains(((long) bx << 40) ^ ((long) y << 20) ^ (bz & 0xFFFFFL))) v.box(bx, y, bz, bx + 1, y + 1, bz + 1);
                        }
                    }
                }
            }

            @Override
            public int surface(int bx, int by, int bz) {
                return Surface.ASPHALT;
            }
        };
    }

    @Test
    void aKartSunkIntoTheGroundStillSeesTheFirstStepAsAStep() {
        int reference = -1;
        for (double cy : new double[] { 63.4, 63.6, 63.82, 64.0, 64.3 }) {
            TerrainField f = new TerrainField(realBuild());
            f.prepare(-1297.5, cy, 165.5, 0.0);
            int walls = f.forEachWallBoxCount(-1297.5, 165.5, 3.0);
            if (reference < 0) reference = walls;
            org.junit.jupiter.api.Assertions.assertEquals(reference, walls, "walls near the kart with its feet at y " + cy);
            assertTrue(!Double.isNaN(f.surface(-1298.5, 166.5)), "the slope is ground with its feet at y " + cy);
        }
    }

    @Test
    void exploreTheRealBuildFromTheFront() {
        StringBuilder out = new StringBuilder();
        int fails = 0;
        int total = 0;
        for (int heading : new int[] { 3300, 3450, 3584, 3700, 3840 }) {
            double dx = Math.sin(heading * Math.PI * 2 / 4096.0);
            double dz = Math.cos(heading * Math.PI * 2 / 4096.0);
            for (double aimX = -1301.5; aimX <= -1296.5; aimX += 0.5) {
                double aimZ = 166.5 + (aimX + 1299.5) * -0.0;
                TerrainCollider c = new TerrainCollider(new TerrainField(realBuild()), LU);
                KartSim k = new KartSim(KartClass.BALANCED, new V3((aimX - dx * 6) * LU, 64 * LU, (aimZ - dz * 6) * LU), heading);
                double top = 64;
                for (int i = 0; i < 160; i++) {
                    c.prepare(k.position(), k.velocity().y / 256.0);
                    k.tick(c, new KartInput(0, true, false, false, false, false));
                    top = Math.max(top, k.position().y / LU);
                }
                total++;
                if (top < 66.0) {
                    fails++;
                    var w = k.lastWallContact();
                    out.append(String.format("heading %d aim %.1f: highest %.2f at x %.2f z %.2f%s; ", heading, aimX, top, k.position().x / LU, k.position().z / LU,
                            w == null ? "" : String.format(" wall at (%.1f %.1f %.1f) n (%.2f %.2f %.2f)", w.point().x / LU, w.point().y / LU, w.point().z / LU, w.normal().x, w.normal().y, w.normal().z)));
                }
            }
        }
        System.out.println("REALFRONT " + fails + " of " + total + ": " + out);
    }

    @Test
    void walls() {
        StringBuilder out = new StringBuilder();
        double[][] spots = { { -1295.70, 166.47, 64.35 }, { -1296.74, 165.97, 64.62 }, { -1296.81, 166.90, 64.88 }, { -1294.72, 167.21, 64.26 } };
        for (double[] sp : spots) {
            TerrainField f = new TerrainField(realBuild());
            f.prepare(sp[0], sp[2], sp[1], 0.0);
            java.util.TreeMap<String, Integer> byTop = new java.util.TreeMap<>();
            f.forEachWallBox(sp[0], sp[1], 2.5, b -> byTop.merge(String.format("y%.0f-%.0f@(%.0f,%.0f)", b[1], b[4], b[0], b[2]), 1, Integer::sum));
            out.append(String.format("kart (%.2f,%.2f) feet %.2f ref %.2f: walls %s", sp[0], sp[1], sp[2], f.ref(), byTop) + System.lineSeparator());
        }
        System.out.println("WALLS " + out.toString().replace(System.lineSeparator(), " || "));
    }

    @Test
    void aColumnWithManyBoxesStillShowsItsTop() {
        // the ground is a stack of blocks, one collision box each: a column of ground and a two high build holds more boxes than the window's scratch space once did
        TerrainField f = new TerrainField(realBuild());
        f.prepare(-1296.5, 64.4, 165.5, 0.0);
        assertTrue(f.forEachWallBoxCount(-1296.5, 165.5, 3.0) == 0, "no wall within 3 blocks of the foot of the slope");
        assertTrue(!Double.isNaN(f.surface(-1298.5, 167.5)), "the second row is ground");
    }

    @Test
    void theGroundOfTheBuildIsNeverSteeperThanFortyFiveDegrees() {
        TerrainField f = new TerrainField(realBuild());
        f.prepare(-1298.5, 66.0, 167.5, 0.0);
        double worst = 1.0;
        for (double x = -1302; x <= -1295; x += 0.1) {
            for (double z = 164; z <= 171; z += 0.1) {
                if (Double.isNaN(f.surface(x, z))) continue;
                worst = Math.min(worst, f.normal(x, z)[1]);
            }
        }
        assertTrue(worst >= 0.70, "the steepest ground has a normal y of " + worst + " (45 degrees is 0.707; under 0.643 the physics calls it a wall)");
    }

    @Test
    void crestProfile() {
        TerrainField f = new TerrainField(realBuild());
        f.prepare(-1299.5, 67.5, 168.5, 0.0);
        StringBuilder out = new StringBuilder();
        // a line up the slope on the diagonal (toward -x, +z) over the crest onto the top, and one straight along z
        double[][] lines = { { -1296.0, 165.0, -0.7071, 0.7071 }, { -1299.5, 164.5, 0.0, 1.0 }, { -1296.5, 169.5, -1.0, 0.0 } };
        for (double[] l : lines) {
            out.append(String.format("line from (%.1f,%.1f) dir (%.2f,%.2f): ", l[0], l[1], l[2], l[3]));
            double prev = Double.NaN;
            double prevSlope = Double.NaN;
            for (double t = 0; t <= 9.0; t += 0.25) {
                double x = l[0] + l[2] * t;
                double z = l[1] + l[3] * t;
                double h = f.surface(x, z);
                if (Double.isNaN(h)) { out.append("[none] "); prev = Double.NaN; continue; }
                double slope = Double.isNaN(prev) ? Double.NaN : (h - prev) / 0.25;
                String mark = (!Double.isNaN(slope) && !Double.isNaN(prevSlope) && Math.abs(slope - prevSlope) > 0.45) ? "*" : "";
                out.append(String.format("%.2f%s ", h, mark));
                prev = h;
                prevSlope = slope;
            }
            out.append(System.lineSeparator());
        }
        System.out.println("CREST " + out.toString().replace(System.lineSeparator(), " || "));
    }
}
