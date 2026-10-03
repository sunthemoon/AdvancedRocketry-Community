package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen;

import com.mojang.serialization.Codec;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetBlocks;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The lightwood tree ({@link ExoplanetShapes#lightwoodTree}) on dirt or grass. The whole 2 × 2 trunk must fit, or
 * nothing is placed (a sapling then stays); branches and leaves fill only free cells. Used by worldgen and by the
 * lightwood sapling.
 */
final class LightwoodTreeFeature extends Feature<NoneFeatureConfiguration> {
    LightwoodTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        if (!level.getBlockState(origin.below()).is(BlockTags.DIRT)) {
            return false;
        }
        TreePlan plan = ExoplanetShapes.lightwoodTree(new Random(context.random().nextLong()));
        int top = Math.max(plan.logs().stream().mapToInt(BlockPos::getY).max().orElse(0),
                plan.leaves().keySet().stream().mapToInt(BlockPos::getY).max().orElse(0));
        if (origin.getY() + top >= level.getMaxBuildHeight()) {
            return false;
        }
        for (BlockPos log : plan.logs()) {
            boolean trunk = log.getX() >= 0 && log.getX() <= 1 && log.getZ() >= 0 && log.getZ() <= 1;
            if (trunk && !TreeFeature.validTreePos(level, origin.offset(log))) {
                return false;
            }
        }
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                BlockPos ground = origin.offset(x, -1, z);
                if (level.getBlockState(ground).is(BlockTags.DIRT)) {
                    level.setBlock(ground, Blocks.DIRT.defaultBlockState(), 19);
                }
            }
        }
        BlockState log = ExoplanetBlocks.LIGHTWOOD_LOG.get().defaultBlockState();
        for (BlockPos cell : plan.logs()) {
            BlockPos position = origin.offset(cell);
            if (TreeFeature.validTreePos(level, position)) {
                level.setBlock(position, log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), 19);
            }
        }
        BlockState leaves = ExoplanetBlocks.LIGHTWOOD_LEAVES.get().defaultBlockState();
        for (Map.Entry<BlockPos, Integer> cell : plan.leaves().entrySet()) {
            BlockPos position = origin.offset(cell.getKey());
            if (TreeFeature.isAirOrLeaves(level, position)) {
                level.setBlock(position, leaves.setValue(LeavesBlock.DISTANCE, cell.getValue()), 19);
            }
        }
        return true;
    }
}
