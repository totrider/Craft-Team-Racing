package com.ctrcraft.pickup;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModSounds;
import com.ctrcraft.items.HeldItem;
import com.ctrcraft.items.RuleSet;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.track.RaceManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What a driver carries: the power fruit count (0-10) and the held power-up, and what touching a pickup does (server side). CTR's rules, from
 * {@code RB_Fruit.c} and {@code RB_Crate.c}: a fruit is +1 power fruit, a fruit crate 5 + (random mod 4), and a power-up crate only starts the roulette
 * (90 frames, 60 game ticks) for a driver who holds nothing and is not already rolling; the crate is used up either way. The item is drawn
 * from the rule set's table when the roulette ends. The power fruit count and the held power-up belong to the <b>kart</b> (saved with it, so getting out and in
 * again, or driving free-roam, keeps them); a race clears them for its entrants when they are put on the grid.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID)
public final class ItemManager {
    private ItemManager() {}

    /** 90 frames at 30 Hz (VehPhysProc: itemRollTimer = 90) in game ticks. */
    public static final int ROLL_TICKS = 60;
    public static final int MAX_POWER_FRUIT = 10;
    /** DRIVER_WUMPA_JUICED_COUNT. */
    public static final int CHARGED = 10;

    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_TURBO = 1;
    public static final int EFFECT_TURBO_CHARGED = 2;
    /** A TNT crate sits on the kart's head (nothing slows the kart: the decomp has no such effect; this only starts the jump counting that shakes it off). */
    public static final int EFFECT_SLOW = 3;
    /** An explosion hit the kart. */
    public static final int EFFECT_BLAST = 4;
    /** Whether the kart already has a rain cloud over it (a second red beaker refreshes it instead of starting one). */
    public static boolean hasCloud(ServerPlayer p) {
        State s = state(p);
        return s != null && s.cloudTicks > 0;
    }

    /** A beaker hit the kart: it spins out. */
    public static final int EFFECT_SPIN = 6;
    /** A kart under a turbo item boost ran into this one: flat for 3.84 s (VEH_PICK_DAMAGE_SQUISH), with a spin out. */
    public static final int EFFECT_SQUISH = 7;
    /** The kart that squashed another is thrown into a high jump (FORCED_JUMP_HIGH). */
    public static final int EFFECT_HIGH_JUMP = 8;
    /** The kart is set on fire (VEH_PICK_DAMAGE_BURN): a spin out, a slow for 3.84 s, flames and smoke. */
    public static final int EFFECT_BURN = 9;
    /** The red beaker's rain cloud: this plus the RAIN_CLOUD_EFFECT_* number (0 slow, 1 item roll, 2 icy ground, 3 jump lockout). */
    public static final int EFFECT_CLOUD = 10;
    /**
     * The Clock (VehPickupItem_ShootNow): this plus the victim's place (0 is first, up to 7) times two plus one for a charged one. Taken after the spin it comes with
     * (RB_Hazard_HurtDriver(victim, 1, ...): a spin, which a shield takes instead).
     */
    public static final int EFFECT_CLOCK = 20;
    /** CLOCK_HURT_DURATION_NORMAL 0x1e00 and _JUICED 0x2d00 ms, in game ticks. */
    public static final int CLOCK_TICKS = 154;
    public static final int CLOCK_TICKS_CHARGED = 230;
    /**
     * Out of a race, a "global" effect (the Clock, and the ones to come) has no field of racers to reach, so it reaches the karts within this many blocks of the user instead.
     * In a race it reaches every other racer, as the original does.
     */
    public static final double GLOBAL_EFFECT_RADIUS = 48.0;
    /** rcloud->timeMS = 0x1e00: 7.68 s in game ticks. */
    public static final int CLOUD_TICKS = 154;

