package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6ShutdownProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

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
