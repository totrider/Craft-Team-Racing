package com.craftteamracing.client;

import java.util.ArrayList;
import java.util.List;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.ModSounds;
import com.craftteamracing.CtrConfig;
import com.craftteamracing.kart.ContactRules;
import com.craftteamracing.kart.FloatSurface;
import com.craftteamracing.kart.KartEntity;
import com.craftteamracing.kart.KartFlags;
import com.craftteamracing.physics.KartEvent;
import com.craftteamracing.kart.KartScale;
import com.craftteamracing.net.KartDismountPayload;
import com.craftteamracing.net.KartHitPayload;
import com.craftteamracing.net.KartStatePayload;
import com.craftteamracing.physics.FixedMath;
import com.craftteamracing.physics.KartBump;
import com.craftteamracing.physics.KartClass;
import com.craftteamracing.physics.KartInput;
import com.craftteamracing.physics.KartSim;
import com.craftteamracing.physics.RevEngine;
import com.craftteamracing.track.RaceClient;
import com.craftteamracing.track.RacePayload;
import com.craftteamracing.terrain.Surface;
import com.craftteamracing.terrain.TerrainCollider;
import com.craftteamracing.terrain.TerrainField;
import com.craftteamracing.physics.V3;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Runs the CTR physics for the kart the local player drives. The sim ticks at CTR's fixed 30 Hz from an accumulator fed by
 * render frames (not Minecraft's 20 Hz tick); each frame the kart entity and its rider are placed at the pose interpolated
 * between the last two sim ticks. The server only validates (vanilla's move-vehicle packet); other players see the kart
 * through normal entity tracking.
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID, value = Dist.CLIENT)
public final class KartDriver {
    private KartDriver() {}

    private static KartSim sim;
    private static TerrainCollider world;
    private static int simEntityId = -1;
    private static double lastFluidY = Double.NEGATIVE_INFINITY;
    private static int lastFluidKind;
    private static long lastNanos;
    private static double accumulator;
    private static boolean latchR1;
    private static boolean latchL1;
    private static boolean firstRideFrame;
    /** The engine revving on the race grid (null when not held there). */
    private static RevEngine rev;

    // the last two sim poses, for interpolation
    private static V3 prevPos = V3.ZERO;
    private static V3 curPos = V3.ZERO;
    private static int prevHeading;
    private static int curHeading;
    private static int prevDrawn;
    private static int curDrawn;

    private static void reset() {
        // jump presses made out of the kart (on foot, or before getting in) must not be waiting when the driving starts: they would fire the double jump at once
        Minecraft rm = Minecraft.getInstance();
        if (rm.options != null) {
            while (rm.options.keyJump.consumeClick()) { }
            while (CraftTeamRacingClient.KEY_HOP2.consumeClick()) { }
        }
        springLatch = false;
        com.craftteamracing.pickup.ItemClient.clear();
        sim = null;
        simEntityId = -1;
        lastFluidY = Double.NEGATIVE_INFINITY;
        accumulator = 0;
        latchR1 = false;
        latchL1 = false;
        rev = null;
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null
                || !(player.getVehicle() instanceof KartEntity kart) || kart.getControllingPassenger() != player) {
            reset();
            return;
        }
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min((now - lastNanos) / 1.0e9, 0.1);
        lastNanos = now;
        if (mc.isPaused()) {
            return;
        }

        if (sim == null || simEntityId != kart.getId()) {
            sim = new KartSim(com.craftteamracing.tuning.TuningClient.resolve(kart.tuning()).stats(), toLu(kart.position()), KartScale.yawToAngle(kart.getYRot()));
            simEntityId = kart.getId();
            accumulator = 0;
            snapshot(true);
            firstRideFrame = true;
        }
        com.craftteamracing.physics.KartStats tuned = com.craftteamracing.tuning.TuningClient.resolve(kart.tuning()).stats();
        if (!tuned.equals(sim.stats())) sim.setStats(tuned);          // the Wrench picked another profile, or the server's profiles changed
        if (firstRideFrame) {
            firstRideFrame = false;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        accumulator = Math.min(accumulator + dt, 0.25);
        Options o = mc.options;
        boolean r1 = o.keyJump.isDown();
        boolean l1 = CraftTeamRacingClient.KEY_HOP2.isDown();
        latchR1 |= r1;
        latchL1 |= l1;
        // a jump press (either jump key) while in the air is the Jump Spring's double jump
        springLatch |= o.keyJump.consumeClick() | CraftTeamRacingClient.KEY_HOP2.consumeClick();
        if (world == null || worldLevel != mc.level) {
            world = new TerrainCollider(new TerrainField(new McBlockSource(mc.level, kart)), KartScale.LU_PER_BLOCK);
            worldLevel = mc.level;
        }
        // the server put us on the race grid: stand there
        RacePayload slot = RaceClient.takePlacement();
        if (slot != null) {
            sim.place(new V3(KartScale.toLu(slot.gx()), KartScale.toLu(slot.gy()), KartScale.toLu(slot.gz())), KartScale.yawToAngle(slot.gyaw()));
            snapshot(true);
            accumulator = 0;
            rev = null;
        }
        while (accumulator >= KartSim.TICK_SECONDS) {
            accumulator -= KartSim.TICK_SECONDS;
            double steer = (o.keyLeft.isDown() ? -1.0 : 0.0) + (o.keyRight.isDown() ? 1.0 : 0.0);
            KartInput input = new KartInput(steer, o.keyUp.isDown(), CraftTeamRacingClient.KEY_BRAKE.isDown(),
                    o.keyDown.isDown(), latchR1, latchL1);
            // Held on the grid until GO (VehStuckProc_RevEngine): the gas revs the engine instead of driving; at GO the revs earn the start boost
            if (RaceClient.hold()) {
                if (rev == null) {
                    rev = new RevEngine(sim.accelSpeed());
                }
                if (rev.tick(o.keyUp.isDown(), KartSim.MS)) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.BOOST_FAIL.get(), 1.0F, 1.0F));
                }
                input = new KartInput(0.0, false, true, false, false, false);
                sim.clearBoost();          // a boost left over from before the grid (a pad, an item) does not go on carrying the kart
            } else if (rev != null) {
                RevEngine.StartBoost boost = rev.release();
                rev = null;
                if (boost != null) {
                    sim.startBoost(boost);
                }
            }
            if (RaceClient.podium()) input = new KartInput(0.0, false, true, false, false, false);          // on the podium: the kart stays where it was put
            latchR1 = false;
            latchL1 = false;
            // in a race the rules decide what a kart may do (the karts' own upgrades do not count); outside one its own upgrades do
            sim.setMask(kart.maskState() != 0);
            boolean race = RaceClient.active();
            int floaterMode = race ? RaceClient.rules().floater() : kart.floaterMode();
            boolean floating = floaterMode >= 1;
            McBlockSource.floaterMode = floaterMode;
            McBlockSource.paddleActive = race ? RaceClient.rules().paddle() : kart.paddleOn();
            double feetY = KartScale.toBlocks(sim.position().y);
            McBlockSource.floaterFeetY = feetY;
            // under the surface with the Floater: lifted to it (the water is not a floor down there, see McBlockSource)
            double surfaceTop = floating && mc.level != null ? FloatSurface.top(mc.level, KartScale.toBlocks(sim.position().x), feetY + 0.05, KartScale.toBlocks(sim.position().z), floaterMode) : Double.NaN;
            sim.setBuoyant(!Double.isNaN(surfaceTop) && feetY < surfaceTop - FloatSurface.SINK - 0.3);
            sim.setLowGravity(race ? RaceClient.rules().lowGravity() : kart.gravityStrength());
            if (springLatch) {
                springLatch = false;
                sim.springJump(race ? RaceClient.rules().spring() : kart.springStrength());
            }
            world.prepare(sim.position(), sim.velocity().y / 256.0);
            int surface = world.surfaceAt(sim.position());
            if (surface >= 0) {
                sim.groundType = Surface.terrain(surface);
                // offroad tires (or a race's offroad rule): grass, dirt, sand, snow and mud drive like asphalt
                if (offroadActive(kart) && (sim.groundType == com.craftteamracing.physics.Terrain.DIRT || sim.groundType == com.craftteamracing.physics.Terrain.SLOW_DIRT
                        || sim.groundType == com.craftteamracing.physics.Terrain.SNOW || sim.groundType == com.craftteamracing.physics.Terrain.MUD)) {
                    sim.groundType = com.craftteamracing.physics.Terrain.ASPHALT;
                }
                if (Surface.isPad(surface) && sim.grounded() && com.craftteamracing.track.RaceClient.rules().boostPads() && !RaceClient.hold()) {          // not while held on the grid: a pad under the kart's slot must not set it off
                    if (Surface.isSuperPad(surface)) sim.superTurboPad();
                    else sim.turboPad();
                }
            }
            // water and lava: a fall into them is cushioned; a kart on the bottom drives as in water; the Floater is on top of the water and has none of it
            int fluidKind = 0;
            if (mc.level != null) {
                var fluidState = mc.level.getFluidState(net.minecraft.core.BlockPos.containing(kart.getX(), kart.getY() + 0.35, kart.getZ()));
                fluidKind = fluidState.is(net.minecraft.tags.FluidTags.LAVA) ? (floaterMode == 2 ? 0 : 2) : fluidState.is(net.minecraft.tags.FluidTags.WATER) && !floating ? 1 : 0;
            }
            double yNow = kart.getY();
            if (fluidKind == 0 && !floating && mc.level != null && lastFluidY > yNow + 0.05) {
                // a fast fall may cross a pond between two steps without ever being in it at a step: look along the way (the surface still breaks the fall)
                for (double y = yNow + 0.35; y <= lastFluidY + 0.35 && fluidKind == 0; y += 0.4) {
                    if (mc.level.getFluidState(net.minecraft.core.BlockPos.containing(kart.getX(), y, kart.getZ())).is(net.minecraft.tags.FluidTags.WATER)) fluidKind = 1;
                }
                if (fluidKind == 1) sim.splash();
            } else if (fluidKind == 1 && lastFluidKind == 0) {
                sim.splash();
            }
            lastFluidKind = fluidKind;
            lastFluidY = yNow;
            sim.setFluid(fluidKind);
            if (fluidKind != 0 && sim.grounded()) sim.groundType = com.craftteamracing.physics.Terrain.WATER;
            if (sim.cloudActive() && mc.level != null) {
                // the rain cloud over the kart (the original's is a 3D cloud model with a rain emitter; these particles stand in for it)
                // (the cloud and its rain are drawn by RainCloudFx)
            }
            if (sim.cloudIce()) sim.groundType = com.craftteamracing.physics.Terrain.ICE;          // RAIN_CLOUD_EFFECT_ICE_TERRAIN
            sim.setOthers(nearbyKarts(mc, kart));
            sim.powerFruit = com.craftteamracing.pickup.ItemClient.powerFruit();
            int itemEffect = com.craftteamracing.pickup.ItemClient.takeEffect();
            if (itemEffect == com.craftteamracing.pickup.ItemManager.EFFECT_TURBO || itemEffect == com.craftteamracing.pickup.ItemManager.EFFECT_TURBO_CHARGED) {
                sim.itemTurbo(itemEffect == com.craftteamracing.pickup.ItemManager.EFFECT_TURBO_CHARGED);
            } else if (itemEffect == com.craftteamracing.pickup.ItemManager.EFFECT_SLOW) {
                tntUntilMs = System.currentTimeMillis() + (com.craftteamracing.pickup.TntEntity.FUSE_TICKS + 12) * 50L;
            } else if (itemEffect == com.craftteamracing.pickup.ItemManager.EFFECT_SPIN) {
                sim.itemSpin();
                ScreenFlash.start();
            } else if (itemEffect == com.craftteamracing.pickup.ItemManager.EFFECT_BURN) {
                sim.itemBurn();
            } else if (itemEffect == com.craftteamracing.pickup.ItemManager.EFFECT_SQUISH) {
                sim.itemSquish();
            } else if (itemEffect == com.craftteamracing.pickup.ItemManager.EFFECT_HIGH_JUMP) {
                sim.forceHighJump();
            } else if (itemEffect >= com.craftteamracing.pickup.ItemManager.EFFECT_CLOCK) {
                int code = itemEffect - com.craftteamracing.pickup.ItemManager.EFFECT_CLOCK;
                sim.setClock(code >> 1, ((code & 1) == 1 ? com.craftteamracing.pickup.ItemManager.CLOCK_TICKS_CHARGED : com.craftteamracing.pickup.ItemManager.CLOCK_TICKS) * 50);
                ScreenWarp.echo((code & 1) == 1 ? com.craftteamracing.pickup.ItemManager.CLOCK_TICKS_CHARGED : com.craftteamracing.pickup.ItemManager.CLOCK_TICKS);          // DISPLAY_Blur_Main while the clock holds the racer
            } else if (itemEffect >= com.craftteamracing.pickup.ItemManager.EFFECT_CLOUD) {
                sim.setCloud(itemEffect - com.craftteamracing.pickup.ItemManager.EFFECT_CLOUD, com.craftteamracing.pickup.ItemManager.CLOUD_TICKS * 50);
            } else if (itemEffect == com.craftteamracing.pickup.ItemManager.EFFECT_CLEAR) {
                tntUntilMs = 0;
            } else if (itemEffect == com.craftteamracing.pickup.ItemManager.EFFECT_BLAST) {
                sim.itemBlast();
                tntUntilMs = 0;                       // the crate on the head is gone
                ScreenFlash.start();                  // RB_Burst_CollThread: the victim's screen flashes white (fadeFromBlack 0x1fff)
            }
            sim.tntOnHead = System.currentTimeMillis() < tntUntilMs;
            sim.springLocked = System.currentTimeMillis() < tntUntilMs + 600L;
            sim.speedCapPercent = com.craftteamracing.track.RaceClient.rules().maxSpeedPercent();
            sim.tick(world, input);
            sendHits(mc, kart, sim.drainBumps());
            maskContacts(mc, kart);
            playEvents(mc, sim.drainEvents());
            snapshot(false);
        }
        place(mc, player, kart, accumulator / KartSim.TICK_SECONDS, dt);

        // Sneak dismounts: tell the server where the kart really is first (see KartDismountPayload).
        if (mc.options.keyShift.isDown()) {
            if (!dismountSent) {
                dismountSent = true;
                PacketDistributor.sendToServer(new KartDismountPayload(kart.getId(), kart.getX(), kart.getY(), kart.getZ()));
            }
        } else {
            dismountSent = false;
        }
    }

    /**
     * The other karts within bumping range (VehPhysForce_CollideDrivers' thread search): their position in lu and the velocity they
     * moved with over the last game tick, in lu/256 per sim frame.
     */
    private static List<KartBump.Other> nearbyKarts(Minecraft mc, KartEntity self) {
        double reach = KartScale.toBlocks(2.0 * KartBump.HIT_RADIUS) + 1.0;
        boolean entities = contactOn();
        // A kart with the mask on, or its Slime Shield up, drives through the other karts and they through it (it hurts what it touches, see maskContacts and ItemManager.crashAttack); nothing
        // else gets this: every other kart, and every mob and boat, keeps colliding. The original bounces the two once on the frame they meet and the blast then throws the other kart up and
        // away, so it feels like passing through. For a moment after the protection ends (the bubble pops on the hit) the one that was hit is still being launched, and the two do not collide yet.
        long nowMs = System.currentTimeMillis();
        boolean ghost = ghosting(self, nowMs) || self.ethereal();          // a finished kart is as good as not there, to the others and to itself
        boolean selfRecent = recentGhost(self, nowMs);
        double touch = KartScale.toBlocks(2.0 * KartBump.HIT_RADIUS) + 0.3;
        List<Entity> near = mc.level.getEntitiesOfClass(Entity.class, self.getBoundingBox().inflate(reach),
                e -> {
                    if (e == self || e.isRemoved() || e.isSpectator() || self.hasPassenger(e)) return false;
                    if (!(e instanceof KartEntity k)) return entities && bumpable(e);
                    boolean kGhost = ghosting(k, nowMs);
                    if (ghost || kGhost) {
                        if (e.distanceTo(self) < touch) TOUCHED.put(k.getId(), nowMs);           // who the protected kart is in touch with, for the grace below
                        return false;
                    }
                    if (k.ethereal()) return false;
                    Long t = TOUCHED.get(k.getId());
                    return !((selfRecent || recentGhost(k, nowMs)) && t != null && nowMs - t < GHOST_GRACE_MS);
                });
        if (near.isEmpty()) {
            return List.of();
        }
        double perFrame = KartScale.LU_PER_BLOCK * 20.0 / 30.0 * 256.0; // blocks per game tick -> lu/256 per sim frame
        List<KartBump.Other> out = new ArrayList<>(near.size());
        for (Entity k : near) {
            V3 v = new V3((k.getX() - k.xo) * perFrame, (k.getY() - k.yo) * perFrame, (k.getZ() - k.zo) * perFrame);
            if (k instanceof KartEntity other) {
                out.add(new KartBump.Other(toLu(k.position()), v, KartBump.weight(com.craftteamracing.tuning.TuningClient.resolve(other.tuning()).stats())));
            } else {
                out.add(new KartBump.Other(toLu(k.position()), v, ContactRules.weight(k.getBbWidth(), k.getBbHeight()),
                        ContactRules.reach(k.getBbWidth()), k.getId()));
            }
        }
        return out;
    }

    /** The grace after a kart's protection (the mask or the bubble) in which the kart it hit, still being launched, does not collide with it. */
    private static final long GHOST_GRACE_MS = 800L;
    private static final java.util.Map<Integer, Long> GHOST_SEEN = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Long> TOUCHED = new java.util.HashMap<>();

    /** Whether the kart is protected from, and through, other karts: the mask is on, or the Slime Shield is up (not while it pops). Remembers when it last was. */
    private static boolean ghosting(KartEntity k, long now) {
        int shield = k.shield();
        boolean on = k.maskState() != 0 || shield > 0 || shield == -1;
        if (on) GHOST_SEEN.put(k.getId(), now);
        else if (GHOST_SEEN.size() > 64) {
            GHOST_SEEN.values().removeIf(t -> now - t > 10_000L);
            TOUCHED.values().removeIf(t -> now - t > 10_000L);
        }
        return on;
    }

    private static boolean recentGhost(KartEntity k, long now) {
        Long t = GHOST_SEEN.get(k.getId());
        return t != null && now - t < GHOST_GRACE_MS;
    }

    /** What a kart runs into besides other karts: living things (mobs, players on foot) and vehicles (boats, minecarts) that are not riding. */
    private static boolean bumpable(Entity e) {
        return (e instanceof LivingEntity && !(e instanceof ArmorStand) || e instanceof VehicleEntity) && e.getVehicle() == null;
    }

    private static boolean contactOn() {
        try {
            return CtrConfig.ENTITY_CONTACT.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }

    /** The server throws and hurts what this kart hit at speed (kart against kart has no damage, as in CTR). */
    private static void sendHits(Minecraft mc, KartEntity kart, List<KartSim.Bump> bumps) {
        long now = System.currentTimeMillis();
        for (KartSim.Bump b : bumps) {
            if (b.id() < 0 && b.impact() > 0xA00 && sim.turboItem() && now - lastSquishSent > 1000L) {
                // VehPhysCrash_Attack: an impact over 0xa00 by a kart under a turbo item boost squashes the kart it ran into (the server checks the rest)
                KartEntity hit = null;
                double best = KartScale.toBlocks(2.0 * KartBump.HIT_RADIUS) + 1.0;
                for (KartEntity other : mc.level.getEntitiesOfClass(KartEntity.class, kart.getBoundingBox().inflate(best), e -> e != kart && !e.isRemoved())) {
                    double d = other.distanceTo(kart);
                    if (d < best) {
                        best = d;
                        hit = other;
                    }
                }
                if (hit != null) {
                    lastSquishSent = now;
                    PacketDistributor.sendToServer(new com.craftteamracing.net.KartSquishPayload(kart.getId(), hit.getId()));
                }
            }
            if (b.id() < 0 || b.impact() < KartBump.FEEDBACK_MIN_IMPACT) {
                continue;
            }
            Long last = LAST_HIT_SENT.get(b.id());
            if (last != null && now - last < ContactRules.HIT_COOLDOWN_TICKS * 50L) {
                continue;
            }
            LAST_HIT_SENT.put(b.id(), now);
            if (LAST_HIT_SENT.size() > 256) {
                LAST_HIT_SENT.values().removeIf(t -> now - t > 5000);
            }
            // the normal runs from the entity to the kart; the entity is hit the opposite way
            PacketDistributor.sendToServer(new KartHitPayload(kart.getId(), b.id(), b.impact(), (float) -b.normal().x, (float) -b.normal().z));
        }
    }

    private static final java.util.Map<Integer, Long> LAST_HIT_SENT = new java.util.HashMap<>();
    private static long lastSquishSent;
    private static final java.util.Map<Integer, Long> MASK_SENT = new java.util.HashMap<>();

    /**
     * The mask's touch (VehPhysCrash_Attack): the kart that has it on goes through the karts it touches (they are no body to it, see nearbyKarts) and hurts each one, whatever the speed of
     * the touch. The server checks the rest (distance, the victim's shield, the mask on both).
     */
    private static void maskContacts(Minecraft mc, KartEntity kart) {
        if (!sim.maskActive() || kart.ethereal()) return;
        long now = System.currentTimeMillis();
        double reach = KartScale.toBlocks(2.0 * KartBump.HIT_RADIUS) + 0.3;
        for (KartEntity other : mc.level.getEntitiesOfClass(KartEntity.class, kart.getBoundingBox().inflate(reach), e -> e != kart && !e.isRemoved() && !e.ethereal() && e.getControllingPassenger() != null)) {
            if (other.distanceTo(kart) > reach) continue;
            Long last = MASK_SENT.get(other.getId());
            if (last != null && now - last < 500L) continue;
            MASK_SENT.put(other.getId(), now);
            PacketDistributor.sendToServer(new com.craftteamracing.net.KartSquishPayload(kart.getId(), other.getId()));
        }
        if (MASK_SENT.size() > 64) MASK_SENT.values().removeIf(t -> now - t > 5000);
    }

    /** Whether the kart ignores rough terrain: in a race its rules say so (they override the kart's own switch); otherwise the kart's offroad tires are installed and switched on. */
    private static boolean offroadActive(KartEntity kart) {
        return com.craftteamracing.track.RaceClient.active() ? com.craftteamracing.track.RaceClient.rules().offroad() : kart.offroadOn();
    }

    private static boolean springLatch;

    /** A rain cloud is over the local kart (the sim's rain cloud timer runs): RainCloudFx draws it. */
    public static boolean cloudActive() {
        return sim != null && sim.cloudActive();
    }

    private static boolean dismountSent;
    private static net.minecraft.world.level.Level worldLevel;

    /** Pushes the sim state to the previous/current slots. */
    private static void snapshot(boolean both) {
        if (!both) {
            prevPos = curPos;
            prevHeading = curHeading;
            prevDrawn = curDrawn;
        }
        curPos = sim.position();
        curHeading = sim.heading();
        curDrawn = sim.drawnYaw();
        if (both) {
            prevPos = curPos;
            prevHeading = curHeading;
            prevDrawn = curDrawn;
        }
    }

    private static void place(Minecraft mc, LocalPlayer player, KartEntity kart, double alpha, double dt) {
        V3 p = new V3(Mth.lerp(alpha, prevPos.x, curPos.x), Mth.lerp(alpha, prevPos.y, curPos.y),
                Mth.lerp(alpha, prevPos.z, curPos.z));
        double x = KartScale.toBlocks(p.x);
        double y = KartScale.toBlocks(p.y);
        double z = KartScale.toBlocks(p.z);
        int heading = FixedMath.wrap(prevHeading + (int) Math.round(alpha * FixedMath.diff(curHeading, prevHeading)));
        int drawn = FixedMath.wrap(prevDrawn + (int) Math.round(alpha * FixedMath.diff(curDrawn, prevDrawn)));
        float yaw = KartScale.angleToYaw(heading);

        kart.clearLerp();
        kart.setPos(x, y, z);
        kart.xo = x;
        kart.yo = y;
        kart.zo = z;
        kart.xOld = x;
        kart.yOld = y;
        kart.zOld = z;
        kart.setYRot(yaw);
        kart.yRotO = yaw;
        V3 v = sim.velocity();
        double perTick = 30.0 / 20.0 / KartScale.LU_PER_BLOCK / 256.0; // lu/256 per sim frame -> blocks per game tick
        kart.setDeltaMovement(v.x * perTick, v.y * perTick, v.z * perTick);
        kart.setOnGround(sim.grounded());
        kart.setDrawnYawAngle(drawn);
        kart.setVisualFlags(visualFlags());
        kart.setEngineData(engineData());
        kart.setTilt(tiltData());

        // the rider sits where the kart says, every frame (vanilla only does it once a tick)
        Vec3 seat = kart.getPassengerRidingPosition(player);
        Vec3 attach = player.getVehicleAttachmentPoint(kart);
        double px = seat.x - attach.x;
        double py = seat.y - attach.y;
        double pz = seat.z - attach.z;
        player.setPos(px, py, pz);
        player.xo = px;
        player.yo = py;
        player.zo = pz;
        player.xOld = px;
        player.yOld = py;
        player.zOld = pz;

        // chase camera: the view swings round behind the kart (Mario Kart style), never against the mouse for long
        float follow = (float) (1.0 - Math.exp(-dt * 7.0));
        float camYaw = Mth.rotLerp(follow, player.getYRot(), KartScale.angleToYaw(sim.drawnYaw()));
        player.setYRot(camYaw);
        player.yRotO = camYaw;
        player.setXRot(Mth.lerp(follow, player.getXRot(), 12.0F));
        player.xRotO = player.getXRot();
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
    }

    private static int visualFlags() {
        // the exhaust: on the grid the revving engine decides (VehEmitter: its state busy none, ready dark, below the acceleration speed bright), otherwise the powerslide meter and the flame
        int mode;
        if (rev != null) {
            mode = switch (rev.state()) {
                case BUSY -> 0;
                case READY -> 2;
                default -> 1;
            };
        } else {
            mode = sim.exhaustMode();
        }
        return KartFlags.withExhaust(KartFlags.encode(sim), mode);
    }

    private static int engineData() {
        int[] e = sim.engineSound();
        return (e[0] & 0xFF) | ((e[1] & 0xFF) << 8) | ((sim.steerCode() & 31) << 16);
    }

    /** The kart's smoothed ground normal (what it leans on) as two bytes: x and z, value = n * 127 + 127. */
    private static int tiltData() {
        com.craftteamracing.physics.V3 up = sim.visualNormal();
        int nx = (int) Math.round(Math.max(-1.0, Math.min(1.0, up.x)) * 127.0) + 127;
        int nz = (int) Math.round(Math.max(-1.0, Math.min(1.0, up.z)) * 127.0) + 127;
        return (nx & 0xFF) | ((nz & 0xFF) << 8);
    }

    /** The grid rev the HUD shows, or null when not revving. */
    public static RevEngine rev() {
        return rev;
    }

    /** The sim the HUD reads, or null when the local player is not driving. */
    public static KartSim activeSim() {
        return sim;
    }

    /** One-shot sounds for what just happened, played at the listener (it is your own kart). */
    /** While a TNT crate is on or flying onto the kart: every jump that starts is reported (the server counts them). */
    private static long tntUntilMs;

    private static void playEvents(Minecraft mc, List<KartEvent> events) {
        for (KartEvent e : events) {
            if (e.type() == KartEvent.Type.HOP && System.currentTimeMillis() < tntUntilMs) {
                PacketDistributor.sendToServer(new com.craftteamracing.pickup.TntShakePayload());       // the server counts the jumps, as the game does
            }
            SoundEvent sound = null;
            float pitch = 1.0F;
            float volume = 1.0F;
            switch (e.type()) {
                case HOP -> sound = ModSounds.HOP.get();
                case LAND -> {
                    if (e.value() >= 160) {
                        sound = ModSounds.LAND.get();
                        volume = Math.min(1.0F, 0.35F + e.value() / 900.0F);
                    }
                }
                case DRIFT_START -> { }                 // no sound: the original has none (the mod's placeholder is not played)
                case BOOST -> {
                    // VehFire_Audio: boost 1 plays higher and quiet (distortion 0x94, volume 0x80), boost 2 plain (0xC0),
                    // boost 3 lower and full (0x6C, 0xFF); the pitch factor is 2^((d - 0x80) / 128)
                    sound = ModSounds.BOOST.get();
                    switch (e.value()) {
                        case 1 -> { pitch = 1.114F; volume = 0x80 / 255.0F + 0.15F; }
                        case 2 -> { pitch = 1.0F; volume = 0xC0 / 255.0F + 0.1F; }
                        default -> { pitch = 0.897F; volume = 1.0F; }
                    }
                }
                case BOOST_EARLY -> { }                 // no sound: the original's early boost only puffs smoke
                case METER_RED -> { }                   // no sound: the original has none (a pling every time the boost is ready is the mod's own invention)
                case METER_FULL -> sound = ModSounds.METER_FULL.get();
                case HANG_TIME -> {
                    sound = ModSounds.HANG_TIME.get();
                    pitch = 1.0F + 0.15F * (e.value() - 1);
                }
                case WALL_RUB -> sound = ModSounds.WALL_HIT.get();
                case SLAM -> sound = ModSounds.SLAM.get();
                // OtherFX_DriverCrashing: crash 0xA from volume 0xDD up, 0xC above 0xA0, else 0xB; the volume is VehPhysCrash's
                case BUMP -> {
                    sound = e.value() >= 0xDD ? ModSounds.CRASH_HARD.get() : e.value() > 0xA0 ? ModSounds.CRASH_MID.get() : ModSounds.CRASH_SOFT.get();
                    volume = e.value() / 255.0F;
                }
                case BURN -> sound = ModSounds.BURN.get();
                case SQUISH -> sound = ModSounds.SQUISH.get();
                case SQUISH_RESTORE -> sound = ModSounds.SQUISH_RESTORE.get();
                case SPIN_OUT -> {
                    if (com.craftteamracing.CtrClientConfig.SPIN_OUT_SOUND.get()) sound = ModSounds.SPIN_OUT.get();         // the mod's own, off unless the player wants it
                }
                default -> { }
            }
            if (sound != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
            }
        }
    }

    private static final int LEAVE_TICKS = 40;
    private static int leaveHeld;

    private static V3 toLu(Vec3 pos) {
        return new V3(KartScale.toLu(pos.x), KartScale.toLu(pos.y), KartScale.toLu(pos.z));
    }

    /** Once a game tick, tell the server where the kart is and how it looks (replaces vanilla's move-vehicle packet). */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (CraftTeamRacingClient.KEY_ITEM.consumeClick()) {
            if (mc.player != null && mc.player.getVehicle() instanceof KartEntity) {
                PacketDistributor.sendToServer(new com.craftteamracing.pickup.UseItemPayload(mc.options.keyUp.isDown(), mc.options.keyDown.isDown()));
            }
        }
        // leaving a race: the key held for two seconds
        if (RaceClient.active() && mc.player != null && mc.player.getVehicle() instanceof KartEntity && CraftTeamRacingClient.KEY_LEAVE.isDown()) {
            leaveHeld++;
            if (leaveHeld >= LEAVE_TICKS) {
                leaveHeld = 0;
                PacketDistributor.sendToServer(new com.craftteamracing.track.LeaveRacePayload());
            } else if (leaveHeld % 10 == 1) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.craftteamracing.race.leaving", leaveHeld * 100 / LEAVE_TICKS), true);
            }
        } else {
            leaveHeld = 0;
        }
        while (CraftTeamRacingClient.KEY_RESPAWN.consumeClick()) {
            if (RaceClient.active()) {
                PacketDistributor.sendToServer(new com.craftteamracing.track.RespawnPayload());
            }
        }
        if (sim != null && mc.player != null && mc.player.getVehicle() instanceof KartEntity kart
                && kart.getId() == simEntityId && !mc.isPaused()) {
            PacketDistributor.sendToServer(new KartStatePayload(kart.getId(), kart.getX(), kart.getY(), kart.getZ(),
                    kart.getYRot(), sim.drawnYaw(), visualFlags(), engineData(), tiltData()));
        }
    }
}
