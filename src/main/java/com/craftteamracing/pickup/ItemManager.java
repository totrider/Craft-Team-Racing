package com.craftteamracing.pickup;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.ModSounds;
import com.craftteamracing.items.HeldItem;
import com.craftteamracing.items.RuleSet;
import com.craftteamracing.kart.KartEntity;
import com.craftteamracing.track.RaceManager;

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
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID)
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
        if (s == null || s.maskTicks > 0) return;
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

    /**
     * MASK_HEAD_DURATION_NORMAL 0x1e00 and _JUICED 0x2d00 in game ticks, and the fade that follows it (0x200, the mask still counts as on). The game takes elapsedTimeMS off the duration every
     * frame and that is 0x20 a frame at 30 frames a second (MainFrame.c), so the "ms" are 32 to a frame: 240 frames (8 s) and 360 frames (12 s), the fade 16 frames. Here 20 ticks a second.
     */
    public static final int MASK_TICKS = 160;
    public static final int MASK_TICKS_CHARGED = 240;
    public static final int MASK_FADE_TICKS = 11;

    /** A finished racer's kart touches nothing: no weapon, crate or fruit takes it into account. */
    public static boolean ethereal(ServerPlayer p) {
        return p.getVehicle() instanceof KartEntity k && k.ethereal();
    }

    /** Whether the driver's kart has the Mask on (the fade included): nothing hurts it, its fruit stay, it hurts the karts it touches. */
    public static boolean maskActive(ServerPlayer p) {
        State s = state(p);
        return s != null && s.maskTicks > 0;
    }

    /** The synced mask state of a kart: 1 Aku Aku (light), 2 Uka Uka (dark), plus 4 while it fades. */
    private static int maskCode(boolean dark, int ticksLeft) {
        return (dark ? 2 : 1) | (ticksLeft <= MASK_FADE_TICKS ? 4 : 0);
    }

    /**
     * VehPickupItem_MaskUseWeapon: the mask goes on (a second one while it is on only restarts it, with the sound), 7.68 s, 11.52 s with ten power fruit. The kart's own mask preference
     * decides which one it is (the original picks by the driver: Crash, Coco, Polar, Pura and Penta get Aku Aku, everybody else Uka Uka). A rain cloud over the kart is gone (RB_RainCloud_Init).
     */
    private static void useMask(ServerPlayer p, State s) {
        KartEntity kart = (KartEntity) p.getVehicle();
        s.maskTicks = (s.powerFruit >= CHARGED ? MASK_TICKS_CHARGED : MASK_TICKS) + MASK_FADE_TICKS;
        s.cloudTicks = 0;
        s.cloudDelay = 0;
        kart.setMaskState(maskCode(kart.darkMask(), s.maskTicks));
        // OtherFX_Play_Echo(STATIC_AKUAKU / STATIC_UKAUKA + 0x1a): 0x53 and 0x54, the voice of the mask
        p.serverLevel().playSound(null, p.blockPosition(), kart.darkMask() ? ModSounds.MASK_UKA.get() : ModSounds.MASK_AKU.get(), SoundSource.PLAYERS, 1.5F, 1.0F);
        consume(s);
        sync(p, s);
    }

    /**
     * VehPhysCrash_Attack, the mask: a kart with the mask on that touches one without it blasts that one (damage type 2, which a shield takes instead), whatever the impact.
     * A kart under a turbo item boost squashes it instead (see {@link #squish}).
     */
    public static void contact(ServerPlayer attacker, ServerPlayer victim, KartEntity victimKart) {
        State s = state(attacker);
        State vs = state(victim);
        if (s == null || vs == null || attacker == victim || ethereal(attacker) || ethereal(victim)) return;
        if (s.maskTicks > 0) {
            if (vs.maskTicks > 0) return;
            int now = attacker.server.getTickCount();
            // VehPhysCrash_Attack runs every frame the karts overlap, and the victim is hit again as soon as it can be: not while it tumbles (a blasted kart takes no damage), but the moment the tumble
            // is over, even if the masked kart has not left it. The lock is therefore set by a hit that lands, and lasts the tumble (0x960 ms, 50 ticks) with a little over; a touch that does nothing
            // (a shield took it, the victim is immune) sets none and is tried again at the client's next report.
            String pair = attacker.getUUID() + ":" + victim.getUUID();
            Integer last = LAST_SQUISH.get(pair);
            if (last != null && now - last < MASK_HIT_LOCK_TICKS) return;
            if (applyEffect(victim, EFFECT_BLAST)) {
                LAST_SQUISH.put(pair, now);
                losePowerFruit(victim, BLAST_POWER_FRUIT_PENALTY);
                // VehPhysCrash_Attack: the one hit gets the hardest crash sound (OtherFX_DriverCrashing at the maximum volume), which the totem's owner hears as the feedback of the hit
                attacker.serverLevel().playSound(attacker, victim.getX(), victim.getY(), victim.getZ(), ModSounds.CRASH_HARD.get(), SoundSource.PLAYERS, 1.6F, 1.0F);
                attacker.playNotifySound(ModSounds.CRASH_HARD.get(), SoundSource.PLAYERS, 1.6F, 1.0F);
            }
            return;
        }
        squish(attacker, victim, victimKart);
    }

    /** The Warp Orb reached a kart: the explosion's hit (RB_Hazard_HurtDriver type 2, which a shield or the mask takes instead) and the power fruit it costs. */
    public static void warpHit(ServerPlayer victim) {
        if (applyEffect(victim, EFFECT_BLAST)) losePowerFruit(victim, BLAST_POWER_FRUIT_PENALTY);
    }

    /** The TNT crate was shaken off: no more slowing. */
    public static final int EFFECT_CLEAR = 5;
    /** What being blasted costs in power fruit (VEH_TUMBLE_WUMPA_PENALTY). */
    public static final int BLAST_POWER_FRUIT_PENALTY = 3;

    /** The driver's kart loses fruit (CTR: not while the mask is on, which is not built yet). */
    public static void losePowerFruit(ServerPlayer p, int count) {
        State s = state(p);
        if (s == null || s.powerFruit == 0 || s.maskTicks > 0) return;          // the mask keeps the fruit (RB_Player: no loss while ACTION_MASK_WEAPON)
        s.powerFruit = Math.max(0, s.powerFruit - count);
        sync(p, s);
    }

    /** Sends the driver's client an effect to apply to its kart (the client sim is the one that moves it). */
    public static boolean applyEffect(ServerPlayer p, int effect) {
        State s = state(p);
        if (s == null) return true;
        if (ethereal(p) && harmful(effect)) return false;
        if (harmful(effect)) {
            // VehPickState_NewState: a kart with the mask on is ignored first of all (the victim laughs)
            if (s.maskTicks > 0) return false;
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
            // VehPhysCrash_Attack only runs against a kart that has neither a bubble nor the mask; a finished (ethereal) kart is not there
            if (vs.shield != 0 || vs.immune > 0 || vs.maskTicks > 0 || ethereal(victim) || ethereal(p)) continue;
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
        ShieldBombEntity bomb = com.craftteamracing.ModEntities.SHIELD_BOMB.get().create(p.serverLevel());
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
        /** How many of the crate's power-up the kart gets (a crate set to a quantity); 1 for one. */
        int fixedCount = 1;
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
        /** The Mask's ticks left, its fade included (0: none): ACTION_MASK_WEAPON is on while it lives. */
        public int maskTicks;
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

    /** The display name of a power-up id ({@code craftteamracing:turbo} is the {@code power.craftteamracing.turbo} entry in the lang file). */
    public static Component nameOf(String id) {
        int colon = id.indexOf(':');
        return Component.translatable("power." + (colon < 0 ? CraftTeamRacingMod.MODID : id.substring(0, colon)) + "." + (colon < 0 ? id : id.substring(colon + 1)));
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
                    RaceManager.Standing place = RaceManager.standing(driver);
                    com.craftteamracing.items.PowerupTable.Drop picked = place == null ? spot.pickDrop(1, 0, 1, RANDOM) : spot.pickDrop(place.field(), place.rank(), place.lapIndex(), RANDOM);
                    s.fixed = picked == null ? "" : picked.id();
                    s.fixedCount = picked == null ? 1 : picked.count();         // the roulette's sound is the driver's client's (RouletteSound): a loop for as long as it runs
                }
                crateBreak(level, pickup);
            }
            case TRIGGER_CRATE -> {
                // the "!" crate gives no power-up: it gives a redstone signal (and a kart outside a race may break it as well)
                crateBreak(level, pickup);
                spot.fire(level);
            }
        }
        sync(driver, s);
        return true;
    }

    private static void crateBreak(ServerLevel level, PickupEntity pickup) {
        level.playSound(null, pickup.blockPosition(), ModSounds.CRATE_BREAK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        com.craftteamracing.net.FxPayload.send(level, "crate_break", pickup.getX(), pickup.getY(), pickup.getZ());
    }

    /** The use key. */
    public static void use(ServerPlayer p, boolean forward, boolean back) {
        if (ethereal(p)) return;          // a finished racer fires nothing
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
            consume(s);
            sync(p, s);
        } else if (id.equals(HeldItem.POTION.id())) {
            // VehPickupItem_ShootNow, beaker: a green one, a red one with ten power fruit; dropped behind, or thrown forward with the forward key held
            KartEntity kart = (KartEntity) p.getVehicle();
            PotionEntity potion = com.craftteamracing.ModEntities.POTION.get().create(p.serverLevel());
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
            consume(s);
            sync(p, s);
        } else if (id.equals(HeldItem.BOMB.id())) {
            // VehPickupItem_ShootNow, WEAPON_ID_BOMB_MISSILE: rolls forward, or backward (back key held, 0.3 of the speed); bigger blast with ten power fruit
            if (!BombEntity.canLaunch()) return;          // ACTIVE_MISSILE_LIMIT: the item is kept
            KartEntity kart = (KartEntity) p.getVehicle();
            BombEntity bomb = com.craftteamracing.ModEntities.BOMB.get().create(p.serverLevel());
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
            MissileEntity missile = com.craftteamracing.ModEntities.MISSILE.get().create(p.serverLevel());
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
            TntEntity tnt = com.craftteamracing.ModEntities.TNT.get().create(p.serverLevel());
            if (tnt != null) {
                tnt.setNitro(s.powerFruit >= CHARGED);
                tnt.setOwner(p.getUUID());
                tnt.setYRot(kart.getYRot());
                tnt.setPos(kart.getX() + Math.sin(yaw) * 1.9, kart.getY() + 0.1, kart.getZ() - Math.cos(yaw) * 1.9);
                p.serverLevel().addFreshEntity(tnt);
                p.serverLevel().playSound(null, p.blockPosition(), ModSounds.MINE_DROP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);        // SOUND_MINE_DROP
            }
            consume(s);
            sync(p, s);
        } else if (id.equals(HeldItem.CLOCK.id())) {
            useClock(p, s);
        } else if (id.equals(HeldItem.MASK.id())) {
            useMask(p, s);
        } else if (id.equals(HeldItem.WARPBALL.id())) {
            // VehPickupItem_ShootNow, WEAPON_ID_WARPBALL: the orb is born at the kart and goes after the leader (see WarpOrbEntity); every other racer it touches is blasted, and with ten
            // power fruit it goes on through all of them
            WarpOrbEntity orb = com.craftteamracing.ModEntities.WARP_ORB.get().create(p.serverLevel());
            if (orb != null) {
                orb.launch(p, (KartEntity) p.getVehicle(), s.powerFruit >= CHARGED);
                p.serverLevel().addFreshEntity(orb);
                p.serverLevel().playSound(null, p.blockPosition(), ModSounds.WARP_LAUNCH.get(), SoundSource.PLAYERS, 3.0F, 1.0F);          // SOUND_WARPBALL 0x4d
            }
            consume(s);
            sync(p, s);
        } else {
            // not built yet: it is used up all the same, so the roulette and the icons can be tested one after another
            p.displayClientMessage(Component.translatable("message.craftteamracing.item.not_built", nameOf(s.held)), true);
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
        com.craftteamracing.net.FxPayload.send(p.serverLevel(), "clock_flash", p.getX(), p.getY(), p.getZ());                 // clockFlash: every racer's screen flashes for 4 frames
        com.craftteamracing.net.FxPayload.sendTo(p, "clock_self");                                                              // clockSend: the user's own screen echoes for 30 frames
        java.util.List<ServerPlayer> victims = RaceManager.opponents(p);
        if (victims == null) {
            victims = new java.util.ArrayList<>();
            for (ServerPlayer o : p.serverLevel().players()) {
                if (o != p && o.getVehicle() instanceof KartEntity ok && !ok.ethereal() && RaceManager.standing(o) == null && o.distanceToSqr(p) <= GLOBAL_EFFECT_RADIUS * GLOBAL_EFFECT_RADIUS) victims.add(o);
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
        consume(s);
        sync(p, s);
    }

    private static final Map<String, Integer> LAST_SQUISH = new HashMap<>();
    /** How long a masked kart's hit keeps the same kart from being hit again: its tumble (0x960 CTR-ms, 50 ticks) and a few ticks over. */
    private static final int MASK_HIT_LOCK_TICKS = 54;

    /**
     * VehPhysCrash_Attack, the turbo squish: a kart under a turbo item boost ran into another one that has no boost going (the impact over 0xa00 is the attacker's client's report):
     * the other is squashed (damage type 3, which a shield takes instead) and the attacker is thrown into a high jump.
     */
    public static void squish(ServerPlayer attacker, ServerPlayer victim, KartEntity victimKart) {
        State s = state(attacker);
        State vs = state(victim);
        if (s == null || vs == null || attacker == victim || s.turboItemTicks <= 0) return;
        if (com.craftteamracing.kart.KartFlags.has(victimKart.getVisualFlags(), com.craftteamracing.kart.KartFlags.BOOSTING)) return;          // driver1->reserves == 0
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
    public static final int STACK_COUNT = com.craftteamracing.items.PowerupRegistry.stackCount(HeldItem.BOMB_3X.id());

    /** What the driver's item box shows, for a viewer who watches through that driver (without the effect, which only the driver's own kart applies), or null when it is not in a kart. */
    public static ItemStatePayload viewOf(ServerPlayer p) {
        State s = state(p);
        return s == null ? null : new ItemStatePayload(s.powerFruit, s.held, s.roll, EFFECT_NONE, 0, s.count);
    }

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
            if (s.maskTicks > 0) {
                s.maskTicks--;
                ((KartEntity) p.getVehicle()).setMaskState(s.maskTicks == 0 ? 0 : maskCode(((KartEntity) p.getVehicle()).darkMask(), s.maskTicks));
            }
            if (s.cloudTicks > 0) s.cloudTicks--;
            if (s.turboItemTicks > 0) s.turboItemTicks--;
            if (s.clockTicks > 0) s.clockTicks--;
            if (s.clockDelay > 0 && --s.clockDelay == 0) applyEffect(p, s.clockPending);
            if (s.cloudDelay > 0 && --s.cloudDelay == 0) applyEffect(p, s.cloudPending);
            if (s.roll > 0 && --s.roll == 0) {
                int dropCount = 0;
                if (!s.fixed.isEmpty()) {
                    s.held = s.fixed;
                    dropCount = s.fixedCount > 1 ? s.fixedCount : 0;
                } else {
                    RaceManager.Standing st = RaceManager.standing(p);
                    // the drop table (this crate is not set to anything itself); its quantity may lean with the driver's place
                    com.craftteamracing.items.PowerupTable.Drop drop = st == null ? rules(p).powerups().drawDrop(1, 0, 1, RANDOM) : rules(p).powerups().drawDrop(st.field(), st.rank(), st.lapIndex(), RANDOM);
                    s.held = drop.id();
                    dropCount = drop.count() > 1 ? drop.count() : s.held.equals(HeldItem.BOMB_3X.id()) || s.held.equals(HeldItem.MISSILE_3X.id()) ? STACK_COUNT : 0;       // VehPhysGeneral: numHeldItems = HELD_ITEM_STACK_COUNT
                }
                s.count = dropCount;
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
