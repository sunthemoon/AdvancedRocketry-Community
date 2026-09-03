package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Pure and deterministic schema-1 to schema-2 migration for managed root payloads. */
public final class SavedDataSchemaMigrator {
    public static final int LEGACY_SCHEMA_VERSION = 1;
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final String FORMAT_EPOCH = "v0.9.0-beta";

    private static final String SCHEMA_KEY = "schema_version";
    private static final String EPOCH_KEY = "format_epoch";
    private static final String MIGRATED_FROM_KEY = "migrated_from_schema";

    private SavedDataSchemaMigrator() {
    }

    public static MigrationResult migrate(
            ManagedSavedDataType type,
            CompoundTag source
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        if (!source.contains(SCHEMA_KEY, Tag.TAG_INT)) {
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.INVALID_SCHEMA,
                    type.dataName() + " is missing schema_version"
            );
        }
        int schema = source.getInt(SCHEMA_KEY);
        if (schema > CURRENT_SCHEMA_VERSION) {
            return new MigrationResult(MigrationStatus.FUTURE, schema, source);
        }
        if (schema != LEGACY_SCHEMA_VERSION && schema != CURRENT_SCHEMA_VERSION) {
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.INVALID_SCHEMA,
                    type.dataName() + " has unsupported schema " + schema
            );
        }
        type.validateRootShape(source);

        if (schema == CURRENT_SCHEMA_VERSION) {
            if (!source.contains(EPOCH_KEY, Tag.TAG_STRING)
                    || !FORMAT_EPOCH.equals(source.getString(EPOCH_KEY))) {
                throw new SavedDataMigrationException(
                        MigrationDiagnosticId.INVALID_SCHEMA,
                        type.dataName() + " schema 2 is missing the Beta format epoch"
                );
            }
            return new MigrationResult(MigrationStatus.CURRENT, schema, source);
        }

        CompoundTag migrated = source.copy();
        migrated.putInt(SCHEMA_KEY, CURRENT_SCHEMA_VERSION);
        migrated.putString(EPOCH_KEY, FORMAT_EPOCH);
        migrated.putInt(MIGRATED_FROM_KEY, LEGACY_SCHEMA_VERSION);
        return new MigrationResult(MigrationStatus.MIGRATED, schema, migrated);
    }

    public static void stampCurrent(ManagedSavedDataType type, CompoundTag target) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        target.putInt(SCHEMA_KEY, CURRENT_SCHEMA_VERSION);
        target.putString(EPOCH_KEY, FORMAT_EPOCH);
    }

    public enum MigrationStatus {
        CURRENT,
        MIGRATED,
        FUTURE
    }

    public record MigrationResult(
            MigrationStatus status,
            int sourceSchema,
            CompoundTag payload
    ) {
        public MigrationResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(payload, "payload");
            payload = payload.copy();
        }

        @Override
        public CompoundTag payload() {
            return payload.copy();
        }

        public boolean changed() {
            return status == MigrationStatus.MIGRATED;
        }
    }
}
