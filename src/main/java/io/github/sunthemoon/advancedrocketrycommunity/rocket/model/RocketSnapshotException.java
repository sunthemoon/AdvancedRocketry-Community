package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.Objects;
import java.util.Optional;

public final class RocketSnapshotException extends IllegalArgumentException {
    private final RocketValidationCode code;
    private final RocketPosition position;

    public RocketSnapshotException(RocketValidationCode code, String message) {
        this(code, null, message, null);
    }

    public RocketSnapshotException(
            RocketValidationCode code,
            RocketPosition position,
            String message
    ) {
        this(code, position, message, null);
    }

    public RocketSnapshotException(
            RocketValidationCode code,
            RocketPosition position,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.position = position;
    }

    public RocketValidationCode code() {
        return code;
    }

    public Optional<RocketPosition> position() {
        return Optional.ofNullable(position);
    }
}
