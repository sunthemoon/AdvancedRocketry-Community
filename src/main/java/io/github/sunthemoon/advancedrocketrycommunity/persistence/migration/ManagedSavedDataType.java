package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Fixed allowlist of global ARCE SavedData files covered by pre-start migration. */
public enum ManagedSavedDataType {
    CELESTIAL(
            "v0.3.0",
            "advancedrocketrycommunity_celestial",
            1L * 1024L * 1024L,
            List.of(new RequiredTag("bodies", Tag.TAG_LIST))
    ),
    ROCKET_TRANSACTIONS(
            "v0.5.0",
            "advancedrocketrycommunity_rocket_transactions",
            64L * 1024L * 1024L,
            List.of(new RequiredTag("transactions", Tag.TAG_LIST))
    ),
    ROCKET_TRANSFERS(
            "v0.6.0",
            "advancedrocketrycommunity_rocket_transfers",
            RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES,
            List.of(new RequiredTag("transfers", Tag.TAG_LIST))
    ),
    STATIONS(
            "v0.7.0",
            "advancedrocketrycommunity_stations",
            StationLimits.MAX_REGISTRY_NBT_BYTES,
            List.of(
                    new RequiredTag("stations", Tag.TAG_LIST),
                    new RequiredTag("reservations", Tag.TAG_LIST)
            )
    ),
    SATELLITE_MISSIONS(
            "v0.8.0",
            "advancedrocketrycommunity_satellite_missions",
            SatelliteLimits.MAX_REGISTRY_NBT_BYTES,
            List.of(
                    new RequiredTag("clock", Tag.TAG_COMPOUND),
                    new RequiredTag("satellites", Tag.TAG_LIST),
                    new RequiredTag("missions", Tag.TAG_LIST),
                    new RequiredTag("research_accounts", Tag.TAG_LIST)
            )
    );

    private static final long FILE_OVERHEAD_BYTES = 64L * 1024L;
    private static final String WARP_ENERGY_KEY = "warp_energy";
    private static final String SATELLITE_INSTANCES_KEY = "instances";
    private static final String SATELLITE_SAVE_EPOCH_KEY = "save_epoch";

    private final String introducedIn;
    private final String dataName;
    private final long maxUncompressedBytes;
    private final List<RequiredTag> requiredTags;

    ManagedSavedDataType(
            String introducedIn,
            String dataName,
            long maxUncompressedBytes,
            List<RequiredTag> requiredTags
    ) {
        this.introducedIn = introducedIn;
        this.dataName = dataName;
        this.maxUncompressedBytes = Math.max(maxUncompressedBytes, RocketLimits.MAX_TOTAL_NBT_BYTES);
        this.requiredTags = List.copyOf(requiredTags);
    }

    public String introducedIn() {
        return introducedIn;
    }

    public String dataName() {
        return dataName;
    }

    public String fileName() {
        return dataName + ".dat";
    }

    public int currentSchemaVersion() {
        return switch (this) {
            case STATIONS -> StationLimits.REGISTRY_SCHEMA_VERSION;
            case SATELLITE_MISSIONS -> SatelliteLimits.REGISTRY_SCHEMA_VERSION;
            default -> SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION;
        };
    }

    public String formatEpoch() {
        return formatEpoch(currentSchemaVersion());
    }

    /** The format epoch a root of this schema must carry; stations evolve independently (ADR-040, ADR-044). */
    public String formatEpoch(int schema) {
        if (this == STATIONS && schema == StationLimits.ORBITAL_REGISTRY_SCHEMA_VERSION) {
            return "v1.5.0-orbital-station";
        }
        if (this == STATIONS && schema == StationLimits.WARP_REGISTRY_SCHEMA_VERSION) {
            return "v1.5.0-station-warp";
        }
        if (this == SATELLITE_MISSIONS && schema == SatelliteLimits.REGISTRY_SCHEMA_VERSION) {
            return SatelliteLimits.REGISTRY_FORMAT_EPOCH;
        }
        return SavedDataSchemaMigrator.FORMAT_EPOCH;
    }

    /** Every root schema from the legacy one up to the current one is readable for migration. */
    public boolean supportsSchema(int schema) {
        return schema >= SavedDataSchemaMigrator.LEGACY_SCHEMA_VERSION && schema <= currentSchemaVersion();
    }

    /**
     * Heap-accounting quota as a multiple of the raw byte bound. Measured ratios: stations 6.49 at
     * their growth bound, satellite missions 5.05 at 6,144 missions (WARP review R1). A factor of 1
     * refused valid data, and the per-declaration guard in {@link BoundedSavedDataIo} already stops a
     * tiny file from forcing a large allocation, so every type uses 8.
     */
    public long heapAccountingFactor() {
        return 8L;
    }

    public long maxUncompressedBytes() {
        return maxUncompressedBytes;
    }

    public long maxCompressedBytes() {
        return maxUncompressedBytes + FILE_OVERHEAD_BYTES;
    }

    void validateRootShape(CompoundTag payload, int schema) {
        for (RequiredTag required : requiredTags) {
            if (!payload.contains(required.name(), required.type())) {
                throw new SavedDataMigrationException(
                        MigrationDiagnosticId.INVALID_SCHEMA,
                        dataName + " is missing required " + required.name()
                );
            }
        }
        if (this == STATIONS) {
            // Root 4 requires the warp energy list; older roots must not carry one (ADR-044 §6).
            boolean warpRoot = schema >= StationLimits.WARP_REGISTRY_SCHEMA_VERSION;
            if (warpRoot ? !payload.contains(WARP_ENERGY_KEY, Tag.TAG_LIST) : payload.contains(WARP_ENERGY_KEY)) {
                throw new SavedDataMigrationException(
                        MigrationDiagnosticId.INVALID_SCHEMA,
                        dataName + " schema " + schema + (warpRoot ? " is missing required " : " cannot carry ")
                                + WARP_ENERGY_KEY
                );
            }
        }
        if (this == SATELLITE_MISSIONS) {
            // Root 3 requires the instance list and save epoch; older roots must not carry them (ADR-050 §10).
            boolean v160Root = schema >= SatelliteLimits.REGISTRY_SCHEMA_VERSION;
            boolean hasInstances = payload.contains(SATELLITE_INSTANCES_KEY, Tag.TAG_LIST);
            boolean hasEpoch = payload.contains(SATELLITE_SAVE_EPOCH_KEY, Tag.TAG_LONG);
            boolean valid = v160Root
                    ? hasInstances && hasEpoch
                    : !payload.contains(SATELLITE_INSTANCES_KEY) && !payload.contains(SATELLITE_SAVE_EPOCH_KEY);
            if (!valid) {
                throw new SavedDataMigrationException(
                        MigrationDiagnosticId.INVALID_SCHEMA,
                        dataName + " schema " + schema + (v160Root ? " is missing its v1.6 sections"
                                : " cannot carry v1.6 sections")
                );
            }
        }
    }

    private record RequiredTag(String name, int type) {
    }
}
