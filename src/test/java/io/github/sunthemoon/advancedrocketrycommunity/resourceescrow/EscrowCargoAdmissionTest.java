package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowCargoAdmissionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;
    static EscrowNativeBinding.Admission capture(EscrowOperation op, int retained) { return new EscrowNativeBinding.Admission(op, wrapper(retained)); }

    @Test void bothDirectionsUseTheExistingExactL1WriterAndRefuseQueuedCapturedAndDurableMixing() throws Exception {
        for (var kind : KINDS) {
            var root = ledger(); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root)); var io = new AwaitedLedgerIo();
            var file = java.nio.file.Files.createDirectories(temp.resolve(kind.name())).resolve(
                    ManagedSavedDataType.RESOURCE_ESCROW.fileName());
            try (var writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove); var coordinator = new EscrowCoordinator(data, writer)) {
                var op = operation(kind, 1, 99); var replies = new ArrayList<Boolean>();
                assertFalse(coordinator.productionAdmissionEnabled());
                assertEquals(EscrowCoordinator.Transfer.QUEUED, coordinator.prepareTransfer(capture(op, 0), replies::add));
                for (int stage = 0; stage < 3; stage++) {
                    assertTrue(coordinator.cargoPending(op.guard().rocket()));
                    for (var otherKind : KINDS) {
                        var other = operation(otherKind, 2, 99);
                        assertEquals(EscrowCoordinator.Transfer.OTHER_TRANSFER_PENDING, coordinator.prepareTransfer(capture(other, 0), ignored -> fail()));
                    }
                    var fill = EscrowSettlementCapacityTest.operation(2, 99, 0, true);
                    assertEquals(EscrowCoordinator.Transfer.ROCKET_BUSY, coordinator.prepareTransfer(capture(fill, 0), ignored -> fail()));
                    assertEquals(EscrowCoordinator.Fill.ENDPOINT_BUSY, coordinator.prepareFill(capture(fill, 0), ignored -> fail()));
                    assertEquals(2, coordinator.nextSequence()); assertEquals(1, coordinator.admissionAuthority().pendingCount());
                    if (stage == 0) { assertTrue(coordinator.commitPreparations()); }
                    if (stage == 1) { io.tickUntil(coordinator::tick, 1, writer::idle); assertEquals(List.of(true), replies); }
                }
                assertEquals(List.of(true), replies); assertEquals(List.of(op), coordinator.ledger().records());
            }
        }
    }

    @Test void cargoCannotQueueBehindFuelAndARefusalDoesNotIssueSequenceReservationOrWrite() {
        var fill = EscrowSettlementCapacityTest.operation(1, 99, 0, true); var root = ledger(fill);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(temp.resolve("fuel"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            for (var kind : KINDS) {
                assertEquals(EscrowCoordinator.Transfer.OTHER_TRANSFER_PENDING, coordinator.prepareTransfer(capture(operation(kind, 2, 99), 0), ignored -> fail()));
            }
            assertEquals(2, coordinator.nextSequence()); assertEquals(root, coordinator.ledger()); assertFalse(data.isDirty());
            assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty()); assertEquals(1, coordinator.admissionAuthority().pendingCount());
        }
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus.ROCKET_BUSY,
                EscrowLoaderAdmission.status(EscrowCoordinator.Transfer.ROCKET_BUSY));
    }

    @Test void sameCaptureAllowsOnlyItsOwnMarkerAndRefusesRetainedMarkersBeforeSideEffects() {
        assertEquals(1, EscrowSettlementCapacity.CARGO_ADMISSION_PER_ROCKET);
        for (var kind : KINDS) {
            for (int retained = 0; retained <= 5; retained++) {
                var root = new EscrowLedger(id(500), 99, 0, List.of(), List.of(), List.of());
                var op = operation(kind, 100, 99, retained, -1, "minecraft:overworld");
                var before = wrapper(retained);
                var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
                var wrong = new RocketEscrowSection(id(500), retained + 1, List.of());
                assertThrows(IllegalArgumentException.class, () -> new EscrowNativeBinding.Admission(op, wrong));
                assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.requireAdmission(root.append(List.of(op)), op, wrong));
                if (retained == 0) {
                    assertDoesNotThrow(() -> EscrowSettlementCapacity.requireAdmission(root.append(List.of(op)), op, before));
                    assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(root.append(List.of(op)), op.id()));
                } else {
                    assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.requireAdmission(root.append(List.of(op)), op, before));
                }
                try (var writer = new EscrowLedgerWriter(temp.resolve(kind + "-" + retained), 0); var coordinator = new EscrowCoordinator(data, writer)) {
                    var result = coordinator.prepareTransfer(new EscrowNativeBinding.Admission(op, before), ignored -> { });
                    if (retained == 0) { assertEquals(EscrowCoordinator.Transfer.QUEUED, result); }
                    else {
                        // Codec fit is not retirement authority for unknown captured markers.
                        assertEquals(EscrowCoordinator.Transfer.OTHER_TRANSFER_PENDING, result);
                        assertEquals(100, coordinator.nextSequence());
                        assertEquals(0, coordinator.admissionAuthority().pendingCount()); assertFalse(data.isDirty());
                        assertFalse(coordinator.commitPreparations()); assertTrue(writer.capturedOperations().isEmpty());
                    }
                    assertEquals(root, coordinator.ledger());
                }
            }
        }
    }

    @Test void turnsBelongOnlyToBoundedLoadedRegistrationsAndEndOnUnloadCancellationOrLifecycleEnd() {
        var turns = new EscrowTransferTurns();
        turns.blocked(id(1), id(99)); assertEquals(0, turns.size());
        try (var first = turns.register(id(1), id(99)); var second = turns.register(id(2), id(99))) {
            turns.blocked(id(1), id(99)); turns.blocked(id(2), id(99));
            assertTrue(turns.allows(id(1), id(99))); assertFalse(turns.allows(id(2), id(99)));
            turns.admitted(id(2), id(99)); assertEquals(1, turns.size());
            turns.admitted(id(1), id(99)); assertEquals(0, turns.size());
            turns.blocked(id(2), id(99)); second.close(); assertTrue(turns.allows(id(1), id(99)));
            turns.blocked(id(1), id(99)); first.close(); assertEquals(0, turns.size());
        }
        for (int i = 0; i < EscrowResourceQueue.CAPACITY; i++) {
            turns.register(id(i), id(1_000 + i)); turns.blocked(id(i), id(1_000 + i));
        }
        assertEquals(EscrowResourceQueue.CAPACITY, turns.size());
        assertThrows(IllegalArgumentException.class, () -> turns.register(id(999), id(999)));
        CompletableFuture.runAsync(() -> assertThrows(IllegalArgumentException.class, turns::size)).join();
        turns.close(); assertEquals(0, turns.size()); assertFalse(turns.allows(id(0), id(1_000)));
        assertEquals(0, new EscrowTransferTurns().size(), "Restart reconstructs no RAM tokens");
    }

    @Test void aTurnDoesNotBypassAnArmedHoldAndClosesWithItsCoordinator() {
        var root = EscrowFlightAuthority.arm(ledger(), EscrowFlightAuthorityTest.launch(ledger(), 1), 1);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(temp.resolve("turn-hold"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            var op = operation(EscrowOperation.Kind.FLUID_CARGO_LOAD, 1, 201); var turns = coordinator.transferTurns();
            turns.register(op.device().identity(), op.guard().rocket()); turns.blocked(op.device().identity(), op.guard().rocket());
            assertEquals(EscrowCoordinator.Transfer.ENDPOINT_BUSY, coordinator.prepareTransfer(capture(op, 0), ignored -> fail()));
            assertEquals(1, turns.size()); assertEquals(1, coordinator.nextSequence()); assertFalse(data.isDirty());
            coordinator.abort(); assertEquals(0, turns.size());
        }
    }

    @Test void onlyTheRegisteredNextDeviceGetsTheIdleTurnAndAdmissionConsumesIt() {
        var root = ledger(); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(temp.resolve("next-turn"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            var preferred = operation(EscrowOperation.Kind.FLUID_CARGO_LOAD, 1, 99); var old = preferred.device();
            var otherDevice = new EscrowEndpoint(old.kind(), old.level(), id(42), null, old.type(), old.x(), old.y(), old.z(),
                    old.unit(), old.beforeEpoch(), old.beforeSignature(), old.afterSignature());
            var other = nativeModel(new EscrowOperation(preferred.id(), 1, preferred.owner(), 1, preferred.phase(), preferred.reason(),
                    preferred.payload(), preferred.payloadHash(), otherDevice, preferred.rocket(), preferred.guard(), preferred.kind(), preferred.settlement()), wrapper(0));
            var turns = coordinator.transferTurns(); turns.register(preferred.device().identity(), preferred.guard().rocket());
            turns.blocked(preferred.device().identity(), preferred.guard().rocket());
            assertEquals(EscrowCoordinator.Transfer.OTHER_TRANSFER_PENDING, coordinator.prepareTransfer(capture(other, 0), ignored -> fail()));
            assertEquals(1, coordinator.nextSequence()); assertFalse(data.isDirty()); assertEquals(0, coordinator.admissionAuthority().pendingCount());
            assertEquals(EscrowCoordinator.Transfer.QUEUED, coordinator.prepareTransfer(capture(preferred, 0), ignored -> { }));
            assertEquals(0, turns.size()); assertEquals(2, coordinator.nextSequence());
        }
    }

    @Test void cargoCannotSubstituteAnotherEpochEvenWithASelfConsistentWrapperDigest() {
        var root = ledger(); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(temp.resolve("wrong-epoch"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            for (var kind : KINDS) {
                var wrapper = new RocketEscrowSection(root.id(), 1, List.of());
                var op = nativeModel(operation(kind, 1, 99), wrapper);
                assertEquals(EscrowCoordinator.Transfer.STALE, coordinator.prepareTransfer(new EscrowNativeBinding.Admission(op, wrapper), ignored -> fail()));
            }
            assertEquals(1, coordinator.nextSequence()); assertEquals(0, coordinator.admissionAuthority().pendingCount());
            assertFalse(data.isDirty()); assertTrue(writer.capturedOperations().isEmpty());
        }
    }

    @Test void generalizedResultsPreserveTheClosedFuelContractAndMapCargoSchedulingToRocketBusy() {
        for (var fill : EscrowCoordinator.Fill.values()) {
            var transfer = EscrowCoordinator.Transfer.of(fill);
            assertEquals(fill, transfer.fill());
            assertEquals(EscrowLoaderAdmission.status(fill), EscrowLoaderAdmission.status(transfer));
        }
        for (var transfer : List.of(EscrowCoordinator.Transfer.ROCKET_BUSY, EscrowCoordinator.Transfer.OTHER_TRANSFER_PENDING,
                EscrowCoordinator.Transfer.RECEIPT_BACKED)) {
            assertEquals(EscrowCoordinator.Fill.ENDPOINT_BUSY, transfer.fill());
            assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus.ROCKET_BUSY,
                    EscrowLoaderAdmission.status(transfer));
        }
    }
}
