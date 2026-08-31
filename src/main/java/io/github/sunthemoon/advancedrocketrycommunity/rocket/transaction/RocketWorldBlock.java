package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import java.util.Objects;
import java.util.Optional;

public final class RocketWorldBlock {
    private final RocketBlockState state;
    private final RocketBlockEntityPayload payload;

    public RocketWorldBlock(RocketBlockState state, RocketBlockEntityPayload payload) {
        this.state = Objects.requireNonNull(state, "state");
        this.payload = payload;
    }

    public static RocketWorldBlock fromSnapshotBlock(RocketBlock block) {
        return new RocketWorldBlock(block.state(), block.blockEntityPayload().orElse(null));
    }

    public RocketBlockState state() {
        return state;
    }

    public Optional<RocketBlockEntityPayload> payload() {
        return Optional.ofNullable(payload);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof RocketWorldBlock other
                && state.equals(other.state)
                && Objects.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, payload);
    }
}
