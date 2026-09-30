package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ADR-050 section 9 load invariants as a pure plan over restored records. Broken references never block the
 * registry: an unfinished mission without its satellite binding is QUARANTINED (the satellite is not changed),
 * a satellite whose current mission is not unfinished becomes RECOVERY_REQUIRED, and an instance whose mission
 * reference is broken is QUARANTINED. Nothing is deleted, completed or paid.
 */
final class RegistryInvariants {
    static final String INSTANCE_MISMATCH = "INSTANCE_MISMATCH";

    private RegistryInvariants() {
    }

    record Plan(Map<UUID, String> missionQuarantines, Set<UUID> recoveries, Set<UUID> instanceQuarantines) {
    }

    static Plan plan(Map<UUID, SatelliteState> satellites, Map<UUID, MissionState> missions,
                     Map<UUID, AsteroidInstance> instances) {
        Map<UUID, String> quarantines = new LinkedHashMap<>();
        for (MissionState mission : missions.values()) {
            if (!mission.status().unfinished() || mission.status() == MissionStatus.QUARANTINED) {
                continue;
            }
            SatelliteState satellite = satellites.get(mission.satelliteId());
            String reason = satellite == null ? "MISSING_SATELLITE"
                    : !satellite.ownerId().equals(mission.ownerId()) ? "SATELLITE_OWNER_MISMATCH"
                    : !satellite.currentMissionId().filter(mission.missionId()::equals).isPresent()
                    ? "SATELLITE_NOT_BOUND" : instanceProblem(mission, instances);
            if (reason != null) {
                quarantines.put(mission.missionId(), reason);
            }
        }
        Set<UUID> recoveries = new LinkedHashSet<>();
        for (SatelliteState satellite : satellites.values()) {
            if (satellite.currentMissionId().isEmpty()) {
                continue;
            }
            MissionState mission = missions.get(satellite.currentMissionId().orElseThrow());
            if (mission == null || !mission.status().unfinished()
                    || !mission.satelliteId().equals(satellite.satelliteId())) {
                recoveries.add(satellite.satelliteId());
            }
        }
        Map<UUID, String> reasons = new HashMap<>();
        missions.values().forEach(mission -> mission.quarantine()
                .ifPresent(quarantine -> reasons.put(mission.missionId(), quarantine.reason())));
        reasons.putAll(quarantines);
        Set<UUID> instanceQuarantines = new LinkedHashSet<>();
        for (AsteroidInstance instance : instances.values()) {
            if (instanceBroken(instance, missions, reasons)) {
                instanceQuarantines.add(instance.instanceId());
            }
        }
        return new Plan(quarantines, recoveries, instanceQuarantines);
    }

    /** A survey or asteroid mission whose instances do not point back at it, or null. */
    static String instanceProblem(MissionState mission, Map<UUID, AsteroidInstance> instances) {
        if (mission.kind() == MissionKind.ASTEROID) {
            AsteroidInstance instance = instances.get(mission.instanceId().orElseThrow());
            return instance != null && instance.state() == InstanceState.ALLOCATED
                    && instance.allocatedMission().filter(mission.missionId()::equals).isPresent()
                    ? null : INSTANCE_MISMATCH;
        }
        if (mission.kind() == MissionKind.SURVEY && mission.payload() instanceof MissionPayload.Survey survey) {
            for (UUID id : survey.instances()) {
                AsteroidInstance instance = instances.get(id);
                if (instance == null || instance.state() != InstanceState.PENDING
                        || !instance.sourceMission().equals(mission.missionId())) {
                    return INSTANCE_MISMATCH;
                }
            }
        }
        return null;
    }

    /** ALLOCATED and PENDING instances and their mission reference each other one to one. */
    private static boolean instanceBroken(AsteroidInstance instance, Map<UUID, MissionState> missions,
                                          Map<UUID, String> reasons) {
        if (instance.state() == InstanceState.ALLOCATED) {
            MissionState mission = missions.get(instance.allocatedMission().orElseThrow());
            return mission == null || !mission.status().unfinished()
                    || INSTANCE_MISMATCH.equals(reasons.get(mission.missionId()))
                    || !mission.instanceId().filter(instance.instanceId()::equals).isPresent();
        }
        if (instance.state() == InstanceState.PENDING) {
            MissionState mission = missions.get(instance.sourceMission());
            return mission == null || !mission.status().unfinished()
                    || !(mission.payload() instanceof MissionPayload.Survey survey)
                    || !survey.instances().contains(instance.instanceId());
        }
        return false;
    }
}
