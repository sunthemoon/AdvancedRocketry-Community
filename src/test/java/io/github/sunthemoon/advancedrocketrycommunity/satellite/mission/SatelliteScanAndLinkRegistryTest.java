package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-049 sections 8–9: scan payment from the lazy battery, and the receiver-link index. */
final class SatelliteScanAndLinkRegistryTest {
    private static final SatelliteStats SURVEY_STATS = new SatelliteStats(4, 10_720, 1_000, 0, 10);

    @Test
    void theLazyBatteryIsCappedAndSaturating() {
        SatelliteKindState.Survey survey = new SatelliteKindState.Survey(100L, 1_000L, 1_000, 32, 8);
        assertEquals(100L, survey.chargeAt(1_000L, 4, 10_720));
        assertEquals(100L, survey.chargeAt(900L, 4, 10_720), "time never runs backwards");
        assertEquals(500L, survey.chargeAt(1_100L, 4, 10_720));
        assertEquals(10_720L, survey.chargeAt(1_000_000L, 4, 10_720));
        assertEquals(10_720L, survey.chargeAt(Long.MAX_VALUE, 1_000, 10_720), "power × Δ saturates");
        assertEquals(100L, survey.chargeAt(5_000L, 0, 10_720));
        assertThrows(IllegalArgumentException.class, () -> survey.chargeAt(1L, -1, 10));
    }

    @Test
    void aScanIsPaidOnlyWhenTheChargeCoversIt() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(1_000L);
        UUID ownerId = UUID.randomUUID();
        UUID satelliteId = UUID.randomUUID();
        registry.launchIdle(time -> survey(satelliteId, ownerId, time), 1_000L);

        // 4 FE per tick: 1,000 FE needs 250 ticks.
        SatelliteOperationResult early = registry.payScan(satelliteId, ownerId, false, 1_249L);
        assertEquals(SatelliteOperationCode.NO_POWER, early.code());
        assertFalse(early.changed());
        assertEquals(new SatelliteKindState.Survey(0L, 1_000L, 1_000, 32, 8),
                registry.satellite(satelliteId).orElseThrow().kindState(), "a refused scan changes nothing");

        SatelliteOperationResult paid = registry.payScan(satelliteId, ownerId, false, 1_300L);
        assertEquals(SatelliteOperationCode.SUCCESS, paid.code());
        assertTrue(paid.changed());
        assertEquals(new SatelliteKindState.Survey(200L, 1_300L, 1_000, 32, 8),
                registry.satellite(satelliteId).orElseThrow().kindState());
        assertEquals(SatelliteOperationCode.NO_POWER, registry.payScan(satelliteId, ownerId, false, 1_301L).code());
    }

    @Test
    void scanPaymentChecksOwnerAndKind() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L);
        UUID ownerId = UUID.randomUUID();
        UUID surveyId = UUID.randomUUID();
        UUID solarId = UUID.randomUUID();
        registry.launchIdle(time -> survey(surveyId, ownerId, time), 0L);
        registry.launchIdle(time -> solar(solarId, ownerId, Optional.empty(), time), 0L);

        assertEquals(SatelliteOperationCode.SATELLITE_NOT_FOUND,
                registry.payScan(UUID.randomUUID(), ownerId, false, 10_000L).code());
        assertEquals(SatelliteOperationCode.UNAUTHORIZED,
                registry.payScan(surveyId, UUID.randomUUID(), false, 10_000L).code());
        assertEquals(SatelliteOperationCode.DEFINITION_NOT_FOUND,
                registry.payScan(solarId, ownerId, false, 10_000L).code());
        assertEquals(SatelliteOperationCode.SUCCESS,
                registry.payScan(surveyId, UUID.randomUUID(), true, 10_000L).code());
    }

    @Test
    void theLinkIndexFollowsEveryChangeAndIsRebuiltOnRestore() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L);
        UUID ownerId = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID first = new UUID(0L, 1L);
        UUID second = new UUID(0L, 2L);
        registry.launchIdle(time -> solar(second, ownerId, Optional.of(receiver), time), 0L);
        registry.launchIdle(time -> solar(first, ownerId, Optional.empty(), time), 0L);
        assertEquals(List.of(second), registry.linkedTo(receiver));

        registry.updateKindState(first, new SatelliteKindState.Solar(100, Optional.of(receiver)));
        assertEquals(List.of(first, second), registry.linkedTo(receiver));
        registry.updateKindState(second, new SatelliteKindState.Solar(100, Optional.of(other)));
        assertEquals(List.of(first), registry.linkedTo(receiver));
        assertEquals(List.of(second), registry.linkedTo(other));

        SatelliteMissionRegistry restored = SatelliteMissionRegistry.restore(0L, 0L);
        registry.satellites().forEach(restored::restoreSatellite);
        restored.restoreAccount(registry.account(ownerId));
        restored.finishRestore();
        assertEquals(List.of(first), restored.linkedTo(receiver));

        registry.updateKindState(first, new SatelliteKindState.Solar(100, Optional.empty()));
        assertTrue(registry.linkedTo(receiver).isEmpty());
        registry.decommission(second, ownerId, false, true);
        assertTrue(registry.linkedTo(other).isEmpty());
    }

    private static SatelliteState survey(UUID satelliteId, UUID ownerId, long time) {
        return SatelliteState.launchIdle(satelliteId, ModIdentity.id("survey_satellite"), ownerId, time,
                ModIdentity.id("earth"), blueprint("survey_scanner_module"),
                new SatelliteKindState.Survey(0L, time, 1_000, 32, 8));
    }

    private static SatelliteState solar(UUID satelliteId, UUID ownerId, Optional<UUID> receiver, long time) {
        return SatelliteState.launchIdle(satelliteId, ModIdentity.id("solar_satellite"), ownerId, time,
                ModIdentity.id("earth"), blueprint("solar_transmitter_module"),
                new SatelliteKindState.Solar(100, receiver));
    }

    private static SatelliteBlueprint blueprint(String primary) {
        return new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"), ModIdentity.id(primary),
                ModIdentity.id("satellite_solar_module"), ModIdentity.id("satellite_battery")), false, SURVEY_STATS);
    }
}
