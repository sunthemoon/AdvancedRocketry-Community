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

    @Test void measuredEightMarkerCodecBoundaryDoesNotRaiseTheFiveMarkerAdmissionLimit() {
        assertEquals(5, EscrowSettlementCapacity.ADMISSION_PER_ROCKET); assertEquals(8, EscrowLimits.PER_ROCKET);
        for (boolean nativeRocket : List.of(false, true)) {
            for (int n = 1; n <= 8; n++) {
                var operations = new ArrayList<EscrowOperation>();
                for (int i = 1; i <= n; i++) { operations.add(operation(i, 99, i - 1, nativeRocket)); }
                // Explicit activation-off root-2 eight-marker codec boundary.
                var root = EscrowTestFixtures.root2(EscrowTestFixtures.ledger(operations.toArray(EscrowOperation[]::new)));
                // Decode remains valid at eight; sizing uses real O1 and receipt encoders, not the admission constant.
                assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
                assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(root, operations.get(0).id()));
                var observed = RocketEscrowSection.neutral();
                var candidate = operations.get(operations.size() - 1);
                if (n <= 5) { assertDoesNotThrow(() -> EscrowSettlementCapacity.requireAdmission(root, candidate, observed)); }
                else { assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.requireAdmission(root, candidate, observed)); }
                System.out.println("settlement measurement native=" + nativeRocket + " markers=" + n + " fits=true admitted=" + (n <= 5));
            }
            var tooMany = new ArrayList<EscrowOperation>();
            for (int i = 1; i <= 9; i++) { tooMany.add(operation(i, 99, i - 1, nativeRocket)); }
            assertThrows(IllegalArgumentException.class, () -> EscrowTestFixtures.ledger(tooMany.toArray(EscrowOperation[]::new)));
        }
    }
    @Test void admissionCountsRetiredAndLiveMarkersOnceAndRefusesBeforeQueueMutation() {
        for (int retired = 0; retired <= 5; retired++) {
            var markers = new ArrayList<EscrowPrefix.Marker>();
            for (int i = 1; i <= retired; i++) { markers.add(new EscrowPrefix.Marker(EscrowTestFixtures.id(5_000 + i), i, "DESTINATION", i)); }
            var root = new EscrowLedger(EscrowTestFixtures.id(500), 99, 0, List.of(), List.of(), List.of());
            var observed = new RocketEscrowSection(root.id(), retired, markers);
            var op = EscrowTestFixtures.nativeModel(capturedOperation(100, 99, retired, true), observed);
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            try (var writer = new EscrowLedgerWriter(directory.resolve("retired-" + retired), 0);
                    var coordinator = new EscrowCoordinator(data, writer)) {
                if (retired < 5) {
                    assertDoesNotThrow(() -> EscrowTestFixtures.prepareCapturedModel(coordinator, op, observed, ignored -> { }));
                    assertEquals(101, coordinator.nextSequence());
                    assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(EscrowTestFixtures.appendModel(root, List.of(op))));
                    var applied = new ArrayList<>(markers); applied.add(new EscrowPrefix.Link(op, op.destination(), "DESTINATION").marker());
                    // A live marker already in the observed wrapper is not charged twice.
                    var appliedSection = new RocketEscrowSection(root.id(), observed.epoch() + 1, applied);
                    var appliedCapture = EscrowTestFixtures.nativeModel(op, appliedSection);
                    assertDoesNotThrow(() -> EscrowSettlementCapacity.requireAdmission(EscrowTestFixtures.appendModel(root, List.of(appliedCapture)), appliedCapture, appliedSection));
                    var held = EscrowTestFixtures.appendModel(root, List.of(op.withReason(EscrowReason.OTHER_STATE)));
                    var observations = List.of(new EscrowSettlementIntent.Observation(op.source(),
                                    new EscrowPrefix.State(root.id(), 1, op.source().afterSignature(),
                                            List.of(new EscrowPrefix.Link(op, op.source(), "SOURCE").marker())), true, Long.MAX_VALUE),
                            new EscrowSettlementIntent.Observation(op.destination(),
                                    new EscrowPrefix.State(root.id(), observed.epoch() + 1, op.destination().afterSignature(), applied),
                                    true, Long.MAX_VALUE, EscrowSignatureKind.FUEL_VECTOR_V1, op.nativeBinding().invariant()));
                    var preview = EscrowTestFixtures.previewModel(held, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                            EscrowTestFixtures.id(700), "~".repeat(16), 0, observations);
                    var intent = preview.confirm(preview.actor(), preview.digest(), 0, observations);
                    var frozen = assertDoesNotThrow(() -> EscrowTestFixtures.freezeModel(intent, held));
                    var receipt = EscrowSettlementTombstones.create(frozen.records().get(0), intent, frozen.fluidAccounts().get(0));
                    int bytes = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(EscrowReceiptCodec.encode(receipt));
                    System.out.println("retired=" + retired + " live=1 receipt_bytes=" + bytes);
                } else {
                    assertThrows(IllegalArgumentException.class, () -> EscrowTestFixtures.prepareCapturedModel(coordinator, op, observed, ignored -> fail()));
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
            for (int n = 1; n <= 5; n++) { EscrowTestFixtures.prepareCapturedModel(coordinator, capturedOperation(n, 99, n - 1, true), RocketEscrowSection.neutral(), ignored -> { }); }
            assertEquals(6, coordinator.nextSequence());
            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> EscrowTestFixtures.prepareCapturedModel(coordinator, capturedOperation(6, 99, 5, true), RocketEscrowSection.neutral(), ignored -> fail()));
            assertEquals("Rocket settlement admission limit exceeded", refusal.getMessage());
            assertEquals(6, coordinator.nextSequence()); assertEquals(5, coordinator.admissionAuthority().pendingCount());
        }
    }
    private static EscrowOperation capturedOperation(long sequence, long logical, long epoch, boolean nativeRocket) {
        return EscrowTestFixtures.accountedModel(operation(sequence, logical, epoch, nativeRocket));
    }
    static EscrowOperation operation(long sequence, long logical, long epoch, boolean nativeRocket) {
        var op = EscrowTestFixtures.operation(sequence, logical, epoch);
        if (!nativeRocket) { return op; }
        var old = op.destination();
        var endpoint = new EscrowEndpoint(old.kind(), old.level(), old.identity(), old.logicalRocket(), "advancedrocketrycommunity:rocket",
                old.x(), old.y(), old.z(), old.unit(), old.beforeEpoch(), old.beforeSignature(), old.afterSignature());
        return EscrowTestFixtures.nativeModel(new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(),
                op.payload(), op.payloadHash(), op.source(), endpoint, op.guard()), RocketEscrowSection.neutral());
    }
    @Test void unrelatedOfflineRepairDoesNotPreventAdmissionAndItsGrowthRemainsReserved() {
        for (var reason : List.of(EscrowReason.RELOCATED_ENDPOINT, EscrowReason.FLOOR_CONFLICT)) {
            var repair = capturedOperation(1, 1, 0, true).withReason(reason);
            var root = EscrowTestFixtures.admissionLedger(repair); var candidate = capturedOperation(2, 2, 0, true);
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(EscrowTestFixtures.appendModel(root, List.of(candidate)), candidate.id()));
            var connected = capturedOperation(2, 1, 1, true);
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(EscrowTestFixtures.appendModel(root, List.of(connected)), connected.id()));
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            try (var writer = new EscrowLedgerWriter(directory.resolve(reason.name()), 0); var coordinator = new EscrowCoordinator(data, writer)) {
                assertDoesNotThrow(() -> EscrowTestFixtures.prepareCapturedModel(coordinator, candidate, RocketEscrowSection.neutral(), ignored -> { }));
                assertEquals(3, coordinator.nextSequence()); assertEquals(List.of(repair), coordinator.ledger().records());
            }
        }
        var repairs = new ArrayList<EscrowOperation>();
        for (int i = 1; i <= 64; i++) { repairs.add(capturedOperation(i, i, 0, false).withReason(EscrowReason.RELOCATED_ENDPOINT)); }
        var full = EscrowTestFixtures.admissionLedger(repairs.toArray(EscrowOperation[]::new));
        // Sixty-four individually decodable repairs still consume a worst-case reservation, never zero bytes.
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(full));
    }
    @Test void previewIneligibleOldComponentKeepsItsFixedReservationWithoutBlockingOtherWrites() {
        var root = EscrowSettlementCompatibilityTest.duplicateCandidates();
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(root, root.records().get(0).id()));
        assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(root));
        var unrelated = operation(3, 3, 0, false).withReason(EscrowReason.OTHER_STATE);
        var whole = root.append(List.of(unrelated));
        var local = EscrowTestFixtures.ledger(unrelated);
        var observations = EscrowSettlementIntentTest.observations(local, false);
        var preview = EscrowSettlementIntent.preview(whole, unrelated.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(700), "operator", 10, observations);
        var intent = preview.confirm(preview.actor(), preview.digest(), 11, observations);
        var o1 = assertDoesNotThrow(() -> intent.freeze(whole));
        var o3 = EscrowSettlementTombstones.install(o1, intent);
        assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(o3));
        var o4 = EscrowSettlementTombstones.release(o3, intent);
        assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(o4));
        assertEquals(root.records(), o4.records());
        var many = new ArrayList<>(root.records());
        for (int i = 3; i <= 64; i++) { many.add(operation(i, i, 0, false).withReason(EscrowReason.RELOCATED_ENDPOINT)); }
        var full = EscrowTestFixtures.ledger(many.toArray(EscrowOperation[]::new));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(full));
    }
}
