package com.craftteamracing.kart;

import com.craftteamracing.CraftTeamRacingMod;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;

/** A kart item that carries the Obsidian Floater is not burned, melted in lava or blown up as an item on the ground, as ancient debris is not. */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID)
public final class ObsidianProtection {
    private ObsidianProtection() {}

    @SubscribeEvent
    public static void onCheck(EntityInvulnerabilityCheckEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !KartItem.isObsidian(item.getItem())) return;
        var source = event.getSource();
        if (source.is(DamageTypes.LAVA) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) event.setInvulnerable(true);
    }
}
