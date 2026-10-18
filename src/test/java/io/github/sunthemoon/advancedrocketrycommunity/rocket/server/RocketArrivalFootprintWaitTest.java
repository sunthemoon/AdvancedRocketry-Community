package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W83b (Root decision R32 item 1): the PREPARED footprint wait logs once per transition and stays bounded. */
class RocketArrivalFootprintWaitTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private final List<String> lines = new ArrayList<>();
    private final Set<UUID> live = new HashSet<>();
    private final RocketArrivalFootprintWait wait = new RocketArrivalFootprintWait(live, lines::add);

    @Test void aWaitLogsOnceWhenItStartsAndOnceWhenItEnds() {
        var transfer = UUID.randomUUID(); live.add(transfer);
        assertTrue(wait.observe(transfer, 100, 3, 4, () -> "logical=L level=moon"), "The first not-ready visit did not start a wait");
        for (long tick = 101; tick < 140; tick++) {
            assertFalse(wait.observe(transfer, tick, (int) (tick % 4), 4, () -> { throw new AssertionError("re-read"); }));
        }
        assertEquals(List.of("ARCE_ARRIVAL_ENTITIES_LOADING transfer=" + transfer + " logical=L level=moon columns=3/4"
                + " reason=entities_loading"), lines);
        assertFalse(wait.observe(transfer, 140, 4, 4, () -> "unused"));
        assertEquals("ARCE_ARRIVAL_ENTITIES_READY transfer=" + transfer + " waited=40", lines.get(1));
        assertFalse(wait.observe(transfer, 141, 4, 4, () -> "unused"));
        assertEquals(2, lines.size(), "A ready visit after the transition logged again");
        assertEquals(0, wait.size());
    }

    @Test void aReadyFirstVisitLogsNothingAndALaterWaitIsANewTransition() {
        var transfer = UUID.randomUUID(); live.add(transfer);
        assertFalse(wait.observe(transfer, 5, 1, 1, () -> "unused"));
        assertTrue(lines.isEmpty());
        assertTrue(wait.observe(transfer, 6, 0, 2, () -> "w"));
        assertFalse(wait.observe(transfer, 7, 2, 2, () -> "w"));
        assertTrue(wait.observe(transfer, 8, 1, 2, () -> "w"), "A second wait was not reported");
        assertEquals(3, lines.size());
    }

    @Test void entriesOfTransfersNoLongerLiveArePrunedWhenAnotherWaitStarts() {
        var gone = UUID.randomUUID(); var kept = UUID.randomUUID(); var next = UUID.randomUUID();
        live.addAll(List.of(gone, kept, next));
        wait.observe(gone, 1, 0, 1, () -> "w"); wait.observe(kept, 1, 0, 1, () -> "w");
        live.remove(gone);
        wait.observe(next, 2, 0, 1, () -> "w");
        assertFalse(wait.waiting(gone)); assertTrue(wait.waiting(kept) && wait.waiting(next));
    }

    @Test void theStateNeverExceedsTheTransferBoundAndAnUnreportedWaitStillWaits() {
        for (int i = 0; i < RocketFlightLimits.MAX_ACTIVE_TRANSFERS + 8; i++) {
            var transfer = UUID.randomUUID(); live.add(transfer);
            boolean reported = wait.observe(transfer, i, 0, 1, () -> "w");
            assertEquals(i < RocketFlightLimits.MAX_ACTIVE_TRANSFERS, reported);
        }
        assertEquals(RocketFlightLimits.MAX_ACTIVE_TRANSFERS, wait.size());
        assertEquals(RocketFlightLimits.MAX_ACTIVE_TRANSFERS, lines.size());
        wait.clear();
        assertEquals(0, wait.size());
    }

    @Test void theReasonIsATranslatedPadReason() {
        assertEquals("message.advancedrocketrycommunity.pad.reason.entities_loading", RocketArrivalFootprintWait.REASON);
        assertEquals(RocketLandingPadSelector.ENTITIES_LOADING, RocketArrivalFootprintWait.REASON);
        var english = io.github.sunthemoon.advancedrocketrycommunity.datagen.V180LandingPadData.language(false);
        var chinese = io.github.sunthemoon.advancedrocketrycommunity.datagen.V180LandingPadData.language(true);
        assertFalse(english.get(RocketArrivalFootprintWait.REASON).isBlank());
        assertFalse(chinese.get(RocketArrivalFootprintWait.REASON).isBlank());
        assertNotEquals(english.get(RocketArrivalFootprintWait.REASON), chinese.get(RocketArrivalFootprintWait.REASON));
        // The request-result detail check accepts pad reasons only; the wait's key must stay one.
        assertDoesNotThrow(() -> new io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult(
                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE,
                0, RocketArrivalFootprintWait.REASON));
    }
}
