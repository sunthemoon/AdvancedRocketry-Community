package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W145's real checked writer consumes the retained source proof, not an admission-only pin. */
class EscrowPinnedFlightQueueTest {
    private static final String LEVEL = "minecraft:overworld";
    private static final ChunkPos SLOT = new ChunkPos(5, 7);
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void ordinaryLedgerAdmissionsDrainWhileSameSlotCapturesWaitThroughTheArmAcknowledgement() throws Exception {
        var data = ResourceEscrowSavedData.create(); var before = data.ledger();
        var admissions = new ArrayList<Boolean>(); var flights = new ArrayList<Boolean>(); var captures = new AtomicInteger();
        var monitor = new EscrowSaveMonitor(); var proof = source(monitor); var io = new AwaitedLedgerIo();
        var operation = EscrowTestFixtures.operation(1); var launch = EscrowFlightAuthorityTest.launch(before, 1);
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        try (var barrier = new EscrowBarrier(); var writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.prepareTest(operation, admissions::add);
            assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> {
                assertEquals(List.of(true), admissions); assertEquals(List.of(operation), root.records());
                captures.incrementAndGet(); return EscrowFlightAuthority.arm(root, launch, generation);
            }, proof::valid, committed -> { assertTrue(proof.valid()); flights.add(committed); proof.release(); }));
            var store = new EscrowChunkStore(null, monitor);
            for (int tick = 0; tick < 100; tick++) { refused(barrier, store, tick); coordinator.tick(tick); }
            assertEquals(0, captures.get()); assertTrue(flights.isEmpty()); assertTrue(admissions.isEmpty());
            coordinator.tick(100); assertTrue(coordinator.commitPreparations()); io.awaitWrites(); coordinator.tick(100);
            for (int tick = 101; tick < 110; tick++) { refused(barrier, store, tick); coordinator.tick(tick); }
            assertEquals(0, captures.get(), "Ordinary completion bypassed the writer cadence");
            coordinator.tick(110); assertEquals(1, captures.get());
            refused(barrier, store, 110); assertTrue(flights.isEmpty());
            io.awaitWrites(); refused(barrier, store, 111); coordinator.tick(111);
            assertEquals(List.of(true), flights); assertFalse(proof.active().get());
            assertEquals(1, coordinator.ledger().issuedLaunchSerial()); assertEquals(2, coordinator.ledger().writerGeneration());
            assertEquals(coordinator.ledger(), EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
            assertTrue(barrier.beginOrdinary(store, LEVEL, SLOT, root -> true, 112, label -> { }, result -> { }));
        } finally { proof.release(); }
    }

    @Test void actualInvalidationWhileQueuedStillRefusesTheFlightAfterTransitionsDrain() throws Exception {
        var op = EscrowTestFixtures.operation(1); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowTestFixtures.ledger(op)));
        var monitor = new EscrowSaveMonitor(); var proof = source(monitor); var io = new AwaitedLedgerIo(); var replies = new ArrayList<Boolean>();
        var launch = EscrowFlightAuthorityTest.launch(data.ledger(), 1);
        try (var writer = io.writer(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0, CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.transition(op.id(), EscrowReason.OTHER_STATE);
            assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> { fail("Invalid proof reached capture"); return root; },
                    proof::valid, committed -> { replies.add(committed); proof.release(); }));
            coordinator.tick(0); monitor.observed(LEVEL, true, SLOT); assertFalse(proof.valid());
            coordinator.persistTransitions(); io.awaitWrites(); coordinator.tick(1); coordinator.tick(10);
            assertEquals(List.of(false), replies); assertEquals(0, coordinator.ledger().issuedLaunchSerial());
            assertTrue(coordinator.ledger().armedFlights().isEmpty()); assertFalse(proof.active().get());
        } finally { proof.release(); }
    }

    @Test void unknownRetainsPinAndExactCandidateUntilCheckedResolution() throws Exception {
        var data = ResourceEscrowSavedData.create(); var monitor = new EscrowSaveMonitor(); var proof = source(monitor);
        var io = new AwaitedLedgerIo(); var attempts = new AtomicInteger(); var captures = new AtomicInteger(); var replies = new ArrayList<Boolean>();
        var launch = EscrowFlightAuthorityTest.launch(data.ledger(), 1);
        try (var barrier = new EscrowBarrier(); var writer = io.writer(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0, (stage, target) -> {
            if (attempts.incrementAndGet() == 1) { throw new java.io.IOException("unresolved replacement"); }
            CheckedSavedDataFile.atomicMove(stage, target);
        }); var coordinator = new EscrowCoordinator(data, writer)) {
            assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> {
                captures.incrementAndGet(); return EscrowFlightAuthority.arm(root, launch, generation);
            }, proof::valid, committed -> { replies.add(committed); proof.release(); }));
            coordinator.tick(0); io.awaitWrites(); coordinator.tick(1); assertTrue(writer.unknown());
            var store = new EscrowChunkStore(null, monitor);
            for (int tick = 2; tick < 201; tick++) { refused(barrier, store, tick); coordinator.tick(tick); }
            assertTrue(proof.valid()); assertTrue(replies.isEmpty()); assertEquals(1, attempts.get()); assertEquals(1, captures.get());
            assertEquals(0, coordinator.ledger().issuedLaunchSerial());
            coordinator.tick(201); io.awaitWrites(); refused(barrier, store, 202); coordinator.tick(202);
            assertEquals(List.of(true), replies); assertEquals(1, captures.get()); assertEquals(2, attempts.get());
            assertEquals(1, coordinator.ledger().issuedLaunchSerial()); assertEquals(1, coordinator.ledger().writerGeneration());
        } finally { proof.release(); }
    }

    @Test void realGenerationGuardImmediatelyBeforeReplacementStillRefuses() throws Exception {
        var data = ResourceEscrowSavedData.create(); var monitor = new EscrowSaveMonitor(); var proof = source(monitor);
        var io = new AwaitedLedgerIo(); var checks = new AtomicInteger(); var replies = new ArrayList<Boolean>();
        var launch = EscrowFlightAuthorityTest.launch(data.ledger(), 1);
        try (var writer = io.writer(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0, CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> EscrowFlightAuthority.arm(root, launch, generation), () -> {
                // The monitor's atomic generation is also what a concurrent invalidation exposes to this IO guard.
                if (checks.incrementAndGet() == 2) { proof.capture().generation().incrementAndGet(); }
                return proof.valid();
            }, committed -> { replies.add(committed); proof.release(); }));
            coordinator.tick(0); io.awaitWrites(); coordinator.tick(1);
            assertEquals(List.of(false), replies); assertEquals(2, checks.get());
            assertEquals(0, coordinator.ledger().issuedLaunchSerial()); assertTrue(coordinator.ledger().armedFlights().isEmpty());
        } finally { proof.release(); }
    }

    private EscrowBarrier.Proof source(EscrowSaveMonitor monitor) {
        // Uses the baseline capture/watch API so the same regression runs against the pre-W151 production sources.
        var generation = monitor.watch(LEVEL, true, SLOT);
        var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory, SLOT,
                generation, generation.get(), 0, () -> { }, () -> monitor.release(LEVEL, true, SLOT));
        return new EscrowBarrier.Proof(null, capture, new AtomicBoolean(true), new AtomicBoolean(true));
    }
    private static void refused(EscrowBarrier barrier, EscrowChunkStore store, long tick) {
        assertFalse(barrier.beginOrdinary(store, LEVEL, SLOT, root -> fail(), tick, label -> { }, result -> { }),
                "Ordinary capture admitted before checked acknowledgement");
    }
}
