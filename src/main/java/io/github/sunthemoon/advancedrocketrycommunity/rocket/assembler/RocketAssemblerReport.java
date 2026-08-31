package io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.Objects;
import java.util.Optional;

public record RocketAssemblerReport(
        RocketValidationCode code,
        RocketStats stats,
        String detail,
        long updatedAtGameTime
) {
    public static final int MAX_DETAIL_LENGTH = 256;

    public RocketAssemblerReport {
        Objects.requireNonNull(code, "code");
        detail = Objects.requireNonNull(detail, "detail");
        if (detail.length() > MAX_DETAIL_LENGTH) {
            detail = detail.substring(0, MAX_DETAIL_LENGTH);
        }
        if (updatedAtGameTime < 0L) {
            throw new IllegalArgumentException("updatedAtGameTime must not be negative");
        }
    }

    public static RocketAssemblerReport idle() {
        return new RocketAssemblerReport(
                RocketValidationCode.EMPTY_STRUCTURE,
                null,
                "not scanned",
                0L
        );
    }

    public Optional<RocketStats> optionalStats() {
        return Optional.ofNullable(stats);
    }
}
