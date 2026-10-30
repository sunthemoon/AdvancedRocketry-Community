package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;

/**
 * ADR-050 section 7: the largest encoded size a record can reach during its lifecycle, reserved when it is
 * admitted so later transitions never fail on the byte budget. The persisted registry uses the record codec;
 * {@link #BOUNDS} reserves each record's fixed bound and serves registries that are never saved.
 */
public interface RecordSizer {
    /** Accounting bytes are retained even when sizing cannot prove the canonical lifecycle encoding fits. */
    record Reservation(int bytes, boolean proven) { }

    /** Exact native list-element bytes/reader charges; UNKNOWN never authorizes the exact-size shortcut. */
    record EncodingSize(long bytes, long heapBytes) {
        public static final EncodingSize UNKNOWN = new EncodingSize(-1, -1);
        public static final EncodingSize ZERO = new EncodingSize(0, 0);
        public boolean known() { return bytes >= 0 && heapBytes >= 0; }
        public EncodingSize plus(EncodingSize other) {
            return new EncodingSize(Math.addExact(bytes, other.bytes), Math.addExact(heapBytes, other.heapBytes));
        }
        public EncodingSize minus(EncodingSize other) {
            return new EncodingSize(Math.subtractExact(bytes, other.bytes), Math.subtractExact(heapBytes, other.heapBytes));
        }
    }

    /**
     * Optional canonical current-state measurement for the five immutable registry record types. Domain-only
     * sizers need not implement it. A failed eager measurement must not change mutation/load acceptance.
     */
    default EncodingSize encodingSize(Object record) { return EncodingSize.UNKNOWN; }

    int ACCOUNT_BYTES = 128;
    /**
     * Upper bound of the root's fixed fields and list headers. A record inside a list encodes without the
     * three-byte named-root header its sizer measures, so this plus the reservations bounds the encoded root.
     */
    int ROOT_FRAMING_BYTES = 4 * 1024;

    int satelliteBytes(SatelliteState satellite);

    int missionBytes(MissionState mission);

    int instanceBytes(AsteroidInstance instance);

    default int groundSurveyBytes(GroundSurveyJob job) {
        return SatelliteLimits.MAX_GROUND_SURVEY_RECORD_NBT_BYTES;
    }

    // Fixed-bound/domain sizers supply a proof by contract. Codec sizers must retain sizing failures instead
    // of returning a capped byte count as if the encoding had succeeded.
    default Reservation satelliteReservation(SatelliteState satellite) {
        return new Reservation(satelliteBytes(satellite), true);
    }

    default Reservation missionReservation(MissionState mission) {
        return new Reservation(missionBytes(mission), true);
    }

    default Reservation instanceReservation(AsteroidInstance instance) {
        return new Reservation(instanceBytes(instance), true);
    }

    default Reservation groundSurveyReservation(GroundSurveyJob job) {
        return new Reservation(groundSurveyBytes(job), true);
    }

    /**
     * Detached preflight; the persisted implementation uses exact resident sizes or the full disk codec. Ground
     * operations call it when the existing reservations plus any candidate additions cannot bound the root.
     */
    default boolean groundMutationFits(SatelliteMissionRegistry registry, GroundSurveyJob job,
            AsteroidInstance instance, boolean removeInstance,
            io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount account, long now,
            long observedTime) {
        return true;
    }

    RecordSizer BOUNDS = new RecordSizer() {
        @Override
        public int satelliteBytes(SatelliteState satellite) {
            return SatelliteLimits.MAX_SATELLITE_RECORD_NBT_BYTES;
        }

        @Override
        public int missionBytes(MissionState mission) {
            return SatelliteLimits.MAX_MISSION_RECORD_NBT_BYTES;
        }

        @Override
        public int instanceBytes(AsteroidInstance instance) {
            return SatelliteLimits.MAX_INSTANCE_RECORD_NBT_BYTES;
        }
    };
}
