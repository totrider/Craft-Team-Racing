package com.craftteamracing.survival;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A Power Palm: a slender trunk four or five logs tall (never taller than an oak's, so every log can be reached from the ground), leaning a block at the top now and then, and a crown of
 * four drooping fronds (each a short arch out to four blocks, its tip hanging) with tufts between them and bunches of Power Fruit beside the top of the trunk. Every leaf is placed with its
 * distance from the nearest log counted along the leaves (the most is 5, where leaves decay at 7), so the crown holds together and never decays. The origin is where the first log goes.
 */
public class PowerPalmFeature extends Feature<NoneFeatureConfiguration> {
    public PowerPalmFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    private static boolean free(WorldGenLevel level, BlockPos pos) {
        return level.isStateAtPosition(pos, s -> s.isAir() || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.LEAVES));
    }

    /** One frond: out from the top log in {@code d} (and {@code d2} for a diagonal), up a block in the middle, its tip hanging (not when it is long: it would be too far from a log). */
    private static void arm(Map<BlockPos, Integer> leaves, BlockPos top, Direction d, Direction d2, int len, RandomSource random) {
        for (int i = 1; i <= len; i++) {
            int dy = i == 1 ? 0 : i <= len - 2 ? 1 : i == len - 1 ? 0 : (len >= 5 ? 0 : -1);
            BlockPos at = top.relative(d, i);
            if (d2 != null) at = at.relative(d2, i);
            at = at.above(dy);
            if (random.nextInt(14) != 0 || i == 1) leaves.put(at, 0);
            if (i >= 2) leaves.put(at.below(), 0);                                       // thickness under the frond
            if (i == len && dy <= 0 && len < 5) leaves.put(at.below(2), 0);              // the hanging tip
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockState ground = level.getBlockState(origin.below());
        if (!ground.is(BlockTags.SAND) && !ground.is(BlockTags.DIRT)) return false;
        // four looks: a classic crown, a bushy one (eight short fronds), a tall one with long fronds, and a windswept one (a trunk that bends, fronds streaming one way)
        int style = random.nextInt(4);
        int height = style == 2 ? 5 : 4 + random.nextInt(2);
        Direction lean = style == 3 ? Direction.Plane.HORIZONTAL.getRandomDirection(random) : random.nextInt(3) == 0 ? Direction.Plane.HORIZONTAL.getRandomDirection(random) : null;
        List<BlockPos> logs = new ArrayList<>();
        for (int i = 0; i < height; i++) {
            BlockPos p = origin.above(i);
            if (lean != null) {
                int shift = style == 3 ? Math.max(0, i - 1) / 2 + (i >= height - 1 ? 1 : 0) : (i >= height - 2 ? 1 : 0);
                p = p.relative(lean, Math.min(shift, 2));
            }
            logs.add(p);
        }
        for (BlockPos p : logs) {
            if (!free(level, p)) return false;
        }
        BlockPos top = logs.get(logs.size() - 1);

        // the crown: relative to the top log; an arm goes out in a direction, arches up, and its tip hangs
        Map<BlockPos, Integer> leaves = new HashMap<>();
        BlockPos topUp = top.above();
        leaves.put(topUp, 0);
        if (style != 1 || random.nextBoolean()) leaves.put(topUp.above(), 0);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int len = switch (style) {
                case 1 -> 3;
                case 2 -> 4 + random.nextInt(2);
                case 3 -> d == lean ? 5 : d == lean.getOpposite() ? 2 : 3 + random.nextInt(2);
                default -> 3 + random.nextInt(2);
            };
            arm(leaves, top, d, null, len, random);
            if (style == 1) arm(leaves, top, d, d.getClockWise(), 2, random);              // the diagonal fronds of the bushy crown
        }
        // the tufts between the arms
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Direction side = d.getClockWise();
            leaves.put(top.relative(d, 1).relative(side, 1), 0);
            if (random.nextInt(4) != 0) leaves.put(top.relative(d, 1).relative(side, 1).above(), 0);
            if (random.nextInt(3) != 0) leaves.put(top.relative(d, 2).relative(side, 1).below(), 0);
        }
        // the fruit: two bunches beside the top of the trunk, in place of leaves
        List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        java.util.Collections.shuffle(sides, new java.util.Random(random.nextLong()));
        List<BlockPos> bunches = List.of(top.relative(sides.get(0)).below(), top.relative(sides.get(1)).below());
        for (BlockPos b : bunches) {
            leaves.remove(b);
        }
        // what may not be built on: not free, or a log; and nothing within two blocks of the ground the trunk stands on (a short palm's drooping tips would reach the grass)
        int lowest = origin.getY() + 2;
        leaves.keySet().removeIf(p -> logs.contains(p) || !free(level, p) || p.getY() < lowest);

        // the distance of each leaf from the nearest log, counted over faces through leaves
        Map<BlockPos, Integer> dist = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos l : logs) {
            dist.put(l, 0);
            queue.add(l);
        }
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            int d = dist.get(at);
            for (Direction dir : Direction.values()) {
                BlockPos n = at.relative(dir);
                if (leaves.containsKey(n) && !dist.containsKey(n)) {
                    dist.put(n, d + 1);
                    queue.add(n);
                }
            }
        }
        leaves.keySet().removeIf(p -> !dist.containsKey(p) || dist.get(p) > 6);         // anything that would not hold is not placed

        for (BlockPos p : logs) {
            setBlock(level, p, ModSurvival.POWER_PALM_LOG.get().defaultBlockState());
        }
        BlockState leaf = ModSurvival.POWER_PALM_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (BlockPos p : leaves.keySet()) {
            setBlock(level, p, leaf.setValue(LeavesBlock.DISTANCE, Math.max(1, Math.min(7, dist.get(p)))));
        }
        for (BlockPos b : bunches) {
            if (free(level, b) && leaves.containsKey(b.above())) setBlock(level, b, ModSurvival.POWER_FRUIT_CLUSTER.get().defaultBlockState().setValue(PowerFruitClusterBlock.FRUITS, 1 + random.nextInt(2)));
        }
        return true;
    }
}
