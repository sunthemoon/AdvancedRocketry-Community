package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStatus;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Shared dry-run/commit admission for fresh launches; callers hold the registry monitor. */
final class SatelliteLaunchAdmission {
    private SatelliteLaunchAdmission() { }

    static SatelliteOperationResult data(SatelliteMissionRegistry registry, UUID satelliteId, UUID missionId,
            UUID ownerId, SatelliteDefinition definition, ResourceLocation targetBodyId, long logicalTime,
            boolean discoveryRequired, boolean commit) {
        SatelliteState existing = registry.satelliteRecord(satelliteId);
        if (existing != null) {
            boolean same = existing.ownerId().equals(ownerId) && existing.definitionId().equals(definition.id())
                    && existing.kind() == SatelliteKind.DATA;
            return registry.result(same ? SatelliteOperationCode.IDEMPOTENT : SatelliteOperationCode.IDENTITY_CONFLICT,
                    false, existing, registry.missionRecord(missionId));
        }
        if (!definition.allows(targetBodyId)) {
            return registry.result(SatelliteOperationCode.TARGET_NOT_ALLOWED, false, null, null);
        }
        if (registry.missionRecord(missionId) != null) {
            return registry.result(SatelliteOperationCode.IDENTITY_CONFLICT, false, null, registry.missionRecord(missionId));
        }
        SatelliteState satellite = SatelliteState.launch(satelliteId, definition.id(), ownerId, logicalTime);
        MissionState mission;
        try {
            mission = MissionState.start(missionId, satelliteId, ownerId, SatelliteDefinitionSnapshot.from(definition),
                    targetBodyId, logicalTime, discoveryRequired, registry.saveEpoch());
        } catch (ArithmeticException exception) {
            return registry.result(SatelliteOperationCode.CAPACITY_REACHED, false, null, null);
        }
        satellite = satellite.startMission(missionId);
        SatelliteOperationCode refused = registry.admission(ownerId, satellite, mission);
        if (refused != null) { return registry.result(refused, false, null, null); }
        if (commit) {
            registry.admitSatellite(satellite);
            registry.admit(mission, List.of());
        }
        return registry.result(SatelliteOperationCode.SUCCESS, commit, satellite, mission);
    }

    static SatelliteOperationResult idle(SatelliteMissionRegistry registry, SatelliteState candidate, boolean commit) {
        if (candidate.kind() == SatelliteKind.DATA || candidate.currentMissionId().isPresent()
                || candidate.status() != SatelliteStatus.OPERATIONAL) {
            throw new IllegalArgumentException("An idle launch needs an operational non-data satellite");
        }
        SatelliteState existing = registry.satelliteRecord(candidate.satelliteId());
        if (existing != null) {
            boolean same = existing.ownerId().equals(candidate.ownerId())
                    && existing.definitionId().equals(candidate.definitionId()) && existing.kind() == candidate.kind();
            return registry.result(same ? SatelliteOperationCode.IDEMPOTENT : SatelliteOperationCode.IDENTITY_CONFLICT,
                    false, existing, null);
        }
        SatelliteOperationCode refused = registry.admission(candidate.ownerId(), candidate, null);
        if (refused != null) { return registry.result(refused, false, null, null); }
        if (commit) { registry.admitSatellite(candidate); }
        return registry.result(SatelliteOperationCode.SUCCESS, commit, candidate, null);
    }
}
