package com.ctrcraft.kart;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModItems;
import com.ctrcraft.pickup.BombEntity;
import com.ctrcraft.pickup.ItemManager;
import com.ctrcraft.pickup.PotionEntity;
import com.ctrcraft.pickup.TntEntity;
import com.ctrcraft.track.RaceManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * What vanilla damage does to a kart (docs/DAMAGE.md). A kart is sturdy: no damage kills or removes it by itself, except what is critical out of a race. The damage is sorted by the
 * vanilla damage type tags (a modded source that carries the right tag, or is derived from one, lands in the right bucket) and by a few types by name; whatever is not mapped is a
 * spin out. The driver takes the vanilla damage as ever (this does not touch it). Every reaction goes through {@link ItemManager#applyEffect}, so a shield takes the hit and the
 * moment of immunity after it applies; a kart reacts at most once a second and a half so a dispenser cannot chain it.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID)
public final class KartDamage {
    private KartDamage() {}

    public enum Reaction { NONE, SPIN, BLAST, BURN, BREAK }

    private static final int COOLDOWN_TICKS = 30;
    private static final Map<UUID, Integer> LAST = new HashMap<>();

    /**
     * The reaction to a damage source. {@code racing}: the kart's driver is in a race. In a race nothing breaks the kart; out of one an explosion or lava does (it is picked up again as
     * an item: "critical damage"). The mod's own weapons do their own damage through their effects, so a vanilla explosion made by one of them does nothing here.
     */
    public static Reaction classify(DamageSource source, boolean racing, boolean obsidian) {
        Entity direct = source.getDirectEntity();
        if (obsidian && (source.is(DamageTypes.LAVA) || source.is(DamageTypeTags.IS_FIRE))) return Reaction.NONE;          // the Obsidian Floater: lava and fire do nothing to the kart
        if (direct instanceof TntEntity || direct instanceof BombEntity || direct instanceof PotionEntity) return Reaction.NONE;
        if (source.is(DamageTypes.LAVA)) return racing ? Reaction.BURN : Reaction.BREAK;
        if (source.is(DamageTypeTags.IS_FIRE)) return Reaction.BURN;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return racing ? Reaction.BLAST : Reaction.BREAK;
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return Reaction.BLAST;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return Reaction.SPIN;
        // the driver's own troubles, which the kart does not share (water slows it by its own rules)
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypes.FLY_INTO_WALL)
                || source.is(DamageTypes.STARVE) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DRY_OUT)) {
            return Reaction.NONE;
        }
        return Reaction.SPIN;                                   // the fallback for everything else: melee, magic, thorns, cactus, berry bushes ...
    }

    /** Applies the reaction to a damage source that hit the kart or its driver. Returns whether the source was dealt with. */
    public static boolean react(KartEntity kart, DamageSource source) {
        if (kart.level().isClientSide || kart.isRemoved()) return true;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            kart.discard();                                      // /kill and the void
            return true;
        }
        ServerPlayer driver = kart.getControllingPassenger() instanceof ServerPlayer p ? p : null;
        if (driver != null && ItemManager.maskActive(driver)) return true;          // the mask: invulnerable to attacks
        boolean racing = driver != null && RaceManager.raceIdOf(driver.getUUID()) != null;
        Reaction r = classify(source, racing, kart.floaterMode() == 2);
        if (r == Reaction.NONE) return true;
        if (r == Reaction.BREAK) {
            kart.breakApart();
            return true;
        }
        if (driver == null) return true;                         // an empty kart has nothing to spin
        int now = driver.server.getTickCount();
        Integer last = LAST.get(kart.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) return true;
        if (LAST.size() > 512) LAST.values().removeIf(t -> now - t > 200);
        LAST.put(kart.getUUID(), now);
        switch (r) {
            case SPIN -> {
                if (ItemManager.applyEffect(driver, ItemManager.EFFECT_SPIN)) ItemManager.losePowerFruit(driver, 1);
            }
            case BLAST -> {
                if (ItemManager.applyEffect(driver, ItemManager.EFFECT_BLAST)) ItemManager.losePowerFruit(driver, ItemManager.BLAST_POWER_FRUIT_PENALTY);
            }
            case BURN -> {
                if (ItemManager.applyEffect(driver, ItemManager.EFFECT_BURN)) ItemManager.losePowerFruit(driver, 1);
            }
            default -> { }
        }
        return true;
    }

    /** The driver is hit (an arrow that struck the player, fire, an explosion beside the kart ...): the kart reacts as if it had been hit itself. The damage itself goes through untouched. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
    public static void onDriverDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getVehicle() instanceof KartEntity kart && kart.getControllingPassenger() == p) {
            react(kart, event.getSource());
        }
    }
}
