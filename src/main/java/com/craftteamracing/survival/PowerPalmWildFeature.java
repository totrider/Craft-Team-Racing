package com.craftteamracing.survival;

import com.craftteamracing.CtrConfig;
import com.mojang.serialization.Codec;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The Power Palms of world generation: the placed feature tries {@link #ATTEMPTS} times a chunk on beaches and each try stands with the chance that makes the server's
 * "powerPalmsPerChunk" the average (or none at all when "powerPalms" is off). A sapling grows the plain {@link PowerPalmFeature} instead, so the config never holds a planted palm back.
 */
public class PowerPalmWildFeature extends Feature<NoneFeatureConfiguration> {
    public static final int ATTEMPTS = 8;

    public PowerPalmWildFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!CtrConfig.POWER_PALMS.get() || context.random().nextDouble() >= CtrConfig.POWER_PALMS_PER_CHUNK.get() / ATTEMPTS) return false;
        return ModSurvival.POWER_PALM_FEATURE.get().place(context);
    }
}
