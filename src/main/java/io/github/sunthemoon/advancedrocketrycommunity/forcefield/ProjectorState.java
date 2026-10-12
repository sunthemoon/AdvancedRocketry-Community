package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Direction;

/** A bit mask is the complete bounded set of claimed straight-line offsets. */
public record ProjectorState(UUID id, Optional<UUID> owner, long generation, Direction direction, int claims) {
    public ProjectorState {
        Objects.requireNonNull(id);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(direction);
        if (generation < 1 || (owner.isEmpty() && claims != 0)) {
            throw new IllegalArgumentException("Invalid projector state");
        }
    }
    public ProjectorState withClaims(int mask) { return new ProjectorState(id, owner, generation, direction, mask); }
    public ProjectorState bind(UUID player) {
        if (owner.isPresent()) { throw new IllegalStateException("Projector already owned"); }
        return new ProjectorState(id, Optional.of(player), generation, direction, claims);
    }
}
