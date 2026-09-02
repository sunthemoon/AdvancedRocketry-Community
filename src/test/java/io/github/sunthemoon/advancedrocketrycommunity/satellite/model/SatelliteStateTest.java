package io.github.sunthemoon.advancedrocketrycommunity.satellite.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SatelliteStateTest {
    @Test
    void onlyOneMissionCanOwnAnOperationalSatellite() {
        UUID missionId = UUID.randomUUID();
        SatelliteState launched = SatelliteState.launch(
                UUID.randomUUID(), SatelliteIds.DATA_SATELLITE, UUID.randomUUID(), 10L
        );
        SatelliteState assigned = launched.startMission(missionId);

        assertTrue(assigned.currentMissionId().isPresent());
        assertThrows(IllegalStateException.class, () -> assigned.startMission(UUID.randomUUID()));
        assertTrue(assigned.finishMission(missionId).currentMissionId().isEmpty());
    }

    @Test
    void recoveryStateClearsMissionAndRequiresExplicitRecovery() {
        SatelliteState assigned = SatelliteState.launch(
                UUID.randomUUID(), SatelliteIds.DATA_SATELLITE, UUID.randomUUID(), 10L
        ).startMission(UUID.randomUUID());

        SatelliteState blocked = assigned.requireRecovery();

        assertEquals(SatelliteStatus.RECOVERY_REQUIRED, blocked.status());
        assertTrue(blocked.currentMissionId().isEmpty());
        assertEquals(SatelliteStatus.OPERATIONAL, blocked.recover().status());
    }
}
