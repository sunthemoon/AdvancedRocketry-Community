package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MissionStateTest {
    @Test
    void missionRequiresDeadlineThenUsesExplicitDiscoveryPhase() {
        MissionState active = mission(true);
        assertThrows(IllegalStateException.class, () -> active.complete(1_199L));

        MissionState ready = active.complete(1_200L);
        MissionState pending = ready.beginClaim(1_201L);
        MissionState claimed = pending.finishDiscovery();

        assertEquals(MissionStatus.READY, ready.status());
        assertEquals(MissionStatus.CLAIM_PENDING_DISCOVERY, pending.status());
        assertEquals(MissionStatus.CLAIMED, claimed.status());
        assertEquals(20, claimed.netResearchCredit());
        assertFalse(claimed.status().unfinished());
        assertThrows(IllegalStateException.class, () -> claimed.beginClaim(1_202L));
    }

    @Test
    void knownTargetClaimSkipsDiscoveryButStillCreditsResearch() {
        MissionState claimed = mission(false).complete(1_200L).beginClaim(1_200L);

        assertEquals(MissionStatus.CLAIMED, claimed.status());
        assertEquals(120, claimed.netResearchCredit());
    }

    @Test
    void cancellationIsTerminalAndCannotBeClaimed() {
        MissionState cancelled = mission(true).cancel(1_050L);

        assertEquals(MissionStatus.CANCELLED, cancelled.status());
        assertTrue(cancelled.resolvedAtLogicalTime().isPresent());
        assertThrows(IllegalStateException.class, () -> cancelled.complete(1_200L));
    }

    private static MissionState mission(boolean discoveryRequired) {
        SatelliteDefinition definition = new SatelliteDefinition(
                SatelliteLimits.DEFINITION_SCHEMA_VERSION,
                SatelliteIds.DATA_SATELLITE,
                200,
                120,
                100,
                List.of(ModIdentity.id("moon"))
        );
        return MissionState.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                SatelliteDefinitionSnapshot.from(definition),
                ModIdentity.id("moon"),
                1_000L,
                discoveryRequired
        );
    }
}
