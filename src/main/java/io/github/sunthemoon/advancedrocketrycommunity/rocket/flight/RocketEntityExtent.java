package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import java.util.Objects;

/**
 * W311: the rocket entity's interaction box relative to its position, the bottom center of the origin block. It
 * encloses every structure block and, above each seat, the space a seated rider occupies, so the rider's eyes start
 * inside it. It reaches only picking and interaction distance; the rocket is never a collision surface.
 */
public record RocketEntityExtent(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    /** A rider's feet are 0.15 above the seat block's bottom (RocketPassengerPosition); a player is 1.8 tall. */
    public static final int SEAT_HEADROOM_BLOCKS = 2;

    public RocketEntityExtent {
        if (!(minX < maxX && minY < maxY && minZ < maxZ)) {
            throw new IllegalArgumentException("Rocket entity extent must not be empty");
        }
    }

    /** Anchors outside the bounds place their rider at the fallback offset, so they add no headroom. */
    public static RocketEntityExtent of(RocketBounds bounds, List<RocketPosition> seats) {
        Objects.requireNonNull(bounds, "bounds");
        double maxY = bounds.maximum().y() + 1.0D;
        for (RocketPosition seat : Objects.requireNonNull(seats, "seats")) {
            if (bounds.contains(seat)) {
                maxY = Math.max(maxY, (double) seat.y() + SEAT_HEADROOM_BLOCKS);
            }
        }
        return new RocketEntityExtent(
                bounds.minimum().x() - 0.5D, bounds.minimum().y(), bounds.minimum().z() - 0.5D,
                bounds.maximum().x() + 0.5D, maxY, bounds.maximum().z() + 0.5D);
    }
}
