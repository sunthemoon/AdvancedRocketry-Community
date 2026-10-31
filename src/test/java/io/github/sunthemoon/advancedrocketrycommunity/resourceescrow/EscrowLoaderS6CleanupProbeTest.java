package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6CleanupProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void fullCheckedOutcomeAndGenerationSelectOnlyTheIntendedWriterBoundary() {
        var root = new EscrowLedger(UUID.randomUUID(), 1, 3, List.of(), List.of(), List.of());
        var active = EscrowFlightAuthority.arm(root, EscrowFlightAuthorityTest.launch(root, 1), 4).generation(4);
        var arm = active.armedFlights().get(0);
        var terminal = EscrowFlightAuthority.finish(active, arm, EscrowFlightTables.Outcome.HANDED_OFF,
                arm.destination(), 0, "11".repeat(32), "22".repeat(32)).generation(5);
        var pruned = EscrowFlightAuthority.prune(terminal, terminal.armedFlights().get(0)).generation(6);
        for (String phase : List.of("F7", "F8_PRUNE")) {
            var expected = EscrowCodec.encode(phase.equals("F7") ? terminal : pruned);
            for (String side : List.of("BEFORE", "REPLACED")) {
                var cut = "LF_S6_" + phase + "_" + side;
                var point = side.equals("BEFORE") ? "K8" : "K9";
                assertTrue(EscrowLoaderS6CleanupProbe.selected(cut));
                assertTrue(EscrowLoaderS6CleanupProbe.ledgerBoundary(cut, point, expected));
                assertFalse(EscrowLoaderS6CleanupProbe.ledgerBoundary(cut, point.equals("K8") ? "K9" : "K8", expected));
                assertFalse(EscrowLoaderS6CleanupProbe.ledgerBoundary(cut, point, EscrowCodec.encode(active)));
                assertFalse(EscrowLoaderS6CleanupProbe.ledgerBoundary(cut + "_OTHER", point, expected));
                var changed = expected.copy(); changed.putLong("writer_generation", 7);
                assertFalse(EscrowLoaderS6CleanupProbe.ledgerBoundary(cut, point, changed));
            }
        }
    }

    @Test void acknowledgementsAreExactAndJournalReplacementIsNotALedgerBoundary() {
        for (String phase : List.of("F7", "F8_JOURNAL", "F8_PRUNE")) {
            var cut = "LF_S6_" + phase + "_ACK";
            assertTrue(EscrowLoaderS6CleanupProbe.acknowledgement(cut, phase + "_ACK"));
            assertFalse(EscrowLoaderS6CleanupProbe.acknowledgement(cut, phase + "_REQUESTED"));
        }
        assertFalse(EscrowLoaderS6CleanupProbe.selected(null));
        assertFalse(EscrowLoaderS6CleanupProbe.selected("LF_S6_REFILL"));
    }
}
