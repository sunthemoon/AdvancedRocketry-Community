package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;

/** Fail-closed decode outcome that preserves unknown future payloads verbatim. */
public final class RocketSnapshotDecodeResult {
    public enum Status {
        VALID,
        FUTURE_SCHEMA,
        INVALID
    }

    private final Status status;
    private final RocketStructureSnapshot snapshot;
    private final CompoundTag preservedPayload;
    private final RocketValidationCode code;
    private final String message;

    private RocketSnapshotDecodeResult(
            Status status,
            RocketStructureSnapshot snapshot,
            CompoundTag preservedPayload,
            RocketValidationCode code,
            String message
    ) {
        this.status = Objects.requireNonNull(status, "status");
        this.snapshot = snapshot;
        this.preservedPayload = preservedPayload == null ? null : io.github.sunthemoon.advancedrocketrycommunity.rocket.model
                .RocketNativeResources.preserve(preservedPayload,
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits.MAX_TOTAL_NBT_BYTES);
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
    }

    public static RocketSnapshotDecodeResult valid(RocketStructureSnapshot snapshot) {
        return new RocketSnapshotDecodeResult(
                Status.VALID,
                Objects.requireNonNull(snapshot, "snapshot"),
                null,
                RocketValidationCode.SUCCESS,
                "valid"
        );
    }

    public static RocketSnapshotDecodeResult future(CompoundTag payload, int schema) {
        return new RocketSnapshotDecodeResult(
                Status.FUTURE_SCHEMA,
                null,
                payload,
                RocketValidationCode.UNSUPPORTED_SCHEMA,
                "Unsupported future rocket snapshot schema " + schema
        );
    }

    public static RocketSnapshotDecodeResult invalid(
            CompoundTag payload,
            RocketValidationCode code,
            String message
    ) {
        return new RocketSnapshotDecodeResult(Status.INVALID, null, payload, code, message);
    }

    public Status status() {
        return status;
    }

    public Optional<RocketStructureSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    public Optional<CompoundTag> preservedPayload() {
        return preservedPayload == null
                ? Optional.empty()
                : Optional.of(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources
                        .preserve(preservedPayload, io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits.MAX_TOTAL_NBT_BYTES));
    }

    public RocketValidationCode code() {
        return code;
    }

    public String message() {
        return message;
    }
}
