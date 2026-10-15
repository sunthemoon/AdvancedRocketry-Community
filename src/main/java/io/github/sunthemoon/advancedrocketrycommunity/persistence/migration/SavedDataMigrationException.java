package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import java.util.Objects;

public final class SavedDataMigrationException extends IllegalArgumentException {
    private final MigrationDiagnosticId diagnosticId;
    private final ManagedSavedDataType dataType;

    public SavedDataMigrationException(MigrationDiagnosticId diagnosticId, String message) {
        this(diagnosticId, message, null, null);
    }

    public SavedDataMigrationException(
            MigrationDiagnosticId diagnosticId,
            String message,
            Throwable cause
    ) {
        this(diagnosticId, message, cause, null);
    }

    private SavedDataMigrationException(MigrationDiagnosticId diagnosticId, String message, Throwable cause, ManagedSavedDataType dataType) {
        super(message, cause);
        this.diagnosticId = Objects.requireNonNull(diagnosticId, "diagnosticId");
        this.dataType = dataType;
    }

    SavedDataMigrationException forType(ManagedSavedDataType type) {
        return dataType == null ? new SavedDataMigrationException(diagnosticId, getMessage(), this, Objects.requireNonNull(type)) : this;
    }

    public java.util.Optional<ManagedSavedDataType> dataType() { return java.util.Optional.ofNullable(dataType); }

    public MigrationDiagnosticId diagnosticId() {
        return diagnosticId;
    }
}
