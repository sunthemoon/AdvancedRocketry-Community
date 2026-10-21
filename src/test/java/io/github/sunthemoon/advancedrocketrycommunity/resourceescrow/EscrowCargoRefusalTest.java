package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowCargoRefusalTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;

    @Test void candidateComponentSizeIsNotRootExhaustionAndHasNoSideEffects() {
        for (var kind : EscrowCargoFixtures.KINDS) {
            var root = ledger(); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            var op = EscrowCargoFixtures.operation(kind, 1, 99, 0, 7900, "minecraft:overworld");
            try (var writer = new EscrowLedgerWriter(temp.resolve(kind.name()), 0);
                    var coordinator = new EscrowCoordinator(data, writer)) {
                assertEquals("CARGO_TOO_LARGE", coordinator.prepareTransfer(
                        new EscrowNativeBinding.Admission(op, RocketEscrowSection.neutral()), ignored -> fail()).name());
                assertEquals(1, coordinator.nextSequence()); assertEquals(0, coordinator.admissionAuthority().pendingCount());
                assertEquals(root, coordinator.ledger()); assertFalse(data.isDirty());
                assertFalse(coordinator.commitPreparations()); assertTrue(writer.capturedOperations().isEmpty());
                assertTrue(writer.idle());
            }
        }
    }

    @Test void duplicateAppendIsStaleRatherThanAnOversizedCandidate() {
        var c = EscrowCargoEligibilityTest.sample(true, 1000, -1);
        var captured = EscrowCargoEligibility.check(ledger(), List.of(), c.op(), c.before(), c.after(),
                c.deviceBefore(), c.deviceAfter(), 64_000).capture();
        var root = ledger(captured.operation()); var saved = EscrowCodec.encode(root);
        var before = c.before().copy(); var device = c.deviceBefore().copy();
        var result = EscrowCargoEligibility.check(root, List.of(), c.op(), c.before(), c.after(),
                c.deviceBefore(), c.deviceAfter(), 64_000);
        assertEquals("INVALID_DATA", result.status().name()); assertNull(result.capture());
        assertEquals(EscrowCoordinator.Transfer.STALE, result.refusal());
        assertEquals(saved, EscrowCodec.encode(root)); assertEquals(before, c.before()); assertEquals(device, c.deviceBefore());
    }

    @Test void conflictingSequenceIsStaleBeforeAnyReservationOrWrite() {
        for (var kind : EscrowCargoFixtures.KINDS) {
            var root = ledger(EscrowCargoFixtures.operation(kind, 1, 99));
            var other = EscrowCargoFixtures.operation(kind, 2, 100);
            var op = new EscrowOperation(other.id(), 1, other.owner(), other.createdTick(), other.phase(), other.reason(),
                    other.payload(), other.payloadHash(), other.source(), other.destination(), other.guard(), other.kind(),
                    other.settlement(), other.nativeBinding());
            var capture = new EscrowNativeBinding.Admission(op, RocketEscrowSection.neutral());
            assertEquals(EscrowCoordinator.Transfer.STALE, EscrowCargoRefusals.capacity(root, List.of(), capture).orElseThrow());
            var saved = EscrowCodec.encode(root); var data = ResourceEscrowSavedData.load(saved);
            try (var writer = new EscrowLedgerWriter(temp.resolve("sequence-" + kind), 0);
                    var coordinator = new EscrowCoordinator(data, writer)) {
                assertEquals(EscrowCoordinator.Transfer.STALE, coordinator.prepareTransfer(capture, ignored -> fail()));
                assertEquals(2, coordinator.nextSequence()); assertEquals(1, coordinator.admissionAuthority().pendingCount());
                assertEquals(saved, EscrowCodec.encode(coordinator.ledger())); assertFalse(data.isDirty());
                assertFalse(coordinator.commitPreparations()); assertTrue(writer.capturedOperations().isEmpty());
            }
        }
    }

    @Test void markerWrapperAndRootLimitsAreDistinctAndDoNotMutateTheirInputs() {
        var root = ledger();
        for (var kind : EscrowCargoFixtures.KINDS) {
            var op = EscrowCargoFixtures.operation(kind, 1, 99, 1, -1, "minecraft:overworld");
            var capture = new EscrowNativeBinding.Admission(op, EscrowCargoFixtures.wrapper(1));
            assertEquals(EscrowCoordinator.Transfer.ROCKET_LIMIT, EscrowCargoRefusals.capacity(root, List.of(), capture).orElseThrow());
            assertThrows(EscrowSettlementCapacity.MarkerLimit.class,
                    () -> EscrowSettlementCapacity.requireAdmission(root.append(List.of(op)), op, capture.before()));
            assertThrows(EscrowSettlementCapacity.StaleCapture.class,
                    () -> EscrowSettlementCapacity.requireAdmission(root.append(List.of(op)), op, RocketEscrowSection.neutral()));
            var foreign = new RocketEscrowSection(id(600), 1, capture.before().markers());
            var foreignOp = nativeModel(op, foreign);
            assertEquals(EscrowCoordinator.Transfer.STALE, EscrowCargoRefusals.capacity(root, List.of(),
                    new EscrowNativeBinding.Admission(foreignOp, foreign)).orElseThrow());
            assertEquals(0, root.issuedThrough()); assertTrue(root.records().isEmpty());
        }
        var records = new java.util.ArrayList<EscrowOperation>();
        for (int i = 1; i <= 63; i++) {
            records.add(EscrowSettlementCapacityTest.operation(i, i, 0, true));
        }
        var full = ledger(records.toArray(EscrowOperation[]::new)); var before = EscrowCodec.encode(full);
        var op = EscrowCargoFixtures.operation(EscrowOperation.Kind.FLUID_CARGO_LOAD, 64, 999);
        var data = ResourceEscrowSavedData.load(before);
        try (var writer = new EscrowLedgerWriter(temp.resolve("full"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            assertEquals(EscrowCoordinator.Transfer.ESCROW_FULL, coordinator.prepareTransfer(
                    new EscrowNativeBinding.Admission(op, RocketEscrowSection.neutral()), ignored -> fail()));
            assertEquals(64, coordinator.nextSequence()); assertEquals(63, coordinator.admissionAuthority().pendingCount());
            assertEquals(before, EscrowCodec.encode(coordinator.ledger())); assertFalse(data.isDirty());
            assertFalse(coordinator.commitPreparations()); assertTrue(writer.capturedOperations().isEmpty());
        }
    }

    @Test void stagedRefusalReachesFinalAdmissionWithoutLeakingATicketOrReservation() {
        for (boolean tooLarge : List.of(false, true)) {
            var c = EscrowCargoEligibilityTest.sample(true, 1000, tooLarge ? 7900 : -1);
            var base = ledger();
            var complete = tooLarge ? null : EscrowCargoEligibility.check(base, List.of(), c.op(), c.before(), c.after(),
                    c.deviceBefore(), c.deviceAfter(), 64_000).capture();
            var root = tooLarge ? base : ledger(complete.operation());
            var session = new io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.CargoTicketTestFixture(c.op().guard().rocket(),
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec
                            .decode(c.before().getCompound("snapshot")).snapshot().orElseThrow(),
                    EscrowCargoPayload.decode(c.op().payload()), true);
            long tick = 1;
            while (!session.advance(tick++)) { assertTrue(tick < 100); }
            var capture = new EscrowCargoPreparedCapture(session.ticket, c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter(), 64_000);
            while (true) {
                session.at(tick++); if (capture.advance(root, List.of())) { break; } assertTrue(tick < 100);
            }
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            try (var writer = new EscrowLedgerWriter(temp.resolve("staged-" + tooLarge), 0);
                    var coordinator = new EscrowCoordinator(data, writer)) {
                session.at(tick);
                assertEquals(tooLarge ? EscrowCoordinator.Transfer.CARGO_TOO_LARGE : EscrowCoordinator.Transfer.STALE,
                        coordinator.prepareCargo(capture, () -> true, ignored -> fail()));
                assertEquals(root.issuedThrough() + 1, coordinator.nextSequence());
                assertEquals(root.records().size(), coordinator.admissionAuthority().pendingCount());
                assertFalse(data.isDirty()); assertFalse(coordinator.commitPreparations());
                assertTrue(writer.capturedOperations().isEmpty());
                assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparation.State.DISCARDED,
                        session.cursor().state());
            }
        }
    }

    @Test void everyTransferOutcomeMapsToAnExistingDeviceStatusAndFillRemainsClosed() {
        for (var result : EscrowCoordinator.Transfer.values()) { assertNotNull(EscrowCargoRefusals.status(result)); }
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus.CARGO_TOO_LARGE,
                EscrowCargoRefusals.status(EscrowCoordinator.Transfer.CARGO_TOO_LARGE));
        assertEquals(EscrowCoordinator.Fill.ESCROW_FULL, EscrowCoordinator.Transfer.CARGO_TOO_LARGE.fill());
        assertEquals(10, EscrowCoordinator.Fill.values().length);
    }
}
