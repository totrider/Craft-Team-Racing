package com.ctrcraft.kart;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.pickup.ItemManager;
import com.ctrcraft.track.RaceManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * What a race does to its drivers' bodies (docs/DAMAGE.md): by the rules of the race (RuleSet.driverDamage and .hunger, both off by default) a driver neither loses health nor gets hungry
 * from the gathering until the race is over. The kart still reacts to a hit (KartDamage, which runs first): only the driver's health is spared. /kill and the void still work.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID)
public final class RacerProtection {
    private RacerProtection() {}

    /** The rider of a kart with the Obsidian Floater on (in a race: the rules' floater on lava) is not hurt by lava or by the fire it brings. */
    private static boolean lavaProof(ServerPlayer p) {
        if (!(p.getVehicle() instanceof KartEntity kart)) return false;
        return RaceManager.raceIdOf(p.getUUID()) != null ? ItemManager.rules(p).floater() == 2 : kart.floaterMode() == 2;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLavaDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !lavaProof(p)) return;
        var source = event.getSource();
        if (source.is(net.minecraft.world.damagesource.DamageTypes.LAVA) || source.is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE) || source.is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || RaceManager.raceIdOf(p.getUUID()) == null) return;
        if (ItemManager.rules(p).driverDamage() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer rider && rider.isOnFire() && lavaProof(rider)) rider.clearFire();
        if (!(event.getEntity() instanceof ServerPlayer p) || RaceManager.raceIdOf(p.getUUID()) == null || ItemManager.rules(p).hunger()) return;
        p.getFoodData().setExhaustion(0.0F);
    }
}
