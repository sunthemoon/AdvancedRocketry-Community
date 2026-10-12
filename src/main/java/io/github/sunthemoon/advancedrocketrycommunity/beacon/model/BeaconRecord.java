package io.github.sunthemoon.advancedrocketrycommunity.beacon.model;

import java.util.Objects;
import java.util.UUID;

/**
 * One schema-1 beacon index record (ADR-065 section 7): the stable beacon UUID, its owner, the body and Level it
 * stands in, its block position, the structure generation it last formed with, whether it was last known active
 * (formed, enabled and owned) and its name.
 */
public record BeaconRecord(UUID beaconId, UUID owner, String body, String level, int x, int y, int z, long generation,
                           boolean active, String name) {
    public BeaconRecord {
        Objects.requireNonNull(beaconId, "beaconId");
        Objects.requireNonNull(owner, "owner");
        if (!BeaconLimits.validId(body) || !BeaconLimits.validId(level)) {
            throw new IllegalArgumentException("A beacon body and Level are bounded explicit ResourceLocations");
        }
        if (!BeaconLimits.validPosition(x, y, z)) {
            throw new IllegalArgumentException("A beacon lies outside the world limits");
        }
        if (generation < 0) {
            throw new IllegalArgumentException("A beacon structure generation is not negative");
        }
        if (!BeaconLimits.validName(name)) {
            throw new IllegalArgumentException("A beacon name has at most " + BeaconLimits.MAX_NAME_CHARS
                    + " printable characters");
        }
    }

    /** This record with another activity and generation; its encoded size, owner and body are unchanged. */
    public BeaconRecord withActivity(boolean value, long atGeneration) {
        return new BeaconRecord(beaconId, owner, body, level, x, y, z, atGeneration, value, name);
    }

    /** Whether this record names the given Level and block position. */
    public boolean at(String levelId, int atX, int atY, int atZ) {
        return level.equals(levelId) && x == atX && y == atY && z == atZ;
    }

    /** Squared block distance to a point, exact in a long for every valid position. */
    public long distanceSquared(long fromX, long fromY, long fromZ) {
        long dx = x - fromX;
        long dy = y - fromY;
        long dz = z - fromZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
