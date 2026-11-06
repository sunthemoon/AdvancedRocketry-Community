package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;

/** Presentation-only seat offsets, shared by authoritative and remote entities. */
public record RocketPassengerPosition(double x, double y, double z) {
    /** The entity origin already includes the block-center offset in X and Z. */
    public static RocketPassengerPosition forSeat(int seatIndex, int capacity,
            List<RocketPosition> anchors, RocketBounds bounds) {
        RocketPassengerPosition fallback = forSeat(seatIndex, capacity);
        if (seatIndex >= anchors.size()) { return fallback; }
        RocketPosition anchor = anchors.get(seatIndex);
        if (!bounds.contains(anchor)) { return fallback; }
        // Preserve the original sitting offset for the usual seat at relative Y=1.
        return new RocketPassengerPosition(anchor.x(), anchor.y() + 0.15D, anchor.z());
    }

    /** Retained while a visual snapshot or the assigned seat block is unavailable. */
    public static RocketPassengerPosition forSeat(int seatIndex, int capacity) {
        if (capacity < 0 || capacity > RocketFlightLimits.MAX_PASSENGERS
                || seatIndex < 0 || seatIndex >= Math.max(1, capacity)) {
            throw new IllegalArgumentException("Seat position is outside the fixed passenger bound");
        }
        double angle = seatIndex * (Math.PI * 2.0D / Math.max(1, capacity));
        double radius = seatIndex == 0 ? 0.0D : 0.35D;
        return new RocketPassengerPosition(Math.cos(angle) * radius, 1.15D, Math.sin(angle) * radius);
    }
}
