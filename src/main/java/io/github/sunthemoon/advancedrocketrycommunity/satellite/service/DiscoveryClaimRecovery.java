package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** Ordered acknowledgments reuse the paid mission as the durable discovery receipt. */
public final class DiscoveryClaimRecovery {
    private DiscoveryClaimRecovery() { }

    public static boolean paidReceipt(MissionState mission) {
        return mission.discoveryRequired() && (mission.status() == MissionStatus.CLAIM_PENDING_DISCOVERY
                || mission.status() == MissionStatus.CLAIMED);
    }

    public static SatelliteOperationResult recover(SatelliteMissionSavedData missions, CelestialSavedData celestial,
            UUID missionId, long gameTime, Predicate<ResourceLocation> available,
            Consumer<SatelliteMissionSavedData> saveMissions, Consumer<CelestialSavedData> saveCelestial) {
        MissionState mission = missions.mission(missionId).orElse(null);
        if (mission == null) {
            return new SatelliteOperationResult(SatelliteOperationCode.MISSION_NOT_FOUND, false,
                    Optional.empty(), Optional.empty(), 0);
        }
        if (!paidReceipt(mission)) { return result(missions, mission, SatelliteOperationCode.NOT_READY); }
        if (!available.test(mission.targetBodyId())) {
            return result(missions, mission, SatelliteOperationCode.CATALOG_UNAVAILABLE);
        }
        CelestialSavedData.MutationResult discovery = celestial.discoverDurably(mission.targetBodyId(), gameTime, candidate -> {
            saveMissions.accept(missions);
            saveCelestial.accept(candidate);
        });
        if (discovery == CelestialSavedData.MutationResult.UNSUPPORTED_SCHEMA
                || discovery == CelestialSavedData.MutationResult.CAPACITY_REACHED) {
            return result(missions, mission, SatelliteOperationCode.PENDING_DISCOVERY);
        }
        SatelliteOperationResult finished = missions.finishDiscovery(missionId);
        if (finished.changed()) { saveMissions.accept(missions); }
        return finished;
    }

    private static SatelliteOperationResult result(SatelliteMissionSavedData data, MissionState mission,
            SatelliteOperationCode code) {
        return new SatelliteOperationResult(code, false, data.satellite(mission.satelliteId()),
                Optional.of(mission), data.account(mission.ownerId()).balance());
    }
}
