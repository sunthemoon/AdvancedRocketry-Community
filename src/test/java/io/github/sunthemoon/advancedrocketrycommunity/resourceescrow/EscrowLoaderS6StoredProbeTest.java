package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6StoredProbeTest {
    @Test void onlyTheThreeAuthorizedSelectionsMayStore() {
        for (String cut : java.util.List.of("LF_S6_F3_ASCENT", "LF_S6_F3_TRANSIT", "LF_S6_F4_STORED")) {
            assertTrue(EscrowLoaderS6StoredProbe.selected(cut));
            assertFalse(EscrowLoaderS6StoredProbe.selected(cut + "_OTHER"));
        }
        for (String cut : java.util.List.of("LF_S6_F2_ACK", "LF_S6_F4_ACK", "LF_S6_F6_PROOF_AFTER_0", "LF_S6_F8_JOURNAL_ACK")) {
            assertFalse(EscrowLoaderS6StoredProbe.selected(cut));
        }
        assertFalse(EscrowLoaderS6StoredProbe.selected(null));
    }

    @Test void sourceStoresWaitForTheActualPhaseAndNativeColumn() {
        assertTrue(EscrowLoaderS6StoredProbe.ready("LF_S6_F3_ASCENT", RocketFlightState.ASCENT, 12));
        assertTrue(EscrowLoaderS6StoredProbe.ready("LF_S6_F3_TRANSIT", RocketFlightState.TRANSIT, 48));
        assertFalse(EscrowLoaderS6StoredProbe.ready("LF_S6_F3_ASCENT", RocketFlightState.ASCENT, 11.9));
        assertFalse(EscrowLoaderS6StoredProbe.ready("LF_S6_F3_ASCENT", RocketFlightState.TRANSIT, 48));
        assertFalse(EscrowLoaderS6StoredProbe.ready("LF_S6_F3_TRANSIT", RocketFlightState.COUNTDOWN, 0));
        for (double height : new double[] {-1, 49, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertFalse(EscrowLoaderS6StoredProbe.ready("LF_S6_F3_ASCENT", RocketFlightState.ASCENT, height));
            assertFalse(EscrowLoaderS6StoredProbe.ready("LF_S6_F3_TRANSIT", RocketFlightState.TRANSIT, height));
        }
    }
}
