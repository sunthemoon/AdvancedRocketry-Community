package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowFlightWritesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void eightIndependentRequestsShareOneGenerationAndOnlyExactAcknowledgementPublishesSerials() throws Exception {
        var data = ResourceEscrowSavedData.create(); var before = data.ledger();
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()); var moves = new AtomicInteger();
        var results = new ArrayList<Boolean>();
        try (var writer = new EscrowLedgerWriter(file, 0, (staged, target) -> {
            moves.incrementAndGet(); CheckedSavedDataFile.atomicMove(staged, target);
        }); var coordinator = new EscrowCoordinator(data, writer)) {
            for (int i = 1; i <= 8; i++) {
                var launch = EscrowFlightAuthorityTest.launch(before, i);
                assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> assertDoesNotThrow(() -> EscrowFlightAuthority.arm(root, launch, generation)),
                        () -> true, results::add));
                assertTrue(coordinator.logicalLocked(launch.logical()));
            }
            assertEquals(before, coordinator.ledger()); assertTrue(results.isEmpty());
            pump(coordinator, 0, () -> results.size() == 8);
            assertEquals(java.util.Collections.nCopies(8, true), results);
            assertEquals(1, moves.get()); assertEquals(1, coordinator.ledger().writerGeneration());
            assertEquals(8, coordinator.ledger().issuedLaunchSerial());
            assertEquals(java.util.stream.LongStream.rangeClosed(1, 8).boxed().toList(),
                    coordinator.ledger().armedFlights().stream().map(EscrowFlightTables.Armed::serial).toList());
            assertTrue(coordinator.ledger().armedFlights().stream().allMatch(value -> value.generation() == 1));
            assertEquals(coordinator.ledger(), EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
        }
    }

    @Test void sourceOrJournalProofInvalidatedAtReplacementCannotPublishOrAcknowledgeAnArm() throws Exception {
        var data = ResourceEscrowSavedData.create(); var before = data.ledger(); var checks = new AtomicInteger();
        var results = new ArrayList<Boolean>(); var launch = EscrowFlightAuthorityTest.launch(before, 1);
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        try (var writer = new EscrowLedgerWriter(file, 0); var coordinator = new EscrowCoordinator(data, writer)) {
            assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> EscrowFlightAuthority.arm(root, launch, generation),
                    () -> checks.incrementAndGet() == 1, results::add));
            pump(coordinator, 0, () -> !results.isEmpty());
            assertEquals(java.util.List.of(false), results); assertEquals(before, coordinator.ledger());
            assertEquals(2, checks.get());
            assertFalse(java.nio.file.Files.exists(file)); assertFalse(coordinator.logicalLocked(launch.logical()));
        }
    }

    @Test void unknownWriteRetainsTheExactCandidateLockAndSerialUntilItsResolution() throws Exception {
        var data = ResourceEscrowSavedData.create(); var before = data.ledger(); var fail = new AtomicBoolean(true);
        var results = new ArrayList<Boolean>(); var launch = EscrowFlightAuthorityTest.launch(before, 1);
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        try (var writer = new EscrowLedgerWriter(file, 0, (staged, target) -> {
            if (fail.get()) { throw new java.io.IOException("injected unresolved publication"); }
            CheckedSavedDataFile.atomicMove(staged, target);
        }); var coordinator = new EscrowCoordinator(data, writer)) {
            assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> assertDoesNotThrow(() -> EscrowFlightAuthority.arm(root, launch, generation)), () -> true, results::add));
            coordinator.tick(0);
            // Pump the same server tick: the writer cannot reach its 200-tick retry while we observe UNKNOWN.
            for (int i = 0; i < 200 && writer.unknown() == false; i++) { coordinator.tick(1); Thread.sleep(2); }
            assertTrue(writer.unknown()); assertTrue(results.isEmpty()); assertEquals(before, coordinator.ledger());
            assertTrue(coordinator.logicalLocked(launch.logical()));
            assertFalse(coordinator.flightWrite(launch.logical(), (root, generation) -> root, () -> true, results::add));
            fail.set(false); pump(coordinator, 201, () -> !results.isEmpty());
            assertEquals(java.util.List.of(true), results); assertEquals(1, coordinator.ledger().issuedLaunchSerial());
            assertEquals(1, coordinator.ledger().armedFlights().get(0).generation());
            assertEquals(1, coordinator.ledger().writerGeneration());
            var payload = CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow();
            assertEquals(coordinator.ledger(), ResourceEscrowSavedData.load(payload).ledger());
            try (var restarted = new EscrowLedgerWriter(file, 1)) { assertDoesNotThrow(() -> restarted.baseline(EscrowCodec.decode(payload))); }
        }
    }

    @Test void queueBoundsAndFailedCaptureDoNotAllowOneLogicalRequestToOverwriteAnother() throws Exception {
        var data = ResourceEscrowSavedData.create(); var before = data.ledger(); var results = new ArrayList<Boolean>();
        try (var writer = new EscrowLedgerWriter(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            var valid = new AtomicBoolean(true);
            for (int i = 1; i <= 64; i++) {
                var launch = EscrowFlightAuthorityTest.launch(before, i);
                assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> EscrowFlightAuthority.arm(root, launch, generation),
                        valid::get, results::add));
            }
            assertFalse(coordinator.flightWrite(EscrowTestFixtures.id(900), (root, generation) -> root, () -> true, results::add));
            valid.set(false); coordinator.tick(0);
            assertEquals(64, results.size()); assertTrue(results.stream().noneMatch(Boolean::booleanValue));
            assertEquals(before, coordinator.ledger()); assertTrue(writer.idle());
            var first = EscrowFlightAuthorityTest.launch(before, 1); var other = EscrowFlightAuthorityTest.launch(before, 2);
            assertTrue(coordinator.flightWrite(first.logical(), (root, generation) -> EscrowFlightAuthority.arm(root, other, generation), () -> true, results::add));
            coordinator.tick(1); assertEquals(65, results.size()); assertFalse(results.get(64));
            assertEquals(before, coordinator.ledger());
        }
    }

    @Test void unknownCandidateCannotRestartBeforeItsExact200TickGate() throws Exception {
        var data = ResourceEscrowSavedData.create(); var initial = data.ledger();
        var launch = EscrowFlightAuthorityTest.launch(initial, 1); var attempts = new AtomicInteger();
        var replies = new ArrayList<Boolean>(); var io = new AwaitedLedgerIo();
        try (var writer = io.writer(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0, (staged, target) -> {
            if (attempts.incrementAndGet() == 1) { throw new java.io.IOException("injected unknown replacement"); }
            CheckedSavedDataFile.atomicMove(staged, target);
        }); var coordinator = new EscrowCoordinator(data, writer)) {
            assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> EscrowFlightAuthority.arm(root, launch, generation),
                    () -> true, replies::add));
            coordinator.tick(0); io.awaitWrites(); coordinator.tick(1);
            assertTrue(writer.unknown()); assertEquals(1, attempts.get());
            for (long tick = 2; tick < 201; tick++) { coordinator.tick(tick); }
            assertEquals(1, attempts.get()); assertTrue(replies.isEmpty()); assertSame(initial, coordinator.ledger());
            assertTrue(coordinator.logicalLocked(launch.logical()));
            coordinator.tick(201); io.awaitWrites(); coordinator.tick(202);
            assertEquals(2, attempts.get()); assertEquals(java.util.List.of(true), replies);
            assertEquals(1, coordinator.ledger().writerGeneration()); assertEquals(1, coordinator.ledger().issuedLaunchSerial());
        }
    }

    @Test void oneRefusedRequestDoesNotReceiveAnotherRequestsCoalescedAcknowledgement() throws Exception {
        var data = ResourceEscrowSavedData.create(); var root = data.ledger(); var moves = new AtomicInteger();
        var refused = new ArrayList<Boolean>(); var accepted = new ArrayList<Boolean>(); var io = new AwaitedLedgerIo();
        try (var writer = io.writer(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0, (staged, target) -> {
            moves.incrementAndGet(); CheckedSavedDataFile.atomicMove(staged, target);
        }); var coordinator = new EscrowCoordinator(data, writer)) {
            var bad = EscrowFlightAuthorityTest.launch(root, 1); var good = EscrowFlightAuthorityTest.launch(root, 2);
            assertTrue(coordinator.flightWrite(bad.logical(), (candidate, generation) -> {
                fail("Invalidated request must not be captured"); return candidate;
            }, () -> false, refused::add));
            assertTrue(coordinator.flightWrite(good.logical(), (candidate, generation) -> EscrowFlightAuthority.arm(candidate, good, generation),
                    () -> true, accepted::add));
            coordinator.tick(0); io.awaitWrites(); coordinator.tick(1);
            assertEquals(java.util.List.of(false), refused); assertEquals(java.util.List.of(true), accepted);
            assertEquals(1, moves.get()); assertEquals(1, coordinator.ledger().writerGeneration());
            assertEquals(java.util.List.of(good.logical()), coordinator.ledger().armedFlights().stream().map(EscrowFlightTables.Armed::logical).toList());
        }
    }

    private static void pump(EscrowCoordinator coordinator, long start, java.util.function.BooleanSupplier done) throws InterruptedException {
        for (long tick = start; tick < start + 1_000 && !done.getAsBoolean(); tick++) { coordinator.tick(tick); Thread.sleep(2); }
        assertTrue(done.getAsBoolean(), "Expected exact writer completion within the bounded test pump");
    }
}
