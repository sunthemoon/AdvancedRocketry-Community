package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowAdmissionAuthority.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class EscrowAdmissionAuthorityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static Pending pending(int op, int rocket, Consumer consumer) {
        return new Pending(EscrowTestFixtures.id(op), EscrowTestFixtures.id(900), EscrowTestFixtures.id(10_000 + rocket), consumer);
    }
    private static Result reserve(EscrowAdmissionAuthority authority, Pending pending) {
        return authority.tryReserve(pending.op(), pending.owner(), pending.rocket(), pending.consumer());
    }
    @Test void uniqueUnionCountsDurableQueuedCapturedUnknownRepairAndReleasedCopiesOnce() {
        var authority = new EscrowAdmissionAuthority(); var states = new ArrayList<Pending>();
        authority.registerIndex(Consumer.PROPULSION, () -> new Index(true, states));
        var op = pending(1, 1, Consumer.PROPULSION);
        assertEquals(Result.RESERVED, reserve(authority, op)); assertEquals(1, authority.pendingCount());
        for (int state = 0; state < 6; state++) { states.add(op); }
        assertEquals(1, authority.pendingCount()); assertEquals(Result.ALREADY_HELD, reserve(authority, op));
        // Retain just the unknown or repair/released reference: it still owns the reservation.
        states.subList(1, states.size()).clear(); assertEquals(1, authority.pendingCount());
        states.clear(); assertEquals(0, authority.pendingCount());
    }
    @Test void mixedConsumersShareTheSixtyFourSlotLimitAndRefusalChangesNothing() {
        var authority = new EscrowAdmissionAuthority(); var ledger = new ArrayList<Pending>(); var bay = new ArrayList<Pending>();
        authority.registerIndex(Consumer.PROPULSION, () -> new Index(true, ledger));
        authority.registerIndex(Consumer.BAY, () -> new Index(true, bay));
        for (int i = 0; i < 64; i++) {
            var op = pending(i + 1, i / 8, i % 2 == 0 ? Consumer.PROPULSION : Consumer.BAY);
            assertEquals(Result.RESERVED, reserve(authority, op));
            (op.consumer() == Consumer.BAY ? bay : ledger).add(op);
        }
        assertEquals(64, authority.pendingCount());
        assertEquals(Result.SERVER_FULL, reserve(authority, pending(100, 100, Consumer.BAY)));
        assertEquals(64, authority.pendingCount());
        ledger.remove(0); assertEquals(Result.RESERVED, reserve(authority, pending(100, 100, Consumer.BAY)));
    }
    @Test void eightPerRocketLimitIsSharedAndAnIdempotentReservationStillWorksAtTheLimit() {
        var authority = new EscrowAdmissionAuthority(); var fluid = new ArrayList<Pending>(); var bays = new ArrayList<Pending>();
        authority.registerIndex(Consumer.PROPULSION, () -> new Index(true, fluid));
        authority.registerIndex(Consumer.BAY, () -> new Index(true, bays));
        for (int i = 0; i < 8; i++) { var op = pending(i + 1, 1, i < 4 ? Consumer.BAY : Consumer.PROPULSION);
            assertEquals(Result.RESERVED, reserve(authority, op)); (op.consumer() == Consumer.BAY ? bays : fluid).add(op); }
        assertEquals(Result.ROCKET_FULL, reserve(authority, pending(20, 1, Consumer.PROPULSION)));
        assertEquals(Result.ALREADY_HELD, reserve(authority, bays.get(0))); assertEquals(8, authority.pendingFor(bays.get(0).rocket()));
    }
    @Test void operationIdentityCannotChangeOwnerRocketOrConsumer() {
        var authority = new EscrowAdmissionAuthority(); var op = pending(1, 1, Consumer.PROPULSION);
        authority.registerIndex(Consumer.PROPULSION, () -> new Index(true, List.of(op)));
        authority.registerIndex(Consumer.BAY, () -> new Index(true, List.of()));
        for (var conflict : List.of(new Pending(op.op(), UUID.randomUUID(), op.rocket(), op.consumer()),
                new Pending(op.op(), op.owner(), UUID.randomUUID(), op.consumer()),
                new Pending(op.op(), op.owner(), op.rocket(), Consumer.BAY))) {
            assertEquals(Result.IDENTITY_CONFLICT, reserve(authority, conflict));
        }
        assertEquals(1, authority.pendingCount());
    }
    @Test void startupRequiresEveryRegisteredIndexAndOverCapacityDoesNotEvictOrBlockIndexing() {
        var authority = new EscrowAdmissionAuthority(); var loaded = new java.util.concurrent.atomic.AtomicBoolean();
        var rows = new ArrayList<Pending>(); for (int i = 0; i < 65; i++) { rows.add(pending(i + 1, i / 8, Consumer.BAY)); }
        authority.registerIndex(Consumer.PROPULSION, () -> new Index(true, List.of()));
        authority.registerIndex(Consumer.BAY, () -> new Index(loaded.get(), rows));
        assertEquals(Result.UNAVAILABLE, reserve(authority, pending(100, 100, Consumer.PROPULSION)));
        loaded.set(true); assertEquals(65, authority.pendingCount());
        assertEquals(Result.SERVER_FULL, reserve(authority, pending(100, 100, Consumer.PROPULSION)));
        assertEquals(Result.ALREADY_HELD, reserve(authority, rows.get(0))); assertEquals(65, authority.pendingCount());
    }
    @Test void coordinatorUsesTheSameAuthorityAndDoesNotIssueASequenceOnSharedRefusal() {
        var data = ResourceEscrowSavedData.create(); var rows = new ArrayList<Pending>();
        for (int i = 0; i < 64; i++) { rows.add(pending(1_000 + i, i / 8, Consumer.BAY)); }
        try (var writer = new EscrowLedgerWriter(java.nio.file.Path.of("unused-admission-test.dat"), 0);
                var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.admissionAuthority().registerIndex(Consumer.BAY, () -> new Index(true, rows));
            assertThrows(IllegalArgumentException.class, () -> coordinator.prepareTest(EscrowTestFixtures.operation(1), ignored -> fail()));
            assertEquals(1, coordinator.nextSequence()); assertTrue(data.ledger().records().isEmpty()); assertFalse(data.isDirty());
        }
    }
    @Test void authorityRequiresItsOwnerThreadAndCannotRegisterAnIndexTwice() throws Exception {
        var authority = new EscrowAdmissionAuthority(); authority.registerIndex(Consumer.PROPULSION, () -> new Index(true, List.of()));
        assertThrows(IllegalStateException.class, () -> authority.registerIndex(Consumer.PROPULSION, () -> new Index(true, List.of())));
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var worker = new Thread(() -> { try { authority.pendingCount(); } catch (Throwable failure) { error.set(failure); } });
        worker.start(); worker.join(5_000); assertFalse(worker.isAlive()); assertInstanceOf(IllegalStateException.class, error.get());
    }
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @Test void realUnknownCaptureKeepsSharedCapacityAcrossRetriesAndExactAcknowledgement() throws Exception {
        var uncertain = new java.util.concurrent.atomic.AtomicBoolean(true);
        var data = ResourceEscrowSavedData.create(); var bay = new ArrayList<Pending>();
        var file = temp.resolve(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var ledgerIo = new AwaitedLedgerIo();
        try (var writer = ledgerIo.writer(file, 0, (staged, target) -> {
                    if (uncertain.get()) { throw new java.io.IOException("Injected ambiguous admission replacement"); }
                    io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.atomicMove(staged, target);
                }); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.admissionAuthority().registerIndex(Consumer.BAY, () -> new Index(true, bay));
            var acknowledged = new java.util.concurrent.atomic.AtomicInteger();
            coordinator.prepareTest(EscrowTestFixtures.operation(1), committed -> { assertTrue(committed); acknowledged.incrementAndGet(); });
            assertTrue(coordinator.commitPreparations());
            long tick = 1;
            try {
                tick = ledgerIo.tickUntil(coordinator::tick, tick, writer::unavailable);
                assertTrue(writer.unavailable()); assertEquals(0, acknowledged.get());
                assertTrue(coordinator.operational(), "Transient writer failure must keep the runtime ticking exact-candidate retries");
                assertEquals(1, coordinator.admissionAuthority().pendingCount());
                for (int i = 0; i < 63; i++) { bay.add(pending(1_000 + i, i / 8, Consumer.BAY)); }
                assertEquals(64, coordinator.admissionAuthority().pendingCount());
                assertEquals(Result.SERVER_FULL, reserve(coordinator.admissionAuthority(), pending(10_000, 20, Consumer.BAY)));
                assertEquals(2, coordinator.nextSequence()); assertTrue(coordinator.ledger().records().isEmpty());
            } finally {
                uncertain.set(false);
                ledgerIo.tickUntil(coordinator::tick, tick, writer::idle);
            }
            assertTrue(writer.idle()); assertEquals(1, acknowledged.get());
            assertEquals(1, coordinator.ledger().records().size());
            assertEquals(64, coordinator.admissionAuthority().pendingCount(), "A committed unresolved operation still counts");
        }
    }

    // W11b3 B3-U: the runtime-owned authority, its PROPULSION delegate and the ledger-owned coordinators that attach to it.

    private static List<Pending> bays(int count, int firstRocket) {
        var rows = new ArrayList<Pending>();
        for (int i = 0; i < count; i++) { rows.add(pending(5_000 + i, firstRocket + i / 8, Consumer.BAY)); }
        return rows;
    }
    private static ResourceEscrowSavedData loaded(EscrowLedger root) { return ResourceEscrowSavedData.load(EscrowCodec.encode(root)); }
    private java.nio.file.Path ledgerFile(String name) throws Exception {
        var directory = java.nio.file.Files.createDirectories(temp.resolve(name));
        return directory.resolve(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW.fileName());
    }
    @Test void sixtyThreeOfOneConsumerPlusOneOfTheOtherFillTheServerInEitherOrder() {
        for (var major : Consumer.values()) {
            var minor = major == Consumer.PROPULSION ? Consumer.BAY : Consumer.PROPULSION;
            var authority = new EscrowAdmissionAuthority(); var majors = new ArrayList<Pending>(); var minors = new ArrayList<Pending>();
            authority.registerIndex(major, () -> new Index(true, majors));
            authority.registerIndex(minor, () -> new Index(true, minors));
            for (int i = 0; i < 63; i++) {
                var op = pending(i + 1, i / 8, major); assertEquals(Result.RESERVED, reserve(authority, op)); majors.add(op);
            }
            var last = pending(64, 100, minor); assertEquals(Result.RESERVED, reserve(authority, last)); minors.add(last);
            assertEquals(64, authority.pendingCount());
            for (var consumer : Consumer.values()) { assertEquals(Result.SERVER_FULL, reserve(authority, pending(200, 200, consumer))); }
            assertEquals(Result.ALREADY_HELD, reserve(authority, last)); assertEquals(Result.ALREADY_HELD, reserve(authority, majors.get(62)));
            assertEquals(64, authority.pendingCount(), "Refusals and idempotent repeats change nothing");
            minors.clear(); assertEquals(63, authority.pendingCount());
            assertEquals(Result.RESERVED, reserve(authority, pending(200, 200, minor)));
        }
    }
    @Test void mixedEightPerRocketRefuseTheNinthForEitherConsumerAndLeaveOtherRocketsOpen() {
        var authority = new EscrowAdmissionAuthority(); var fluid = new ArrayList<Pending>(); var bays = new ArrayList<Pending>();
        authority.registerIndex(Consumer.PROPULSION, () -> new Index(true, fluid));
        authority.registerIndex(Consumer.BAY, () -> new Index(true, bays));
        for (int i = 0; i < 8; i++) {
            var op = pending(i + 1, 1, i % 2 == 0 ? Consumer.PROPULSION : Consumer.BAY);
            assertEquals(Result.RESERVED, reserve(authority, op)); (op.consumer() == Consumer.BAY ? bays : fluid).add(op);
        }
        for (var consumer : Consumer.values()) { assertEquals(Result.ROCKET_FULL, reserve(authority, pending(9, 1, consumer))); }
        assertEquals(8, authority.pendingFor(fluid.get(0).rocket())); assertEquals(8, authority.pendingCount());
        var other = pending(9, 2, Consumer.BAY); assertEquals(Result.RESERVED, reserve(authority, other)); bays.add(other);
        bays.remove(0); assertEquals(7, authority.pendingFor(fluid.get(0).rocket()));
        assertEquals(Result.RESERVED, reserve(authority, pending(10, 1, Consumer.PROPULSION)));
    }
    @Test void anOperationIdReportedByBothStoresIsAConflictAndAdmissionFailsClosedUntilItResolves() {
        var authority = new EscrowAdmissionAuthority(); var fluid = new ArrayList<Pending>(); var bays = new ArrayList<Pending>();
        authority.registerIndex(Consumer.PROPULSION, () -> new Index(true, fluid));
        authority.registerIndex(Consumer.BAY, () -> new Index(true, bays));
        var op = pending(1, 1, Consumer.PROPULSION); fluid.add(op);
        bays.add(new Pending(op.op(), op.owner(), op.rocket(), Consumer.BAY));
        assertEquals(1, authority.pendingCount(), "A colliding UUID is still one slot");
        for (var consumer : Consumer.values()) {
            assertEquals(Result.IDENTITY_CONFLICT, reserve(authority, new Pending(op.op(), op.owner(), op.rocket(), consumer)));
            assertEquals(Result.UNAVAILABLE, reserve(authority, pending(2, 2, consumer)));
        }
        assertEquals(1, authority.pendingCount());
        bays.clear();
        assertEquals(Result.ALREADY_HELD, reserve(authority, op)); assertEquals(Result.RESERVED, reserve(authority, pending(2, 2, Consumer.BAY)));
    }
    @Test void delegateIsNotLoadedThenNoFileThenLoadedAndOnlyAnAttachedLedgerFillsIt() throws Exception {
        var authority = new EscrowAdmissionAuthority(); var delegate = authority.register(Consumer.PROPULSION);
        var bays = new ArrayList<Pending>(); authority.registerIndex(Consumer.BAY, () -> new Index(true, bays));
        assertEquals(Delegate.State.NOT_LOADED, delegate.state());
        for (var consumer : Consumer.values()) { assertEquals(Result.UNAVAILABLE, reserve(authority, pending(1, 1, consumer))); }
        assertEquals(0, authority.pendingCount());
        assertThrows(IllegalStateException.class, () -> authority.register(Consumer.PROPULSION), "One PROPULSION registration");
        delegate.noFile(); assertEquals(Delegate.State.NO_FILE, delegate.state());
        var bay = pending(1, 1, Consumer.BAY); assertEquals(Result.RESERVED, reserve(authority, bay)); bays.add(bay);
        assertEquals(1, authority.pendingCount(), "NO_FILE is loaded and known to be empty");
        var durable = EscrowTestFixtures.operation(5); var data = loaded(EscrowTestFixtures.ledger(durable));
        try (var writer = new EscrowLedgerWriter(ledgerFile("states"), 0); var coordinator = new EscrowCoordinator(data, writer, delegate)) {
            assertEquals(Delegate.State.LOADED, delegate.state()); assertSame(authority, coordinator.admissionAuthority());
            assertEquals(2, authority.pendingCount()); assertEquals(Result.ALREADY_HELD, reserve(authority, Pending.of(durable)));
            delegate.noFile(); assertEquals(Delegate.State.LOADED, delegate.state(), "A late lookup does not detach the ledger");
            assertTrue(delegate.retarget(null)); assertEquals(Delegate.State.NO_FILE, delegate.state());
            assertEquals(1, authority.pendingCount());
            assertTrue(coordinator.attachAdmission()); assertEquals(Delegate.State.LOADED, delegate.state());
            assertEquals(2, authority.pendingCount());
        }
    }
    @Test void anExistingLedgerBoundAtStartupAdmitsAFillAndABayInTheFirstTick() throws Exception {
        var authority = new EscrowAdmissionAuthority(); var delegate = authority.register(Consumer.PROPULSION);
        var bayLoaded = new java.util.concurrent.atomic.AtomicBoolean(); var bays = new ArrayList<Pending>();
        authority.registerIndex(Consumer.BAY, () -> new Index(bayLoaded.get(), bays));
        // Before the startup lookup both stores fail closed.
        for (var consumer : Consumer.values()) { assertEquals(Result.UNAVAILABLE, reserve(authority, pending(1, 1, consumer))); }
        // ServerStartingEvent: both startup indexes load, and the existing ledger binds before any tick.
        bayLoaded.set(true); var durable = EscrowTestFixtures.operation(1); var data = loaded(EscrowTestFixtures.ledger(durable));
        try (var writer = new EscrowLedgerWriter(ledgerFile("first-tick"), 0); var coordinator = new EscrowCoordinator(data, writer, delegate)) {
            assertEquals(Delegate.State.LOADED, delegate.state());
            // The first tick: no coordinator or writer tick has run yet.
            var fill = EscrowTestFixtures.operation(2);
            assertDoesNotThrow(() -> coordinator.prepareTest(fill, ignored -> { }));
            var bay = pending(1_001, 1, Consumer.BAY); assertEquals(Result.RESERVED, reserve(authority, bay)); bays.add(bay);
            assertEquals(3, authority.pendingCount()); assertEquals(3, coordinator.nextSequence());
        }
    }
    @Test void durableQueuedCapturedAndUnknownCopiesOfOneLedgerCountOnce() throws Exception {
        var authority = new EscrowAdmissionAuthority(); var delegate = authority.register(Consumer.PROPULSION);
        var bays = bays(62, 100); authority.registerIndex(Consumer.BAY, () -> new Index(true, bays));
        var durable = EscrowTestFixtures.operation(1); var data = loaded(EscrowTestFixtures.ledger(durable));
        var uncertain = new java.util.concurrent.atomic.AtomicBoolean(true); var ledgerIo = new AwaitedLedgerIo();
        try (var writer = ledgerIo.writer(ledgerFile("copies"), 0, (staged, target) -> {
                    if (uncertain.get()) { throw new java.io.IOException("Injected ambiguous replacement"); }
                    CheckedSavedDataFile.atomicMove(staged, target);
                }); var coordinator = new EscrowCoordinator(data, writer, delegate)) {
            assertEquals(63, authority.pendingCount());
            var replies = new ArrayList<Boolean>(); var queued = EscrowTestFixtures.operation(2);
            coordinator.prepareTest(queued, replies::add); assertEquals(64, authority.pendingCount());
            assertTrue(coordinator.commitPreparations());
            assertEquals(List.of(durable, queued), writer.capturedOperations(), "Both are also captured candidates");
            assertEquals(64, authority.pendingCount());
            long tick = ledgerIo.tickUntil(coordinator::tick, 1, writer::unknown);
            assertTrue(writer.unknown()); assertTrue(replies.isEmpty());
            assertEquals(64, authority.pendingCount(), "Durable, queued, captured and UNKNOWN copies cost one slot each");
            assertEquals(Result.ALREADY_HELD, reserve(authority, Pending.of(durable)));
            assertEquals(Result.ALREADY_HELD, reserve(authority, Pending.of(queued)));
            assertEquals(Result.SERVER_FULL, reserve(authority, pending(9_000, 300, Consumer.BAY)));
            uncertain.set(false); ledgerIo.tickUntil(coordinator::tick, tick, writer::idle);
            assertEquals(List.of(true), replies); assertEquals(List.of(durable, queued), coordinator.ledger().records());
            assertEquals(64, authority.pendingCount());
        }
    }
    @Test void repairAndReleasedButUnresolvedEntriesKeepTheirSlotsAndRocketCount() throws Exception {
        var repair = EscrowTestFixtures.operation(1, 7, 0).withReason(EscrowReason.OTHER_STATE);
        assertEquals(EscrowOperation.Phase.REPAIR, repair.phase());
        var rocket = repair.guard().rocket();
        var authority = new EscrowAdmissionAuthority(); var delegate = authority.register(Consumer.PROPULSION);
        // The bay index lists its REPAIR receipt and its released-but-unresolved receipt like any other pending state.
        var bays = new ArrayList<>(List.of(new Pending(EscrowTestFixtures.id(10), EscrowTestFixtures.id(900), rocket, Consumer.BAY),
                new Pending(EscrowTestFixtures.id(11), EscrowTestFixtures.id(900), rocket, Consumer.BAY)));
        authority.registerIndex(Consumer.BAY, () -> new Index(true, bays));
        try (var writer = new EscrowLedgerWriter(ledgerFile("repair"), 0);
                var coordinator = new EscrowCoordinator(loaded(EscrowTestFixtures.ledger(repair)), writer, delegate)) {
            assertEquals(3, authority.pendingCount()); assertEquals(3, authority.pendingFor(rocket));
            assertEquals(Result.ALREADY_HELD, reserve(authority, Pending.of(repair)));
            for (var receipt : List.copyOf(bays)) { assertEquals(Result.ALREADY_HELD, reserve(authority, receipt)); }
            for (int i = 0; i < 5; i++) {
                var next = new Pending(EscrowTestFixtures.id(20 + i), EscrowTestFixtures.id(900), rocket, Consumer.BAY);
                assertEquals(Result.RESERVED, reserve(authority, next)); bays.add(next);
            }
            assertEquals(Result.ROCKET_FULL, reserve(authority, new Pending(EscrowTestFixtures.id(30), EscrowTestFixtures.id(900), rocket, Consumer.BAY)));
            assertEquals(8, authority.pendingFor(rocket)); assertEquals(List.of(repair), coordinator.ledger().records());
        }
    }
    @Test void overCapacityRestartIndexesEveryRecordWithoutEviction() throws Exception {
        var old = EscrowTestFixtures.ledger(java.util.stream.LongStream.rangeClosed(1, 64)
                .mapToObj(EscrowTestFixtures::operation).toArray(EscrowOperation[]::new));
        // A restart is a new runtime: a new authority and delegate, and the loaded ledger binding at startup.
        var authority = new EscrowAdmissionAuthority(); var delegate = authority.register(Consumer.PROPULSION);
        var bay = pending(1_000, 300, Consumer.BAY); var bays = new ArrayList<>(List.of(bay));
        authority.registerIndex(Consumer.BAY, () -> new Index(true, bays));
        var data = loaded(old);
        try (var writer = new EscrowLedgerWriter(ledgerFile("over-capacity"), 0); var coordinator = new EscrowCoordinator(data, writer, delegate)) {
            assertEquals(65, authority.pendingCount());
            for (var consumer : Consumer.values()) { assertEquals(Result.SERVER_FULL, reserve(authority, pending(2_000, 400, consumer))); }
            assertEquals(Result.ALREADY_HELD, reserve(authority, bay));
            assertEquals(Result.ALREADY_HELD, reserve(authority, Pending.of(old.records().get(63))));
            assertEquals(old, coordinator.ledger()); assertEquals(List.of(bay), bays); assertFalse(data.isDirty());
            bays.clear(); assertEquals(64, authority.pendingCount());
            assertEquals(Result.SERVER_FULL, reserve(authority, pending(2_000, 400, Consumer.BAY)));
            assertEquals(old, coordinator.ledger());
        }
    }
    @Test void aRejectedBayReceiptReArmsWithTheSameOperationIdOnlyWhenASlotIsFree() {
        var authority = new EscrowAdmissionAuthority(); var fluid = new ArrayList<Pending>(); var bays = new ArrayList<Pending>();
        authority.registerIndex(Consumer.PROPULSION, () -> new Index(true, fluid));
        authority.registerIndex(Consumer.BAY, () -> new Index(true, bays));
        var receipt = pending(1, 1, Consumer.BAY); assertEquals(Result.RESERVED, reserve(authority, receipt)); bays.add(receipt);
        assertEquals(1, authority.pendingCount(), "The INTENT receipt holds its slot");
        // The exactly acknowledged REJECTED outcome leaves the pending union; the receipt keeps only its retained slot.
        bays.remove(receipt); assertEquals(0, authority.pendingCount());
        for (int i = 0; i < 64; i++) {
            var op = pending(100 + i, 10 + i / 8, Consumer.PROPULSION); assertEquals(Result.RESERVED, reserve(authority, op)); fluid.add(op);
        }
        assertEquals(Result.SERVER_FULL, reserve(authority, receipt), "A full server leaves the REJECTED receipt unchanged");
        assertEquals(64, authority.pendingCount()); assertEquals(0, authority.pendingFor(receipt.rocket()));
        fluid.remove(0);
        assertEquals(Result.RESERVED, reserve(authority, receipt), "Re-arm reacquires a slot for the same operation ID");
        bays.add(receipt); assertEquals(64, authority.pendingCount());
        assertEquals(Result.ALREADY_HELD, reserve(authority, receipt));
        assertEquals(Result.IDENTITY_CONFLICT, reserve(authority, new Pending(receipt.op(), EscrowTestFixtures.id(901), receipt.rocket(), Consumer.BAY)));
    }
    @Test void aDefinitePreReplaceL1FailureReleasesExactlyItsOwnReservations() throws Exception {
        var authority = new EscrowAdmissionAuthority(); var delegate = authority.register(Consumer.PROPULSION);
        var bays = bays(62, 100); authority.registerIndex(Consumer.BAY, () -> new Index(true, bays));
        var data = loaded(new EscrowLedger(EscrowTestFixtures.id(500), 0, 1, List.of(), List.of(), List.of()));
        var failures = new java.util.concurrent.atomic.AtomicInteger(1); var ledgerIo = new AwaitedLedgerIo();
        try (var writer = ledgerIo.writer(ledgerFile("release"), 1,
                CheckedSavedDataFile::atomicMove, cut -> {
                    if (cut.equals("K1") && failures.getAndDecrement() > 0) { throw new IllegalStateException("Injected definite pre-replace failure"); }
                }); var coordinator = new EscrowCoordinator(data, writer, delegate)) {
            var replies = new ArrayList<Boolean>();
            coordinator.prepareTest(EscrowTestFixtures.operation(1), replies::add);
            coordinator.prepareTest(EscrowTestFixtures.operation(2), replies::add);
            assertEquals(64, authority.pendingCount()); assertTrue(delegate.holdsWork());
            assertEquals(Result.SERVER_FULL, reserve(authority, pending(9_000, 300, Consumer.BAY)));
            assertTrue(coordinator.commitPreparations());
            long tick = ledgerIo.tickUntil(coordinator::tick, 1, writer::idle);
            assertEquals(List.of(false, false), replies); assertTrue(coordinator.ledger().records().isEmpty());
            assertEquals(62, authority.pendingCount(), "Only the two never-applied admissions are released");
            assertFalse(delegate.holdsWork());
            var bay = pending(9_000, 300, Consumer.BAY); assertEquals(Result.RESERVED, reserve(authority, bay)); bays.add(bay);
            // A later exact L1 keeps its slot: only an exact resolution would release it.
            var durable = EscrowTestFixtures.operation(3); coordinator.prepareTest(durable, replies::add);
            coordinator.tick(Math.max(tick, 10)); assertTrue(coordinator.commitPreparations());
            ledgerIo.tickUntil(coordinator::tick, Math.max(tick, 10) + 1, writer::idle);
            assertEquals(List.of(false, false, true), replies); assertEquals(List.of(durable), coordinator.ledger().records());
            assertEquals(64, authority.pendingCount()); assertEquals(Result.ALREADY_HELD, reserve(authority, Pending.of(durable)));
        }
    }
    @Test void aSecondLedgerRetargetsAndReplacementRefusesWithoutThrowingWhileWorkIsHeld() throws Exception {
        var authority = new EscrowAdmissionAuthority(); var delegate = authority.register(Consumer.PROPULSION); delegate.noFile();
        var earlier = EscrowTestFixtures.operation(7); var first = loaded(EscrowTestFixtures.ledger(earlier));
        var second = ResourceEscrowSavedData.create();
        var uncertain = new java.util.concurrent.atomic.AtomicBoolean(true); var ledgerIo = new AwaitedLedgerIo();
        try (var firstWriter = new EscrowLedgerWriter(ledgerFile("first"), 0); var a = new EscrowCoordinator(first, firstWriter, delegate);
                var secondWriter = ledgerIo.writer(ledgerFile("second"), 0, (staged, target) -> {
                    if (uncertain.get()) { throw new java.io.IOException("Injected ambiguous replacement"); }
                    CheckedSavedDataFile.atomicMove(staged, target);
                });
                var b = assertDoesNotThrow(() -> new EscrowCoordinator(second, secondWriter, delegate), "A second ledger retargets")) {
            assertEquals(Delegate.State.LOADED, delegate.state()); assertEquals(0, authority.pendingCount());
            var replies = new ArrayList<Boolean>(); var op = EscrowTestFixtures.operation(1);
            b.prepareTest(op, replies::add); assertEquals(1, authority.pendingCount());
            // Queued, then captured, then UNKNOWN work each refuse replacement without throwing or changing anything.
            assertTrue(delegate.holdsWork());
            assertFalse(assertDoesNotThrow(a::attachAdmission)); assertFalse(assertDoesNotThrow(() -> delegate.retarget(null)));
            try (var thirdWriter = new EscrowLedgerWriter(ledgerFile("third"), 0)) {
                assertThrows(IllegalArgumentException.class, () -> new EscrowCoordinator(ResourceEscrowSavedData.create(), thirdWriter, delegate),
                        "Only the runtime's checked path replaces a ledger; a direct construction cannot drop held work");
            }
            assertEquals(1, authority.pendingCount()); assertEquals(Result.ALREADY_HELD, reserve(authority, Pending.of(op)));
            assertTrue(b.commitPreparations()); assertTrue(delegate.holdsWork()); assertFalse(a.attachAdmission());
            long tick = ledgerIo.tickUntil(b::tick, 1, secondWriter::unknown);
            assertTrue(secondWriter.unknown()); assertTrue(delegate.holdsWork()); assertFalse(a.attachAdmission());
            assertFalse(delegate.retarget(null)); assertEquals(1, authority.pendingCount());
            uncertain.set(false); ledgerIo.tickUntil(b::tick, tick, secondWriter::idle);
            assertEquals(List.of(true), replies); assertFalse(delegate.holdsWork());
            assertTrue(a.attachAdmission()); assertEquals(1, authority.pendingCount());
            assertEquals(Result.ALREADY_HELD, reserve(authority, Pending.of(earlier)));
            assertTrue(delegate.retarget(null)); assertEquals(Delegate.State.NO_FILE, delegate.state()); assertEquals(0, authority.pendingCount());
            assertTrue(b.attachAdmission()); assertEquals(Result.ALREADY_HELD, reserve(authority, Pending.of(op)));
        }
    }
}
