package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import com.mojang.serialization.Codec;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A charred tree on Venus (ADR-063 section 5): the legacy generator stood a bare trunk of charcoal logs, six to eight
 * blocks high, on solid ground with open air above. This adds at most one short stub branch near the top. Every
 * write lies within {@link #MAX_REACH} blocks of the origin horizontally and {@link #MAX_HEIGHT} above it.
 */
public final class CharredTreeFeature extends Feature<NoneFeatureConfiguration> {
    public static final int MIN_HEIGHT = 6;
    public static final int MAX_HEIGHT = 8;
    public static final int MAX_REACH = 1;

    public CharredTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        if (origin.getY() <= level.getMinBuildHeight() || origin.getY() + height >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockState ground = level.getBlockState(origin.below());
        if (!ground.isFaceSturdy(level, origin.below(), Direction.UP)) {
            return false;
        }
        for (int y = 0; y < height; y++) {
            if (!level.isEmptyBlock(origin.above(y))) {
                return false;
            }
        }
        BlockState log = SurfaceContent.CHARCOAL_LOG.get().defaultBlockState();
        for (int y = 0; y < height; y++) {
            level.setBlock(origin.above(y), log, Block.UPDATE_CLIENTS);
        }
        if (random.nextInt(3) == 0) {
            Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockPos stub = origin.above(height - 2 - random.nextInt(2)).relative(side);
            if (level.isEmptyBlock(stub)) {
                level.setBlock(stub, log.setValue(RotatedPillarBlock.AXIS, side.getAxis()), Block.UPDATE_CLIENTS);
            }
        }
        return true;
    }
}