    /**
     * RB_RainCloud_Init: a red beaker puts a cloud over the kart. The first one: item roll if the kart holds an item, otherwise slow; a second one restarts the 7.68 s and
     * draws the effect again ((rng % 400) / 100: slow, item roll, icy ground or jump lockout). Slow takes the damaged speed off the top speed (KartSim.targetSpeed); heavy friction is
     * not drawn by the game's own code either.
     */
    public static void cloud(ServerPlayer p, boolean fresh) {
        State s = state(p);
        if (s == null) return;
        int effect = fresh ? (s.held.isEmpty() ? 0 : 1) : RANDOM.nextInt(4);
        s.cloudTicks = CLOUD_TICKS;
        if (effect == 1 && !s.held.isEmpty() && s.roll == 0) {
            // the held item is swapped for a new one drawn when the cloud is gone
            s.held = "";
            s.fixed = "";
            s.roll = CLOUD_TICKS;
        }
        s.cloudPending = EFFECT_CLOUD + effect;      // a moment after the spin: the client takes one effect at a time
        s.cloudDelay = 4;
    }

    /** The TNT crate was shaken off: no more slowing. */
    public static final int EFFECT_CLEAR = 5;
    /** What being blasted costs in power fruit (VEH_TUMBLE_WUMPA_PENALTY). */
    public static final int BLAST_POWER_FRUIT_PENALTY = 3;

    /** The driver's kart loses fruit (CTR: not while the mask is on, which is not built yet). */
    public static void losePowerFruit(ServerPlayer p, int count) {
        State s = state(p);
        if (s == null || s.powerFruit == 0) return;
        s.powerFruit = Math.max(0, s.powerFruit - count);
        sync(p, s);
    }

    /** Sends the driver's client an effect to apply to its kart (the client sim is the one that moves it). */
    public static boolean applyEffect(ServerPlayer p, int effect) {
        State s = state(p);
        if (s == null) return true;
        if (harmful(effect)) {
            // a hit pops the shield and does nothing else (VehPickState: SHIELD_FLAG_POP_ON_DAMAGE, then invincibleTimer 0x2a0 ms), and the driver cannot be hit again for that moment
            // (VehPickState_NewState returns 0: the hazard's own side effects, the power fruit lost, the rain cloud, a TNT crate landing on the head, do not happen)
            if (s.immune > 0) return false;
            if (s.shield != 0) {
                popShield(p, s);
                return false;
            }
        }
        s.effect = effect;
        s.effectSeq++;
        sync(p, s);
        // RB_Hazard_HurtDriver sets damageColorTimer: every racer's HUD portrait of this driver flashes red
        if (effect == EFFECT_SPIN || effect == EFFECT_BLAST) PacketDistributor.sendToAllPlayers(new HitFlashPayload(p.getUUID()));
        return true;
    }

    /** The effects that hurt: slow, blast, spin and the rain cloud's. */
    private static boolean harmful(int effect) {
        return effect == EFFECT_SLOW || effect == EFFECT_BLAST || effect == EFFECT_SPIN || effect == EFFECT_SQUISH || effect == EFFECT_BURN || effect >= EFFECT_CLOUD;
    }

    private static final int SHIELD_TICKS = 240;            // SHIELD_DURATION_NORMAL 0x2d00 ms at 32 ms a frame: 360 frames, 12 seconds
    private static final int SHIELD_FADE_TICKS = 40;        // the last 60 frames fade
    private static final int SHIELD_POP_TICKS = 8;          // RB_SHIELD_POP_SCALE: 11 frames
    private static final int SHIELD_IMMUNE_TICKS = 14;      // VEH_PICK_SHIELD_DAMAGE_INVINCIBLE_TIMER 0x2a0 ms

    private static void setKartShield(ServerPlayer p, State s) {
        if (!(p.getVehicle() instanceof KartEntity kart)) return;
        kart.setShield(s.shieldPop > 0 ? -2 : s.shield < 0 ? -1 : s.shield == 0 ? 0 : Math.min(s.shield, SHIELD_FADE_TICKS + 1));
    }

    /** A driver who finishes the race loses the bubble (RB_ShieldDark_ThTick_Grow: ACTION_RACE_FINISHED pops it). */
    public static void onRaceFinished(ServerPlayer p) {
        State s = state(p);
        if (s != null && s.shield != 0 && s.shieldPop == 0) popShield(p, s);
    }

