package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W116 (W98-R6): only a PREPARED record whose loaded source never left its pad lets an offline seat not hold recovery. */
final class RocketRetirementGuardsTest {
    private static final RocketTransferRecord RECORD = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test
    void aCountdownOrStationaryImageAtItsOriginNeverLeftThePad() {
        var transit = RECORD.sourceFlightData();
        var countdown = transit.markFailed(1L).recover(true, 2L).withPlan(transit.plan().orElseThrow()).startCountdown(3L);
        assertTrue(RocketRetirementGuards.neverLeftPad(RocketTransferPhase.PREPARED, countdown, true), "COUNTDOWN at the origin");
        assertTrue(RocketRetirementGuards.neverLeftPad(RocketTransferPhase.PREPARED, countdown, false),
                "A COUNTDOWN image never left its pad wherever the entity stands");
        var stationary = RocketTransferEntities.stationarySource(RECORD, 500L);
        assertTrue(RocketRetirementGuards.neverLeftPad(RocketTransferPhase.PREPARED, stationary, true),
                "A cancelled countdown's saved return: stationary at the origin");
    }

    @Test
    void anImageThatMayHaveLeftThePadOrALaterPhaseKeepsTheOfflineWait() {
        var transit = RECORD.sourceFlightData();
        assertFalse(RocketRetirementGuards.neverLeftPad(RocketTransferPhase.PREPARED, transit, true), "TRANSIT");
        var countdown = transit.markFailed(1L).recover(true, 2L).withPlan(transit.plan().orElseThrow()).startCountdown(3L);
        assertFalse(RocketRetirementGuards.neverLeftPad(RocketTransferPhase.PREPARED, countdown.completeCountdown(4L), true),
                "ASCENT, even at the origin's height");
        var stationary = RocketTransferEntities.stationarySource(RECORD, 500L);
        assertFalse(RocketRetirementGuards.neverLeftPad(RocketTransferPhase.PREPARED, stationary, false),
                "A stationary image away from its origin");
        var planned = transit.markFailed(1L).recover(true, 2L).withPlan(transit.plan().orElseThrow());
        assertFalse(RocketRetirementGuards.neverLeftPad(RocketTransferPhase.PREPARED, planned, true), "FUELED with a plan");
        assertFalse(RocketRetirementGuards.neverLeftPad(RocketTransferPhase.PREPARED, null, true), "No loaded image (a rebuild)");
        var spawned = RECORD.destinationSpawned(UUID.randomUUID()).phase();
        assertFalse(RocketRetirementGuards.neverLeftPad(spawned, countdown, true), "DESTINATION_SPAWNED is never a pad-only return");
        assertFalse(RocketRetirementGuards.neverLeftPad(spawned, stationary, true));
    }
}
