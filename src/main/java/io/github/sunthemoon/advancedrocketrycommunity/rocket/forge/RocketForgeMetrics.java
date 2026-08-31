package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketBlockMetrics;
import net.minecraft.world.level.block.state.BlockState;

public final class RocketForgeMetrics {
    private RocketForgeMetrics() {
    }

    public static RocketBlockMetrics resolve(BlockState state) {
        boolean engine = state.is(ModBlockTags.ROCKET_ENGINES);
        boolean tank = state.is(ModBlockTags.ROCKET_FUEL_TANKS);
        boolean seat = state.is(ModBlockTags.ROCKET_SEATS);
        boolean guidance = state.is(ModBlockTags.ROCKET_GUIDANCE);
        int roles = (engine ? 1 : 0) + (tank ? 1 : 0) + (seat ? 1 : 0) + (guidance ? 1 : 0);
        if (roles > 1) {
            throw new IllegalArgumentException("Rocket block belongs to multiple exclusive role tags");
        }
        if (engine) {
            return new RocketBlockMetrics(100L, 1_000L, 0L, true, false, false);
        }
        if (tank) {
            return new RocketBlockMetrics(50L, 0L, 1_000L, false, false, false);
        }
        if (seat) {
            return new RocketBlockMetrics(20L, 0L, 0L, false, true, false);
        }
        if (guidance) {
            return new RocketBlockMetrics(30L, 0L, 0L, false, false, true);
        }
        return RocketBlockMetrics.structural(10L);
    }
}
