package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

/** Stable operator-facing identifiers for the Beta world compatibility boundary. */
public enum MigrationDiagnosticId {
    NO_MANAGED_DATA("ARCE-BETA-1000"),
    DATA_CURRENT("ARCE-BETA-1001"),
    MIGRATION_COMPLETE("ARCE-BETA-1002"),
    INVALID_SCHEMA("ARCE-BETA-2000"),
    FUTURE_SCHEMA("ARCE-BETA-2001"),
    OVERSIZED_DATA("ARCE-BETA-2002"),
    UNSAFE_PATH("ARCE-BETA-2003"),
    BACKUP_LIMIT("ARCE-BETA-2004"),
    BACKUP_FAILED("ARCE-BETA-2005"),
    STAGING_FAILED("ARCE-BETA-2006"),
    COMMIT_ROLLED_BACK("ARCE-BETA-2007"),
    ROLLBACK_FAILED("ARCE-BETA-2008");

    private final String code;

    MigrationDiagnosticId(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
