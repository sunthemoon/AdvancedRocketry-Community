package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.component;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketBlockMetrics;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable loading metadata. No world, callback, session state or mutable registry. */
public final class RocketComponentCatalog {
    private static final RocketComponentCatalog EMPTY = new RocketComponentCatalog(Map.of());
    private final Map<Block, RocketBlockMetrics> metrics;

    RocketComponentCatalog(Map<Block, RocketBlockMetrics> metrics) {
        this.metrics = Map.copyOf(metrics);
    }

    public static RocketComponentCatalog empty() {
        return EMPTY;
    }

    /** Null denotes legacy tag/default resolution, not a rejected block. */
    public RocketBlockMetrics find(BlockState state) {
        return metrics.get(Objects.requireNonNull(state, "state").getBlock());
    }

    public int size() {
        return metrics.size();
    }
}
