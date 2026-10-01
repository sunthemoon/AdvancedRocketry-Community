package io.github.sunthemoon.advancedrocketrycommunity.endgame.root;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-054 sections 9 and 11: the retired ID of a removed endpoint. A retired ID never registers again; its returning
 * copy is frozen. A tombstone is <b>young</b> until a chunk-save or chunk-load tag showed the endpoint's absence at
 * least 40 ticks earlier, and then <b>settled</b> (review R3-M2).
 */
public sealed interface Tombstone permits Tombstone.Young, Tombstone.Settled {
    UUID id();

    UUID owner();

    long pos();

    /** The hash of the Level key; a settled tombstone keeps only this, so it fits 64 bytes. */
    int levelHash();

    static int hash(ResourceLocation level) {
        return level.toString().hashCode();
    }

    /** Takes its endpoint's place in the owner's and the server's endpoint limits until it settles. */
    record Young(UUID id, UUID owner, ResourceLocation level, long pos) implements Tombstone {
        public Young {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(level, "level");
            if (level.toString().length() > EndpointRecord.MAX_LEVEL_LENGTH) {
                throw new IllegalArgumentException("A Level key has at most 128 characters");
            }
        }

        @Override
        public int levelHash() {
            return hash(level);
        }

        public Settled settle(int order) {
            return new Settled(id, owner, levelHash(), pos, order);
        }
    }

    /**
     * Six longs encoded: ID, owner, position and {@code levelHash << 32 | order}.
     *
     * @param order monotone settlement order; eviction removes the lowest first
     */
    record Settled(UUID id, UUID owner, int levelHash, long pos, int order) implements Tombstone {
        public Settled {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(owner, "owner");
            if (order < 0) {
                throw new IllegalArgumentException("A settlement order is not negative");
            }
        }
    }
}
