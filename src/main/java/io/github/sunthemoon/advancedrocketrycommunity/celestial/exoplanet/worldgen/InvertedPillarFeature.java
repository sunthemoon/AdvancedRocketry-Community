package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen;

import com.mojang.serialization.Codec;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen.ExoplanetShapes.PillarPart;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The inverted pillar ({@link ExoplanetShapes#invertedPillar}) standing on the sea floor at its origin, replacing
 * water and anything else except blocks features may not replace.
 */
final class InvertedPillarFeature extends Feature<NoneFeatureConfiguration> {
    InvertedPillarFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        Map<BlockPos, PillarPart> cells = ExoplanetShapes.invertedPillar(new Random(context.random().nextLong()));
        int top = cells.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0);
        if (origin.getY() + top >= level.getMaxBuildHeight()) {
            return false;
        }
        for (Map.Entry<BlockPos, PillarPart> cell : cells.entrySet()) {
            BlockPos position = origin.offset(cell.getKey());
            if (!level.getBlockState(position).is(BlockTags.FEATURES_CANNOT_REPLACE)) {
                level.setBlock(position, state(cell.getValue()), 2);
            }
        }
        return true;
    }

    private static BlockState state(PillarPart part) {
        return switch (part) {
            case MOSSY_COBBLESTONE -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case COBBLESTONE -> Blocks.COBBLESTONE.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
            case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
        };
    }
}
