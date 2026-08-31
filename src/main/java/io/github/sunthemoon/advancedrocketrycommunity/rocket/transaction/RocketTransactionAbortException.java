package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.Objects;
import java.util.Optional;

public final class RocketTransactionAbortException extends RuntimeException {
    private final RocketValidationCode code;
    private final RocketPosition position;

    public RocketTransactionAbortException(RocketValidationCode code, String message) {
        this(code, null, message);
    }

    public RocketTransactionAbortException(
            RocketValidationCode code,
            RocketPosition position,
            String message
    ) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.position = position;
        if (code == RocketValidationCode.SUCCESS) {
            throw new IllegalArgumentException("Abort code cannot be SUCCESS");
        }
    }

    public RocketValidationCode code() {
        return code;
    }

    public Optional<RocketPosition> position() {
        return Optional.ofNullable(position);
    }
}
