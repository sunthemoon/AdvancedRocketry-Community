package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Fixed-field diagnostic for a canonical encoding that cannot be saved within the existing bounds. */
public final class SatelliteCanonicalBoundsException extends IllegalArgumentException {
    enum Kind { SATELLITE, MISSION, ACCOUNT, INSTANCE, GROUND_SURVEY }

    private SatelliteCanonicalBoundsException(String kind, String id, int bytes, int bound) {
        super("ARCE_SATELLITE_CANONICAL_BOUND record=" + kind + " id=" + id + " bytes=" + bytes + " bound=" + bound);
    }

    static void check(Kind kind, UUID id, CompoundTag encoded, int bound) {
        int bytes = SatelliteNbtSize.uncompressedBytes(encoded);
        if (bytes > bound) {
            throw new SatelliteCanonicalBoundsException(kind.name().toLowerCase(java.util.Locale.ROOT),
                    id.toString(), bytes, bound);
        }
    }

    static void checkRoot(CompoundTag encoded, int bound) {
        int bytes = SatelliteNbtSize.uncompressedBytes(encoded);
        if (bytes > bound) {
            throw new SatelliteCanonicalBoundsException("root", SatelliteMissionSavedData.DATA_NAME, bytes, bound);
        }
    }

    /** Migration adds a small fixed number of wrappers; never traverse an arbitrary cause graph without a bound. */
    public static Optional<SatelliteCanonicalBoundsException> find(Throwable failure) {
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            if (failure instanceof SatelliteCanonicalBoundsException bounds) { return Optional.of(bounds); }
        }
        return Optional.empty();
    }
}
