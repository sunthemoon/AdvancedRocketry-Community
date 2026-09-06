package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

/** Presentation-only seat offsets, shared by authoritative and remote entities. */
public record RocketPassengerPosition(double x, double y, double z) {
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
