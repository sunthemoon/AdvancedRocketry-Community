package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import java.util.Objects;

public final class SavedDataMigrationException extends IllegalArgumentException {
    private final MigrationDiagnosticId diagnosticId;
    private ManagedSavedDataType dataType;

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

    SavedDataMigrationException forType(ManagedSavedDataType type) {
        // Annotate once at discovery; keep the original exception, cause and stack unchanged.
        if (dataType == null) { dataType = Objects.requireNonNull(type); }
        return this;
    }

    public java.util.Optional<ManagedSavedDataType> dataType() { return java.util.Optional.ofNullable(dataType); }

    public MigrationDiagnosticId diagnosticId() {
        return diagnosticId;
    }
}
