package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.model;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-059 section 1: one bound elevator (at most 512 bytes encoded). The anchor's body and Level key are stored at
 * bind; a later catalog mapping of the body to another Level makes the pair invalid ({@code PAIR_LEVEL_CHANGED}), and
 * it is kept. {@code pairId} is random at bind and never reused.
 */
public record ElevatorPair(UUID pairId, UUID stationId, UUID terminalId, UUID anchorId, ResourceLocation bodyId,
                           ResourceLocation levelKey, int x, int z, int anchorY, long boundAt, UUID boundBy) {
    public static final int MAX_ID_LENGTH = 128;
    public static final int MAX_COORDINATE = 30_000_000;
    public static final int MIN_Y = -2048;
    public static final int MAX_Y = 4096;

    public ElevatorPair {
        Objects.requireNonNull(pairId, "pairId");
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(terminalId, "terminalId");
        Objects.requireNonNull(anchorId, "anchorId");
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(levelKey, "levelKey");
        Objects.requireNonNull(boundBy, "boundBy");
        if (bodyId.toString().length() > MAX_ID_LENGTH || levelKey.toString().length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("A pair's body ID or Level key has at most 128 characters");
        }
        if (Math.abs(x) > MAX_COORDINATE || Math.abs(z) > MAX_COORDINATE || anchorY < MIN_Y || anchorY > MAX_Y) {
            throw new IllegalArgumentException("A pair's anchor position is outside its bounds");
        }
        if (terminalId.equals(anchorId)) {
            throw new IllegalArgumentException("A pair joins two different endpoints");
        }
        if (boundAt < 0L) {
            throw new IllegalArgumentException("A bind time is not negative");
        }
    }

    /** Whether this pair names the endpoint as its anchor or terminal. */
    public boolean names(UUID endpoint) {
        return terminalId.equals(endpoint) || anchorId.equals(endpoint);
    }

    /** The other end of the pair from {@code endpoint}, which the pair names. */
    public UUID otherEnd(UUID endpoint) {
        if (terminalId.equals(endpoint)) {
            return anchorId;
        }
        if (anchorId.equals(endpoint)) {
            return terminalId;
        }
        throw new IllegalArgumentException("The pair does not name " + endpoint);
    }

    public Column column() {
        return new Column(levelKey, x, z);
    }

    /** One anchor column {@code (Level, x, z)}: at most one pair stands on it. */
    public record Column(ResourceLocation level, int x, int z) {
        public Column {
            Objects.requireNonNull(level, "level");
        }
    }
}
