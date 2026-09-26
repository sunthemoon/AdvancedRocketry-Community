package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundary;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable lookup: no callbacks, world references or per-scan allocation. */
public final class AtmosphereBoundaryCatalog {
    private static final AtmosphereBoundaryCatalog EMPTY = new AtmosphereBoundaryCatalog(Map.of());
    private final Map<BlockState, AtmosphereBoundary> states;

    AtmosphereBoundaryCatalog(Map<BlockState, AtmosphereBoundary> states) {
        this.states = Map.copyOf(states);
    }

    public static AtmosphereBoundaryCatalog empty() {
        return EMPTY;
    }

    public AtmosphereBoundary classify(BlockState state) {
        return states.getOrDefault(state, AtmosphereBoundary.DEFAULT);
    }

    public boolean isRegistered(BlockState state) {
        return states.containsKey(state);
    }

    public boolean isEmpty() {
        return states.isEmpty();
    }

    public int stateCount() {
        return states.size();
    }
}
