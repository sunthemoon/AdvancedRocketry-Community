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
     * Detached full-root/read preflight; the persisted implementation uses the same disk codec. Ground
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
