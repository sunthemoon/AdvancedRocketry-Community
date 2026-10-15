package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowAdmissionCaptureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void nativeAdmissionCountsOnlyTheSectionWhoseDigestWasCapturedWithTheBinding() {
        var retired = new EscrowPrefix.Marker(EscrowTestFixtures.id(90), 90, "DESTINATION", 1);
        var root = new EscrowLedger(EscrowTestFixtures.id(500), 99, 0, List.of(), List.of(), List.of());
        var section = new RocketEscrowSection(root.id(), 1, List.of(retired));
        var op = EscrowTestFixtures.nativeModel(EscrowSettlementCapacityTest.operation(100, 99, 1, true), section);
        assertDoesNotThrow(() -> new EscrowNativeBinding.Admission(op, section));
        assertThrows(IllegalArgumentException.class, () -> new EscrowNativeBinding.Admission(op, RocketEscrowSection.neutral()));
        var changed = new RocketEscrowSection(root.id(), 1,
                List.of(new EscrowPrefix.Marker(EscrowTestFixtures.id(91), 90, "DESTINATION", 1)));
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(directory.resolve("unused.dat"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            for (var stale : List.of(RocketEscrowSection.neutral(), new RocketEscrowSection(root.id(), 1, List.of()), changed)) {
                var failure = assertThrows(IllegalArgumentException.class, () -> coordinator.prepareTest(op, stale, ignored -> fail()));
                assertEquals("Native admission marker section differs from the captured before wrapper", failure.getMessage());
                assertEquals(100, coordinator.nextSequence()); assertFalse(data.isDirty());
                assertEquals(0, coordinator.admissionAuthority().pendingCount()); assertTrue(writer.capturedOperations().isEmpty());
            }
            assertDoesNotThrow(() -> coordinator.prepareTest(op, section, ignored -> { }));
            assertEquals(101, coordinator.nextSequence()); assertEquals(1, coordinator.admissionAuthority().pendingCount());
        }
    }

    @Test void fixtureCaptureKeepsRetiredMarkersAndProjectsOnlyAContiguousModeledPrefix() {
        var retired = new EscrowPrefix.Marker(EscrowTestFixtures.id(90), 90, "DESTINATION", 1);
        var section = new RocketEscrowSection(EscrowTestFixtures.id(500), 1, List.of(retired));
        var first = EscrowSettlementCapacityTest.operation(100, 99, 1, true);
        var second = EscrowSettlementCapacityTest.operation(101, 99, 2, true);
        var root = EscrowTestFixtures.ledger(first);
        assertSame(section, EscrowNativeBinding.captureSection(first, section, root));
        var projected = EscrowNativeBinding.captureSection(second, section, root);
        assertEquals(List.of(retired, new EscrowPrefix.Link(first, first.destination(), "DESTINATION").marker()), projected.markers());
        assertEquals(2, projected.epoch()); assertEquals(root.id(), projected.ledger());
        assertThrows(IllegalArgumentException.class, () -> EscrowNativeBinding.captureSection(second, section, EscrowTestFixtures.ledger()));
        assertThrows(IllegalArgumentException.class, () -> EscrowNativeBinding.captureSection(first, projected, root));
    }
}
