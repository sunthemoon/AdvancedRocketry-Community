package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen.ExoplanetWorldgen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * The lightwood sapling grows the lightwood tree of worldgen (ADR-063 section 6, revision 6). Vanilla's sapling rules
 * apply: two stages, bone meal succeeding in 45 % of uses (as legacy), and the sapling stays when the tree does not
 * fit.
 */
final class LightwoodTreeGrower extends AbstractTreeGrower {
    @Override
    protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean flowers) {
        return ExoplanetWorldgen.LIGHTWOOD_TREE;
    }
}
