package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import java.util.Objects;

/** A loader codec refusal. The reason tells the device whether its raw data waits for a newer jar or a Fluid's mod. */
public final class LoaderDataException extends IllegalArgumentException {
    public enum Reason {
        /** A newer schema: preserved and unsupported until a jar that reads it is installed. */
        FUTURE,
        /** Malformed, oversized, unknown or older data: preserved and unsupported until offline repair. */
        INVALID,
        /** Well-formed, but naming a Fluid that is not registered now: preserved until its mod returns. */
        UNREGISTERED_FLUID
    }

    private final Reason reason;

    LoaderDataException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
