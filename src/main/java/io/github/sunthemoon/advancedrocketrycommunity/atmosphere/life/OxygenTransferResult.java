package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

public record OxygenTransferResult(
        boolean accepted,
        int oxygenUnits,
        int canistersConsumed
) {
    public OxygenTransferResult {
        if (oxygenUnits < 0 || canistersConsumed < 0 || canistersConsumed > 1) {
            throw new IllegalArgumentException("Invalid bounded oxygen transfer result");
        }
        if (accepted != (canistersConsumed == 1)) {
            throw new IllegalArgumentException("Accepted transfer must consume exactly one canister");
        }
    }
}