    /**
     * The crash attack (VehPhysCrash_Attack): a kart with its bubble up that runs into a kart without one pops its bubble (SHIELD_FLAG_CRASH_ATTACK: the screen fades from black and the
     * pop sound plays) and blasts the other kart (damage type 2). Both with a bubble up, or neither, do nothing.
     */
    private static void crashAttack(ServerPlayer p, State s) {
        if (!(p.getVehicle() instanceof KartEntity kart)) return;
        for (KartEntity other : p.serverLevel().getEntitiesOfClass(KartEntity.class, kart.getBoundingBox().inflate(0.4))) {
            if (other == kart || !(other.getControllingPassenger() instanceof ServerPlayer victim) || victim == p) continue;
            State vs = other.items();
            if (vs.shield != 0 || vs.immune > 0) continue;
            popShield(p, s);
            applyEffect(victim, EFFECT_BLAST);
            return;
        }
    }

    /** A hit pops the shield: the animation, the sound (0x58) and a moment's immunity. */
    private static void popShield(ServerPlayer p, State s) {
        s.shield = 0;
        s.shieldPop = SHIELD_POP_TICKS;
        s.immune = SHIELD_IMMUNE_TICKS;
        setKartShield(p, s);
        p.serverLevel().playSound(null, p.blockPosition(), ModSounds.SHIELD_POP.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    /** The use key with the bubble round the kart: it is thrown forward as a small explosive (RB_ShieldDark_ThTick_Grow, SHIELD_FLAG_SHOOT). */
    private static void throwShield(ServerPlayer p, State s) {
        KartEntity kart = (KartEntity) p.getVehicle();
        boolean blue = s.shield < 0;
        ShieldBombEntity bomb = com.ctrcraft.ModEntities.SHIELD_BOMB.get().create(p.serverLevel());
        if (bomb != null && BombEntity.canLaunch()) {
            bomb.setBlue(blue);
            bomb.launch(p, kart, false, false);
            p.serverLevel().addFreshEntity(bomb);
        }
        s.shield = 0;
        setKartShield(p, s);
        p.serverLevel().playSound(null, p.blockPosition(), ModSounds.SHIELD_POP.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    /** What a kart carries. {@code power fruit} and {@code held} are saved with the kart; the rest is the roulette and the effect in progress. */
    public static final class State {
        public int powerFruit;
        public String held = "";
        int roll;
        String fixed = "";
        int effect;
        int effectSeq;
        int cloudTicks;
        /** The turbo item's boost, ticks left (0x960 ms): ACTION_TURBO_ITEM, which lets this kart squash another it runs into. */
        int turboItemTicks;
        /** The Clock's hold on this kart: ticks left (no weapon can be fired, a crate gives nothing), and the effect that tells the client's sim how hard it slows, after a moment's delay. */
        int clockTicks;
        int clockPending;
        int clockDelay;
        int cloudPending;
        int cloudDelay;
        /** HELD_ITEM_STACK_COUNT: the shots a bomb_3x / missile_3x still has (numHeldItems), 0 for every other item. */
        int count;
        /** The cooldown between two shots of a stack, game ticks (noItemTimer: 5 frames). */
        int cooldown;
        /** The bomb this kart's driver last fired, while it may still be rolling (the use key detonates it). */
        UUID bomb;
        /** The Power Shield round the kart: ticks left (green), -1 (blue: it stays), 0 none; the ticks its pop animation has left; and the ticks of immunity after it popped. */
        int shield;
        int shieldPop;
        int immune;

        void clear() {
            cloudTicks = 0;
            turboItemTicks = 0;
            clockTicks = 0;
            clockDelay = 0;
            bomb = null;
            powerFruit = 0;
            held = "";
            count = 0;
            cooldown = 0;
            shield = 0;
            shieldPop = 0;
            immune = 0;
            roll = 0;
            fixed = "";
            effect = EFFECT_NONE;
        }
    }

    /** The kart each player's client was last told about (so a change of kart, or getting out, is sent once). */
    private static final Map<UUID, UUID> SYNCED = new HashMap<>();
    private static final Random RANDOM = new Random();
    /** The rules every race uses until the rulebook can set them per track. */
    /** The rules of the race a player is in, or CTR's. */
    public static RuleSet rules(ServerPlayer p) {
        RuleSet r = RaceManager.rulesOf(p.getUUID());
        return r == null ? RuleSet.CTR : r;
    }

    /** The display name of a power-up id ({@code ctrcraft:turbo} is the {@code power.ctrcraft.turbo} entry in the lang file). */
    public static Component nameOf(String id) {
        int colon = id.indexOf(':');
        return Component.translatable("power." + (colon < 0 ? CtrCraftMod.MODID : id.substring(0, colon)) + "." + (colon < 0 ? id : id.substring(colon + 1)));
    }

    /** The state of the kart the player sits in, or null. */
    private static State state(ServerPlayer p) {
        return p.getVehicle() instanceof KartEntity kart ? kart.items() : null;
    }

    /** A race puts its entrants on the grid with nothing: no fruit, no power-up. */
    public static void reset(ServerPlayer p) {
        State s = state(p);
        if (s == null) return;
        s.clear();
        sync(p, s);
    }

    /** A kart reached a pickup. Returns whether the pickup is used up. */
    public static boolean collect(PickupEntity pickup, PickupSpotBlockEntity spot, ServerPlayer driver) {
        State s = state(driver);
        if (s == null) return false;
        ServerLevel level = (ServerLevel) pickup.level();
        switch (pickup.kind()) {
            case FRUIT -> {
                s.powerFruit = Math.min(MAX_POWER_FRUIT, s.powerFruit + 1);
                level.playSound(null, pickup.blockPosition(), ModSounds.FRUIT_PICKUP.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            case FRUIT_CRATE -> {
                s.powerFruit = Math.min(MAX_POWER_FRUIT, s.powerFruit + 5 + RANDOM.nextInt(4));
                crateBreak(level, pickup);
            }
            case POWERUP_CRATE -> {
                // RB_Crate: a received Clock suppresses the reward, but the crate is still used up
                if (s.clockTicks == 0 && s.held.isEmpty() && s.roll == 0) {
                    s.roll = ROLL_TICKS;
                    s.fixed = spot.pick(RANDOM);         // the roulette's sound is the driver's client's (RouletteSound): a loop for as long as it runs
                }
                crateBreak(level, pickup);
            }
        }
        sync(driver, s);
        return true;
    }

    private static void crateBreak(ServerLevel level, PickupEntity pickup) {
        level.playSound(null, pickup.blockPosition(), ModSounds.CRATE_BREAK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        com.ctrcraft.net.FxPayload.send(level, "crate_break", pickup.getX(), pickup.getY(), pickup.getZ());
    }

    /** The use key. */
    public static void use(ServerPlayer p, boolean forward, boolean back) {
        State s = state(p);
        if (s == null) return;
        // a bomb of this driver is still rolling: the key detonates it (VehPhysProc: instBombThrow, tw->flags |= 2), before anything else the key does
        if (s.bomb != null) {
            Entity rolling = p.serverLevel().getEntity(s.bomb);
            s.bomb = null;
            if (rolling instanceof BombEntity bomb && !bomb.isRemoved()) {
                bomb.detonate();
                return;
            }
        }
        // the bubble round the kart is shot (VehPhysProc: instBubbleHold, after the bomb's check and before anything else)
        if (s.shield != 0 && s.shieldPop == 0) {
            throwShield(p, s);
            return;
        }
        if (s.held.isEmpty() || s.roll > 0 || s.cooldown > 0) return;
        if (s.clockTicks > 0) return;              // VehPhysProc: a driver the Clock has hit cannot fire a weapon (clockReceive != 0)
        // the 3x items are fired one at a time as the plain bomb or missile (VehPickupItem_Shoot: Bomb3x and Missile3x become the one-shot weapons)
        String id = s.held.equals(HeldItem.BOMB_3X.id()) ? HeldItem.BOMB.id() : s.held.equals(HeldItem.MISSILE_3X.id()) ? HeldItem.MISSILE.id() : s.held;
        if (id.equals(HeldItem.TURBO.id())) {
            // VehPickupItem_ShootNow: VehFire_Increment(0x960, ..., 0x80), 0x100 with ten power fruit
            s.effect = s.powerFruit >= CHARGED ? EFFECT_TURBO_CHARGED : EFFECT_TURBO;
            s.turboItemTicks = 48;
            s.effectSeq++;
            s.held = "";
            sync(p, s);
        } else if (id.equals(HeldItem.POTION.id())) {
            // VehPickupItem_ShootNow, beaker: a green one, a red one with ten power fruit; dropped behind, or thrown forward with the forward key held
            KartEntity kart = (KartEntity) p.getVehicle();
            PotionEntity potion = com.ctrcraft.ModEntities.POTION.get().create(p.serverLevel());
            if (potion != null) {
                double yaw = Math.toRadians(kart.getYRot());
                potion.setRed(s.powerFruit >= CHARGED);
                potion.setOwner(p.getUUID());
                potion.setYRot(kart.getYRot());
                if (forward) {
                    potion.setPos(kart.getX() - Math.sin(yaw) * 1.2, kart.getY() + 0.6, kart.getZ() + Math.cos(yaw) * 1.2);
                    potion.throwFrom(-Math.sin(yaw), Math.cos(yaw));
                } else {
                    potion.setPos(kart.getX() + Math.sin(yaw) * 1.6, kart.getY() + 0.1, kart.getZ() - Math.cos(yaw) * 1.6);
                }
                p.serverLevel().addFreshEntity(potion);
                p.serverLevel().playSound(null, p.blockPosition(), ModSounds.MINE_DROP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            s.held = "";
            sync(p, s);
        } else if (id.equals(HeldItem.BOMB.id())) {
            // VehPickupItem_ShootNow, WEAPON_ID_BOMB_MISSILE: rolls forward, or backward (back key held, 0.3 of the speed); bigger blast with ten power fruit
            if (!BombEntity.canLaunch()) return;          // ACTIVE_MISSILE_LIMIT: the item is kept
            KartEntity kart = (KartEntity) p.getVehicle();
            BombEntity bomb = com.ctrcraft.ModEntities.BOMB.get().create(p.serverLevel());
            if (bomb != null) {
                bomb.launch(p, kart, s.powerFruit >= CHARGED, back);
                p.serverLevel().addFreshEntity(bomb);
                s.bomb = bomb.getUUID();
                p.serverLevel().playSound(null, p.blockPosition(), ModSounds.BOMB_LAUNCH.get(), SoundSource.PLAYERS, 3.5F, 1.0F);       // SOUND_BOMB_LAUNCH 0x47
            }
            consume(s);
            sync(p, s);
        } else if (id.equals(HeldItem.SHIELD.id())) {
            // VehPickupItem_ShootNow, WEAPON_ID_SHIELD: the bubble grows round the kart; green for 360 frames, blue (ten power fruit) until it is hit or thrown
            s.shield = s.powerFruit >= CHARGED ? -1 : SHIELD_TICKS;
            s.shieldPop = 0;
            s.held = "";
            setKartShield(p, s);
            p.serverLevel().playSound(null, p.blockPosition(), ModSounds.SHIELD_ON.get(), SoundSource.PLAYERS, 1.0F, 1.0F);          // SOUND_SHIELD 0x57
            sync(p, s);
        } else if (id.equals(HeldItem.MISSILE.id())) {
            // VehPickupItem_ShootNow, WEAPON_ID_BOMB_MISSILE with the rocket: flies ahead after the nearest kart in front; faster and sharper with ten power fruit
            if (!BombEntity.canLaunch()) return;          // ACTIVE_MISSILE_LIMIT: the item is kept
            KartEntity kart = (KartEntity) p.getVehicle();
            MissileEntity missile = com.ctrcraft.ModEntities.MISSILE.get().create(p.serverLevel());
            if (missile != null) {
                missile.launch(p, kart, s.powerFruit >= CHARGED, false);
                p.serverLevel().addFreshEntity(missile);
                p.serverLevel().playSound(null, p.blockPosition(), ModSounds.MISSILE_LAUNCH.get(), SoundSource.PLAYERS, 3.5F, 1.0F);       // SOUND_MISSILE_LAUNCH 0x4a
            }
            consume(s);
            sync(p, s);
        } else if (id.equals(HeldItem.TNT.id())) {
            // VehPickupItem_ShootNow, TNT: the crate is dropped behind the kart; with ten power fruit it is a Nitro crate
            KartEntity kart = (KartEntity) p.getVehicle();
            double yaw = Math.toRadians(kart.getYRot());
            TntEntity tnt = com.ctrcraft.ModEntities.TNT.get().create(p.serverLevel());
            if (tnt != null) {
                tnt.setNitro(s.powerFruit >= CHARGED);
                tnt.setOwner(p.getUUID());
                tnt.setYRot(kart.getYRot());
                tnt.setPos(kart.getX() + Math.sin(yaw) * 1.9, kart.getY() + 0.1, kart.getZ() - Math.cos(yaw) * 1.9);
                p.serverLevel().addFreshEntity(tnt);
                p.serverLevel().playSound(null, p.blockPosition(), ModSounds.MINE_DROP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);        // SOUND_MINE_DROP
            }
            s.held = "";
            sync(p, s);
        } else if (id.equals(HeldItem.CLOCK.id())) {
            useClock(p, s);
        } else {
            // not built yet: it is used up all the same, so the roulette and the icons can be tested one after another
            p.displayClientMessage(Component.translatable("message.ctrcraft.item.not_built", nameOf(s.held)), true);
            s.held = "";
            sync(p, s);
        }
    }

    /**
     * The Clock (VehPickupItem_ShootNow, WEAPON_ID_CLOCK): every other racer is hit as by a spin (a shield takes it instead, and then nothing else happens to that racer) and gets the
     * clock timer, 7.68 s (11.52 s with ten power fruit): the top speed is cut by a bigger amount the nearer to first the racer is, no weapon can be fired and a crate gives nothing.
     * Out of a race it reaches the karts within {@link #GLOBAL_EFFECT_RADIUS} of the user.
     */
    private static void useClock(ServerPlayer p, State s) {
        boolean charged = s.powerFruit >= CHARGED;
        p.serverLevel().playSound(null, p.blockPosition(), ModSounds.CLOCK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);          // OtherFX_Play(SOUND_CLOCK)
        java.util.List<ServerPlayer> victims = RaceManager.opponents(p);
        if (victims == null) {
            victims = new java.util.ArrayList<>();
            for (ServerPlayer o : p.serverLevel().players()) {
                if (o != p && o.getVehicle() instanceof KartEntity && RaceManager.standing(o) == null && o.distanceToSqr(p) <= GLOBAL_EFFECT_RADIUS * GLOBAL_EFFECT_RADIUS) victims.add(o);
            }
        }
        for (ServerPlayer v : victims) {
            State vs = state(v);
            if (vs == null) continue;
            if (!applyEffect(v, EFFECT_SPIN)) continue;                       // the shield (or the moment after it) took the hit: no clock
            losePowerFruit(v, 1);                                              // VehPhysProc_SpinFirst_Init: a spin costs a power fruit
            RaceManager.Standing st = RaceManager.standing(v);
            int rank = st == null ? 0 : Math.min(7, st.rank());
            vs.clockTicks = charged ? CLOCK_TICKS_CHARGED : CLOCK_TICKS;
            vs.clockPending = EFFECT_CLOCK + rank * 2 + (charged ? 1 : 0);
            vs.clockDelay = 4;                                                 // a moment after the spin: the client takes one effect at a time
        }
        s.held = "";
        sync(p, s);
    }

    private static final Map<String, Integer> LAST_SQUISH = new HashMap<>();

    /**
     * VehPhysCrash_Attack, the turbo squish: a kart under a turbo item boost ran into another one that has no boost going (the impact over 0xa00 is the attacker's client's report):
     * the other is squashed (damage type 3, which a shield takes instead) and the attacker is thrown into a high jump.
     */
    public static void squish(ServerPlayer attacker, ServerPlayer victim, KartEntity victimKart) {
        State s = state(attacker);
        State vs = state(victim);
        if (s == null || vs == null || attacker == victim || s.turboItemTicks <= 0) return;
        if (com.ctrcraft.kart.KartFlags.has(victimKart.getVisualFlags(), com.ctrcraft.kart.KartFlags.BOOSTING)) return;          // driver1->reserves == 0
        int now = attacker.server.getTickCount();
        Integer last = LAST_SQUISH.put(attacker.getUUID() + ":" + victim.getUUID(), now);
        if (last != null && now - last < 40) return;
        if (!applyEffect(victim, EFFECT_SQUISH)) return;                       // the shield (or the moment after it) took the hit
        losePowerFruit(victim, 1);                                             // a squash starts with a spin, which costs a power fruit
        s.effect = EFFECT_HIGH_JUMP;
        s.effectSeq++;
        sync(attacker, s);
    }

    /** A shot of a stack is used: one fewer left and a short cooldown (noItemTimer 5 frames); the last one takes the item away. A plain item goes at once. */
    private static void consume(State s) {
        if (s.count > 1) {
            s.count--;
            s.cooldown = 4;
        } else {
            s.count = 0;
            s.held = "";
        }
    }

    /** HELD_ITEM_STACK_COUNT. */
    public static final int STACK_COUNT = com.ctrcraft.items.PowerupRegistry.stackCount(HeldItem.BOMB_3X.id());

    private static void sync(ServerPlayer p, State s) {
        PacketDistributor.sendToPlayer(p, new ItemStatePayload(s.powerFruit, s.held, s.roll, s.effect, s.effectSeq, s.count));
    }


    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            State s = state(p);
            if (s == null) {
                // out of the kart: the HUD goes blank (the kart keeps what it carries)
                if (SYNCED.remove(p.getUUID()) != null) PacketDistributor.sendToPlayer(p, ItemStatePayload.EMPTY);
                continue;
            }
            UUID kart = p.getVehicle().getUUID();
            if (!kart.equals(SYNCED.put(p.getUUID(), kart))) {
                // got in: show what this kart carries, but not the last effect it was hit by (the kart keeps its state, and the client would play that effect again:
                // the white flash and the sound of a beaker, a spin...)
                s.effect = EFFECT_NONE;
                s.effectSeq = 0;
                sync(p, s);
            }
            if (s.cooldown > 0) s.cooldown--;
            if (s.immune > 0) s.immune--;
            if (s.shieldPop > 0 && --s.shieldPop == 0) setKartShield(p, s);
            if (s.shield != 0 && s.shieldPop == 0) crashAttack(p, s);
            if (s.shield > 0) {
                if (--s.shield == 0) {
                    // it ran out: it goes with the pop sound and no animation (duration == 0)
                    p.serverLevel().playSound(null, p.blockPosition(), ModSounds.SHIELD_POP.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
                }
                setKartShield(p, s);
            }
            if (s.cloudTicks > 0) s.cloudTicks--;
            if (s.turboItemTicks > 0) s.turboItemTicks--;
            if (s.clockTicks > 0) s.clockTicks--;
            if (s.clockDelay > 0 && --s.clockDelay == 0) applyEffect(p, s.clockPending);
            if (s.cloudDelay > 0 && --s.cloudDelay == 0) applyEffect(p, s.cloudPending);
            if (s.roll > 0 && --s.roll == 0) {
                if (!s.fixed.isEmpty()) {
                    s.held = s.fixed;
                } else {
                    RaceManager.Standing st = RaceManager.standing(p);
                    s.held = st == null ? rules(p).powerups().draw(1, 0, 1, RANDOM) : rules(p).powerups().draw(st.field(), st.rank(), st.lapIndex(), RANDOM);
                }
                s.count = s.held.equals(HeldItem.BOMB_3X.id()) || s.held.equals(HeldItem.MISSILE_3X.id()) ? STACK_COUNT : 0;       // VehPhysGeneral: numHeldItems = HELD_ITEM_STACK_COUNT
                // VehPhysProc: the item is announced with OtherFX 0x5E, or 0x41 for a kart with ten power fruit
                p.serverLevel().playSound(null, p.blockPosition(), s.powerFruit >= CHARGED ? ModSounds.ITEM_GET_CHARGED.get() : ModSounds.ITEM_GET.get(),
                        SoundSource.PLAYERS, 0.8F, 1.0F);
                sync(p, s);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SYNCED.remove(event.getEntity().getUUID());
    }
}
