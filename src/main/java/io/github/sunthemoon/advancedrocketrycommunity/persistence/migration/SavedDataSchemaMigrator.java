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
        if (type == ManagedSavedDataType.ROCKET_REJOINS) {
            // MigrationResult takes defensive copies; validate the sidecar's structure before those recursive walks.
            try { io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinCodec.bounds(source); }
            catch (RuntimeException invalid) {
                throw new SavedDataMigrationException(MigrationDiagnosticId.INVALID_SCHEMA,
                        type.dataName() + " exceeds its structural envelope", invalid);
            }
        }
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
        if (preReleaseStationRoot(type, source, schema)) {
            return migratePreReleaseStations(type, source, schema);
        }
        type.validateRootShape(source, schema);

        if (schema >= type.firstEpochSchema()) {
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

        if (type == ManagedSavedDataType.ROCKET_TRANSFERS) {
            return migrateTransferJournal(type, source, schema);
        }

        if (type == ManagedSavedDataType.SATELLITE_MISSIONS) {
            try {
                SatelliteRegistryPayload.Upgrade upgrade = SatelliteRegistryPayload.upgradeWithSnapshot(source, schema);
                CompoundTag migrated = upgrade.payload();
                stampCurrent(type, migrated);
                if (schema == LEGACY_SCHEMA_VERSION && !migrated.contains(MIGRATED_FROM_KEY)) {
                    migrated.putInt(MIGRATED_FROM_KEY, LEGACY_SCHEMA_VERSION);
                }
                SatelliteRegistryPayload.decodeCurrent(migrated);
                io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                        "ARCE_SATELLITE_SCHEMA4_UPGRADE source_schema={} budget={}", schema, upgrade.snapshot());
                return new MigrationResult(MigrationStatus.MIGRATED, schema, migrated);
            } catch (RuntimeException exception) {
                throw new SavedDataMigrationException(MigrationDiagnosticId.INVALID_SCHEMA,
                        io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence
                                .SatelliteCanonicalBoundsException.find(exception).map(Throwable::getMessage)
                                .orElse(type.dataName() + " has invalid legacy satellite data"), exception);
            }
        }

        CompoundTag migrated = source.copy();
        migrated.putInt(SCHEMA_KEY, CURRENT_SCHEMA_VERSION);
        migrated.putString(EPOCH_KEY, FORMAT_EPOCH);
        migrated.putInt(MIGRATED_FROM_KEY, LEGACY_SCHEMA_VERSION);
        return new MigrationResult(MigrationStatus.MIGRATED, schema, migrated);
    }

    /**
     * W44 section 1.4: the exact whitelist that precedes generic epoch validation and CURRENT classification:
     * station schema 5, the exact pre-release epoch, and no outbox key at all. Anything else takes the generic path,
     * which refuses a new epoch without an outbox, an old epoch with one and every unknown epoch.
     */
    private static boolean preReleaseStationRoot(ManagedSavedDataType type, CompoundTag source, int schema) {
        return type == ManagedSavedDataType.STATIONS
                && schema == io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits
                .CONTROLS_REGISTRY_SCHEMA_VERSION
                && source.contains(EPOCH_KEY, Tag.TAG_STRING)
                && ManagedSavedDataType.STATION_PRE_RELEASE_EPOCH.equals(source.getString(EPOCH_KEY))
                && !source.contains(ManagedSavedDataType.FIRST_STATION_WARP_KEY);
    }

    /**
     * W44 section 1.4: the unreleased pre-W44 root 5 is validated completely under its old contract, including its
     * clock; every unrelated field is copied, the EMPTY outbox added and the new epoch stamped; the result is then
     * validated and must fit the unchanged 4 MiB root bound, or the migration refuses intact.
     */
    private static MigrationResult migratePreReleaseStations(ManagedSavedDataType type, CompoundTag source, int schema) {
        try {
            type.validateRootShape(source, schema);
            CompoundTag migrated = StationRegistryPayload.upgradePreReleaseRoot(source);
            stampCurrent(type, migrated);
            StationRegistryPayload.decodeCurrent(migrated);
            return new MigrationResult(MigrationStatus.MIGRATED, schema, migrated);
        } catch (RuntimeException exception) {
            throw new SavedDataMigrationException(MigrationDiagnosticId.INVALID_SCHEMA,
                    type.dataName() + " pre-release root 5 cannot take the first-warp outbox", exception);
        }
    }

    /**
     * W44 section 2.2: journal roots 1 and 2 become root 3. Every record and unrelated field is preserved, the EMPTY
     * Moon landing outbox is added (no old root carries a trustworthy landing marker), the root is stamped 3 with its
     * epoch, and the result must decode under the existing checksum-first record rules before staging. Root 1 keeps
     * {@code migrated_from_schema = 1} as before; COMMITTED schema-1 records still migrate at runtime.
     */
    private static MigrationResult migrateTransferJournal(ManagedSavedDataType type, CompoundTag source, int schema) {
        try {
            CompoundTag migrated = source.copy();
            migrated.put(ManagedSavedDataType.FIRST_MOON_LANDING_KEY, io.github.sunthemoon.advancedrocketrycommunity
                    .rocket.flight.persistence.RocketTransferSavedData.emptyFirstMoonLanding());
            stampCurrent(type, migrated);
            if (schema == LEGACY_SCHEMA_VERSION) {
                migrated.putInt(MIGRATED_FROM_KEY, LEGACY_SCHEMA_VERSION);
            }
            if (!io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData
                    .load(migrated).operational()) {
                throw new IllegalArgumentException("Migrated transfer journal failed validation");
            }
            return new MigrationResult(MigrationStatus.MIGRATED, schema, migrated);
        } catch (RuntimeException exception) {
            throw new SavedDataMigrationException(MigrationDiagnosticId.INVALID_SCHEMA,
                    type.dataName() + " has invalid legacy transfer journal data", exception);
        }
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
