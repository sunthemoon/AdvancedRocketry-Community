package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowSettlementCapacityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void measuredFiveMarkerSettlementFitsButSixDoesNotWithUnchangedCodecs() {
        assertEquals(5, EscrowSettlementCapacity.ADMISSION_PER_ROCKET); assertEquals(8, EscrowLimits.PER_ROCKET);
        for (boolean nativeRocket : List.of(false, true)) {
            for (int n = 1; n <= 8; n++) {
                var operations = new ArrayList<EscrowOperation>();
                for (int i = 1; i <= n; i++) { operations.add(operation(i, 99, i - 1, nativeRocket)); }
                var root = EscrowTestFixtures.ledger(operations.toArray(EscrowOperation[]::new));
                // Decode remains valid at eight; sizing uses real O1 and receipt encoders, not the admission constant.
                assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
                if (n <= 5) { assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(root)); }
                else { assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(root)); }
                System.out.println("settlement measurement native=" + nativeRocket + " markers=" + n + " fits=" + (n <= 5));
            }
        }
    }
    @Test void admissionCountsRetiredAndLiveMarkersOnceAndRefusesBeforeQueueMutation() {
        for (int retired = 0; retired <= 5; retired++) {
            var markers = new ArrayList<EscrowPrefix.Marker>();
            for (int i = 1; i <= retired; i++) { markers.add(new EscrowPrefix.Marker(EscrowTestFixtures.id(5_000 + i), i, "DESTINATION", i)); }
            var op = operation(100, 99, retired, true);
            var root = new EscrowLedger(EscrowTestFixtures.id(500), 99, 0, List.of(), List.of(), List.of());
            var observed = new RocketEscrowSection(root.id(), retired, markers);
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            try (var writer = new EscrowLedgerWriter(directory.resolve("retired-" + retired), 0);
                    var coordinator = new EscrowCoordinator(data, writer)) {
                if (retired < 5) {
                    assertDoesNotThrow(() -> coordinator.prepareTest(op, observed, ignored -> { }));
                    assertEquals(101, coordinator.nextSequence());
                    assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(root.append(List.of(op))));
                    var applied = new ArrayList<>(markers); applied.add(new EscrowPrefix.Link(op, op.destination(), "DESTINATION").marker());
                    // A live marker already in the observed wrapper is not charged twice.
                    assertDoesNotThrow(() -> EscrowSettlementCapacity.requireAdmission(root.append(List.of(op)), op,
                            new RocketEscrowSection(root.id(), observed.epoch() + 1, applied)));
                    var held = root.append(List.of(op.withReason(EscrowReason.OTHER_STATE)));
                    var observations = List.of(new EscrowSettlementIntent.Observation(op.source(),
                                    new EscrowPrefix.State(root.id(), 1, op.source().afterSignature(),
                                            List.of(new EscrowPrefix.Link(op, op.source(), "SOURCE").marker())), true, Long.MAX_VALUE),
                            new EscrowSettlementIntent.Observation(op.destination(),
                                    new EscrowPrefix.State(root.id(), observed.epoch() + 1, op.destination().afterSignature(), applied),
                                    true, Long.MAX_VALUE, EscrowSignatureKind.FUEL_VECTOR_V1, op.nativeBinding().invariant()));
                    var preview = EscrowSettlementIntent.preview(held, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                            EscrowTestFixtures.id(700), "\0".repeat(128), 0, observations);
                    var intent = preview.confirm(preview.actor(), preview.digest(), 0, observations);
                    var frozen = assertDoesNotThrow(() -> intent.freeze(held));
                    var receipt = EscrowSettlementTombstones.create(frozen.records().get(0), intent);
                    int bytes = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(EscrowReceiptCodec.encode(receipt));
                    System.out.println("retired=" + retired + " live=1 receipt_bytes=" + bytes);
                } else {
                    assertThrows(IllegalArgumentException.class, () -> coordinator.prepareTest(op, observed, ignored -> fail()));
                    assertEquals(100, coordinator.nextSequence()); assertFalse(data.isDirty());
                    assertFalse(coordinator.commitPreparations()); assertTrue(writer.capturedOperations().isEmpty());
                }
                assertEquals(root, coordinator.ledger());
            }
        }
    }
    @Test void fifthPendingFillIsAdmittedAndSixthIsRefusedWithoutChangingTheDecodeBound() {
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowTestFixtures.ledger()));
        try (var writer = new EscrowLedgerWriter(directory.resolve("queued"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            for (int n = 1; n <= 5; n++) { coordinator.prepareTest(operation(n, 99, n - 1, true), RocketEscrowSection.neutral(), ignored -> { }); }
            assertEquals(6, coordinator.nextSequence());
            assertThrows(IllegalArgumentException.class, () -> coordinator.prepareTest(operation(6, 99, 5, true), RocketEscrowSection.neutral(), ignored -> fail()));
            assertEquals(6, coordinator.nextSequence()); assertEquals(5, coordinator.admissionAuthority().pendingCount());
        }
    }
    static EscrowOperation operation(long sequence, long logical, long epoch, boolean nativeRocket) {
        var op = EscrowTestFixtures.operation(sequence, logical, epoch);
        if (!nativeRocket) { return op; }
        var old = op.destination();
        var endpoint = new EscrowEndpoint(old.kind(), old.level(), old.identity(), old.logicalRocket(), "advancedrocketrycommunity:rocket",
                old.x(), old.y(), old.z(), old.unit(), old.beforeEpoch(), old.beforeSignature(), old.afterSignature());
        return EscrowTestFixtures.nativeModel(new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(),
                op.payload(), op.payloadHash(), op.source(), endpoint, op.guard()));
    }
}
