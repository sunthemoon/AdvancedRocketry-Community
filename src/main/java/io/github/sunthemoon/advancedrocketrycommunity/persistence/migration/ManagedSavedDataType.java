package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
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
    ),
    /** ADR-054 section 10: schema 1 is the only readable schema, so there is no legacy migration. */
    ENDGAME(
            "v1.7.0",
            "advancedrocketrycommunity_endgame",
            EndgameLimits.MAX_ROOT_BYTES,
            List.of(
                    new RequiredTag("endpoints", Tag.TAG_LIST),
                    new RequiredTag("dispatched_through", Tag.TAG_LONG_ARRAY),
                    new RequiredTag("transits", Tag.TAG_LIST),
                    new RequiredTag("elevator_pairs", Tag.TAG_LIST),
                    new RequiredTag("zones", Tag.TAG_LIST),
                    new RequiredTag("save_epoch", Tag.TAG_LONG)
            )
    ),
    /** D3-A: schema 1 from introduction; missing roots remain absent until gated admission. */
    RESOURCE_ESCROW(
            "v1.8.0", "advancedrocketrycommunity_resource_escrow", 1_048_576L,
            List.of(new RequiredTag("records", Tag.TAG_LIST), new RequiredTag("floors", Tag.TAG_LIST),
                    new RequiredTag("tombstones", Tag.TAG_LIST), new RequiredTag("ledger_id", Tag.TAG_INT_ARRAY),
                    new RequiredTag("issued_through", Tag.TAG_LONG), new RequiredTag("writer_generation", Tag.TAG_LONG))
    );

    private static final long FILE_OVERHEAD_BYTES = 64L * 1024L;
    private static final String WARP_ENERGY_KEY = "warp_energy";
    private static final String ORBITAL_CLOCK_KEY = "orbital_clock";
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
            case ENDGAME -> EndgameLimits.ROOT_SCHEMA_VERSION;
            case RESOURCE_ESCROW -> 1;
            default -> SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION;
        };
    }

    public String formatEpoch() {
        return formatEpoch(currentSchemaVersion());
    }

    /** The format epoch a root of this schema must carry; stations evolve independently (ADR-040, ADR-044, ADR-065). */
    public String formatEpoch(int schema) {
        if (this == STATIONS && schema == StationLimits.ORBITAL_REGISTRY_SCHEMA_VERSION) {
            return "v1.5.0-orbital-station";
        }
        if (this == STATIONS && schema == StationLimits.WARP_REGISTRY_SCHEMA_VERSION) {
            return "v1.5.0-station-warp";
        }
        if (this == STATIONS && schema == StationLimits.CONTROLS_REGISTRY_SCHEMA_VERSION) {
            return "v1.8.0-station-orbital-controls";
        }
        if (this == SATELLITE_MISSIONS) {
            // Historical epochs are literals; a later root changes the current constant, never these.
            if (schema == SatelliteLimits.INSTANCE_REGISTRY_SCHEMA_VERSION) {
                return "v1.6.0-satellite-missions";
            }
            if (schema == SatelliteLimits.GROUND_REGISTRY_SCHEMA_VERSION) {
                return "v1.8.0-ground-surveys";
            }
        }
        if (this == ENDGAME) {
            return EndgameLimits.ROOT_FORMAT_EPOCH;
        }
        if (this == RESOURCE_ESCROW) {
            return io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLimits.EPOCH;
        }
        return SavedDataSchemaMigrator.FORMAT_EPOCH;
    }

    /**
     * The first schema whose roots carry a format epoch. The Beta roots gained one at the global schema 2; the
     * endgame root has it from its schema 1 (ADR-054 section 10, review R2-L7).
     */
    public int firstEpochSchema() {
        return !hasLegacySchema() ? currentSchemaVersion() : SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION;
    }

    /** Whether older roots of this type exist; the endgame root was introduced at its current schema. */
    public boolean hasLegacySchema() {
        return this != ENDGAME && this != RESOURCE_ESCROW;
    }

    /**
     * Every root schema from the legacy one up to the current one is readable for migration; the endgame root
     * reads only its current schema.
     */
    public boolean supportsSchema(int schema) {
        int oldest = !hasLegacySchema() ? currentSchemaVersion() : SavedDataSchemaMigrator.LEGACY_SCHEMA_VERSION;
        return schema >= oldest && schema <= currentSchemaVersion();
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
            // Root 5 requires the orbital clock; a current root without it is malformed (ADR-065 §5.2.1).
            boolean clockRoot = schema >= StationLimits.CONTROLS_REGISTRY_SCHEMA_VERSION;
            if (clockRoot ? !payload.contains(ORBITAL_CLOCK_KEY, Tag.TAG_COMPOUND)
                    : payload.contains(ORBITAL_CLOCK_KEY)) {
                throw new SavedDataMigrationException(
                        MigrationDiagnosticId.INVALID_SCHEMA,
                        dataName + " schema " + schema + (clockRoot ? " is missing required " : " cannot carry ")
                                + ORBITAL_CLOCK_KEY
                );
            }
        }
        if (this == SATELLITE_MISSIONS) {
            // Root 3 requires the instance list and save epoch; older roots must not carry them (ADR-050 §10).
            boolean v160Root = schema >= SatelliteLimits.INSTANCE_REGISTRY_SCHEMA_VERSION;
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
            boolean groundRoot = schema >= SatelliteLimits.GROUND_REGISTRY_SCHEMA_VERSION;
            if (groundRoot ? !payload.contains("ground_surveys", Tag.TAG_LIST) : payload.contains("ground_surveys")) {
                throw new SavedDataMigrationException(MigrationDiagnosticId.INVALID_SCHEMA,
                        dataName + " schema " + schema + (groundRoot ? " is missing ground_surveys"
                                : " cannot carry ground_surveys"));
            }
        }
    }

    private record RequiredTag(String name, int type) {
    }
}
