package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6FlightProbeTest {
    @Test void exactSelectedBoundariesCannotInterceptThePrecedingLoaderFill() {
        for (var side : List.of("STORE_BEFORE", "STORE_AFTER", "FORCE_BEFORE", "FORCE_AFTER", "READBACK_BEFORE", "READBACK_AFTER")) {
            var cut = "LF_S6_F1_" + side;
            assertTrue(EscrowLoaderS6FlightProbe.selected(true, cut));
            assertFalse(EscrowLoaderS6FlightProbe.selected(false, cut));
            assertTrue(EscrowLoaderS6FlightProbe.boundary(cut, "BARRIER_" + side, true, false));
            assertFalse(EscrowLoaderS6FlightProbe.boundary(cut, "BARRIER_" + side, false, false));
            assertFalse(EscrowLoaderS6FlightProbe.boundary(cut, "BARRIER_" + side + "_OTHER", true, false));
            assertFalse(EscrowLoaderS6FlightProbe.selected(true, cut + "_OTHER"));
        }
        assertFalse(EscrowLoaderS6FlightProbe.selected(true, null));
        assertFalse(EscrowLoaderS6FlightProbe.selected(true, "LF_S6_F1_"));
    }

    @Test void armAndPreparedSelectionsUseTheirActualAcknowledgementAndWriterCallbacks() {
        for (var side : List.of("BEFORE", "REPLACED", "ACK")) {
            var arm = "LF_S6_F1_ARM_" + side;
            var hook = side.equals("BEFORE") ? "K8" : side.equals("REPLACED") ? "K9" : "F1_ARM_ACK";
            assertTrue(EscrowLoaderS6FlightProbe.selected(true, arm));
            assertTrue(EscrowLoaderS6FlightProbe.boundary(arm, hook, false, true));
            assertFalse(EscrowLoaderS6FlightProbe.boundary(arm, hook, false, false));
            assertTrue(EscrowLoaderS6FlightProbe.selected(true, "LF_S6_F2_" + side));
            assertFalse(EscrowLoaderS6FlightProbe.boundary("LF_S6_F2_" + side, "K9", false, true));
        }
        assertTrue(EscrowLoaderS6FlightProbe.boundary("LF_S6_F2_ACK", "F2_PREPARED_ACK", false, true));
        assertFalse(EscrowLoaderS6FlightProbe.boundary("LF_S6_F2_ACK", "F2_PREPARED_SUBMITTED", false, true));
    }
}
