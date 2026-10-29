package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class W206ObservationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void candidateGeometryIsServerFreeAndPinsBorderAndSixteenColumnExtremes() {
        assertEquals(1, W206Observation.candidate("contained", 8, 8).count());
        assertEquals(2, W206Observation.candidate("border", 8, 8).count());
        var square = W206Observation.candidate("square", 8, 8);
        assertEquals(16, square.count());
        assertEquals(4, square.maxColumnX() - square.minColumnX() + 1);
        var strip = W206Observation.candidate("strip", 8, 8);
        assertEquals(16, strip.count());
        assertEquals(1, strip.maxColumnZ() - strip.minColumnZ() + 1);
        assertTrue(strip.admissible());
    }

    @Test void coldQualificationRejectsRemoteTicketInfluenceNotOnlyExactColumnTickets() {
        var columns = W206Observation.candidate("contained", 8, 8).columns();
        assertTrue(W206Observation.noInfluence(columns, List.of()));
        assertFalse(W206Observation.noInfluence(columns,
                List.of(new W206Observation.TicketRow("moon", 9, 0, 22, "start", "unit"))));
        assertTrue(W206Observation.noInfluence(columns,
                List.of(new W206Observation.TicketRow("moon", 10, 0, 22, "start", "unit"))));
        assertFalse(W206Observation.noInfluence(columns,
                List.of(new W206Observation.TicketRow("moon", 0, 0, 31, "arce_rocket_warmup", "other"))));
    }

    @Test void fullTickIncludesCoreAndEndAndRejectsInvalidDurations() {
        assertEquals(105, W206Observation.fullTick(100, 5));
        assertThrows(IllegalArgumentException.class, () -> W206Observation.fullTick(-1, 1));
        assertThrows(ArithmeticException.class, () -> W206Observation.fullTick(Long.MAX_VALUE, 1));
    }

    @Test void startCallbacksBeforeTheNativeTickIncrementBelongToThisCompletedTick() {
        var callbacks = List.<Map<String, Object>>of(Map.of("owner_thread", true, "tick", 41),
                Map.of("owner_thread", true, "tick", 42), Map.of("owner_thread", false, "tick", 42));
        assertEquals(2, W206Observation.ownerCallbacks(callbacks, 42));
    }

    @Test void ticketReleaseDoesNotRequireChunkRetentionButDoesRequireLandingEvidence() {
        assertTrue(W206Observation.released(true, false, List.of(), List.of()));
        assertFalse(W206Observation.released(false, true, List.of(), List.of()));
        assertFalse(W206Observation.released(true, false, List.of("flight"), List.of()));
        assertFalse(W206Observation.released(true, false, List.of(), List.of("warmup")));
    }
}
