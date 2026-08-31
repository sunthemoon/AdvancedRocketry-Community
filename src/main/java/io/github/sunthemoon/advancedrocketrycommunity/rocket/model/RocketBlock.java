package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import java.util.Objects;
import java.util.Optional;

public final class RocketBlock implements Comparable<RocketBlock> {
    private final RocketPosition position;
    private final RocketBlockState state;
    private final RocketBlockEntityPayload blockEntityPayload;

    public RocketBlock(
            RocketPosition position,
            RocketBlockState state,
            RocketBlockEntityPayload blockEntityPayload
    ) {
        this.position = Objects.requireNonNull(position, "position");
        this.state = Objects.requireNonNull(state, "state");
        this.blockEntityPayload = blockEntityPayload;
    }

    public RocketBlock(RocketPosition position, RocketBlockState state) {
        this(position, state, null);
    }

    public RocketPosition position() {
        return position;
    }

    public RocketBlockState state() {
        return state;
    }

    public Optional<RocketBlockEntityPayload> blockEntityPayload() {
        return Optional.ofNullable(blockEntityPayload);
    }

    @Override
    public int compareTo(RocketBlock other) {
        return position.compareTo(other.position);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof RocketBlock other
                && position.equals(other.position)
                && state.equals(other.state)
                && Objects.equals(blockEntityPayload, other.blockEntityPayload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, state, blockEntityPayload);
    }
}
