package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowFlightQueueContentionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void eightFlightsWaitBehindOrdinaryAdmissionsAndCaptureTheirAcknowledgedRoot() throws Exception {
        var data = ResourceEscrowSavedData.create(); var before = data.ledger();
        var ordinary = new ArrayList<Boolean>(); var flights = new ArrayList<Boolean>();
        var operations = List.of(EscrowTestFixtures.operation(1), EscrowTestFixtures.operation(2), EscrowTestFixtures.operation(3));
        var captures = new AtomicInteger(); var io = new AwaitedLedgerIo();
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        try (var writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            operations.forEach(op -> coordinator.prepareTest(op, ordinary::add));
            assertTrue(writer.idle());
            for (int i = 1; i <= 8; i++) {
                var launch = EscrowFlightAuthorityTest.launch(before, i);
                assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> {
                    assertEquals(List.of(true, true, true), ordinary);
                    assertEquals(operations, root.records());
                    assertEquals(1, root.writerGeneration());
                    captures.incrementAndGet();
                    return EscrowFlightAuthority.arm(root, launch, generation);
                }, () -> true, flights::add), "Ordinary admissions must delay capture, not refuse the flight queue");
                assertTrue(coordinator.logicalLocked(launch.logical()));
            }
            coordinator.tick(0);
            assertEquals(0, captures.get()); assertTrue(flights.isEmpty()); assertSame(before, coordinator.ledger());
            assertThrows(IllegalArgumentException.class, () -> coordinator.prepareTest(
                    EscrowTestFixtures.operation(4, EscrowFlightAuthorityTest.launch(before, 1).logical().getLeastSignificantBits(), 0),
                    ignored -> fail("A waiting flight admitted a same-logical fill")));
            assertEquals(operations, coordinator.pendingAdmissions());
            assertTrue(coordinator.commitPreparations());
            io.awaitWrites(); coordinator.tick(1);
            assertEquals(List.of(true, true, true), ordinary);
            assertEquals(0, captures.get(), "Flight capture bypassed the ten-tick writer cadence");
            assertTrue(flights.isEmpty()); assertEquals(0, coordinator.ledger().issuedLaunchSerial());
            coordinator.tick(9); assertEquals(0, captures.get());
            coordinator.tick(10); assertEquals(8, captures.get()); assertTrue(flights.isEmpty());
            io.awaitWrites(); coordinator.tick(11);
            assertEquals(java.util.Collections.nCopies(8, true), flights);
            assertEquals(2, coordinator.ledger().writerGeneration());
            assertEquals(8, coordinator.ledger().issuedLaunchSerial());
            assertEquals(operations, coordinator.ledger().records());
            assertEquals(coordinator.ledger(), EscrowCodec.decode(CheckedSavedDataFile.readPayload(
                    file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
        }
    }

    @Test void queuedTransitionIsAcknowledgedBeforeTheWaitingFlightCapture() throws Exception {
        var operation = EscrowTestFixtures.operation(1);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowTestFixtures.ledger(operation)));
        var before = data.ledger(); var launch = EscrowFlightAuthorityTest.launch(before, 1);
        var flights = new ArrayList<Boolean>(); var captures = new AtomicInteger(); var io = new AwaitedLedgerIo();
        try (var writer = io.writer(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0, CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.transition(operation.id(), EscrowReason.OTHER_STATE);
            assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> {
                assertEquals(EscrowReason.OTHER_STATE, root.records().get(0).reason());
                captures.incrementAndGet(); return EscrowFlightAuthority.arm(root, launch, generation);
            }, () -> true, flights::add), "Pending transitions must delay capture, not refuse queue admission");
            coordinator.tick(0); assertEquals(0, captures.get());
            coordinator.persistTransitions(); io.awaitWrites(); coordinator.tick(1);
            assertTrue(flights.isEmpty()); assertEquals(0, captures.get());
            coordinator.tick(10); io.awaitWrites(); coordinator.tick(11);
            assertEquals(List.of(true), flights); assertEquals(1, captures.get());
            assertEquals(EscrowReason.OTHER_STATE, coordinator.ledger().records().get(0).reason());
            assertEquals(2, coordinator.ledger().writerGeneration());
        }
    }

    @Test void proofInvalidatedWhileWaitingRefusesOnlyThatFlightWithoutIssuingASerial() throws Exception {
        var data = ResourceEscrowSavedData.create(); var before = data.ledger(); var valid = new AtomicBoolean(true);
        var operation = EscrowTestFixtures.operation(1); var results = new ArrayList<Boolean>(); var io = new AwaitedLedgerIo();
        var launch = EscrowFlightAuthorityTest.launch(before, 1);
        try (var writer = io.writer(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0, CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.prepareTest(operation, committed -> assertTrue(committed));
            assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> {
                fail("An invalidated retained proof must not reach capture"); return root;
            }, valid::get, results::add));
            valid.set(false); coordinator.tick(0); assertTrue(results.isEmpty());
            assertTrue(coordinator.commitPreparations()); io.awaitWrites(); coordinator.tick(1); coordinator.tick(10);
            assertEquals(List.of(false), results); assertFalse(coordinator.logicalLocked(launch.logical()));
            assertTrue(coordinator.ledger().armedFlights().isEmpty());
            assertEquals(0, coordinator.ledger().issuedLaunchSerial());
            assertEquals(List.of(operation), coordinator.ledger().records());
            assertEquals(1, coordinator.ledger().writerGeneration()); assertTrue(writer.idle());
        }
    }
}
