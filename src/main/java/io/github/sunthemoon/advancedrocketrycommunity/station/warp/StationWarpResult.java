package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of a warp command: the station observed (or published on commit), the quote when one was
 * computed, and the station's folded balance at that moment.
 */
public record StationWarpResult(
        StationManagementCode code,
        Optional<StationState> station,
        Optional<WarpQuote> quote,
        int balance
) {
    public StationWarpResult {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(quote, "quote");
    }

    static StationWarpResult of(StationManagementCode code, StationState station) {
        return new StationWarpResult(code, Optional.ofNullable(station), Optional.empty(), 0);
    }

    static StationWarpResult quoted(StationManagementCode code, WarpQuote quote, int balance) {
        return new StationWarpResult(code, Optional.of(quote.observed()), Optional.of(quote), balance);
    }
}
