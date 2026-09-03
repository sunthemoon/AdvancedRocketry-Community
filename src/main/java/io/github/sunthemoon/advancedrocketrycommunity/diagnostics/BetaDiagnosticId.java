package io.github.sunthemoon.advancedrocketrycommunity.diagnostics;

/** Stable, bounded support identifiers outside the migration-specific range. */
public enum BetaDiagnosticId {
    OPTIONAL_COMPATIBILITY("ARCE-BETA-1100"),
    OPERATOR_REPORT("ARCE-BETA-1101");

    private final String code;

    BetaDiagnosticId(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
