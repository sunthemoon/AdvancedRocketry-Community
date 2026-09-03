package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import java.util.Objects;

public final class SavedDataMigrationException extends IllegalArgumentException {
    private final MigrationDiagnosticId diagnosticId;

    public SavedDataMigrationException(MigrationDiagnosticId diagnosticId, String message) {
        super(message);
        this.diagnosticId = Objects.requireNonNull(diagnosticId, "diagnosticId");
    }

    public SavedDataMigrationException(
            MigrationDiagnosticId diagnosticId,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.diagnosticId = Objects.requireNonNull(diagnosticId, "diagnosticId");
    }

    public MigrationDiagnosticId diagnosticId() {
        return diagnosticId;
    }
}
