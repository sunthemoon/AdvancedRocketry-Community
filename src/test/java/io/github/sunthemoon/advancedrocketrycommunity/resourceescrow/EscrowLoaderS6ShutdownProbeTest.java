package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6ShutdownProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void pendingRetirementStartsOtherWriteOnlyAfterTheNativeProofNotBeforeReconciliation() {
        String cut = "LF_S6_G3_L2_QUEUED";
        assertFalse(EscrowLoaderS6ShutdownProbe.otherWriteBoundary(cut, "K1", false));
        assertTrue(EscrowLoaderS6ShutdownProbe.otherWriteBoundary(cut, "C6_JOINT_READBACK", false));
        assertFalse(EscrowLoaderS6ShutdownProbe.otherWriteBoundary(cut, "C6_JOINT_READBACK", true));
        for (String label : java.util.List.of("C2_SOURCE_AFTER", "C3_FROZEN", "C7_ACKNOWLEDGED", "K8")) {
            assertFalse(EscrowLoaderS6ShutdownProbe.otherWriteBoundary(cut, label, false));
        }
        assertFalse(EscrowLoaderS6ShutdownProbe.otherWriteBoundary("LF_S6_G3_L1_QUEUED", "C6_JOINT_READBACK", false));
    }

    @Test void l2QueuedMeansProvedCheckpointWaitingBehindAnActualOtherWrite() {
        String cut = "LF_S6_G3_L2_QUEUED";
        assertTrue(EscrowLoaderS6ShutdownProbe.selected(true, "LF_M_K7", cut));
        assertFalse(EscrowLoaderS6ShutdownProbe.selected(false, "LF_M_K7", cut));
        String state = "active:true,successor:false,completions:0,running:true,unknown:false,generation:3,durable:2";
        assertTrue(EscrowLoaderS6ShutdownProbe.l2QueuedReady(state, true));
        assertFalse(EscrowLoaderS6ShutdownProbe.l2QueuedReady(state, false));
        for (String changed : java.util.List.of(state.replace("active:true", "active:false"),
                state.replace("running:true", "running:false"), state.replace("generation:3", "generation:4"),
                state.replace("durable:2", "durable:3"), state.replace("unknown:false", "unknown:true"),
                state.replace("successor:false", "successor:true"), state.replace("completions:0", "completions:1"))) {
            assertFalse(EscrowLoaderS6ShutdownProbe.l2QueuedReady(changed, true));
        }
    }

    @Test void furtherShutdownRowsKeepTheirExactNativeFaultSelectorPairs() {
        for (var pair : java.util.Map.of("LF_S6_G3_L1_QUEUED", "LF_M_K7", "LF_S6_G3_FAILED_CLOSE", "LF_M_K7",
                "LF_S6_G3_L1_UNKNOWN", "LF_M_E4L1_UNKNOWN", "LF_S6_G3_L2_UNKNOWN", "LF_M_E4L2_UNKNOWN").entrySet()) {
            assertTrue(EscrowLoaderS6ShutdownProbe.selected(true, pair.getValue(), pair.getKey()));
            assertFalse(EscrowLoaderS6ShutdownProbe.selected(false, pair.getValue(), pair.getKey()));
            assertFalse(EscrowLoaderS6ShutdownProbe.selected(true, pair.getValue() + "_OTHER", pair.getKey()));
        }
    }

    @Test void queuedObservationRequiresAnActualRunningL1AndUncapturedSuccessor() {
        String state = "active:true,successor:true,completions:0,running:true,unknown:false,generation:2,durable:1";
        assertTrue(EscrowLoaderS6ShutdownProbe.queuedReady(state));
        for (String changed : java.util.List.of(state.replace("successor:true", "successor:false"),
                state.replace("active:true", "active:false"), state.replace("running:true", "running:false"),
                state.replace("unknown:false", "unknown:true"), state.replace("generation:2", "generation:3"),
                state.replace("completions:0", "completions:1"))) {
            assertFalse(EscrowLoaderS6ShutdownProbe.queuedReady(changed));
        }
    }

    @Test void unknownObservationPinsTheActualCandidateAndPreviousDurableGeneration() {
        String l1 = "active:true,successor:false,completions:0,running:false,unknown:true,generation:2,durable:1";
        String l2 = l1.replace("generation:2,durable:1", "generation:3,durable:2");
        assertTrue(EscrowLoaderS6ShutdownProbe.unknownReady("LF_S6_G3_L1_UNKNOWN", l1));
        assertTrue(EscrowLoaderS6ShutdownProbe.unknownReady("LF_S6_G3_L2_UNKNOWN", l2));
        assertFalse(EscrowLoaderS6ShutdownProbe.unknownReady("LF_S6_G3_L1_UNKNOWN", l2));
        assertFalse(EscrowLoaderS6ShutdownProbe.unknownReady("LF_S6_G3_L2_UNKNOWN", l1));
        assertFalse(EscrowLoaderS6ShutdownProbe.unknownReady("LF_S6_G3_L1_UNKNOWN", l1.replace("unknown:true", "unknown:false")));
    }

    @Test void onlyTheExactReleaseGatedNativeCaseAndSelectorPairIsAccepted() {
        var cut = EscrowLoaderS6ShutdownProbe.L1_INFLIGHT;
        assertTrue(EscrowLoaderS6ShutdownProbe.selected(true, "LF_M_K7", cut));
        assertFalse(EscrowLoaderS6ShutdownProbe.selected(false, "LF_M_K7", cut));
        assertFalse(EscrowLoaderS6ShutdownProbe.selected(true, "LF_M_K7", null));
        assertFalse(EscrowLoaderS6ShutdownProbe.selected(true, "LF_M_K7", cut + "_OTHER"));
        assertFalse(EscrowLoaderS6ShutdownProbe.selected(true, "LF_M_K1", cut));
    }

    @Test void pauseIsOnlyBeforeTheInitialL1ReplacementNeverOnRecovery() {
        assertTrue(EscrowLoaderS6ShutdownProbe.stopBoundary("K1", false));
        assertFalse(EscrowLoaderS6ShutdownProbe.stopBoundary("K1", true));
        for (var label : java.util.List.of("BASELINE_STAGED", "K2", "K8", "K9", "C6_JOINT_READBACK")) {
            assertFalse(EscrowLoaderS6ShutdownProbe.stopBoundary(label, false));
        }
    }

    @Test void l2SelectionWaitsForItsOwnPreReplacementWriterBoundary() {
        String cut = EscrowLoaderS6ShutdownProbe.L2_INFLIGHT;
        assertTrue(EscrowLoaderS6ShutdownProbe.selected(true, "LF_M_K7", cut));
        assertTrue(EscrowLoaderS6ShutdownProbe.stopBoundary(cut, "K8", false));
        assertFalse(EscrowLoaderS6ShutdownProbe.stopBoundary(cut, "K1", false));
        assertFalse(EscrowLoaderS6ShutdownProbe.stopBoundary(cut, "K8", true));
    }

    @Test void observationPropertyRoutesOnlyItsExactPairAndSuppressesTheUnusedMatrix() throws Exception {
        String previous = System.getProperty(EscrowLoaderS6ShutdownProbe.PROPERTY);
        try {
            System.setProperty(EscrowLoaderS6ShutdownProbe.PROPERTY, EscrowLoaderS6ShutdownProbe.L1_INFLIGHT);
            assertTrue(EscrowLoaderS6Probe.selected(true, "LF_M_K7"));
            assertFalse(EscrowLoaderS6Probe.selected(false, "LF_M_K7"));
            assertFalse(EscrowLoaderS6Probe.selected(true, "LF_M_K1"));
            var source = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowLoaderReleaseProbe.java"));
            assertTrue(source.contains("matrix = s6 == null &&"));
        } finally {
            if (previous == null) { System.clearProperty(EscrowLoaderS6ShutdownProbe.PROPERTY); }
            else { System.setProperty(EscrowLoaderS6ShutdownProbe.PROPERTY, previous); }
        }
    }
}
