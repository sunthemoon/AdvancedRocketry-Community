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
        return this == STATIONS ? StationLimits.REGISTRY_SCHEMA_VERSION
                : SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION;
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
        return SavedDataSchemaMigrator.FORMAT_EPOCH;
    }

    /** Every root schema from the legacy one up to the current one is readable for migration. */
    public boolean supportsSchema(int schema) {
        return schema >= SavedDataSchemaMigrator.LEGACY_SCHEMA_VERSION && schema <= currentSchemaVersion();
    }

    /**
     * Heap-accounting quota as a multiple of the raw byte bound. Stations were measured at 5.67 at
     * capacity (ADR-041 capacity fix); the other types keep their earlier factor 1.
     */
    public long heapAccountingFactor() {
        return this == STATIONS ? 8L : 1L;
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
    }

    private record RequiredTag(String name, int type) {
    }
}
