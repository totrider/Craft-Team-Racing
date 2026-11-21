package com.craftteamracing.terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * The smooth ground the kart drives on, built from blocky terrain. No Minecraft imports.
 *
 * <p><b>The idea.</b> The world is sampled as a height map at half-block resolution: for every half-block cell, the top of the
 * highest collision box the kart could stand on (within a step-up above the kart and a drop below it, with headroom). The ground
 * surface is then that height map <i>dilated by a 45-degree cone</i>: {@code S(p) = max over cells c of (h_c - distance(p, c))},
 * only counting cells no more than a step (1.25 blocks) above the cell under p. Consequences:
 * <ul>
 *   <li>flat floors stay flat, and the surface is never below a block's real top, so the kart never sinks into terrain;</li>
 *   <li>a one-block step becomes a 45-degree ramp up its lower side, a slab or stair step a gentler one, a staircase a smooth
 *       slope; the steepest the surface ever gets is 45 degrees, so it is always "ground" (normal.y >= 0.643) for the physics;</li>
 *   <li>a taller step (a wall), a gap or a cliff edge gets no ramp: the surface ends at the edge, and the real block boxes that are
 *       too tall to climb collide as walls.</li>
 * </ul>
 * The window around the kart is rebuilt once per physics tick ({@link #prepare}); queries are cheap.
 */
public final class TerrainField {
    /** Half a block: the height map's cell size. */
    public static final double CELL = 0.5;
    /** Block columns around the kart's own in each direction (a kart covers at most ~2.3 blocks a tick). */
    private static final int RADIUS = 4;
    /** How far above the kart's feet (blocks) ground counts as a step up (a full block with a little spare). */
    public static final double STEP_UP = 1.25;
    /** How far below the kart's feet ground is looked for (blocks): falling fast must not skip it. */
    public static final double DROP = 6.0;
    /** Free height needed above a surface for the kart to be on it (blocks). */
    private static final double HEADROOM = 0.9;
    /** A box counts as a wall when it stands this much above the ground surface at its closest point (blocks). */
    private static final double WALL_RISE = 0.5;
    /**
     * How far (blocks) the surface a bomb or kart rides can be under the real top of what it rides over: the smoothing and the half-block sampling put it up to this much below
     * a steep slope's crest. A window built around the ride height alone then ends up to this much short of the step up it is supposed to reach, so a block that follows the crest
     * is a wall. Prepare the window around {@code ride + RIDE_LAG} and look for walls above {@code ride + RIDE_LAG + STEP_UP}.
     */
    public static final double RIDE_LAG = 0.5;
    /** How many steps further up than the kart's own step the field follows a staircase (each round is one more step of {@link #STEP_UP}). */
    private static final int EXTEND_ROUNDS = 3;
    /** How far (blocks) the kart's feet can be under the top of the ground it stands on and still count as standing on it (see {@link #prepare}). */
    private static final double RIDE_SLACK = 0.6;
    /** How much ({@link #raise}) a cell of the cone surface may be lifted to meet its neighbours: enough to close the steps the gating leaves (about half a block), not to grow ramps of its own. */
    private static final double MAX_FILL = 0.75;
    /** Steepest ramp: 1 up per 1 across (45 degrees). */
    private static final double SLOPE = 1.0;
    private static final double GRADIENT_STEP = 0.25;
    /**
     * After the cone pass the surface is blurred (two passes of [1 2 1] over half-block cells: a kernel about a block wide whose
     * response vanishes at a one-block period, so a staircase of 0.5 / 1.0 steps becomes one incline) and may then sit this much
     * below the cone surface (blocks, about 3 px): the kart rides a hair into a tread's corner instead of in bumps.
     */
    private static final double SMOOTH_DIP = 0.18;
    /** The blur only mixes cells whose surfaces differ by no more than this (a cliff's foot must not be lifted). */
    private static final double SMOOTH_RANGE = STEP_UP;

    /** Which version of the terrain rules this is, shown by /ctr debug terrain so a screenshot says which build it came from. */
    public static final String REVISION = "4: follows staircases up by steps, step counted from the ground under the kart, tall columns keep their top, the surface is continuous, ground never steeper than 45 degrees";

    private double[] scratch = new double[64];
    private final BlockSource src;
    private final int columns = 2 * RADIUS + 1;
    private final int cells = 2 * columns;

    private int bx0;
    private int bz0;
    private double ref;
    private double[] height;
    private double[] smooth;
    private int[] kind;
    @SuppressWarnings("unchecked")
    private final List<double[]>[] boxesByColumn = new List[columns * columns];

    public TerrainField(BlockSource src) {
        this.src = src;
        this.height = new double[cells * cells];
        this.smooth = new double[cells * cells];
        this.kind = new int[cells * cells];
        for (int i = 0; i < boxesByColumn.length; i++) {
            boxesByColumn[i] = new ArrayList<>();
        }
    }

    /**
     * Rebuilds the window around the kart.
     *
     * @param cx,cy,cz the kart's origin (feet) in blocks
     * @param vy       its vertical speed in blocks per tick: a fast climb looks that much higher
     */
    public void prepare(double cx, double cy, double cz, double vy) {
        bx0 = (int) Math.floor(cx) - RADIUS;
        bz0 = (int) Math.floor(cz) - RADIUS;
        ref = cy + Math.max(vy, 0.0);
        double yMin = ref - DROP - 1.0;
        double yMax = ref + STEP_UP + HEADROOM + 2.0 + EXTEND_ROUNDS * STEP_UP + RIDE_SLACK;
        for (int i = 0; i < columns; i++) {
            for (int j = 0; j < columns; j++) {
                List<double[]> list = boxesByColumn[i * columns + j];
                list.clear();
                int bx = bx0 + i;
                int bz = bz0 + j;
                src.boxes(bx, yMin, bz, bx + 1, yMax, bz + 1, (x0, y0, z0, x1, y1, z1) -> list.add(new double[] { x0, y0, z0, x1, y1, z1 }));
            }
        }
        // The kart rides a hair into the ground it drives on (the smoothing lets it sit up to 0.18 under the surface, and a wall or a steep ramp can press it further): its feet can be well
        // under the top of the ground it stands on. The step above it is counted from that ground, not from the sunken feet, or the first step of a staircase is already out of reach.
        int ki = (int) Math.floor((cx - bx0) / CELL);
        int kj = (int) Math.floor((cz - bz0) / CELL);
        if (ki >= 0 && kj >= 0 && ki < cells && kj < cells) {
            double own = columnTop(ki, kj, cy + RIDE_SLACK);
            if (!Double.isNaN(own) && own > ref) {
                ref = own;
            }
        }
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                height[i * cells + j] = columnTop(i, j, ref + STEP_UP);
            }
        }
        // Ground further up a slope: a step or stair that starts more than a step above the kart is still ground when it can be reached from ground the kart can already see, by steps
        // of no more than a step each (a staircase of full blocks, however long). Without this the field only sees one step above the kart's own height, so each next tread of a steep
        // staircase stays a wall until the kart has driven a block up. A real wall (two blocks or more above its neighbour) is not reached by a step and stays one.
        double[] seen = new double[cells * cells];
        for (int round = 0; round < EXTEND_ROUNDS; round++) {
            System.arraycopy(height, 0, seen, 0, height.length);
            boolean grew = false;
            for (int i = 0; i < cells; i++) {
                for (int j = 0; j < cells; j++) {
                    if (!Double.isNaN(seen[i * cells + j])) {
                        continue;
                    }
                    double near = Double.NaN;
                    for (int di = -1; di <= 1; di++) {
                        for (int dj = -1; dj <= 1; dj++) {
                            int ni = i + di;
                            int nj = j + dj;
                            if ((di == 0 && dj == 0) || ni < 0 || nj < 0 || ni >= cells || nj >= cells) {
                                continue;
                            }
                            double v = seen[ni * cells + nj];
                            if (!Double.isNaN(v) && (Double.isNaN(near) || v > near)) {
                                near = v;
                            }
                        }
                    }
                    if (Double.isNaN(near)) {
                        continue;
                    }
                    double top = columnTop(i, j, near + STEP_UP);
                    if (!Double.isNaN(top) && top > ref + STEP_UP + 1e-6) {
                        height[i * cells + j] = top;
                        grew = true;
                    }
                }
            }
            if (!grew) {
                break;
            }
        }
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                double best = height[i * cells + j];
                double x = bx0 + (i + 0.5) * CELL;
                double z = bz0 + (j + 0.5) * CELL;
                kind[i * cells + j] = Double.isNaN(best) ? 0 : src.surface((int) Math.floor(x), (int) Math.floor(best - 1e-4), (int) Math.floor(z));
            }
        }
        buildSmooth();
    }

    /** The top of the highest collision box of the cell's column that the kart could stand on with the free headroom, no higher than {@code max} and no lower than {@link #DROP} below the kart; NaN if none. */
    private double columnTop(int i, int j, double max) {
        double x = bx0 + (i + 0.5) * CELL;
        double z = bz0 + (j + 0.5) * CELL;
        List<double[]> list = boxesByColumn[(i / 2) * columns + (j / 2)];
        // a column holds one box per block of ground and build in the window's height (a plain column of ground alone is seven), so the scratch space is sized to the list: a fixed small
        // array dropped the boxes past its end, which were the top ones, and a stack taller than a block or two lost its top
        if (scratch.length < list.size() * 2) {
            scratch = new double[list.size() * 2];
        }
        double[] at = scratch;
        int n = 0;
        for (double[] b : list) {
            if (x >= b[0] && x <= b[3] && z >= b[2] && z <= b[5]) {
                at[n++] = b[1];
                at[n++] = b[4];
            }
        }
        double best = Double.NaN;
        for (int a = 0; a < n; a += 2) {
            double top = at[a + 1];
            if (top > max + 1e-6 || top < ref - DROP || (!Double.isNaN(best) && top <= best)) {
                continue;
            }
            boolean free = true;
            for (int o = 0; o < n; o += 2) {
                if (o != a && at[o] < top + HEADROOM - 1e-6 && at[o + 1] > top + 1e-6) {
                    free = false;
                    break;
                }
            }
            if (free) {
                best = top;
            }
        }
        return best;
    }

    /** The cone surface at every cell centre, blurred twice (separably) over the cells it may be mixed with. */
    private void buildSmooth() {
        double[] a = new double[cells * cells];
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                a[i * cells + j] = envelope(bx0 + (i + 0.5) * CELL, bz0 + (j + 0.5) * CELL);
            }
        }
        raise(a);
        double[] b = new double[cells * cells];
        for (int pass = 0; pass < 2; pass++) {
            blur(a, b, true);
            blur(b, a, false);
        }
        System.arraycopy(a, 0, smooth, 0, a.length);
    }

    /**
     * Makes the cone surface continuous where its gating left it a step: a cell may not sit more than 45 degrees under a neighbour within a step of it (two sweeps over the cells, forward
     * and back, the cone dilation again but over the surface instead of the raw heights). Where the rule that decides which cells a cell sees changes from one cell to the next, the surface
     * used to jump by up to half a block across a block edge, which the kart feels as a sharp bump on a staircase; now that jump is a 45 degree ramp.
     */
    private void raise(double[] a) {
        double side = CELL * SLOPE;
        double diagonal = CELL * Math.sqrt(2.0) * SLOPE;
        double[] before = a.clone();                  // which cells may lift which is decided on the surface as it was, so a lifted cell does not shut its neighbours out
        for (int sweep = 0; sweep < 2; sweep++) {
            boolean forward = sweep == 0;
            for (int ii = 0; ii < cells; ii++) {
                int i = forward ? ii : cells - 1 - ii;
                for (int jj = 0; jj < cells; jj++) {
                    int j = forward ? jj : cells - 1 - jj;
                    double own = a[i * cells + j];
                    if (Double.isNaN(own)) {
                        continue;
                    }
                    for (int d = 0; d < 4; d++) {
                        int di = forward ? (d == 3 ? 0 : -1) : (d == 3 ? 0 : 1);
                        int dj = forward ? (d == 0 ? -1 : d == 1 ? 0 : d == 2 ? 1 : -1) : (d == 0 ? 1 : d == 1 ? 0 : d == 2 ? -1 : 1);
                        int ni = i + di;
                        int nj = j + dj;
                        if (ni < 0 || nj < 0 || ni >= cells || nj >= cells) {
                            continue;
                        }
                        double v = a[ni * cells + nj];
                        if (Double.isNaN(v) || before[ni * cells + nj] - before[i * cells + j] > STEP_UP) {
                            continue;
                        }
                        double lifted = Math.min(v - (di != 0 && dj != 0 ? diagonal : side), before[i * cells + j] + MAX_FILL);
                        if (lifted > own) {
                            own = lifted;
                        }
                    }
                    a[i * cells + j] = own;
                }
            }
        }
    }

    /** One [1 2 1] / 4 pass along x (or z) of {@code in} into {@code out}, over valid cells within {@link #SMOOTH_RANGE}. */
    private void blur(double[] in, double[] out, boolean alongX) {
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                double c = in[i * cells + j];
                if (Double.isNaN(c)) {
                    out[i * cells + j] = Double.NaN;
                    continue;
                }
                double sum = 2 * c;
                double w = 2;
                for (int d = -1; d <= 1; d += 2) {
                    int ni = alongX ? i + d : i;
                    int nj = alongX ? j : j + d;
                    if (ni < 0 || nj < 0 || ni >= cells || nj >= cells) {
                        continue;
                    }
                    double v = in[ni * cells + nj];
                    if (!Double.isNaN(v) && Math.abs(v - c) <= SMOOTH_RANGE) {
                        sum += v;
                        w += 1;
                    }
                }
                out[i * cells + j] = sum / w;
            }
        }
    }

    private int cellIndex(double x, double z) {
        int i = (int) Math.floor((x - bx0) / CELL);
        int j = (int) Math.floor((z - bz0) / CELL);
        if (i < 0 || j < 0 || i >= cells || j >= cells) {
            return -1;
        }
        return i * cells + j;
    }

    /** How many boxes {@link #forEachWallBox} reports (for tests). */
    public int forEachWallBoxCount(double kx, double kz, double range) {
        int[] n = { 0 };
        forEachWallBox(kx, kz, range, b -> n[0]++);
        return n[0];
    }

    /** The window's picture for the debug log: one line per block row (z), one character per block (x), the accepted ground height above {@code base} (a digit, + past 9), or . for none. */
    public String heightGrid(double cx, double cz, int radius, double base) {
        StringBuilder sb = new StringBuilder();
        int x0 = (int) Math.floor(cx) - radius;
        int z0 = (int) Math.floor(cz) - radius;
        for (int z = z0; z <= (int) Math.floor(cz) + radius; z++) {
            for (int x = x0; x <= (int) Math.floor(cx) + radius; x++) {
                double h = cellHeight(x + 0.25, z + 0.25);
                sb.append(Double.isNaN(h) ? '.' : (h - base > 9.4 ? '+' : (char) ('0' + (int) Math.max(0, Math.min(9, Math.round(h - base))))));
            }
            sb.append('/');
        }
        return sb.toString();
    }

    /** How many collision boxes the prepared window holds in the block column at (bx, bz), -1 outside it. */
    public int columnBoxCount(int bx, int bz) {
        int i = bx - bx0;
        int j = bz - bz0;
        return i < 0 || j < 0 || i >= columns || j >= columns ? -1 : boxesByColumn[i * columns + j].size();
    }

    /** The reference height the last {@link #prepare} counted steps from. */
    public double ref() {
        return ref;
    }

    /** The raw height (top of the ground the kart could stand on) of the half-block cell at (x, z), NaN where none: for the debug log. */
    public double cellHeight(double x, double z) {
        int c = cellIndex(x, z);
        return c < 0 ? Double.NaN : height[c];
    }

    /** The window's west edge in blocks (block x of its first column): the debug view reads the field as the kart sees it. */
    public double windowX0() {
        return bx0;
    }

    /** The window's north edge in blocks. */
    public double windowZ0() {
        return bz0;
    }

    /** Cells along one side of the window (half a block each). */
    public int cellCount() {
        return cells;
    }

    /**
     * The collision boxes of the prepared window that the kart at (kx, kz) would meet as walls, as {@link #deepestWall} counts them: a box standing more than {@link #WALL_RISE} above
     * the ground surface at its point closest to the kart, or one with no ground at all beside it. Each is {x0, y0, z0, x1, y1, z1}; only boxes within {@code range} blocks of the kart.
     */
    public void forEachWallBox(double kx, double kz, double range, java.util.function.Consumer<double[]> out) {
        for (List<double[]> column : boxesByColumn) {
            for (double[] b : column) {
                double px = clamp(kx, b[0], b[3]);
                double pz = clamp(kz, b[2], b[5]);
                if ((px - kx) * (px - kx) + (pz - kz) * (pz - kz) > range * range) {
                    continue;
                }
                double s = surface(px, pz);
                if (Double.isNaN(s) || b[4] > s + WALL_RISE) {
                    out.accept(b);
                }
            }
        }
    }

    /** The surface kind ({@link Surface} code) under (x, z), or -1 where there is no ground. */
    public int kindAt(double x, double z) {
        int c = cellIndex(x, z);
        return c < 0 || Double.isNaN(height[c]) ? -1 : kind[c];
    }

    /**
     * The ground surface height at (x, z) in blocks, or NaN where there is no ground the kart could be on: the cone surface
     * ({@link #envelope}) smoothed by the blur, and never more than {@link #SMOOTH_DIP} below the cone surface.
     */
    public double surface(double x, double z) {
        double e = envelope(x, z);
        if (Double.isNaN(e)) {
            return Double.NaN;
        }
        // bilinear between the blurred cell centres around (x, z), over the valid ones
        double fx = (x - bx0) / CELL - 0.5;
        double fz = (z - bz0) / CELL - 0.5;
        int i0 = (int) Math.floor(fx);
        int j0 = (int) Math.floor(fz);
        double tx = fx - i0;
        double tz = fz - j0;
        double sum = 0;
        double wsum = 0;
        for (int di = 0; di <= 1; di++) {
            for (int dj = 0; dj <= 1; dj++) {
                int i = i0 + di;
                int j = j0 + dj;
                if (i < 0 || j < 0 || i >= cells || j >= cells) {
                    continue;
                }
                double v = smooth[i * cells + j];
                if (Double.isNaN(v) || Math.abs(v - e) > SMOOTH_RANGE) {
                    continue;
                }
                double w = (di == 0 ? 1 - tx : tx) * (dj == 0 ? 1 - tz : tz);
                sum += w * v;
                wsum += w;
            }
        }
        double blurred = wsum > 1e-9 ? sum / wsum : e;
        return Math.max(blurred, e - SMOOTH_DIP);
    }

    /** The cone surface: the height map dilated by a 45 degree cone (see the class doc), NaN without ground. */
    private double envelope(double x, double z) {
        int c = cellIndex(x, z);
        if (c < 0) {
            return Double.NaN;
        }
        double own = height[c];
        if (Double.isNaN(own)) {
            return Double.NaN;
        }
        int ci = c / cells;
        int cj = c % cells;
        double best = own;
        for (int di = -3; di <= 3; di++) {
            int i = ci + di;
            if (i < 0 || i >= cells) {
                continue;
            }
            for (int dj = -3; dj <= 3; dj++) {
                int j = cj + dj;
                if (j < 0 || j >= cells || (di == 0 && dj == 0)) {
                    continue;
                }
                double h = height[i * cells + j];
                if (Double.isNaN(h) || h <= best || h > own + STEP_UP) {
                    continue;
                }
                double cx0 = bx0 + i * CELL;
                double cz0 = bz0 + j * CELL;
                double dx = Math.max(Math.max(cx0 - x, x - (cx0 + CELL)), 0.0);
                double dz = Math.max(Math.max(cz0 - z, z - (cz0 + CELL)), 0.0);
                double v = h - SLOPE * Math.sqrt(dx * dx + dz * dz);
                if (v > best) {
                    best = v;
                }
            }
        }
        return best;
    }

    /** The surface's unit normal at (x, z) (0, 1, 0 where it is flat or has no neighbours): {nx, ny, nz}. */
    public double[] normal(double x, double z) {
        double s = surface(x, z);
        if (Double.isNaN(s)) {
            return new double[] { 0, 1, 0 };
        }
        double xp = pick(surface(x + GRADIENT_STEP, z), s);
        double xm = pick(surface(x - GRADIENT_STEP, z), s);
        double zp = pick(surface(x, z + GRADIENT_STEP), s);
        double zm = pick(surface(x, z - GRADIENT_STEP), s);
        double gx = (xp - xm) / (2 * GRADIENT_STEP);
        double gz = (zp - zm) / (2 * GRADIENT_STEP);
        // The surface is never meant to be steeper than 45 degrees (SLOPE), so all of it is ground. Sampled by finite differences it can read steeper where the blurred half-block cells rise
        // along both axes at once (a staircase climbed on its diagonal: 1 along x and 1 along z is a 54.7 degree slope), and the physics counts a normal under 50 degrees as a wall: a bump
        // on ground that is no wall, with the boost lost. The slope is held to the design's.
        double steep = Math.hypot(gx, gz);
        if (steep > SLOPE) {
            gx *= SLOPE / steep;
            gz *= SLOPE / steep;
        }
        double len = Math.sqrt(gx * gx + 1 + gz * gz);
        return new double[] { -gx / len, 1 / len, -gz / len };
    }

    private static double pick(double v, double fallback) {
        return Double.isNaN(v) ? fallback : v;
    }

    /**
     * The deepest overlap of a sphere with the terrain: the ground surface (sampled under the sphere) or a wall box. Blocks, +Y up.
     * Returns {depth, nx, ny, nz, px, py, pz} or null.
     */
    public double[] deepest(double cx, double cy, double cz, double r) {
        double[] best = null;
        // the ground: the plane tangent to the surface under a few points of the sphere's footprint
        double q = 0.6 * r;
        double[][] offsets = { { 0, 0 }, { q, 0 }, { -q, 0 }, { 0, q }, { 0, -q } };
        for (double[] o : offsets) {
            double qx = cx + o[0];
            double qz = cz + o[1];
            double s = surface(qx, qz);
            if (Double.isNaN(s)) {
                continue;
            }
            double[] n = normal(qx, qz);
            double d = (cx - qx) * n[0] + (cy - s) * n[1] + (cz - qz) * n[2];
            if (d < r && d > -1.0 && (best == null || r - d > best[0])) {
                best = new double[] { r - d, n[0], n[1], n[2], qx, s, qz };
            }
        }
        double[] wall = deepestWall(cx, cy, cz, r);
        if (wall != null && (best == null || wall[0] > best[0])) {
            best = wall;
        }
        return best;
    }

    /**
     * Whether a collision box of the prepared window stands in the volume {@code half} either side of (cx, cz) between heights y0 and y1: for something rolling along the ground
     * surface, a wall. Put the volume a little above where the surface is under it (0.6 to 0.9 above, for a 0.7 block tall body): a step, stair or ramp rises less than that
     * under the body's front edge, because the surface already climbs ahead of it (the cone), so only what the ground does not cover is in the way.
     */
    public boolean blocked(double cx, double cz, double y0, double y1, double half) {
        return blockingBox(cx, cz, y0, y1, half) != null;
    }

    /** The first collision box {@link #blocked} found (x0, y0, z0, x1, y1, z1), or null. */
    public double[] blockingBox(double cx, double cz, double y0, double y1, double half) {
        int i0 = (int) Math.floor(cx - half) - bx0;
        int i1 = (int) Math.floor(cx + half) - bx0;
        int j0 = (int) Math.floor(cz - half) - bz0;
        int j1 = (int) Math.floor(cz + half) - bz0;
        for (int i = Math.max(i0, 0); i <= Math.min(i1, columns - 1); i++) {
            for (int j = Math.max(j0, 0); j <= Math.min(j1, columns - 1); j++) {
                for (double[] b : boxesByColumn[i * columns + j]) {
                    if (b[0] < cx + half && b[3] > cx - half && b[2] < cz + half && b[5] > cz - half && b[1] < y1 && b[4] > y0) {
                        return b;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The deepest overlap of a sphere with a wall: a collision box the ground surface does not cover (one that stands more than {@link #WALL_RISE} above the surface at its
     * closest point). Same result as {@link #deepest} without the ground; null when there is none.
     */
    public double[] deepestWall(double cx, double cy, double cz, double r) {
        double[] best = null;
        int i0 = (int) Math.floor(cx - r) - bx0;
        int i1 = (int) Math.floor(cx + r) - bx0;
        int j0 = (int) Math.floor(cz - r) - bz0;
        int j1 = (int) Math.floor(cz + r) - bz0;
        for (int i = Math.max(i0, 0); i <= Math.min(i1, columns - 1); i++) {
            for (int j = Math.max(j0, 0); j <= Math.min(j1, columns - 1); j++) {
                for (double[] b : boxesByColumn[i * columns + j]) {
                    double px = clamp(cx, b[0], b[3]);
                    double py = clamp(cy, b[1], b[4]);
                    double pz = clamp(cz, b[2], b[5]);
                    double dx = cx - px;
                    double dy = cy - py;
                    double dz = cz - pz;
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > r * r) {
                        continue;
                    }
                    double s = surface(px, pz);
                    if (!Double.isNaN(s) && b[4] <= s + WALL_RISE) {
                        continue; // part of the ground the surface already covers
                    }
                    double depth;
                    double nx;
                    double ny;
                    double nz;
                    if (d2 > 1e-12) {
                        double d = Math.sqrt(d2);
                        depth = r - d;
                        nx = dx / d;
                        ny = dy / d;
                        nz = dz / d;
                    } else {
                        double[] pen = { cx - b[0], b[3] - cx, cy - b[1], b[4] - cy, cz - b[2], b[5] - cz };
                        int axis = 0;
                        for (int k = 1; k < 6; k++) {
                            if (pen[k] < pen[axis]) axis = k;
                        }
                        depth = r + pen[axis];
                        nx = axis == 0 ? -1 : axis == 1 ? 1 : 0;
                        ny = axis == 2 ? -1 : axis == 3 ? 1 : 0;
                        nz = axis == 4 ? -1 : axis == 5 ? 1 : 0;
                        px = cx;
                        py = cy;
                        pz = cz;
                    }
                    if (best == null || depth > best[0]) {
                        best = new double[] { depth, nx, ny, nz, px, py, pz };
                    }
                }
            }
        }
        return best;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
