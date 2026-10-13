package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;

/**
 * ADR-050 section 7: the largest encoded size a record can reach during its lifecycle, reserved when it is
 * admitted so later transitions never fail on the byte budget. The persisted registry uses the record codec;
 * {@link #BOUNDS} reserves each record's fixed bound and serves registries that are never saved.
 */
public interface RecordSizer {
    int ACCOUNT_BYTES = 128;

    int satelliteBytes(SatelliteState satellite);

    int missionBytes(MissionState mission);

    int instanceBytes(AsteroidInstance instance);

    default int groundSurveyBytes(GroundSurveyJob job) {
        return SatelliteLimits.MAX_GROUND_SURVEY_RECORD_NBT_BYTES;
    }

    /** Detached full-root/read preflight; the persisted implementation uses the same disk codec. */
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
