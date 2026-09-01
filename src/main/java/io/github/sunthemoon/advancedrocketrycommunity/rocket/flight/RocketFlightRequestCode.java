package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Locale;

public enum RocketFlightRequestCode {
    SUCCESS,
    ENTITY_UNAVAILABLE,
    OUT_OF_RANGE,
    UNAUTHORIZED,
    INVALID_STATE,
    INVALID_DESTINATION,
    RATE_LIMITED,
    REQUEST_REPLAYED,
    REQUEST_LEDGER_FULL,
    TRANSFER_JOURNAL_BLOCKED,
    TRANSFER_LIMIT_REACHED,
    LANDING_PAD_UNAVAILABLE,
    TRANSFER_PREPARE_FAILED,
    NO_SEAT_AVAILABLE,
    ALREADY_BOARDED,
    NOT_BOARDED,
    MISSING_FLIGHT_COMPONENTS,
    INSUFFICIENT_THRUST,
    FUEL_STATE_MISMATCH,
    INSUFFICIENT_CAPACITY,
    INSUFFICIENT_FUEL,
    ARITHMETIC_OVERFLOW;

    public String translationKey() {
        return "flight.advancedrocketrycommunity.request."
                + name().toLowerCase(Locale.ROOT);
    }

    public static RocketFlightRequestCode fromPlanCode(RocketFlightPlanCode code) {
        return switch (code) {
            case SUCCESS -> SUCCESS;
            case SAME_DESTINATION, UNSUPPORTED_ROUTE -> INVALID_DESTINATION;
            case MISSING_FLIGHT_COMPONENTS -> MISSING_FLIGHT_COMPONENTS;
            case INSUFFICIENT_THRUST -> INSUFFICIENT_THRUST;
            case FUEL_STATE_MISMATCH -> FUEL_STATE_MISMATCH;
            case INSUFFICIENT_CAPACITY -> INSUFFICIENT_CAPACITY;
            case INSUFFICIENT_FUEL -> INSUFFICIENT_FUEL;
            case ARITHMETIC_OVERFLOW -> ARITHMETIC_OVERFLOW;
        };
    }
}
