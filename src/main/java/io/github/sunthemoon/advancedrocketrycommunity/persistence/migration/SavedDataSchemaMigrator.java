package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistryPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryPayload;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Pure per-authority migration; station schema evolves independently of Beta roots. */
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
        if (schema > type.currentSchemaVersion()) {
            return new MigrationResult(MigrationStatus.FUTURE, schema, source);
        }
        if (!type.supportsSchema(schema)) {
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.INVALID_SCHEMA,
                    type.dataName() + " has unsupported schema " + schema
            );
        }
        type.validateRootShape(source, schema);

        if (schema >= CURRENT_SCHEMA_VERSION) {
            String expectedEpoch = type.formatEpoch(schema);
            if (!source.contains(EPOCH_KEY, Tag.TAG_STRING)
                    || !expectedEpoch.equals(source.getString(EPOCH_KEY))) {
                throw new SavedDataMigrationException(
                        MigrationDiagnosticId.INVALID_SCHEMA,
                        type.dataName() + " schema " + schema + " has an invalid format epoch"
                );
            }
        }
        if (schema == type.currentSchemaVersion()) {
            return new MigrationResult(MigrationStatus.CURRENT, schema, source);
        }

        if (type == ManagedSavedDataType.STATIONS) {
            try {
                CompoundTag migrated = StationRegistryPayload.upgrade(source, schema);
                stampCurrent(type, migrated);
                if (schema == LEGACY_SCHEMA_VERSION && !migrated.contains(MIGRATED_FROM_KEY)) {
                    migrated.putInt(MIGRATED_FROM_KEY, LEGACY_SCHEMA_VERSION);
                }
                StationRegistryPayload.decodeCurrent(migrated);
                return new MigrationResult(MigrationStatus.MIGRATED, schema, migrated);
            } catch (RuntimeException exception) {
                throw new SavedDataMigrationException(MigrationDiagnosticId.INVALID_SCHEMA,
                        type.dataName() + " has invalid legacy station data", exception);
            }
        }

        if (type == ManagedSavedDataType.SATELLITE_MISSIONS) {
            try {
                CompoundTag migrated = SatelliteRegistryPayload.upgrade(source, schema);
                stampCurrent(type, migrated);
                if (schema == LEGACY_SCHEMA_VERSION && !migrated.contains(MIGRATED_FROM_KEY)) {
                    migrated.putInt(MIGRATED_FROM_KEY, LEGACY_SCHEMA_VERSION);
                }
                SatelliteRegistryPayload.decodeCurrent(migrated);
                return new MigrationResult(MigrationStatus.MIGRATED, schema, migrated);
            } catch (RuntimeException exception) {
                throw new SavedDataMigrationException(MigrationDiagnosticId.INVALID_SCHEMA,
                        type.dataName() + " has invalid legacy satellite data", exception);
            }
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
        target.putInt(SCHEMA_KEY, type.currentSchemaVersion());
        target.putString(EPOCH_KEY, type.formatEpoch());
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
