package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;

/** Fail-closed decode result that preserves unsupported/corrupt flight payloads verbatim. */
public final class RocketFlightDecodeResult {
    public enum Status {
        VALID,
        FUTURE_SCHEMA,
        INVALID
    }

    private final Status status;
    private final RocketFlightData data;
    private final CompoundTag preservedPayload;
    private final String message;

    private RocketFlightDecodeResult(
            Status status,
            RocketFlightData data,
            CompoundTag preservedPayload,
            String message
    ) {
        this.status = Objects.requireNonNull(status, "status");
        this.data = data;
        this.preservedPayload = preservedPayload == null ? null : preservedPayload.copy();
        this.message = Objects.requireNonNull(message, "message");
    }

    public static RocketFlightDecodeResult valid(RocketFlightData data) {
        return new RocketFlightDecodeResult(
                Status.VALID,
                Objects.requireNonNull(data, "data"),
                null,
                "valid"
        );
    }

    public static RocketFlightDecodeResult future(CompoundTag payload, int schema) {
        return new RocketFlightDecodeResult(
                Status.FUTURE_SCHEMA,
                null,
                payload,
                "Unsupported future rocket flight schema " + schema
        );
    }

    public static RocketFlightDecodeResult invalid(CompoundTag payload, String message) {
        return new RocketFlightDecodeResult(Status.INVALID, null, payload, message);
    }

    public Status status() {
        return status;
    }

    public Optional<RocketFlightData> data() {
        return Optional.ofNullable(data);
    }

    public Optional<CompoundTag> preservedPayload() {
        return preservedPayload == null
                ? Optional.empty()
                : Optional.of(preservedPayload.copy());
    }

    public String message() {
        return message;
    }
}
