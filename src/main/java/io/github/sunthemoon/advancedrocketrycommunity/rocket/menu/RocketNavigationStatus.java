package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanCode;
import java.util.Locale;

/** Explicit display wire IDs; none of these grant launch authority. */
public enum RocketNavigationStatus {
    READY(0), CURRENT(1), NO_ROUTE(2), MISSING_COMPONENTS(3), INSUFFICIENT_THRUST(4),
    FUEL_STATE_MISMATCH(5), INSUFFICIENT_CAPACITY(6), INSUFFICIENT_FUEL(7),
    INVALID_STATE(8), UNAUTHORIZED(9), UNAVAILABLE(10), ARITHMETIC_OVERFLOW(11);

    private final int wireId;

    RocketNavigationStatus(int wireId) { this.wireId = wireId; }

    public int wireId() { return wireId; }

    public String translationKey() {
        return "navigation.advancedrocketrycommunity." + name().toLowerCase(Locale.ROOT);
    }

    public static RocketNavigationStatus fromWire(int id) {
        for (var value : values()) {
            if (value.wireId == id) { return value; }
        }
        throw new IllegalArgumentException("Unknown navigation status " + id);
    }

    public static RocketNavigationStatus resolve(RocketFlightPlanCode code, boolean launchableState, boolean authorized) {
        if (!authorized) { return UNAUTHORIZED; }
        if (!launchableState) { return INVALID_STATE; }
        return switch (code) {
            case SUCCESS -> READY;
            case SAME_DESTINATION -> CURRENT;
            case UNSUPPORTED_ROUTE -> NO_ROUTE;
            case MISSING_FLIGHT_COMPONENTS -> MISSING_COMPONENTS;
            case INSUFFICIENT_THRUST -> INSUFFICIENT_THRUST;
            case FUEL_STATE_MISMATCH -> FUEL_STATE_MISMATCH;
            case INSUFFICIENT_CAPACITY -> INSUFFICIENT_CAPACITY;
            case INSUFFICIENT_FUEL -> INSUFFICIENT_FUEL;
            case ARITHMETIC_OVERFLOW -> ARITHMETIC_OVERFLOW;
        };
    }
}
