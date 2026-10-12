package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Objects;

public record RocketFlightRequestResult(RocketFlightRequestCode code, long requiredFuel, String detailKey) {
    public RocketFlightRequestResult(RocketFlightRequestCode code, long requiredFuel) { this(code, requiredFuel, null); }
    public RocketFlightRequestResult {
        Objects.requireNonNull(code, "code");
        if (detailKey != null && (!detailKey.startsWith("message.advancedrocketrycommunity.pad.reason.")
                || detailKey.length() > 128)) { throw new IllegalArgumentException("Unbounded pad refusal key"); }
        if (requiredFuel < 0L || requiredFuel > RocketFlightLimits.MAX_TRAVEL_FUEL) {
            throw new IllegalArgumentException("Flight request fuel quote is outside the fixed bound");
        }
    }

    public boolean success() {
        return code == RocketFlightRequestCode.SUCCESS;
    }

    public static RocketFlightRequestResult failure(RocketFlightRequestCode code) {
        return failure(code, 0L);
    }

    public static RocketFlightRequestResult failure(RocketFlightRequestCode code, long requiredFuel) {
        if (code == RocketFlightRequestCode.SUCCESS) {
            throw new IllegalArgumentException("Failure result cannot use SUCCESS");
        }
        return new RocketFlightRequestResult(code, requiredFuel);
    }
}
