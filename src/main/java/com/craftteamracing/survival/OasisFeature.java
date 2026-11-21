package com.craftteamracing.survival;

import java.util.ArrayList;
import java.util.List;

import com.craftteamracing.CtrConfig;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A desert oasis: a small pond dug into the sand, a rim of grass round it, a little sugar cane at the water's edge and two to four Power Palms. It is a feature placed into desert biomes,
 * not a structure and not a biome. Each try in a desert chunk stands with the chance of one in "oasisRarity" (server config, {@code worldgen}); it is turned off with "oases". The whole oasis
 * stays within ten blocks of where it is placed, so it never reaches further than the neighbouring chunks world generation may write to. It needs flat enough sand: where the ground
 * is more than two blocks off level, or there is water or lava in it, nothing is built.
 */
public class OasisFeature extends Feature<NoneFeatureConfiguration> {
    public OasisFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!CtrConfig.OASES.get() || context.random().nextInt(Math.max(1, CtrConfig.OASIS_RARITY.get())) != 0) return false;
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int baseY = origin.getY() - 1;                                       // the level of the ground and of the pond's water
        if (!level.getBlockState(origin.below()).is(BlockTags.SAND)) return false;

        int pond = 2 + random.nextInt(2);                                    // the pond's radius, 2 or 3
        double[] wobble = new double[8];                                     // a ragged edge: the radius changes a little all round
        for (int i = 0; i < wobble.length; i++) wobble[i] = random.nextDouble() * 1.2 - 0.6;
        int reach = pond + 4;

        // the site: nearly flat, and neither water nor lava under it
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx * dx + dz * dz > reach * reach) continue;
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (Math.abs(top - baseY) > 2) return false;
                if (!level.getBlockState(new BlockPos(x, top, z)).getFluidState().isEmpty()) return false;
            }
        }

        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        List<BlockPos> shore = new ArrayList<>();
        List<BlockPos> pondColumns = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int bucket = (int) ((Math.atan2(dz, dx) + Math.PI) / (2 * Math.PI) * wobble.length) % wobble.length;
                double edge = pond + wobble[bucket];
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                if (d <= edge) {                                              // water two deep, with sand under it
                    place(level, new BlockPos(x, baseY - 2, z), sand);
                    place(level, new BlockPos(x, baseY - 1, z), water);
                    place(level, new BlockPos(x, baseY, z), water);
                    clearAbove(level, new BlockPos(x, baseY, z), air);
                    pondColumns.add(new BlockPos(x, baseY, z));
                } else if (d <= edge + 2.2) {                                 // the shore: a rim of grass over dirt, a few bare sand patches
                    boolean bare = random.nextInt(5) == 0;
                    for (int y = baseY - 2; y < baseY; y++) place(level, new BlockPos(x, y, z), bare ? sand : dirt);
                    BlockPos top = new BlockPos(x, baseY, z);
                    place(level, top, bare ? sand : grass);
                    clearAbove(level, top, air);
                    shore.add(top);
                }
            }
        }

        // sugar cane where the shore touches the water
        for (BlockPos s : shore) {
            if (random.nextInt(4) != 0) continue;
            boolean wet = false;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(s.relative(dir)).is(Blocks.WATER)) wet = true;
            }
            if (!wet) continue;
            int height = 1 + random.nextInt(3);
            for (int i = 1; i <= height; i++) {
                BlockPos at = s.above(i);
                BlockState cane = Blocks.SUGAR_CANE.defaultBlockState();
                if (!level.getBlockState(at).isAir() || !cane.canSurvive(level, at)) break;
                place(level, at, cane);
            }
        }

        // the palms, round the pond on the grass, never close together
        int wanted = 2 + random.nextInt(3);
        List<BlockPos> palms = new ArrayList<>();
        for (int tries = 0; tries < 16 && palms.size() < wanted; tries++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double d = pond + 1.5 + random.nextDouble() * 1.6;
            BlockPos ground = new BlockPos(origin.getX() + (int) Math.round(Math.cos(angle) * d), baseY, origin.getZ() + (int) Math.round(Math.sin(angle) * d));
            if (!level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) continue;
            boolean apart = true;
            for (BlockPos other : palms) {
                if (other.distSqr(ground) < 9) apart = false;
            }
            if (!apart) continue;
            FeaturePlaceContext<NoneFeatureConfiguration> palm = new FeaturePlaceContext<>(context.topFeature(), level, context.chunkGenerator(), random, ground.above(), NoneFeatureConfiguration.INSTANCE);
            if (ModSurvival.POWER_PALM_FEATURE.get().place(palm)) palms.add(ground);
        }
        return true;
    }

    private void place(WorldGenLevel level, BlockPos pos, BlockState state) {
        setBlock(level, pos, state);
    }

    /** Takes the dunes, cacti and loose plants off the five blocks over a pond or shore column. */
    private void clearAbove(WorldGenLevel level, BlockPos ground, BlockState air) {
        for (int i = 1; i <= 5; i++) {
            BlockPos p = ground.above(i);
            if (!level.getBlockState(p).isAir()) place(level, p, air);
        }
    }
}
