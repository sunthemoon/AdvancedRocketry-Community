package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import java.util.Objects;

public final class SavedDataMigrationException extends IllegalArgumentException {
    private final MigrationDiagnosticId diagnosticId;
    private ManagedSavedDataType dataType;
    private boolean presenceUnknown;

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

    /** W328F: marks a refusal whose cause is a failed status check, so the hint never parses the message. */
    SavedDataMigrationException presenceUnknown() {
        presenceUnknown = true;
        return this;
    }

    /** Whether startup was refused because a managed file's or the data directory's presence is unknown. */
    public boolean isPresenceUnknown() { return presenceUnknown; }

    public MigrationDiagnosticId diagnosticId() {
        return diagnosticId;
    }
}
