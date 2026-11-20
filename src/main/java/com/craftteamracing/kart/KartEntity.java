package com.craftteamracing.kart;

import com.craftteamracing.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * A drivable kart. The driver's client runs the CTR physics (see {@code client/KartDriver}) and moves this entity;
 * the vanilla "move vehicle" packet carries the position to the server and from there to everybody else. The server
 * does not simulate a driven kart (it only validates the move, as it does for boats). An empty kart just falls and stops.
 */
public class KartEntity extends Entity {
    /** CTR's drawn yaw (heading + drift/spin offset), 4096ths of a turn, so other players see the drift angle. */
    private static final EntityDataAccessor<Integer> DATA_DRAWN_YAW =
            SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FLAGS =
            SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);

    /** Engine sound: distortion (low byte) | volume << 8, CTR's own scale (see KartSim.engineSound). */
    private static final EntityDataAccessor<Integer> DATA_ENGINE =
            SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    /** The ground normal the kart leans on, x and z as bytes (value = n * 127 + 127); 0x7F7F is flat. */
    /** The kart's colour (RGB): what the tintable parts of its model (the tub, the struts, the seat) are multiplied by. A dye changes it. */
    private static final EntityDataAccessor<Integer> DATA_COLOUR =
            SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    public static final int DEFAULT_COLOUR = 0xD63434;
    /** The seat's colour (tint index 1 of the model). A dye used while sneaking changes it. */
    private static final EntityDataAccessor<Integer> DATA_SEAT_COLOUR =
            SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    public static final int DEFAULT_SEAT_COLOUR = 0xF0C43C;
    /** The kart's upgrades: which are installed (an item used on the kart) and which of them are switched on. */
    /** The mask preference: false light (Aku Aku, the good mask), true dark (Uka Uka, the evil one). Seen by everyone, so another player's mask shows as they chose. */
    /** The tuning profile's id ("": Balanced): the driver's client reads its numbers from the profiles the server sent. */
    private static final EntityDataAccessor<String> DATA_TUNING = SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_DARK_MASK = SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.BOOLEAN);
    /** The Mask power-up on this kart: 0 none, 1 Aku Aku (light), 2 Uka Uka (dark), plus 4 while it fades away. Set by the server, seen by everyone. */
    /** A kart whose driver has finished the race (or been put on a podium): nothing touches it and it touches nothing. */
    private static final EntityDataAccessor<Boolean> DATA_ETHEREAL = SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_MASK = SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_UPGRADES = SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    public static final int UP_OFFROAD_UNLOCKED = 1;
    public static final int UP_OFFROAD_ON = 2;
    public static final int UP_BUMPER_UNLOCKED = 4;
    public static final int UP_BUMPER_ON = 8;
    /** Floater: the kart floats on water like a boat and has no water slowdown. */
    public static final int UP_FLOATER_UNLOCKED = 16;
    public static final int UP_FLOATER_ON = 32;
    /** Jump Spring: a jump of the kart's own on a key (the spring's high jump). */
    public static final int UP_SPRING_UNLOCKED = 64;
    public static final int UP_SPRING_ON = 128;                      // the first version's switch: a kart that has it and no strength gets the default one
    public static final int UP_SPRING_SHIFT = 16;                    // the Jump Spring's strength, 0 to 10 tenths, in bits 16 to 19
    public static final int UP_SPRING_DEFAULT = 5 << UP_SPRING_SHIFT;
    /** Low Gravity Module: unlocked here; its strength, 0 to 10 tenths, is in bits 12 to 15. */
    public static final int UP_GRAVITY_UNLOCKED = 256;
    public static final int UP_GRAVITY_SHIFT = 12;
    /** The Obsidian Floater replaced the Floater: it floats on lava as well and keeps the rider from lava's damage and fire. */
    public static final int UP_OBSIDIAN = 512;
    /** Paddle: a floating kart drives at full speed on the water instead of at the water's slowdown (as the Offroad Tires do for rough ground). */
    public static final int UP_PADDLE_UNLOCKED = 1024;
    public static final int UP_PADDLE_ON = 2048;
    public static final int UP_GRAVITY_DEFAULT = 5 << UP_GRAVITY_SHIFT;
    private static final EntityDataAccessor<Integer> DATA_SHIELD = SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TILT =
            SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    public static final int TILT_FLAT = 0x7F7F;

    // client-side smoothing of a kart somebody else drives
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot;
    private int lerpSteps;
    private float yawPrevDeg, yawDeg;

    public KartEntity(EntityType<? extends KartEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DRAWN_YAW, 0);
        builder.define(DATA_FLAGS, 0);
        builder.define(DATA_ENGINE, 0x823C);
        builder.define(DATA_TILT, TILT_FLAT);
        builder.define(DATA_SHIELD, 0);
        builder.define(DATA_COLOUR, DEFAULT_COLOUR);
        builder.define(DATA_SEAT_COLOUR, DEFAULT_SEAT_COLOUR);
        builder.define(DATA_UPGRADES, 0);
        builder.define(DATA_TUNING, "");
        builder.define(DATA_DARK_MASK, false);
        builder.define(DATA_MASK, 0);
        builder.define(DATA_ETHEREAL, false);
    }

    public int colour() {
        return entityData.get(DATA_COLOUR);
    }

    public int seatColour() {
        return entityData.get(DATA_SEAT_COLOUR);
    }

    public void setSeatColour(int rgb) {
        entityData.set(DATA_SEAT_COLOUR, rgb & 0xFFFFFF);
    }

    public void setColour(int rgb) {
        entityData.set(DATA_COLOUR, rgb & 0xFFFFFF);
    }

    /**
     * The Power Shield round the kart: 0 none, -1 the blue one (it stays), 1 to 41 the green one (41 while it is steady, the ticks it has left in its last 40, when it fades), -2 while
     * it pops (8 ticks, the 11 frames of RB_ShieldDark_ThTick_Pop). Set by the server.
     */
    public int shield() {
        return entityData.get(DATA_SHIELD);
    }

    public void setShield(int state) {
        if (entityData.get(DATA_SHIELD) != state) entityData.set(DATA_SHIELD, state);
    }

    public int getTilt() {
        return entityData.get(DATA_TILT);
    }

    public void setTilt(int tilt) {
        entityData.set(DATA_TILT, tilt);
    }

    /** The driver's steering as the animation shows it, -1 left to +1 right (bits 16-20 of the engine word: 0 unset, 1 to 29, 15 straight). */
    public float steer() {
        int code = (getEngineData() >> 16) & 31;
        return code == 0 ? 0.0F : (code - 15) / 14.0F;
    }

    public int getEngineData() {
        return entityData.get(DATA_ENGINE);
    }

    public void setEngineData(int engine) {
        entityData.set(DATA_ENGINE, engine);
    }

    public int getDrawnYawAngle() {
        return entityData.get(DATA_DRAWN_YAW);
    }

    public void setDrawnYawAngle(int angle) {
        entityData.set(DATA_DRAWN_YAW, angle);
    }

    public int getVisualFlags() {
        return entityData.get(DATA_FLAGS);
    }

    public void setVisualFlags(int flags) {
        entityData.set(DATA_FLAGS, flags);
    }

    /** The yaw to draw the model with, degrees (Minecraft convention). The local driver's is exact; others' is smoothed. */
    public float renderYawDegrees(float partialTick) {
        if (isLocallyDriven()) {
            return KartScale.angleToYaw(getDrawnYawAngle());
        }
        return Mth.rotLerp(partialTick, yawPrevDeg, yawDeg);
    }

    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;
    private float wheelSpin;
    private float wheelSpinO;

    /** How far the wheels have turned, degrees, positive rolling forward (client side: worked out from how far the kart has moved along the way it faces). */
    public float wheelSpin(float partialTick) {
        return Mth.lerp(partialTick, wheelSpinO, wheelSpin);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && maskState() != 0 && getControllingPassenger() == null) {
            // the driver got out: the mask goes with them
            items.maskTicks = 0;
            setMaskState(0);
        }
        if (level().isClientSide) {
            wheelSpinO = wheelSpin;
            if (!Double.isNaN(lastX)) {
                double yaw = Math.toRadians(yawDeg);
                double along = (getX() - lastX) * -Math.sin(yaw) + (getZ() - lastZ) * Math.cos(yaw);
                wheelSpin += (float) Math.toDegrees(along / KartWheels.radius());
            }
            lastX = getX();
            lastZ = getZ();
            yawPrevDeg = yawDeg;
            yawDeg = KartScale.angleToYaw(getDrawnYawAngle());
            if (FMLEnvironment.dist.isClient()) {
                com.craftteamracing.client.KartEffects.tick(this);
            }
            if (!isLocallyDriven() && lerpSteps > 0) {
                double f = 1.0 / lerpSteps;
                setPos(Mth.lerp(f, getX(), lerpX), Mth.lerp(f, getY(), lerpY), Mth.lerp(f, getZ(), lerpZ));
                setYRot(Mth.rotLerp((float) f, getYRot(), lerpYRot));
                lerpSteps--;
            }
        } else if (getControllingPassenger() == null) {
            // nobody drives: the effects the driver left behind (wheelie, flame, skid) end with them
            if (getVisualFlags() != 0) {
                setVisualFlags(0);
            }
            if (getEngineData() != 0x823C) {
                setEngineData(0x823C);
            }
            if (!parkChecked) {
                // the driver got out: a kart standing on blocks stays exactly where it was and as it was (its place, its lean on the slope): nothing is recomputed, which on a step or an
                // uneven edge sank it into the ground and flattened it. It sleeps like a resting kart, and wakes (falls, settles) only when the blocks under it change.
                parkChecked = true;
                if (restsOnBlocks()) {
                    restTilted = true;
                    restSignature = com.craftteamracing.pickup.GroundTilt.signature(level(), getX(), getY(), getZ());
                }
            }
            if (restTilted) {
                // asleep on a slope: nothing to compute until the blocks under it change (looked at about once a second)
                if (tickCount % 20 == 0 && com.craftteamracing.pickup.GroundTilt.signature(level(), getX(), getY(), getZ()) != restSignature) restTilted = false;
                return;
            }
            // fall, stop: in water or lava the fall is held back (a sinking kart drops slowly), and a kart with the Floater rises to the surface and rides in it a little below its top
            Vec3 dm = getDeltaMovement();
            int mode = floaterMode();
            double surface = FloatSurface.top(level(), getX(), getY() + 0.05, getZ(), mode);
            net.minecraft.world.level.material.FluidState here = level().getFluidState(net.minecraft.core.BlockPos.containing(getX(), getY() + 0.35, getZ()));
            if (!Double.isNaN(surface)) {
                double target = surface - FloatSurface.SINK;
                setDeltaMovement(dm.x * 0.6, dm.y * 0.6 + (target - getY()) * 0.12, dm.z * 0.6);
                restTilted = false;
            } else if (here.is(net.minecraft.tags.FluidTags.WATER) || here.is(net.minecraft.tags.FluidTags.LAVA)) {
                setDeltaMovement(dm.x * 0.7, Math.max(dm.y - 0.01, -0.06) * 0.8, dm.z * 0.7);
            } else {
                dm = dm.add(0, -0.04, 0);
                setDeltaMovement(dm.x * 0.6, dm.y * 0.98, dm.z * 0.6);
            }
            move(MoverType.SELF, getDeltaMovement());
            // an empty kart that has come to rest lies on the slope it is on (the driver's tilt would otherwise snap back to flat), flat on the slope's plane, once
            if (onGround() && getDeltaMovement().lengthSqr() < 1.0E-4) {
                restTilted = true;
                com.craftteamracing.pickup.GroundTilt.Rest r = com.craftteamracing.pickup.GroundTilt.rest(level(), this, getX(), getY(), getZ());
                setTilt(r.tilt());          // (its height is left as it is: lowering it to the slope's plane sank it into the edge of a step)
                restSignature = com.craftteamracing.pickup.GroundTilt.signature(level(), getX(), getY(), getZ());
            } else if (getTilt() != TILT_FLAT) {
                setTilt(TILT_FLAT);
            }
        } else {
            restTilted = false;
            parkChecked = false;
        }
    }

    private boolean restTilted;
    /** The first tick without a driver has looked at whether the kart stands on blocks (then it stays as it is). */
    private boolean parkChecked;

    /** The kart's box is on something: it rests on blocks (not in a fluid, not in the air), so it is left where it is. */
    private boolean restsOnBlocks() {
        if (!level().getFluidState(net.minecraft.core.BlockPos.containing(getX(), getY() + 0.35, getZ())).isEmpty()) return false;
        return !level().noCollision(this, getBoundingBox().inflate(-0.01).move(0.0, -0.15, 0.0));
    }
    private int restSignature;
    private int rejectedMoves;

    /** Server: counts a driver report that was refused; returns how many in a row. */
    public int noteRejectedMove() {
        return ++rejectedMoves;
    }

    public void clearRejectedMoves() {
        rejectedMoves = 0;
    }

    /** Client: the local player is the one driving this kart (their client runs its physics and owns its position). */
    public boolean isLocallyDriven() {
        return level().isClientSide && getFirstPassenger() instanceof Player player && player.isLocalPlayer();
    }

    /**
     * Never "controlled by the local instance" in vanilla's sense: that would make the client send vanilla's move-vehicle packet,
     * which the server re-validates with box collisions and rolls back. Position travels in {@code KartStatePayload} instead.
     */
    @Override
    public boolean isControlledByLocalInstance() {
        return false;
    }

    /** Forget any pending smoothing target (a stale one would snap a just-left kart back). */
    public void clearLerp() {
        lerpSteps = 0;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (isLocallyDriven()) {
            return; // the driver's own client is the authority on where its kart is
        }
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = Math.max(steps, 1);
    }

    /** What the kart carries (power fruit and the held power-up, {@link com.craftteamracing.pickup.ItemManager}): it stays with the kart when the driver gets out. */
    private final com.craftteamracing.pickup.ItemManager.State items = new com.craftteamracing.pickup.ItemManager.State();

    public com.craftteamracing.pickup.ItemManager.State items() {
        return items;
    }

    /** Whether driving this kart into a Start node's area can start a race (the Wrench's screen sets it). Kept on the server. */
    private boolean canStartRaces = true;

    public boolean canStartRaces() {
        return canStartRaces;
    }

    public void setCanStartRaces(boolean on) {
        canStartRaces = on;
    }

    public boolean ethereal() {
        return entityData.get(DATA_ETHEREAL);
    }

    public void setEthereal(boolean on) {
        entityData.set(DATA_ETHEREAL, on);
    }

    public int maskState() {
        return entityData.get(DATA_MASK);
    }

    public void setMaskState(int state) {
        entityData.set(DATA_MASK, state);
    }

    public boolean darkMask() {
        return entityData.get(DATA_DARK_MASK);
    }

    public void setDarkMask(boolean dark) {
        entityData.set(DATA_DARK_MASK, dark);
    }

    public String tuning() {
        return entityData.get(DATA_TUNING);
    }

    public void setTuning(String id) {
        entityData.set(DATA_TUNING, id == null ? "" : id);
    }

    public int upgrades() {
        return entityData.get(DATA_UPGRADES);
    }

    public void setUpgrades(int bits) {
        entityData.set(DATA_UPGRADES, bits);
    }

    /** Offroad tires are installed and switched on: the kart ignores rough terrain (a race's own rules override this). */
    public boolean offroadOn() {
        return (upgrades() & (UP_OFFROAD_UNLOCKED | UP_OFFROAD_ON)) == (UP_OFFROAD_UNLOCKED | UP_OFFROAD_ON);
    }

    /** A soft bumper is installed and switched on: the kart does no collision damage to the entities it bumps into (a race's own rules override this). */
    public boolean softBumperOn() {
        return (upgrades() & (UP_BUMPER_UNLOCKED | UP_BUMPER_ON)) == (UP_BUMPER_UNLOCKED | UP_BUMPER_ON);
    }

    public boolean floaterOn() {
        return (upgrades() & (UP_FLOATER_UNLOCKED | UP_FLOATER_ON)) == (UP_FLOATER_UNLOCKED | UP_FLOATER_ON);
    }

    /** 0 does not float, 1 floats on water, 2 floats on water and lava (the Obsidian Floater). */
    public int floaterMode() {
        return !floaterOn() ? 0 : (upgrades() & UP_OBSIDIAN) != 0 ? 2 : 1;
    }

    public boolean paddleOn() {
        return (upgrades() & (UP_PADDLE_UNLOCKED | UP_PADDLE_ON)) == (UP_PADDLE_UNLOCKED | UP_PADDLE_ON);
    }

    /** The Jump Spring's strength in tenths (0 when it is not installed or set to 0): the double jump, 5 being the spring's own high jump. */
    public int springStrength() {
        int u = upgrades();
        if ((u & UP_SPRING_UNLOCKED) == 0) return 0;
        int strength = (u >> UP_SPRING_SHIFT) & 15;
        return strength == 0 && (u & UP_SPRING_ON) != 0 ? 5 : strength;
    }

    /** The Low Gravity Module's strength in tenths (0 when it is not installed or set to 0). */
    public int gravityStrength() {
        return (upgrades() & UP_GRAVITY_UNLOCKED) == 0 ? 0 : (upgrades() >> UP_GRAVITY_SHIFT) & 15;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof WrenchItem) {
            // the Wrench opens the kart's settings screen (the screen sends its choices back: KartSettingsPayload)
            if (!level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new com.craftteamracing.net.OpenKartSettingsPayload(getId(), canStartRaces, upgrades(), darkMask(), tuning()));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        // an upgrade used on the kart is installed in it (it unlocks the switch in the settings screen); the item is used up
        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        // an installed upgrade starts switched on (the Low Gravity Module at 50 %)
        int unlock = 0;
        int defaults = 0;
        int check = -1;
        if (held.is(ModItems.OFFROAD_TIRES.get())) { unlock = UP_OFFROAD_UNLOCKED; defaults = UP_OFFROAD_ON; }
        else if (held.is(ModItems.SOFT_BUMPER.get())) { unlock = UP_BUMPER_UNLOCKED; defaults = UP_BUMPER_ON; }
        else if (held.is(ModItems.FLOATER.get())) { unlock = UP_FLOATER_UNLOCKED; defaults = UP_FLOATER_ON; }
        else if (held.is(ModItems.OBSIDIAN_FLOATER.get())) {
            // replaces the Floater: the same switch, now for lava as well (a kart with no Floater gets the switch, on)
            unlock = UP_FLOATER_UNLOCKED | UP_OBSIDIAN;
            defaults = (upgrades() & UP_FLOATER_UNLOCKED) != 0 ? 0 : UP_FLOATER_ON;
            check = UP_OBSIDIAN;
        }
        else if (held.is(ModItems.PADDLE.get())) { unlock = UP_PADDLE_UNLOCKED; defaults = UP_PADDLE_ON; }
        else if (held.is(ModItems.JUMP_SPRING.get())) { unlock = UP_SPRING_UNLOCKED; defaults = UP_SPRING_DEFAULT; }
        else if (held.is(ModItems.LOW_GRAVITY_MODULE.get())) { unlock = UP_GRAVITY_UNLOCKED; defaults = UP_GRAVITY_DEFAULT; }
        if (unlock != 0) {
            if (!level().isClientSide) {
                if ((upgrades() & (check == -1 ? unlock : check)) != 0) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.craftteamracing.kart.upgrade_installed"), true);
                } else {
                    setUpgrades(upgrades() | unlock | defaults);
                    if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.craftteamracing.kart.upgrade_added"), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.DyeItem dye) {
            // a dye in the hand paints the kart: the tub and struts, or the seat when sneaking
            if (!level().isClientSide) {
                int rgb = dye.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF;
                // the dye mixes into the colour already there, as in the crafting grid (KartPaint.mix)
                if (player.isSecondaryUseActive()) setSeatColour(KartPaint.mix(seatColour(), DEFAULT_SEAT_COLOUR, rgb));
                else setColour(KartPaint.mix(colour(), DEFAULT_COLOUR, rgb));
                if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    /** Where the rider sits, relative to the kart: on the seat of the model, 0.07 block behind the origin (the original driver sits 5 units behind the middle), not in the middle. */
    public static final double SEAT_BACK = 0.07;
    /**
     * The seat's height: where the rider's FEET are put, but a player riding is drawn 0.6 lower than that (the vehicle attachment of the player type), and his hips are 0.7 above his
     * feet, so his hips end up at HIP_HEIGHT = 0.28 above the kart's origin (the original driver's waist is 0.56 up, which was too high on the bulky stand-in kart: seated between that and the first try, -0.04).
     */
    private static final double SEAT_HEIGHT = 0.18;
    public static final double HIP_HEIGHT = SEAT_HEIGHT - 0.6 + 0.7;
    /** The top of the rider's head (torso and head are 1.17 above the hips). */
    public static final double HEAD_TOP = HIP_HEIGHT + 1.17;
    /** The rider's shoulder line (the origin of the player model's body), blocks above the kart's origin: 12 model pixels (0.703) above the hips. */
    public static final double SHOULDER_HEIGHT = HIP_HEIGHT + 0.703;

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        double yaw = Math.toRadians(getYRot());
        // Minecraft: forward = (-sin yaw, cos yaw), so back is (sin yaw, -cos yaw)
        return new Vec3(Math.sin(yaw) * SEAT_BACK, SEAT_HEIGHT, -Math.cos(yaw) * SEAT_BACK);
    }

    /**
     * Gets the rider out beside the kart (left, right, behind, ahead; the first with a floor and room), not on top of it:
     * vanilla's default is the top of the kart's box, which leaves the rider's feet inside it.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        double yaw = Math.toRadians(getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        double d = 1.0; // kart half width 0.35 + player half width 0.3, with room
        double[][] sides = { { 1, 0 }, { -1, 0 }, { 0, -1 }, { 0, 1 } }; // left, right, behind, ahead
        for (double[] side : sides) {
            double x = getX() + lx * side[0] * d + fx * side[1] * d;
            double z = getZ() + lz * side[0] * d + fz * side[1] * d;
            for (int dy = 0; dy >= -1; dy--) { // on the same level, or one block down
                BlockPos below = BlockPos.containing(x, getY() + dy, z);
                Vec3 floor = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), below, false);
                if (floor == null) {
                    continue;
                }
                Vec3 at = new Vec3(x, floor.y, z);
                if (DismountHelper.canDismountTo(level(), passenger, passenger.getLocalBoundsForPose(Pose.STANDING).move(at))) {
                    return at;
                }
            }
        }
        return new Vec3(getX(), getBoundingBox().maxY + 0.1, getZ());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true; // gravity is the kart physics' (or the empty-kart fall in tick())
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || level().isClientSide || isRemoved()) {
            return true;
        }
        // a hit with the hand: crouching with the Wrench picks an empty kart up (a creative player may pick up a driven one); any other punch does nothing, and a shot arrow or a
        // thrown trident is not a punch (docs/DAMAGE.md)
        if (source.getEntity() instanceof Player player && source.getDirectEntity() == player) {
            if (player.isShiftKeyDown() && player.getMainHandItem().is(ModItems.WRENCH.get()) && (getControllingPassenger() == null || player.getAbilities().instabuild)) {
                if (player.getAbilities().instabuild) {
                    // creative: the kart goes into the inventory too, with its upgrades and paint (nothing is dropped if the stack goes in)
                    ItemStack stack = toItem();
                    if (!player.getInventory().add(stack)) player.drop(stack, false);
                    discard();
                } else {
                    breakApart();
                }
            }
            return true;
        }
        return com.craftteamracing.kart.KartDamage.react(this, source);
    }

    /** The kart comes apart into its item (picked up, or destroyed by critical damage out of a race). */
    public void breakApart() {
        if (!isRemoved()) {
            spawnAtLocation(toItem());
            discard();
        }
    }

    @Override
    public ItemStack getPickResult() {
        return toItem();
    }

    /** The kart as an item, carrying what it was fitted with: upgrades and their switches, paint, the mask preference, whether it can start races. Placing the item restores it. */
    public ItemStack toItem() {
        ItemStack stack = new ItemStack(ModItems.KART.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt("Upgrades", upgrades());
        tag.putBoolean("DarkMask", darkMask());
        tag.putBoolean("CanStartRaces", canStartRaces);
        if (!tuning().isEmpty()) tag.putString("Tuning", tuning());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        if ((upgrades() & UP_OBSIDIAN) != 0) stack.set(net.minecraft.core.component.DataComponents.FIRE_RESISTANT, net.minecraft.util.Unit.INSTANCE);
        if (colour() != DEFAULT_COLOUR || seatColour() != DEFAULT_SEAT_COLOUR) stack.set(com.craftteamracing.ModComponents.KART_PAINT.get(), new KartPaint(colour(), seatColour()));
        return stack;
    }

    /** What {@link #toItem} saved (and its paint, which crafting may have changed), put back when the item is placed. */
    public void applyItem(ItemStack stack) {
        KartPaint paint = stack.get(com.craftteamracing.ModComponents.KART_PAINT.get());
        if (paint != null) {
            setColour(paint.frame());
            setSeatColour(paint.seat());
        }
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data != null) applyItemData(data.copyTag());
    }

    private void applyItemData(CompoundTag tag) {
        if (tag.contains("Upgrades")) setUpgrades(tag.getInt("Upgrades"));
        if (tag.contains("DarkMask")) setDarkMask(tag.getBoolean("DarkMask"));
        if (tag.contains("CanStartRaces")) canStartRaces = tag.getBoolean("CanStartRaces");
        if (tag.contains("Tuning")) setTuning(tag.getString("Tuning"));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setDrawnYawAngle(tag.getInt("DrawnYaw"));
        setColour(tag.contains("Colour") ? tag.getInt("Colour") : DEFAULT_COLOUR);
        setSeatColour(tag.contains("SeatColour") ? tag.getInt("SeatColour") : DEFAULT_SEAT_COLOUR);
        canStartRaces = !tag.contains("CanStartRaces") || tag.getBoolean("CanStartRaces");
        setUpgrades(tag.getInt("Upgrades"));
        setTuning(tag.getString("Tuning"));
        setDarkMask(tag.getBoolean("DarkMask"));
        items.powerFruit = Math.max(0, Math.min(com.craftteamracing.pickup.ItemManager.MAX_POWER_FRUIT, tag.contains("PowerFruit") ? tag.getInt("PowerFruit") : tag.getInt("Wumpa")));       // "Wumpa": the key older saves used
        items.held = tag.getString("HeldPowerup");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("DrawnYaw", getDrawnYawAngle());
        tag.putInt("Colour", colour());
        tag.putInt("SeatColour", seatColour());
        tag.putBoolean("CanStartRaces", canStartRaces);
        tag.putInt("Upgrades", upgrades());
        tag.putString("Tuning", tuning());
        tag.putBoolean("DarkMask", darkMask());
        tag.putInt("PowerFruit", items.powerFruit);
        tag.putString("HeldPowerup", items.held);
    }
}
