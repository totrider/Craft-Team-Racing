package com.craftteamracing.pickup;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.craftteamracing.CtrConfig;
import com.craftteamracing.ModBlockEntities;
import com.craftteamracing.ModEntities;
import com.craftteamracing.track.RaceManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a pickup spot does. While it is <b>active</b> it keeps its pickup in the world; when not, it keeps none. A spot is active when a race on
 * a track near it is on (the track's nodes within the configured radius, see {@link RaceManager#activeNear}) or when it has a redstone signal,
 * which lets it work without any track (for testing, and for builds that are not races). A power-up crate spot can be set to always hold one
 * particular power-up instead of the place-based roulette.
 */
public class PickupSpotBlockEntity extends BlockEntity {
    /** The power-up this spot always gives ("" for CTR's roulette). Only used by power-up crate spots. */
    private String itemId = "";
    private UUID pickup;
    private boolean active;
    private int respawn;
    /** A fruit that was taken stays gone until the spot has been inactive. */
    private boolean spent;
    /** A redstone signal switched the spot on: it keeps its pickup (and respawns a crate) after the signal is gone, until a race takes over or the block is removed. */
    private boolean armed;
    private boolean signal;
    private boolean raceActive;
    // ---- the trigger crate ("!"): it gives a redstone signal when it is broken
    /** The timers' steps (see {@link TriggerTime}): respawn (0 is the crates' usual delay), redstone duration (0 is always), and how many times it can be used (0 is without end). */
    private int respawnStep;
    private int durationStep = 11;
    private int uses;
    private int usesLeft;
    private boolean on;
    private long outputUntil;
    private final List<BlockPos> links = new ArrayList<>();

    public PickupSpotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PICKUP_SPOT.get(), pos, state);
    }

    public PickupKind kind() {
        return ((PickupSpotBlock) getBlockState().getBlock()).kind();
    }

    public String itemId() {
        return itemId;
    }

    /** The power-ups this spot gives, one of them at random per crate (none: the general drop table). */
    public String pick(java.util.Random random) {
        return com.craftteamracing.items.CrateItems.pick(itemId, random);
    }

    /** The power-up and how many of it a kart gets from this crate (null: the general drop table). */
    public com.craftteamracing.items.CrateItems.Entry pickEntry(java.util.Random random) {
        return com.craftteamracing.items.CrateItems.pickEntry(itemId, random);
    }

    public void setItemId(String id) {
        String normalized = com.craftteamracing.items.CrateItems.normalize(id);
        boolean changed = !normalized.equals(this.itemId);
        this.itemId = normalized;
        setChanged();
        // the crate that is standing there was made with the old setting: it goes, and the next tick brings a new one (a crate already taken keeps its respawn timer)
        if (changed && pickup != null && level instanceof net.minecraft.server.level.ServerLevel server) {
            Entity current = server.getEntity(pickup);
            if (current != null) current.discard();
            pickup = null;
            spent = false;
        }
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);          // the builder's client draws the power-up's icon over the spot
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public boolean owns(Entity e) {
        return e.getUUID().equals(pickup);
    }

    /** The pickup was taken by a kart. */
    public void taken(PickupEntity e) {
        pickup = null;
        if (kind() == PickupKind.TRIGGER_CRATE) respawn = respawnStep == 0 ? TriggerTime.DEFAULT_RESPAWN : (int) Math.min(Integer.MAX_VALUE, TriggerTime.ticks(respawnStep));
        else if (kind().respawnTicks > 0) respawn = kind().respawnTicks;
        else spent = true;
        setChanged();
    }

    // ---- the trigger crate
    public int respawnStep() {
        return respawnStep;
    }

    public int durationStep() {
        return durationStep;
    }

    public int uses() {
        return uses;
    }

    public List<BlockPos> links() {
        return links;
    }

    /** Whether the spot gives its redstone signal now. */
    public boolean emitting() {
        return on;
    }

    /** The settings: a new number of uses starts the count again. */
    public void configure(int respawnStep, int durationStep, int uses) {
        this.respawnStep = respawnStep;
        this.durationStep = durationStep;
        if (uses != this.uses) usesLeft = uses;
        this.uses = uses;
        setChanged();
    }

    /** Links the spot to a node (does nothing when it is linked already): returns whether a link was made. */
    public boolean link(BlockPos node) {
        if (links.contains(node)) return false;
        links.add(node.immutable());
        if (on && level != null) RedstoneLinkNodeBlock.setPowered(level, node, true);
        changed();
        return true;
    }

    /** Takes a link away: returns whether there was one. */
    public boolean unlink(BlockPos node) {
        if (!links.remove(node)) return false;
        if (on && level != null) RedstoneLinkNodeBlock.setPowered(level, node, false);
        changed();
        return true;
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);          // the builder's client draws the links
    }

    /** Links the spot to a Redstone Linking Node, or takes the link away when there is one: returns whether it is linked now. */
    public boolean toggleLink(BlockPos node) {
        if (links.remove(node)) {
            if (on && level != null) RedstoneLinkNodeBlock.setPowered(level, node, false);
            setChanged();
            return false;
        }
        links.add(node.immutable());
        if (on && level != null) RedstoneLinkNodeBlock.setPowered(level, node, true);
        setChanged();
        return true;
    }

    /** The crate was broken: the signal goes on (for as long as the settings say) and a use is spent. */
    public void fire(ServerLevel server) {
        if (uses > 0 && usesLeft > 0) usesLeft--;
        outputUntil = durationStep == 0 ? Long.MAX_VALUE : server.getGameTime() + TriggerTime.ticks(durationStep);
        setOutput(server, true);
        setChanged();
    }

    /** The signal on or off: this block's own, and the linked nodes'. */
    public void setOutput(ServerLevel server, boolean now) {
        if (now) {
            for (BlockPos node : links) {
                if (server.isLoaded(node)) RedstoneLinkNodeBlock.setPowered(server, node, true);
            }
        }
        if (now == on) return;
        on = now;
        Block block = getBlockState().getBlock();
        server.updateNeighborsAt(worldPosition, block);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) server.updateNeighborsAt(worldPosition.relative(d), block);
        if (!now) {
            for (BlockPos node : links) {
                if (server.isLoaded(node)) RedstoneLinkNodeBlock.setPowered(server, node, false);
            }
        }
        setChanged();
    }

    /** The trigger crate does not need a race, and a signal reaching it does nothing (it gives one): it is simply there, until its uses are spent. */
    private static void triggerTick(ServerLevel server, BlockPos pos, PickupSpotBlockEntity be) {
        boolean blocked = blocked(server, pos);
        if (be.respawn > 0 && !blocked) be.respawn--;
        if (be.on && be.outputUntil != Long.MAX_VALUE && server.getGameTime() >= be.outputUntil) be.setOutput(server, false);
        be.active = true;
        Entity current = be.pickup == null ? null : server.getEntity(be.pickup);
        if (current != null && current.isRemoved()) current = null;
        boolean spentUp = be.uses > 0 && be.usesLeft <= 0;
        if (spentUp) {
            if (current != null) current.discard();
            be.pickup = null;
            return;
        }
        if (current == null && be.respawn == 0 && !blocked) {
            PickupEntity e = ModEntities.PICKUP.get().create(server);
            if (e == null) return;
            e.setKind(be.kind());
            e.setSpot(pos);
            e.setPos(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
            server.addFreshEntity(e);
            be.pickup = e.getUUID();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PickupSpotBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        if (be.kind() == PickupKind.TRIGGER_CRATE) {
            triggerTick(server, pos, be);
            return;
        }
        // RB_CrateAny_ThTick_Grow: the respawn waits while a kart or a mine (a TNT crate...) sits in the way
        boolean blocked = blocked(server, pos);
        if (be.respawn > 0 && !blocked) be.respawn--;
        // a redstone signal arriving arms the spot and brings back what was taken (a pulse again brings back a fruit that was taken)
        boolean signal = server.hasNeighborSignal(pos);
        if (signal && !be.signal) {
            be.armed = true;
            be.spent = false;
            be.respawn = 0;
            be.setChanged();
        }
        be.signal = signal;
        if (server.getGameTime() % 10 == 0) {
            com.craftteamracing.items.RuleSet rules = RaceManager.rulesNear(server, pos, CtrConfig.PICKUP_TRACK_RADIUS.get());
            // the race's rules say which pickups it has (RuleSet: fruit, fruit crates, power-up crates)
            boolean race = rules != null && switch (be.kind()) {
                case FRUIT -> rules.fruit();
                case FRUIT_CRATE -> rules.fruitCrates();
                case POWERUP_CRATE -> rules.powerupCrates();
                case TRIGGER_CRATE -> false;
            };
            if (race && !be.raceActive) {
                be.spent = false;                    // a race begins: everything is there again
                be.respawn = 0;
            }
            be.raceActive = race;
            boolean now = race || be.armed;
            if (now != be.active) {
                be.active = now;
                if (!now) {
                    be.spent = false;
                    be.respawn = 0;
                }
            }
        }
        Entity current = be.pickup == null ? null : server.getEntity(be.pickup);
        if (current != null && current.isRemoved()) current = null;
        if (!be.active) {
            if (current != null) current.discard();
            be.pickup = null;
            return;
        }
        if (current == null && be.respawn == 0 && !be.spent && !blocked) {
            PickupEntity e = ModEntities.PICKUP.get().create(server);
            if (e == null) return;
            e.setKind(be.kind());
            e.setSpot(pos);
            e.setItem(be.itemId());
            e.setPos(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
            server.addFreshEntity(e);
            be.pickup = e.getUUID();
        }
    }

    /** A kart or a placed power-up (TNT, Nitro) is on the spot. */
    private static boolean blocked(ServerLevel level, BlockPos pos) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos).inflate(0.5, 0.3, 0.5);
        return !level.getEntitiesOfClass(com.craftteamracing.kart.KartEntity.class, box).isEmpty() || !level.getEntitiesOfClass(TntEntity.class, box).isEmpty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Item", itemId);
        tag.putBoolean("Armed", armed);
        tag.putInt("RespawnStep", respawnStep);
        tag.putInt("DurationStep", durationStep);
        tag.putInt("Uses", uses);
        tag.putInt("UsesLeft", usesLeft);
        tag.putBoolean("On", on);
        tag.putLong("OutputUntil", outputUntil);
        tag.putLongArray("Links", links.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        itemId = tag.getString("Item");
        armed = tag.getBoolean("Armed");
        if (tag.contains("DurationStep")) {
            respawnStep = tag.getInt("RespawnStep");
            durationStep = tag.getInt("DurationStep");
            uses = tag.getInt("Uses");
            usesLeft = tag.getInt("UsesLeft");
            on = tag.getBoolean("On");
            outputUntil = tag.getLong("OutputUntil");
            links.clear();
            for (long l : tag.getLongArray("Links")) links.add(BlockPos.of(l));
        }
    }
}
