package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundary;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundaryProvider;
import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;

/** Internal constant rule, expanded as data by the registry rather than as a timed callback. */
public record ConstantAtmosphereBoundaryProvider(AtmosphereBoundary boundary) implements AtmosphereBoundaryProvider {
    public ConstantAtmosphereBoundaryProvider {
        Objects.requireNonNull(boundary, "boundary");
    }

    @Override
    public AtmosphereBoundary classify(BlockState state) {
        return boundary;
    }
}
