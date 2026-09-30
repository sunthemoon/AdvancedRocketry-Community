package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-050 sections 5–9 in the pure registry: queues, limits, budgets, retention and load invariants. */
final class RegistryLifecycleTest {
    private static final ResourceLocation MOON = ModIdentity.id("moon");
    private static final ResourceLocation EARTH = ModIdentity.id("earth");
    private static final RecordSizer SMALL = new RecordSizer() {
        @Override
        public int satelliteBytes(SatelliteState satellite) {
            return 100;
        }

        @Override
        public int missionBytes(MissionState mission) {
            return 100;
        }

        @Override
        public int instanceBytes(AsteroidInstance instance) {
            return 100;
        }
    };

    @Test
    void cancelledAndEarlyClaimedMissionsLeaveTheDeadlineQueue() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L, SMALL);
        UUID owner = UUID.randomUUID();
        UUID satellite = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        registry.launch(satellite, first, owner, definition(), MOON, 0L, false);
        assertEquals(1, registry.scheduledCount());
        assertTrue(registry.cancel(first, owner, false, 10L).success());
        assertEquals(0, registry.scheduledCount(), "a cancel leaves no queue entry");
        UUID second = UUID.randomUUID();
        registry.startMission(satellite, second, owner, definition(), MOON, 20L, false);
        assertEquals(1, registry.scheduledCount());
        assertTrue(registry.claim(second, owner, 400L).success());
        assertEquals(0, registry.scheduledCount(), "a lazily completed claim leaves no queue entry");
    }

    @Test
    void thousandsOfStartsAndClaimsNeverFillTheQueue() {
        // Review finding H2: a claim used to leave its entry, and the queue threw at 1,024 entries.
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L, SMALL);
        UUID owner = UUID.randomUUID();
        UUID satellite = UUID.randomUUID();
        UUID mission = UUID.randomUUID();
        registry.launch(satellite, mission, owner, definition(), MOON, 0L, false);
        long time = 0L;
        for (int round = 0; round < 1_200; round++) {
            time += 200L;
            assertTrue(registry.claim(mission, owner, time).success(), "claim " + round);
            mission = UUID.randomUUID();
            assertTrue(registry.startMission(satellite, mission, owner, definition(), MOON, time, false).success(),
                    "start " + round);
            assertTrue(registry.scheduledCount() <= registry.unfinishedMissionCount());
        }
    }

    @Test
    void admissionLimitsRefuseWithExplicitCodes() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L, SMALL);
        registry.applyLimits(new RegistryLimits(3, 2, 128, 3_072, 4_096, 256, 2_048, 16, 10, 2));
        UUID owner = UUID.randomUUID();
        assertTrue(registry.launch(UUID.randomUUID(), UUID.randomUUID(), owner, definition(), MOON, 0L, false).success());
        assertTrue(registry.launch(UUID.randomUUID(), UUID.randomUUID(), owner, definition(), MOON, 0L, false).success());
        assertEquals(SatelliteOperationCode.OWNER_LIMIT,
                registry.launch(UUID.randomUUID(), UUID.randomUUID(), owner, definition(), MOON, 0L, false).code());
        assertTrue(registry.launch(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), definition(), MOON, 0L,
                false).success());
        assertEquals(SatelliteOperationCode.CAPACITY_REACHED, registry.launch(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), definition(), MOON, 0L, false).code(), "the global unfinished limit");
        assertEquals(3, registry.satellites().size(), "a refused launch registers nothing");
        assertThrows(IllegalArgumentException.class, () -> new RegistryLimits(1_025, 64, 128, 3_072, 4_096, 256,
                2_048, 16, 10, 2));
        assertThrows(IllegalArgumentException.class, () -> new RegistryLimits(1_024, 64, 128, 3_072, 4_096, 256,
                2_048, 16, 9, 2));
    }

    @Test
    void theByteBudgetReservesLifecycleSizesAndFreesThemOnRemoval() {
        RecordSizer huge = new RecordSizer() {
            @Override
            public int satelliteBytes(SatelliteState satellite) {
                return 3 * 1024 * 1024;
            }

            @Override
            public int missionBytes(MissionState mission) {
                return 100;
            }

            @Override
            public int instanceBytes(AsteroidInstance instance) {
                return 100;
            }
        };
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L, huge);
        UUID owner = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID mission = UUID.randomUUID();
        assertTrue(registry.launch(first, mission, owner, definition(), MOON, 0L, false).success());
        assertEquals(3L * 1024L * 1024L, registry.reservedBytes("SATELLITES"));
        assertEquals(SatelliteOperationCode.STORAGE_BUDGET,
                registry.launch(UUID.randomUUID(), UUID.randomUUID(), owner, definition(), MOON, 0L, false).code());
        assertTrue(registry.claim(mission, owner, 200L).success());
        assertTrue(registry.decommission(first, owner, false, false).success());
        assertEquals(0L, registry.reservedBytes("SATELLITES"));
        assertTrue(registry.launch(UUID.randomUUID(), UUID.randomUUID(), owner, definition(), MOON, 300L, false).success());
    }

    @Test
    void finishedMissionsArePrunedPerOwnerAfterTheReplayWindowExceptDiscoveryEvidence() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L, SMALL);
        registry.applyLimits(new RegistryLimits(1_024, 64, 3, 3_072, 4_096, 256, 2_048, 16, 10, 2));
        UUID owner = UUID.randomUUID();
        UUID satellite = UUID.randomUUID();
        List<UUID> finished = new ArrayList<>();
        UUID evidence = UUID.randomUUID();
        registry.launch(satellite, evidence, owner, definition(), EARTH, 0L, true);
        registry.claim(evidence, owner, 200L);
        registry.finishDiscovery(evidence);
        long time = 200L;
        for (int index = 0; index < 5; index++) {
            UUID mission = UUID.randomUUID();
            registry.startMission(satellite, mission, owner, definition(), MOON, time, false);
            time += 200L;
            registry.claim(mission, owner, time);
            finished.add(mission);
        }
        assertEquals(6, registry.finishedMissionCount());
        assertEquals(0, registry.completeDue(time + 100L).pruned(), "nothing is eligible before 1,200 ticks");
        SchedulerPass pass = registry.completeDue(time + 1_300L);
        assertEquals(3, pass.pruned());
        assertTrue(pass.changed());
        assertEquals(3, registry.finishedMissionCount());
        assertTrue(registry.mission(evidence).isPresent(), "the only discovery evidence for Earth is kept");
        assertTrue(registry.mission(finished.get(0)).isEmpty() && registry.mission(finished.get(1)).isEmpty(),
                "the oldest eligible records go first");
        assertTrue(registry.mission(finished.get(4)).isPresent());
    }

    @Test
    void theGlobalPruneRunsAboveTheThresholdAtMost64PerPass() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L, SMALL);
        long time = 0L;
        int finished = 0;
        while (finished < MissionRetention.GLOBAL_FINISHED_THRESHOLD + 100) {
            UUID owner = UUID.randomUUID();
            UUID satellite = UUID.randomUUID();
            for (int index = 0; index < 120; index++) {
                UUID mission = UUID.randomUUID();
                if (index == 0) {
                    registry.launch(satellite, mission, owner, definition(), MOON, time, false);
                } else {
                    registry.startMission(satellite, mission, owner, definition(), MOON, time, false);
                }
                time += 200L;
                assertTrue(registry.claim(mission, owner, time).success());
                finished++;
            }
        }
        int excess = registry.finishedMissionCount() - MissionRetention.GLOBAL_FINISHED_THRESHOLD;
        assertTrue(excess > 128, "the fixture needs at least three passes");
        int passes = 0;
        while (registry.finishedMissionCount() > MissionRetention.GLOBAL_FINISHED_THRESHOLD) {
            SchedulerPass pass = registry.completeDue(time + 1_300L + 20L * passes);
            assertEquals(Math.min(64, excess - 64 * passes), pass.pruned(), "pass " + passes);
            passes++;
        }
        assertEquals((excess + 63) / 64, passes);
        assertEquals(MissionRetention.GLOBAL_FINISHED_THRESHOLD, registry.finishedMissionCount());
        assertEquals(0, registry.completeDue(time + 1_300L + 20L * passes).pruned());
    }

    @Test
    void brokenReferencesAreQuarantinedOrMarkedForRecoveryAndOperatorsCanRestoreThem() {
        SatelliteMissionRegistry source = SatelliteMissionRegistry.create(0L, SMALL);
        UUID owner = UUID.randomUUID();
        UUID bound = UUID.randomUUID();
        UUID boundMission = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        UUID orphanMission = UUID.randomUUID();
        source.launch(bound, boundMission, owner, definition(), MOON, 0L, false);
        source.launch(orphan, orphanMission, owner, definition(), MOON, 0L, false);

        SatelliteMissionRegistry restored = SatelliteMissionRegistry.restore(0L, 0L, 1L, SMALL);
        // The bound satellite's mission is missing; the orphan mission's satellite is missing.
        restored.restoreSatellite(source.satellite(bound).orElseThrow());
        restored.restoreMission(source.mission(orphanMission).orElseThrow());
        RestoreReport report = restored.finishRestore();

        assertEquals(new RestoreReport(1, 1, 0, 1), report);
        assertEquals(SatelliteStatus.RECOVERY_REQUIRED, restored.satellite(bound).orElseThrow().status());
        assertTrue(restored.satellite(bound).orElseThrow().currentMissionId().isEmpty());
        MissionState held = restored.mission(orphanMission).orElseThrow();
        assertEquals(MissionStatus.QUARANTINED, held.status());
        assertEquals("MISSING_SATELLITE", held.quarantine().orElseThrow().reason());
        assertEquals(0, restored.scheduledCount(), "a quarantined mission is not scheduled");
        assertEquals(1L, restored.unfinishedMissionCount(), "quarantine counts as unfinished");

        assertEquals(SatelliteOperationCode.RECOVERY_REQUIRED, restored.releaseQuarantine(orphanMission).code(),
                "a release needs the invariants to hold");
        assertEquals(SatelliteOperationCode.RECOVERY_REQUIRED,
                restored.claim(orphanMission, owner, 500L).code(), "a quarantined mission cannot be claimed");
        assertEquals(SatelliteOperationCode.RECOVERY_REQUIRED,
                restored.cancel(orphanMission, owner, false, 500L).code(), "only an operator cancels it");
        assertTrue(restored.cancel(orphanMission, UUID.randomUUID(), true, 500L).success());
        assertEquals(MissionStatus.CANCELLED, restored.mission(orphanMission).orElseThrow().status());
        assertEquals(SatelliteOperationCode.RECOVERY_REQUIRED,
                restored.startMission(bound, UUID.randomUUID(), owner, definition(), MOON, 600L, false).code());
        assertTrue(restored.recoverSatellite(bound).success());
        assertEquals(SatelliteOperationCode.IDEMPOTENT, restored.recoverSatellite(bound).code());
        assertTrue(restored.startMission(bound, UUID.randomUUID(), owner, definition(), MOON, 600L, false).success());
    }

    @Test
    void aReleasedQuarantineReturnsToItsPreviousStatusAndSchedule() {
        SatelliteMissionRegistry source = SatelliteMissionRegistry.create(0L, SMALL);
        UUID owner = UUID.randomUUID();
        UUID satellite = UUID.randomUUID();
        UUID mission = UUID.randomUUID();
        source.launch(satellite, mission, owner, definition(), MOON, 0L, false);
        SatelliteMissionRegistry restored = SatelliteMissionRegistry.restore(0L, 0L, 1L, SMALL);
        restored.restoreSatellite(source.satellite(satellite).orElseThrow());
        restored.restoreMission(source.mission(mission).orElseThrow().quarantine("SATELLITE_NOT_BOUND", false));
        restored.restoreAccount(source.account(owner));
        assertFalse(restored.finishRestore().changed(), "an already quarantined mission stays as it is");

        assertTrue(restored.releaseQuarantine(mission).success());
        assertEquals(MissionStatus.ACTIVE, restored.mission(mission).orElseThrow().status());
        assertEquals(1, restored.scheduledCount());
        assertEquals(1, restored.completeDue(200L).completed());
        assertEquals(SatelliteOperationCode.IDEMPOTENT, restored.releaseQuarantine(mission).code());
    }

    private static SatelliteDefinition definition() {
        return new SatelliteDefinition(SatelliteLimits.DEFINITION_SCHEMA_VERSION, SatelliteIds.DATA_SATELLITE, 200, 120, 100,
                List.of(EARTH, MOON, ModIdentity.id("space")));
    }
}
