package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Fixed allowlist of global ARCE SavedData files covered by the Beta transaction. */
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

    public long maxUncompressedBytes() {
        return maxUncompressedBytes;
    }

    public long maxCompressedBytes() {
        return maxUncompressedBytes + FILE_OVERHEAD_BYTES;
    }

    void validateRootShape(CompoundTag payload) {
        for (RequiredTag required : requiredTags) {
            if (!payload.contains(required.name(), required.type())) {
                throw new SavedDataMigrationException(
                        MigrationDiagnosticId.INVALID_SCHEMA,
                        dataName + " is missing required " + required.name()
                );
            }
        }
    }

    private record RequiredTag(String name, int type) {
    }
}
