package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongFunction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-049 sections 6–7: idle launch, idempotent replay, the per-owner limit and decommissioning. */
final class SatelliteLifecycleRegistryTest {
    private static final ResourceLocation SOLAR_DEFINITION = ModIdentity.id("solar_satellite");
    private static final ResourceLocation SURVEY_DEFINITION = ModIdentity.id("survey_satellite");
    private static final ResourceLocation MOON = ModIdentity.id("moon");

    @Test
    void idleLaunchReplaysIdempotentlyAndRejectsAnotherIdentity() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(1_000L);
        UUID satelliteId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        SatelliteOperationResult first = registry.launchIdle(solar(satelliteId, ownerId, Optional.empty()), 1_000L);
        SatelliteOperationResult replay = registry.launchIdle(solar(satelliteId, ownerId, Optional.empty()), 1_050L);
        SatelliteOperationResult otherOwner = registry.launchIdle(
                solar(satelliteId, UUID.randomUUID(), Optional.empty()), 1_060L);
        SatelliteOperationResult otherKind = registry.launchIdle(survey(satelliteId, ownerId), 1_070L);

        assertEquals(SatelliteOperationCode.SUCCESS, first.code());
        assertTrue(first.changed());
        assertEquals(SatelliteOperationCode.IDEMPOTENT, replay.code());
        assertFalse(replay.changed());
        assertEquals(SatelliteOperationCode.IDENTITY_CONFLICT, otherOwner.code());
        assertEquals(SatelliteOperationCode.IDENTITY_CONFLICT, otherKind.code());
        assertEquals(1, registry.satellites().size());
        assertTrue(registry.missions().isEmpty());
        SatelliteState stored = registry.satellite(satelliteId).orElseThrow();
        assertEquals(SatelliteKind.SOLAR, stored.kind());
        assertEquals(Optional.of(MOON), stored.orbitBody());
        assertEquals(1_000L, stored.launchedAtLogicalTime());
    }

    @Test
    void anIdleLaunchNeedsAnOperationalNonDataSatellite() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L);
        UUID satelliteId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> registry.launchIdle(
                time -> SatelliteState.launch(satelliteId, SatelliteIds.DATA_SATELLITE, ownerId, time), 0L));
        assertTrue(registry.satellites().isEmpty());
    }

    @Test
    void aReplayedDataPackageIsConsumedWhateverTheSatelliteDidSince() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(1_000L);
        UUID satelliteId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        registry.launch(satelliteId, missionId, ownerId, definition(), MOON, 1_000L, true);
        registry.completeDue(1_200L);
        registry.claim(missionId, ownerId, 1_201L);
        registry.finishDiscovery(missionId);
        assertTrue(registry.satellite(satelliteId).orElseThrow().currentMissionId().isEmpty());

        SatelliteOperationResult replay = registry.launch(
                satelliteId, UUID.randomUUID(), ownerId, definition(), MOON, 1_300L, true);

        assertEquals(SatelliteOperationCode.IDEMPOTENT, replay.code());
        assertFalse(replay.changed());
        assertEquals(1, registry.missions().size());
        assertEquals(20, registry.account(ownerId).balance());
    }

    @Test
    void theOwnerLimitCountsEveryKindAndFreesOnDecommission() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L);
        UUID ownerId = UUID.randomUUID();
        UUID firstId = null;
        for (int index = 0; index < SatelliteLimits.MAX_SATELLITES_PER_OWNER; index++) {
            UUID satelliteId = UUID.randomUUID();
            firstId = firstId == null ? satelliteId : firstId;
            assertEquals(SatelliteOperationCode.SUCCESS,
                    registry.launchIdle(solar(satelliteId, ownerId, Optional.empty()), index).code());
        }
        assertEquals(SatelliteLimits.MAX_SATELLITES_PER_OWNER, registry.ownerSatellites(ownerId));
        assertEquals(SatelliteOperationCode.OWNER_LIMIT,
                registry.launchIdle(survey(UUID.randomUUID(), ownerId), 300L).code());
        assertEquals(SatelliteOperationCode.OWNER_LIMIT, registry.launch(
                UUID.randomUUID(), UUID.randomUUID(), ownerId, definition(), MOON, 300L, true).code());
        assertEquals(SatelliteOperationCode.SUCCESS,
                registry.launchIdle(survey(UUID.randomUUID(), UUID.randomUUID()), 300L).code());

        assertEquals(SatelliteOperationCode.SUCCESS, registry.decommission(firstId, ownerId, false, false).code());
        assertEquals(SatelliteLimits.MAX_SATELLITES_PER_OWNER - 1, registry.ownerSatellites(ownerId));
        assertEquals(SatelliteOperationCode.SUCCESS,
                registry.launchIdle(survey(UUID.randomUUID(), ownerId), 301L).code());
    }

    @Test
    void restoreRebuildsTheOwnerCount() {
        SatelliteMissionRegistry source = SatelliteMissionRegistry.create(0L);
        UUID ownerId = UUID.randomUUID();
        for (int index = 0; index < SatelliteLimits.MAX_SATELLITES_PER_OWNER; index++) {
            source.launchIdle(solar(UUID.randomUUID(), ownerId, Optional.empty()), index);
        }
        SatelliteMissionRegistry restored = SatelliteMissionRegistry.restore(1_000L, 1_000L);
        source.satellites().forEach(restored::restoreSatellite);
        restored.restoreAccount(source.account(ownerId));
        restored.finishRestore();

        assertEquals(SatelliteLimits.MAX_SATELLITES_PER_OWNER, restored.ownerSatellites(ownerId));
        assertEquals(SatelliteOperationCode.OWNER_LIMIT,
                restored.launchIdle(survey(UUID.randomUUID(), ownerId), 1_000L).code());
    }

    @Test
    void decommissionRemovesOnlyAnIdleSatelliteOfItsOwner() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(1_000L);
        UUID ownerId = UUID.randomUUID();
        UUID dataId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        registry.launch(dataId, missionId, ownerId, definition(), MOON, 1_000L, true);

        assertEquals(SatelliteOperationCode.SATELLITE_NOT_FOUND,
                registry.decommission(UUID.randomUUID(), ownerId, false, false).code());
        assertEquals(SatelliteOperationCode.UNAUTHORIZED,
                registry.decommission(dataId, UUID.randomUUID(), false, false).code());
        assertEquals(SatelliteOperationCode.MISSION_BUSY,
                registry.decommission(dataId, ownerId, false, false).code());
        assertEquals(SatelliteOperationCode.MISSION_BUSY,
                registry.decommission(dataId, UUID.randomUUID(), true, false).code());

        registry.completeDue(1_200L);
        registry.claim(missionId, ownerId, 1_201L);
        registry.finishDiscovery(missionId);
        SatelliteOperationResult removed = registry.decommission(dataId, ownerId, false, false);

        assertEquals(SatelliteOperationCode.SUCCESS, removed.code());
        assertTrue(removed.changed());
        assertTrue(registry.satellite(dataId).isEmpty());
        // The finished mission keeps referencing the removed satellite (ADR-050 section 9).
        assertEquals(MissionStatus.CLAIMED, registry.mission(missionId).orElseThrow().status());
        assertEquals(0, registry.ownerSatellites(ownerId));
        assertEquals(SatelliteOperationCode.SATELLITE_NOT_FOUND,
                registry.decommission(dataId, ownerId, false, false).code());
    }

    @Test
    void anOperatorMayDecommissionAnotherOwnersIdleSatellite() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L);
        UUID ownerId = UUID.randomUUID();
        UUID satelliteId = UUID.randomUUID();
        registry.launchIdle(survey(satelliteId, ownerId), 0L);

        assertEquals(SatelliteOperationCode.UNAUTHORIZED,
                registry.decommission(satelliteId, UUID.randomUUID(), false, false).code());
        assertEquals(SatelliteOperationCode.SUCCESS,
                registry.decommission(satelliteId, UUID.randomUUID(), true, false).code());
    }

    @Test
    void aLinkedSolarSatelliteIsBusyUntilItsReceiverIsMissing() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L);
        UUID ownerId = UUID.randomUUID();
        UUID satelliteId = UUID.randomUUID();
        registry.launchIdle(solar(satelliteId, ownerId, Optional.of(UUID.randomUUID())), 0L);

        assertEquals(SatelliteOperationCode.MISSION_BUSY,
                registry.decommission(satelliteId, ownerId, false, false).code());
        assertTrue(registry.satellite(satelliteId).isPresent());
        assertEquals(SatelliteOperationCode.SUCCESS,
                registry.decommission(satelliteId, ownerId, false, true).code());
        assertTrue(registry.satellite(satelliteId).isEmpty());
    }

    @Test
    void kindStateUpdatesKeepIdentityAndAreIdempotent() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L);
        UUID ownerId = UUID.randomUUID();
        UUID satelliteId = UUID.randomUUID();
        registry.launchIdle(solar(satelliteId, ownerId, Optional.empty()), 0L);
        SatelliteKindState linked = new SatelliteKindState.Solar(100, Optional.of(UUID.randomUUID()));

        SatelliteOperationResult updated = registry.updateKindState(satelliteId, linked);
        SatelliteOperationResult again = registry.updateKindState(satelliteId, linked);

        assertEquals(SatelliteOperationCode.SUCCESS, updated.code());
        assertTrue(updated.changed());
        assertEquals(SatelliteOperationCode.IDEMPOTENT, again.code());
        assertFalse(again.changed());
        assertEquals(linked, registry.satellite(satelliteId).orElseThrow().kindState());
        assertEquals(SatelliteKind.SOLAR, registry.satellite(satelliteId).orElseThrow().kind());
        assertEquals(SatelliteOperationCode.SATELLITE_NOT_FOUND,
                registry.updateKindState(UUID.randomUUID(), linked).code());
    }

    private static LongFunction<SatelliteState> solar(UUID satelliteId, UUID ownerId, Optional<UUID> receiver) {
        return time -> SatelliteState.launchIdle(satelliteId, SOLAR_DEFINITION, ownerId, time, MOON,
                blueprint(ModIdentity.id("solar_transmitter_module")),
                new SatelliteKindState.Solar(100, receiver));
    }

    private static LongFunction<SatelliteState> survey(UUID satelliteId, UUID ownerId) {
        return time -> SatelliteState.launchIdle(satelliteId, SURVEY_DEFINITION, ownerId, time, MOON,
                blueprint(ModIdentity.id("survey_scanner_module")),
                new SatelliteKindState.Survey(0L, time, 1_000, 32, 8));
    }

    private static SatelliteBlueprint blueprint(ResourceLocation primary) {
        return new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"), primary,
                ModIdentity.id("satellite_solar_module"), ModIdentity.id("satellite_battery")), false,
                new SatelliteStats(4, 10_720, 0, 0, 10));
    }

    private static SatelliteDefinition definition() {
        return new SatelliteDefinition(
                SatelliteLimits.DEFINITION_SCHEMA_VERSION,
                SatelliteIds.DATA_SATELLITE,
                200,
                120,
                100,
                List.of(ModIdentity.id("earth"), MOON, ModIdentity.id("space"))
        );
    }
}
