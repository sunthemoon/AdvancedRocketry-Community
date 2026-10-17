package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W11b3 B3-E, W11B3-DESIGN section 2's production ledger creation: the virtual-root check and the reservation while
 * NO_FILE create nothing; creation queues the reserved fill; the empty generation-1 baseline is acknowledged before L1;
 * a definite baseline failure (or a failed bind-time readback) removes the fill and its reservation; UNKNOWN keeps both.
 */
class EscrowLedgerCreationTest {
    private static final UUID ID = EscrowTestFixtures.id(777);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    private static EscrowOperation fill(long sequence, long rocket) { return EscrowSettlementCapacityTest.operation(sequence, rocket, 0, true); }
    private static EscrowNativeBinding.Admission capture(EscrowOperation op) { return new EscrowNativeBinding.Admission(op, RocketEscrowSection.neutral()); }
    private Path file(String name) throws IOException {
        return Files.createDirectories(directory.resolve(name)).resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
    }
    private static EscrowLedger disk(Path file) {
        return EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
    }

    /** Step 1 on a NO_FILE delegate: the pure check, then one reservation. Nothing is stored. */
    private static EscrowAdmissionAuthority.Delegate reserved(EscrowAdmissionAuthority authority, EscrowOperation op) {
        var delegate = authority.register(EscrowAdmissionAuthority.Consumer.PROPULSION); delegate.noFile();
        assertTrue(EscrowCoordinator.fillRefusal(EscrowLedger.empty(ID), List.of(), capture(op)).isEmpty());
        assertFalse(EscrowCoordinator.invalid(op));
        assertEquals(EscrowAdmissionAuthority.Result.RESERVED, authority.tryReserve(op.id(), op.owner(), op.guard().rocket(),
                EscrowAdmissionAuthority.Consumer.PROPULSION));
        assertEquals(EscrowAdmissionAuthority.Delegate.State.NO_FILE, delegate.state()); assertEquals(1, authority.pendingCount());
        return delegate;
    }
    /** Step 2 as {@code ResourceEscrowSavedData.createForFill} and {@code bind} perform it. */
    private static EscrowCoordinator created(EscrowLedgerWriter writer, EscrowAdmissionAuthority.Delegate delegate) {
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowLedger.empty(ID)));
        writer.baseline(data.ledger());
        var coordinator = new EscrowCoordinator(data, writer, delegate);
        coordinator.beginCreationBaseline();
        return coordinator;
    }

    @Test void theFirstFillCreatesTheLedgerAndItsEmptyBaselineIsAcknowledgedBeforeL1() throws Exception {
        var authority = new EscrowAdmissionAuthority(); var op = fill(1, 1); var delegate = reserved(authority, op);
        var file = file("created"); var io = new AwaitedLedgerIo(); var replies = new ArrayList<Boolean>();
        assertFalse(Files.exists(file));
        try (var writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove); var coordinator = created(writer, delegate)) {
            assertEquals(EscrowAdmissionAuthority.Delegate.State.LOADED, delegate.state());
            assertTrue(coordinator.creationBaselinePending());
            coordinator.enqueueCreated(op, replies::add);
            assertEquals(1, authority.pendingCount(), "The reservation and its queue entry are one slot");
            assertEquals(2, coordinator.nextSequence()); assertTrue(coordinator.admitting(op.destination().key()));
            assertThrows(IllegalArgumentException.class, () -> coordinator.enqueueCreated(fill(2, 2), ignored -> { }),
                    "Creation queues exactly its one reserved fill");
            // Step 3: the first checked write is the empty root at generation 1; autosave and L1 wait for it.
            assertTrue(coordinator.commitPreparations());
            long tick = io.tickUntil(coordinator::tick, 1, writer::idle);
            var baseline = disk(file);
            assertEquals(ID, baseline.id()); assertEquals(1, baseline.writerGeneration());
            assertTrue(baseline.records().isEmpty()); assertEquals(0, baseline.issuedThrough());
            assertFalse(coordinator.creationBaselinePending()); assertTrue(replies.isEmpty(), "The baseline is not L1");
            assertTrue(coordinator.admitting(op.source().key()));
            // From there on it is the existing-ledger path: L1 carries the queued fill.
            tick += 10; coordinator.tick(tick);
            assertTrue(coordinator.commitPreparations());
            io.tickUntil(coordinator::tick, tick + 1, writer::idle);
            assertEquals(List.of(true), replies); assertEquals(List.of(op), disk(file).records());
            assertEquals(2, disk(file).writerGeneration()); assertEquals(1, authority.pendingCount());
        }
    }

    @Test void aDefiniteBaselineFailureRemovesTheFillAndItsReservationAndALaterWriteMayPublishTheRoot() throws Exception {
        var authority = new EscrowAdmissionAuthority(); var op = fill(1, 1); var delegate = reserved(authority, op);
        // The staged file cannot be written: the failure precedes any replacement, so it is definite.
        var missing = directory.resolve("not-yet").resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var io = new AwaitedLedgerIo(); var replies = new ArrayList<Boolean>();
        try (var writer = io.writer(missing, 0, CheckedSavedDataFile::atomicMove); var coordinator = created(writer, delegate)) {
            coordinator.enqueueCreated(op, replies::add);
            assertTrue(coordinator.commitPreparations());
            long tick = io.tickUntil(coordinator::tick, 1, writer::idle);
            assertEquals(List.of(false), replies, "One definite failure callback");
            assertFalse(coordinator.admitting(op.source().key()) || coordinator.admitting(op.destination().key()));
            assertEquals(0, authority.pendingCount(), "The reservation left with the queued fill");
            assertFalse(Files.exists(missing), "The disk holds no ledger");
            assertTrue(coordinator.creationBaselinePending()); assertTrue(coordinator.ledger().records().isEmpty());
            // The empty root stays in RAM; the next admission writes the baseline first, then its own L1.
            Files.createDirectories(missing.getParent());
            var next = fill(coordinator.nextSequence(), 2); var later = new ArrayList<Boolean>();
            assertEquals(EscrowCoordinator.Fill.QUEUED, coordinator.prepareFill(capture(next), later::add));
            tick += 10; coordinator.tick(tick); assertTrue(coordinator.commitPreparations());
            tick = io.tickUntil(coordinator::tick, tick + 1, writer::idle);
            assertTrue(disk(missing).records().isEmpty() && later.isEmpty(), "A valid empty root, published before L1");
            tick += 10; coordinator.tick(tick); assertTrue(coordinator.commitPreparations());
            io.tickUntil(coordinator::tick, tick + 1, writer::idle);
            assertEquals(List.of(true), later); assertEquals(List.of(next), disk(missing).records());
        }
    }

    @Test void anUnknownBaselineKeepsTheFillAndItsReservationUntilTheExactCandidateResolves() throws Exception {
        var authority = new EscrowAdmissionAuthority(); var op = fill(1, 1); var delegate = reserved(authority, op);
        var file = file("unknown"); var io = new AwaitedLedgerIo(); var replies = new ArrayList<Boolean>();
        var attempts = new AtomicInteger();
        try (var writer = io.writer(file, 0, (staged, target) -> {
            if (attempts.incrementAndGet() == 1) { throw new IOException("Release-test ambiguous replacement outcome"); }
            CheckedSavedDataFile.atomicMove(staged, target);
        }); var coordinator = created(writer, delegate)) {
            coordinator.enqueueCreated(op, replies::add);
            assertTrue(coordinator.commitPreparations());
            io.awaitWrites(); coordinator.tick(1);
            assertTrue(writer.unknown() && !writer.idle(), "The baseline is UNKNOWN");
            assertTrue(replies.isEmpty() && coordinator.creationBaselinePending() && coordinator.admitting(op.source().key()));
            assertEquals(1, authority.pendingCount());
            assertFalse(coordinator.commitPreparations(), "L1 never overtakes an unresolved baseline");
            // The writer retries the exact candidate after its 200-tick backoff; only then does L1 follow.
            long tick = io.tickUntil(coordinator::tick, 2, writer::idle);
            assertTrue(tick > 200); assertEquals(2, attempts.get());
            assertTrue(disk(file).records().isEmpty()); assertFalse(coordinator.creationBaselinePending()); assertTrue(replies.isEmpty());
            tick += 10; coordinator.tick(tick); assertTrue(coordinator.commitPreparations());
            io.tickUntil(coordinator::tick, tick + 1, writer::idle);
            assertEquals(List.of(true), replies); assertEquals(List.of(op), disk(file).records());
        }
    }

    @Test void aFailedBindTimeReadbackIsDefiniteAndLeavesTheForeignFileUntouched() throws Exception {
        var file = file("foreign");
        try (var foreign = new EscrowLedgerWriter(file, 0)) {
            foreign.request(() -> EscrowLedgerWriter.Snapshot.of(EscrowLedger.empty(EscrowTestFixtures.id(9)).generation(1), Set.of()), ignored -> { });
        }
        var before = Files.readAllBytes(file);
        var authority = new EscrowAdmissionAuthority(); var op = fill(1, 1); var delegate = reserved(authority, op);
        var replies = new ArrayList<Boolean>();
        try (var writer = new EscrowLedgerWriter(file, 0); var coordinator = created(writer, delegate)) {
            assertFalse(writer.baselineAvailable(), "The checked readback differs from the new empty root");
            coordinator.enqueueCreated(op, replies::add);
            assertEquals(List.of(false), replies); assertEquals(0, authority.pendingCount());
            assertFalse(coordinator.operational()); assertFalse(coordinator.commitPreparations());
            assertEquals(EscrowCoordinator.Fill.UNAVAILABLE, coordinator.prepareFill(capture(fill(1, 2)), ignored -> fail()));
        }
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test void creationIsOnlyForAnUnwrittenEmptyLedger() throws Exception {
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowTestFixtures.ledger(fill(1, 1)).generation(1)));
        try (var writer = new EscrowLedgerWriter(file("written"), 1); var coordinator = new EscrowCoordinator(data, writer)) {
            assertThrows(IllegalArgumentException.class, coordinator::beginCreationBaseline);
            assertFalse(coordinator.creationBaselinePending());
            assertThrows(IllegalArgumentException.class, () -> coordinator.enqueueCreated(fill(2, 2), ignored -> { }));
        }
    }
}
