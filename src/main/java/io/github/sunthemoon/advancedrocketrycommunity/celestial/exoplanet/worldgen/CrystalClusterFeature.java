package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen;

import com.mojang.serialization.Codec;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetBlocks;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The crystal cluster ({@link ExoplanetShapes#crystalCluster}) of one crystal colour, rooted in the ground at its
 * origin, replacing anything except blocks features may not replace.
 */
final class CrystalClusterFeature extends Feature<NoneFeatureConfiguration> {
    CrystalClusterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        ExoplanetShapes.Crystal crystal = ExoplanetShapes.crystalCluster(new Random(context.random().nextLong()));
        int top = crystal.cells().stream().mapToInt(BlockPos::getY).max().orElse(0);
        if (origin.getY() + top >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockState block = ExoplanetBlocks.CRYSTALS.get(crystal.colour()).get().defaultBlockState();
        for (BlockPos cell : crystal.cells()) {
            BlockPos position = origin.offset(cell);
            if (!level.getBlockState(position).is(BlockTags.FEATURES_CANNOT_REPLACE)) {
                level.setBlock(position, block, 2);
            }
        }
        return true;
    }
}
