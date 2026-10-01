package io.github.sunthemoon.advancedrocketrycommunity.endgame.root;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-054 section 9: one index record per registered endpoint (at most 384 bytes encoded: two UUIDs, a kind ID of at
 * most 64 characters, a Level key of at most 128 characters, a position, a state and the registration epoch).
 *
 * @param registeredEpoch the save epoch at registration; the registration is durable once the root's save epoch is
 *                        greater (ADR-050 section 2)
 */
public record EndpointRecord(UUID id, ResourceLocation kind, UUID owner, ResourceLocation level, long pos,
                             State state, long registeredEpoch) {
    public static final int MAX_KIND_LENGTH = 64;
    public static final int MAX_LEVEL_LENGTH = 128;

    public EndpointRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(state, "state");
        if (kind.toString().length() > MAX_KIND_LENGTH) {
            throw new IllegalArgumentException("An endpoint kind ID has at most 64 characters");
        }
        if (level.toString().length() > MAX_LEVEL_LENGTH) {
            throw new IllegalArgumentException("A Level key has at most 128 characters");
        }
        if (registeredEpoch < 1L) {
            throw new IllegalArgumentException("A registration epoch is positive");
        }
    }

    public EndpointRecord withState(State next) {
        return new EndpointRecord(id, kind, owner, level, pos, next, registeredEpoch);
    }

    public enum State {
        /** Registered and selectable. */
        ACTIVE,
        /** Its block entity is gone from a loaded chunk: retired, but kept until the owner forgets it. */
        MISSING
    }
}
