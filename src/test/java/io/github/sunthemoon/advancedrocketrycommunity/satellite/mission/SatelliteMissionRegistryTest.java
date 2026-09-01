package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SatelliteMissionRegistryTest {
    @Test
    void launchIsIdempotentAndConflictingOwnerIsRejected() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(1_000L);
        UUID satelliteId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        SatelliteOperationResult first = registry.launch(
                satelliteId, missionId, ownerId, definition(), ModIdentity.id("moon"), 1_000L, true
        );
        SatelliteOperationResult replay = registry.launch(
                satelliteId, missionId, ownerId, definition(), ModIdentity.id("moon"), 1_001L, true
        );
        SatelliteOperationResult conflict = registry.launch(
                satelliteId, missionId, UUID.randomUUID(), definition(), ModIdentity.id("moon"), 1_002L, true
        );

        assertEquals(SatelliteOperationCode.SUCCESS, first.code());
        assertEquals(SatelliteOperationCode.IDEMPOTENT, replay.code());
        assertEquals(SatelliteOperationCode.IDENTITY_CONFLICT, conflict.code());
        assertEquals(1, registry.satellites().size());
        assertEquals(1, registry.missions().size());
    }

    @Test
    void offlineCompletionAndClaimCreditExactlyOnce() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(1_000L);
        UUID satelliteId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        registry.launch(
                satelliteId, missionId, ownerId, definition(), ModIdentity.id("moon"), 1_000L, true
        );

        SatelliteMissionRegistry.SchedulerPass pass = registry.completeDue(1_200L);
        SatelliteOperationResult claim = registry.claim(missionId, ownerId, 1_201L);
        SatelliteOperationResult replay = registry.claim(missionId, ownerId, 1_202L);
        SatelliteOperationResult finish = registry.finishDiscovery(missionId);
        SatelliteOperationResult finalReplay = registry.claim(missionId, ownerId, 1_203L);

        assertEquals(1, pass.completed());
        assertEquals(SatelliteOperationCode.PENDING_DISCOVERY, claim.code());
        assertEquals(20, claim.researchBalance());
        assertEquals(SatelliteOperationCode.PENDING_DISCOVERY, replay.code());
        assertEquals(20, replay.researchBalance());
        assertEquals(SatelliteOperationCode.SUCCESS, finish.code());
        assertEquals(SatelliteOperationCode.ALREADY_CLAIMED, finalReplay.code());
        assertEquals(20, registry.account(ownerId).balance());
        assertTrue(registry.satellite(satelliteId).orElseThrow().currentMissionId().isEmpty());
    }

    @Test
    void ownerAndOneMissionAuthorityAreEnforced() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(1_000L);
        UUID satelliteId = UUID.randomUUID();
        UUID firstMission = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        registry.launch(
                satelliteId, firstMission, ownerId, definition(), ModIdentity.id("moon"), 1_000L, true
        );

        assertEquals(SatelliteOperationCode.UNAUTHORIZED, registry.claim(
                firstMission, UUID.randomUUID(), 1_200L
        ).code());
        assertEquals(SatelliteOperationCode.MISSION_BUSY, registry.startMission(
                satelliteId,
                UUID.randomUUID(),
                ownerId,
                definition(),
                ModIdentity.id("earth"),
                1_100L,
                false
        ).code());
        assertFalse(registry.cancel(firstMission, UUID.randomUUID(), false, 1_100L).changed());
        assertTrue(registry.cancel(firstMission, ownerId, false, 1_100L).changed());
    }

    @Test
    void restoredCrossReferencesMustBeConsistent() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.restore(1_000L, 1_000L);
        UUID satelliteId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        SatelliteMissionRegistry source = SatelliteMissionRegistry.create(1_000L);
        SatelliteOperationResult launched = source.launch(
                satelliteId, missionId, ownerId, definition(), ModIdentity.id("moon"), 1_000L, true
        );
        registry.restoreSatellite(launched.satellite().orElseThrow());
        registry.restoreMission(launched.mission().orElseThrow());
        registry.restoreAccount(source.account(ownerId));
        registry.finishRestore();

        assertEquals(1, registry.unfinishedMissionCount());
        assertEquals(1_200L, registry.completeDue(1_200L).logicalGameTime());
    }

    private static SatelliteDefinition definition() {
        return new SatelliteDefinition(
                SatelliteLimits.DEFINITION_SCHEMA_VERSION,
                SatelliteIds.DATA_SATELLITE,
                200,
                120,
                100,
                List.of(ModIdentity.id("earth"), ModIdentity.id("moon"), ModIdentity.id("space"))
        );
    }
}
