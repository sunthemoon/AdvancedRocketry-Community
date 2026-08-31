package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.Collection;
import java.util.Objects;

public record RocketBounds(RocketPosition minimum, RocketPosition maximum) {
    public RocketBounds {
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        if (minimum.x() > maximum.x()
                || minimum.y() > maximum.y()
                || minimum.z() > maximum.z()) {
            throw new IllegalArgumentException("Minimum rocket bounds must not exceed maximum");
        }
        if (volumeOf(minimum, maximum) > RocketLimits.MAX_BOUNDING_VOLUME) {
            throw new RocketSnapshotException(
                    RocketValidationCode.BOUNDING_VOLUME_EXCEEDED,
                    "Rocket bounding volume exceeds " + RocketLimits.MAX_BOUNDING_VOLUME
            );
        }
    }

    public static RocketBounds enclosing(Collection<RocketPosition> positions) {
        Objects.requireNonNull(positions, "positions");
        if (positions.isEmpty()) {
            throw new RocketSnapshotException(
                    RocketValidationCode.EMPTY_STRUCTURE,
                    "A rocket structure must contain at least one block"
            );
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (RocketPosition position : positions) {
            Objects.requireNonNull(position, "position");
            minX = Math.min(minX, position.x());
            minY = Math.min(minY, position.y());
            minZ = Math.min(minZ, position.z());
            maxX = Math.max(maxX, position.x());
            maxY = Math.max(maxY, position.y());
            maxZ = Math.max(maxZ, position.z());
        }
        return new RocketBounds(
                new RocketPosition(minX, minY, minZ),
                new RocketPosition(maxX, maxY, maxZ)
        );
    }

    public long volume() {
        return volumeOf(minimum, maximum);
    }

    public int sizeX() {
        return Math.toIntExact((long) maximum.x() - minimum.x() + 1L);
    }

    public int sizeY() {
        return Math.toIntExact((long) maximum.y() - minimum.y() + 1L);
    }

    public int sizeZ() {
        return Math.toIntExact((long) maximum.z() - minimum.z() + 1L);
    }

    public boolean contains(RocketPosition position) {
        return position.x() >= minimum.x() && position.x() <= maximum.x()
                && position.y() >= minimum.y() && position.y() <= maximum.y()
                && position.z() >= minimum.z() && position.z() <= maximum.z();
    }

    private static long volumeOf(RocketPosition minimum, RocketPosition maximum) {
        long sizeX = (long) maximum.x() - minimum.x() + 1L;
        long sizeY = (long) maximum.y() - minimum.y() + 1L;
        long sizeZ = (long) maximum.z() - minimum.z() + 1L;
        try {
            return Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
