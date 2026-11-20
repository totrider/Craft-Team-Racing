package com.craftteamracing.pickup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.craftteamracing.ModSounds;
import com.craftteamracing.kart.KartEntity;
import com.craftteamracing.kart.KartScale;
import com.craftteamracing.track.RaceManager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Warp Orb power-up (VehPickupItem_ShootNow WEAPON_ID_WARPBALL, RB_Warpball_ThTick): an orb that flies after the kart in first place, 112 lu a frame (twice a kart's top speed) and
 * blasts every other kart it touches (RB_Hazard_HurtDriver type 2: the explosion's hit, which a shield takes), within 0x9000 (192 lu) after 10 frames of sparing its owner. Without ten power
 * fruit it ends when it has hit the kart it was after or the kart in first place; with them it goes on through every kart ahead of its owner, nearest first. The target is, each tick, the
 * first racer in the order of the race that is not the owner (the powered orb: the nearest unhit racer ahead of the owner).
 *
 * <p><b>Stand-ins, not the original:</b> in a race the orb follows the rings of the course (the main path of the track's recorded route), always the way the course runs like the original, towards its target and homes in once it has followed the course to the ring nearest its target (by the target's place on the track, not a distance) and eases round the corners between rings; the original's
 * checkpoint chain also has branches and the orb turns round at walls, which this does not do. Out of a race there is no course: it flies straight at the nearest kart through whatever is in the way. Out of a race there is no order: it goes after the nearest driven kart within {@link ItemManager#GLOBAL_EFFECT_RADIUS} blocks (the
 * powered one after each of them in turn). With no target (the owner is alone) it flies on straight and goes away after 80 ticks (0x78 frames of turning round).
 */
public class WarpOrbEntity extends Entity {
    private static final EntityDataAccessor<Boolean> DATA_POWERED = SynchedEntityData.defineId(WarpOrbEntity.class, EntityDataSerializers.BOOLEAN);
    /** 112 lu a frame, 1.5 frames to a tick, in blocks. */
    private static final double SPEED = 112.0 / KartScale.LU_PER_BLOCK * 1.5;
    /** 0x9000 as a squared distance: 192 lu. */
    private static final double HIT_RADIUS = 192.0 / KartScale.LU_PER_BLOCK;
    /** WARPBALL_PARENT_SAFETY_FRAMES 10 frames. */
    private static final int SAFETY_TICKS = 7;
    /** 0x78 frames. */
    private static final int LOSE_TICKS = 80;
    private static final int RE_HIT_TICKS = 40;

    private UUID owner;
    private final Map<UUID, Integer> hit = new HashMap<>();
    private Vec3 heading = Vec3.ZERO;
    private int age;
    private int noTargetTicks;

    public WarpOrbEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public boolean powered() {
        return entityData.get(DATA_POWERED);
    }

    /** The orb's look on the client (client/OrbFx): electric sparks and motes of the end. */
    private void clientFx() {
        com.craftteamracing.client.OrbFx.tick(this);
    }

    public void launch(ServerPlayer driver, KartEntity kart, boolean powered) {
        owner = driver.getUUID();
        entityData.set(DATA_POWERED, powered);
        double yaw = Math.toRadians(kart.getYRot());
        heading = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        setPos(kart.getX(), kart.getY() + 0.3, kart.getZ());
        // fired from first place (VehPickupItem_ShootNow: the driver ranked first has nobody to aim at): the orb has no target at all, powered or not, and flies straight on until it meets a wall or
        // goes out by itself
        List<ServerPlayer> ranked = RaceManager.rankedRacers(driver);
        noTarget = ranked != null && !ranked.isEmpty() && ranked.get(0) == driver;
    }

    private boolean noTarget;

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_POWERED, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerPlayer driver = owner == null ? null : level().getServer().getPlayerList().getPlayer(owner);
        if (driver == null) {
            discard();
            return;
        }
        age++;
        hit.replaceAll((id, t) -> t - 1);
        hit.values().removeIf(t -> t <= 0);
        ServerPlayer target = noTarget ? null : chooseTarget(driver);
        Vec3 here = position();
        if (target != null && target.getVehicle() instanceof KartEntity tk) {
            Vec3 goal = tk.position().add(0.0, 0.3, 0.0);
            // on a race's course the orb follows the rings of the course towards its target (RB_Warpball_ThTick's path mode), and homes in on it once it is near; elsewhere it goes straight
            Vec3 waypoint = courseWaypoint(driver, target, here, goal);
            Vec3 to = (waypoint != null ? waypoint : goal).subtract(here);
            double dist = to.length();
            if (dist > 1.0E-4) {
                // it does not turn on the spot at a ring: the heading eases towards the next one (a curve through the course), quicker once it is homing in
                Vec3 want = to.scale(1.0 / dist);
                double k = waypoint != null ? CURVE_EASE : HOMING_EASE;
                Vec3 eased = heading.scale(1.0 - k).add(want.scale(k));
                heading = eased.lengthSqr() > 1.0E-6 ? eased.normalize() : want;
            }
            setPos(here.add(heading.scale(Math.min(SPEED, dist))));
            noTargetTicks = 0;
        } else {
            Vec3 next = here.add(heading.scale(SPEED));
            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(next);
            if (noTarget && !level().getBlockState(bp).getCollisionShape(level(), bp).isEmpty()) {
                end();                         // a wall: it is gone
                return;
            }
            setPos(next);
            if (++noTargetTicks > LOSE_TICKS) {
                end();
                return;
            }
        }
        if (age % 10 == 1) level().playSound(null, getX(), getY() + 3.0, getZ(), ModSounds.WARP_FLY.get(), SoundSource.PLAYERS, 2.2F, 1.0F);
        if (age > SAFETY_TICKS) collide(driver, target);
        if (age > 1200) end();
    }

    /** How much of the way to the wanted direction the heading turns each tick: along the course, and homing in. */
    private static final double CURVE_EASE = 0.5;
    private static final double HOMING_EASE = 0.45;
    /** A ring counts as passed this close (blocks): the orb cuts the corner instead of touching the ring. */
    private static final double PASS_RADIUS = 7.0;
    private int nodeIdx = -2;
    private final List<Vec3> route = new java.util.ArrayList<>();
    /** The main path index of each ring of the route, -1 for a ring of a branch. */
    private final List<Integer> routeMain = new java.util.ArrayList<>();
    private int routeStep;
    private UUID plannedFor;

    /**
     * The next ring of the course to fly to, or null to go straight for the target (not in a race, near the target, or the target has no place on the course yet). The orb keeps its own
     * place on the course: it starts at its owner's, passes a ring when it is within three blocks of it, and goes along the course the short way to the target's ring.
     */
    private Vec3 courseWaypoint(ServerPlayer driver, ServerPlayer target, Vec3 here, Vec3 goal) {
        // in a race the orb homes in by the target's place on the track, not by a distance: it follows the course to the ring nearest the target and, having got there, goes straight for it
        // (out of a race there is no course, see tick)
        // the orb is given a route, follows it to the end, and only then looks again where its target is now (or at once when it has another target)
        if (!target.getUUID().equals(plannedFor) || routeStep >= route.size()) plan(driver, target, here);
        // a ring is passed when the orb has got to the end of the stretch of line that leads to it (not by coming within some distance of it: a ring to one side of a turn is then never reached)
        while (routeStep < route.size()) {
            Vec3 a = routeStep == 0 ? routeFrom : route.get(routeStep - 1);
            Vec3 b = route.get(routeStep);
            Vec3 ab = b.subtract(a);
            double len = ab.length();
            double along = len > 1.0E-6 ? here.subtract(a).dot(ab) / len : len;
            if (along < len - 1.5 && here.distanceTo(b) >= 2.5) break;
            if (routeMain.get(routeStep) >= 0) nodeIdx = routeMain.get(routeStep);
            routeStep++;
        }
        if (routeStep >= route.size()) return null;
        // pure pursuit: fly at the point of the line a few blocks ahead of where the orb is along it. Along a straight stretch that is on the line, so it keeps to it; the turn only starts
        // within that distance of a ring, and it is as wide as that distance, so the corners are rounded and nothing else is
        Vec3 a = routeStep == 0 ? routeFrom : route.get(routeStep - 1);
        Vec3 b = route.get(routeStep);
        Vec3 ab = b.subtract(a);
        double len = ab.length();
        double along = len > 1.0E-6 ? Math.max(0.0, Math.min(len, here.subtract(a).dot(ab) / len)) : 0.0;
        double aim = along + LOOKAHEAD;
        if (aim <= len || len < 1.0E-6) return len < 1.0E-6 ? b : a.add(ab.scale(aim / len));
        double left = aim - len;
        Vec3 from = b;
        for (int k = routeStep + 1; k < route.size(); k++) {
            Vec3 next = route.get(k);
            double seg = from.distanceTo(next);
            if (left <= seg) return from.add(next.subtract(from).scale(seg > 1.0E-6 ? left / seg : 0.0));
            left -= seg;
            from = next;
        }
        return from;
    }

    /** The orb flies at the point of its route this far (blocks) ahead of it. */
    private static final double LOOKAHEAD = 9.0;
    private Vec3 routeFrom = Vec3.ZERO;

    /**
     * The route to where the target is in the race now: the rings of the main path the short way from the orb's last ring to the ring the target last passed and, when the target is on a
     * branch, the rings of that branch it has passed. No route (go straight for it) when the orb is on the target's ring, or beside the branch the target is on (between its start and its
     * end, where the orb does not go back to the start of it). A branch is only ever taken to get to a target that is on it.
     */
    private void plan(ServerPlayer driver, ServerPlayer target, Vec3 here) {
        routeFrom = here;
        com.craftteamracing.CraftTeamRacingMod.LOGGER.debug("Warp orb plans: target {}", target.getGameProfile().getName());
        route.clear();
        routeMain.clear();
        routeStep = 0;
        plannedFor = target.getUUID();
        List<Vec3> nodes = RaceManager.courseNodes(driver);
        int to = RaceManager.courseIndex(target);
        if (nodes == null || nodes.size() < 2 || to < 0) return;
        int n = nodes.size();
        // where the orb is on the course, by where it really is (its own place, not the owner's: it may have gone a long way since it was fired)
        double[] o = project(nodes, here);
        int oi = (int) o[0];
        nodeIdx = oi;
        int dest = to;
        int exit = RaceManager.courseBranchExit(target);
        List<Vec3> tail = List.of();
        if (exit >= 0) {
            // on a shortcut: the rings of it the target has passed, after the ring it leaves the main path at
            tail = RaceManager.courseBranch(target);
        } else {
            // on the main path: the stretch of it the target is on, by where they really are
            double[] t = project(nodes, target.position());
            dest = (int) t[0];
            // just ahead of the orb in the stretch it is in: straight at it (RB_Warpball_SetTargetDriver: a target within a few nodes ahead is gone for directly)
            if (dest == oi && t[1] >= o[1]) return;
        }
        if (exit >= 0 && oi != dest) {
            // beside the shortcut the target is on already (between its start and its end on the main path): straight at the target, the orb does not go back to the start of it
            int past = Math.floorMod(oi - dest, n);
            int span = Math.floorMod(exit - dest, n);
            if (past > 0 && past < span) return;
        }
        // always the way the course runs (RB_Warpball_NewPathNode follows the checkpoint chain forwards): a target behind the orb is reached by going round the lap, not by turning back
        var branches = exit < 0 ? RaceManager.courseBranches(driver) : List.<com.craftteamracing.track.RaceProgress.BranchView>of();
        walk:
        for (int idx = oi; ; ) {
            idx = Math.floorMod(idx + 1, n);
            route.add(nodes.get(idx));
            routeMain.add(idx);
            if (idx == dest) break;
            // a shortcut that leaves the main path ahead of the orb and rejoins it at or before the target's stretch is taken when it is the shorter way (the orb is never sent down one the
            // target is not past the end of: that would be the way into a stretch the target is in)
            for (var b : branches) {
                if (b.entryIdx() != idx) continue;
                int toExit = Math.floorMod(b.exitIdx() - idx, n);
                if (Math.floorMod(dest - idx, n) < toExit) continue;
                double mainLen = 0.0;
                for (int k = idx; k != b.exitIdx(); k = Math.floorMod(k + 1, n)) mainLen += nodes.get(k).distanceTo(nodes.get(Math.floorMod(k + 1, n)));
                List<Vec3> ring = new java.util.ArrayList<>();
                Vec3 prev = nodes.get(idx);
                double branchLen = 0.0;
                for (long c : b.chain()) {
                    net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.of(c);
                    Vec3 v = new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
                    branchLen += prev.distanceTo(v);
                    prev = v;
                    ring.add(v);
                }
                branchLen += prev.distanceTo(nodes.get(b.exitIdx()));
                if (branchLen >= mainLen) continue;
                for (Vec3 v : ring) {
                    route.add(v);
                    routeMain.add(-1);
                }
                idx = b.exitIdx();
                route.add(nodes.get(idx));
                routeMain.add(idx);
                if (idx == dest) break walk;
                break;
            }
        }
        for (Vec3 ring : tail) {
            route.add(ring);
            routeMain.add(-1);
        }
    }

    /** The place of a point on the main path (a closed line through the rings): {the index of the ring the segment it is nearest starts at, how far along that segment, 0 to 1}. */
    private static double[] project(List<Vec3> nodes, Vec3 p) {
        int n = nodes.size();
        double best = Double.MAX_VALUE;
        int bi = 0;
        double bt = 0.0;
        for (int i = 0; i < n; i++) {
            Vec3 a = nodes.get(i);
            Vec3 b = nodes.get((i + 1) % n);
            Vec3 ab = b.subtract(a);
            double len2 = ab.lengthSqr();
            double t = len2 > 0 ? Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len2)) : 0.0;
            double d = a.add(ab.scale(t)).distanceToSqr(p);
            if (d < best) {
                best = d;
                bi = i;
                bt = t;
            }
        }
        return new double[] { bi, bt };
    }

    private void collide(ServerPlayer driver, ServerPlayer target) {
        AABB box = getBoundingBox().inflate(HIT_RADIUS, 2.0, HIT_RADIUS);
        List<KartEntity> karts = level().getEntitiesOfClass(KartEntity.class, box);
        List<ServerPlayer> ranked = RaceManager.rankedRacers(driver);
        for (KartEntity kart : karts) {
            if (!(kart.getControllingPassenger() instanceof ServerPlayer victim) || victim == driver || kart.ethereal() || hit.containsKey(victim.getUUID())) continue;
            if (kart.position().subtract(position()).multiply(1.0, 0.0, 1.0).length() > HIT_RADIUS) continue;
            ItemManager.warpHit(victim);
            hit.put(victim.getUUID(), RE_HIT_TICKS);
            boolean leader = ranked != null && !ranked.isEmpty() && ranked.get(0) == victim;
            if (!powered() && (victim == target || leader)) {
                end();
                return;
            }
            if (ranked != null) {
                // the karts behind the one hit are done with too (driversHit marks every rank from the victim's down)
                int from = ranked.indexOf(victim);
                for (int i = Math.max(from, 0); i < ranked.size(); i++) hit.put(ranked.get(i).getUUID(), Integer.MAX_VALUE / 2);
            }
        }
    }

    /** The orb ends: the sound (0x4f) and gone (the original fades it over six frames). */
    private void end() {
        level().playSound(null, getX(), getY() + 3.0, getZ(), ModSounds.WARP_END.get(), SoundSource.PLAYERS, 4.0F, 1.0F);          // the orb is big and high: it carries as far as an explosion (4.0: 64 blocks)
        discard();
    }

    private ServerPlayer chooseTarget(ServerPlayer driver) {
        List<ServerPlayer> ranked = RaceManager.rankedRacers(driver);
        if (ranked != null) {
            int me = ranked.indexOf(driver);
            if (!powered()) {
                // RB_Warpball_GetDriverTarget: the first racer in the order of the race that is not the owner
                for (ServerPlayer p : ranked) if (p != driver && !hit.containsKey(p.getUUID())) return p;
                return null;
            }
            // the powered orb (RB_Warpball_GetDriverTarget): the racer nearest ahead of the orb along the course, going round the lap (so the one in the lead goes after the ones behind it)
            int from = nodeIdx == -2 ? Math.max(0, RaceManager.courseIndex(driver)) : nodeIdx;
            int n = Math.max(1, RaceManager.courseNodes(driver) == null ? 1 : RaceManager.courseNodes(driver).size());
            ServerPlayer best = null;
            int bestD = Integer.MAX_VALUE;
            for (ServerPlayer p : ranked) {
                if (p == driver || hit.containsKey(p.getUUID())) continue;
                int d = Math.floorMod(RaceManager.courseIndex(p) - from, n);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            return best;
        }
        // out of a race: the nearest driven kart within reach (a global effect's radius), every one of them for the powered orb
        ServerPlayer best = null;
        double bestD = ItemManager.GLOBAL_EFFECT_RADIUS * ItemManager.GLOBAL_EFFECT_RADIUS;
        for (ServerPlayer p : level().getServer().getPlayerList().getPlayers()) {
            if (p == driver || p.level() != level() || !(p.getVehicle() instanceof KartEntity ek) || ek.ethereal() || hit.containsKey(p.getUUID())) continue;
            double d = p.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
