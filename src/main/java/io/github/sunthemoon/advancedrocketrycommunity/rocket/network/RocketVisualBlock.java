package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.Objects;

public record RocketVisualBlock(RocketPosition position, RocketBlockState state)
        implements Comparable<RocketVisualBlock> {
    public RocketVisualBlock {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
    }

    @Override
    public int compareTo(RocketVisualBlock other) {
        return position.compareTo(other.position);
    }
}
