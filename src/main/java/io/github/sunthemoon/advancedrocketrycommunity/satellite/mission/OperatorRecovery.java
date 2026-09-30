package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStatus;
import java.util.UUID;

/** ADR-050 sections 4, 8 and 9: operator returns of held records. Callers hold the registry's lock. */
final class OperatorRecovery {
    private OperatorRecovery() {
    }

    /**
     * Returns a QUARANTINED mission to its previous status once its invariants hold again; otherwise the mission
     * stays quarantined.
     */
    static SatelliteOperationResult releaseQuarantine(SatelliteMissionRegistry registry, UUID missionId) {
        MissionState mission = registry.missionRecord(missionId);
        if (mission == null) {
            return registry.result(SatelliteOperationCode.MISSION_NOT_FOUND, false, null, null);
        }
        SatelliteState satellite = registry.satelliteRecord(mission.satelliteId());
        if (mission.status() != MissionStatus.QUARANTINED) {
            return registry.result(SatelliteOperationCode.IDEMPOTENT, false, satellite, mission);
        }
        MissionState released = mission.releaseQuarantine();
        boolean bound = satellite != null && satellite.ownerId().equals(mission.ownerId())
                && satellite.currentMissionId().filter(missionId::equals).isPresent();
        if (!bound || RegistryInvariants.instanceProblem(released, registry.instanceMap()) != null) {
            return registry.result(SatelliteOperationCode.RECOVERY_REQUIRED, false, satellite, mission);
        }
        registry.putMission(released);
        if (released.status() == MissionStatus.ACTIVE) {
            registry.schedule(released);
        }
        return registry.result(SatelliteOperationCode.SUCCESS, true, satellite, released);
    }

    /** Returns a RECOVERY_REQUIRED satellite to service. */
    static SatelliteOperationResult recoverSatellite(SatelliteMissionRegistry registry, UUID satelliteId) {
        SatelliteState satellite = registry.satelliteRecord(satelliteId);
        if (satellite == null) {
            return registry.result(SatelliteOperationCode.SATELLITE_NOT_FOUND, false, null, null);
        }
        if (satellite.status() != SatelliteStatus.RECOVERY_REQUIRED) {
            return registry.result(SatelliteOperationCode.IDEMPOTENT, false, satellite, null);
        }
        SatelliteState recovered = satellite.recover();
        registry.storeSatellite(recovered);
        return registry.result(SatelliteOperationCode.SUCCESS, true, recovered, null);
    }
}
