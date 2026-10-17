package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowAdmissionAuthority.*;

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
}
