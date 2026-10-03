package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The giant swamp tree ({@link ExoplanetShapes#giantSwampTree}) of oak logs and oak leaves, as the legacy tree. It
 * stands on a sturdy floor, also under shallow water; its roots replace the ground below the origin except blocks
 * features may not replace, its trunk core above the origin must fit, and its other logs and leaves fill free cells.
 */
final class GiantSwampTreeFeature extends Feature<NoneFeatureConfiguration> {
    GiantSwampTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        if (!level.getBlockState(origin.below()).isFaceSturdy(level, origin.below(), Direction.UP)) {
            return false;
        }
        TreePlan plan = ExoplanetShapes.giantSwampTree(new Random(context.random().nextLong()));
        int top = plan.leaves().keySet().stream().mapToInt(BlockPos::getY).max().orElse(0);
        int bottom = plan.logs().stream().mapToInt(BlockPos::getY).min().orElse(0);
        if (origin.getY() + top >= level.getMaxBuildHeight() || origin.getY() + bottom <= level.getMinBuildHeight()) {
            return false;
        }
        for (BlockPos log : plan.logs()) {
            boolean core = log.getY() >= 0 && Math.abs(log.getX()) <= 1 && Math.abs(log.getZ()) <= 1;
            if (core && !TreeFeature.validTreePos(level, origin.offset(log))) {
                return false;
            }
        }
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        for (BlockPos cell : plan.logs()) {
            BlockPos position = origin.offset(cell);
            BlockState existing = level.getBlockState(position);
            boolean root = cell.getY() < 0 && !existing.is(BlockTags.FEATURES_CANNOT_REPLACE);
            if (root || TreeFeature.validTreePos(level, position)) {
                level.setBlock(position, log, 19);
            }
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        for (Map.Entry<BlockPos, Integer> cell : plan.leaves().entrySet()) {
            BlockPos position = origin.offset(cell.getKey());
            if (TreeFeature.isAirOrLeaves(level, position)) {
                level.setBlock(position, leaves.setValue(LeavesBlock.DISTANCE, cell.getValue()), 19);
            }
        }
        return true;
    }
}
