package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * W11b3 B3-E: prepareFill's typed results as player status, the device progress, the disabled production switch
 * and the fair queue.
 */
class EscrowLoaderAdmissionTest {
    @Test void everyPrepareFillResultHasOneStatusAndOnlyQueuedIsProgress() {
        var expected = new EnumMap<EscrowCoordinator.Fill, FluidFuelLoaderStatus>(EscrowCoordinator.Fill.class);
        expected.put(EscrowCoordinator.Fill.QUEUED, FluidFuelLoaderStatus.PREPARING);
        expected.put(EscrowCoordinator.Fill.UNAVAILABLE, FluidFuelLoaderStatus.LEDGER_UNAVAILABLE);
        expected.put(EscrowCoordinator.Fill.IDENTITY_CONFLICT, FluidFuelLoaderStatus.LEDGER_UNAVAILABLE);
        expected.put(EscrowCoordinator.Fill.INVALID, FluidFuelLoaderStatus.INVALID_DATA);
        expected.put(EscrowCoordinator.Fill.STALE, FluidFuelLoaderStatus.ESCROW_PENDING);
        expected.put(EscrowCoordinator.Fill.ENDPOINT_BUSY, FluidFuelLoaderStatus.ESCROW_PENDING);
        for (var full : List.of(EscrowCoordinator.Fill.ROCKET_LIMIT, EscrowCoordinator.Fill.ESCROW_FULL,
                EscrowCoordinator.Fill.SERVER_FULL, EscrowCoordinator.Fill.ROCKET_FULL)) {
            expected.put(full, FluidFuelLoaderStatus.ESCROW_FULL);
        }
        assertEquals(EscrowCoordinator.Fill.values().length, expected.size(), "Every typed result is mapped");
        for (var entry : expected.entrySet()) {
            var status = EscrowLoaderAdmission.status(entry.getKey());
            assertEquals(entry.getValue(), status, entry.getKey().name());
            assertEquals(entry.getKey() != EscrowCoordinator.Fill.QUEUED, status.refusal(), "Only a queued fill is progress: " + entry.getKey());
        }
    }

    @Test void theAuthorityRefusalsMapAsPrepareFillMapsThem() {
        Map<EscrowAdmissionAuthority.Result, EscrowCoordinator.Fill> expected = Map.of(
                EscrowAdmissionAuthority.Result.SERVER_FULL, EscrowCoordinator.Fill.SERVER_FULL,
                EscrowAdmissionAuthority.Result.ROCKET_FULL, EscrowCoordinator.Fill.ROCKET_FULL,
                EscrowAdmissionAuthority.Result.IDENTITY_CONFLICT, EscrowCoordinator.Fill.IDENTITY_CONFLICT,
                EscrowAdmissionAuthority.Result.ALREADY_HELD, EscrowCoordinator.Fill.STALE,
                EscrowAdmissionAuthority.Result.UNAVAILABLE, EscrowCoordinator.Fill.UNAVAILABLE,
                EscrowAdmissionAuthority.Result.RESERVED, EscrowCoordinator.Fill.QUEUED);
        assertEquals(EscrowAdmissionAuthority.Result.values().length, expected.size());
        expected.forEach((result, fill) -> assertEquals(fill, EscrowCoordinator.fill(result), result.name()));
    }

    @Test void productionAdmissionStaysDisabled() {
        assertFalse(EscrowCoordinator.PRODUCTION_ADMISSION, "Only Root's reviewed enable commit may change this");
    }

    /** W95-R1: all 64 combinations of (durable, applied, operational, writer unavailable, held, writer unknown). */
    @Test void progressPinsEveryCombinationAndNeverSaysNoFuelMovedAfterTheDebit() {
        var counts = new EnumMap<FluidFuelLoaderStatus, Integer>(FluidFuelLoaderStatus.class);
        for (int bits = 0; bits < 64; bits++) {
            boolean durable = (bits & 1) != 0, applied = (bits & 2) != 0, operational = (bits & 4) != 0;
            boolean unavailable = (bits & 8) != 0, held = (bits & 16) != 0, unknown = (bits & 32) != 0;
            var status = EscrowLoaderAdmission.progress(durable, applied, operational, unavailable, held, unknown);
            String label = "durable=" + durable + " applied=" + applied + " operational=" + operational + " unavailable=" + unavailable
                    + " held=" + held + " unknown=" + unknown;
            counts.merge(status, 1, Integer::sum);
            if (!operational || unavailable) {
                // A failed baseline, a failed bind-time readback or three unresolved writes.
                assertEquals(applied ? FluidFuelLoaderStatus.ESCROW_PENDING : FluidFuelLoaderStatus.LEDGER_UNAVAILABLE, status, label);
            } else if (!applied) {
                assertEquals(durable && held ? FluidFuelLoaderStatus.ESCROW_PENDING : FluidFuelLoaderStatus.PREPARING, status, label);
            } else {
                assertEquals(durable && !held && !unknown ? FluidFuelLoaderStatus.TRANSFERRING : FluidFuelLoaderStatus.ESCROW_PENDING, status, label);
            }
            // PREPARING and LEDGER_UNAVAILABLE say that no fuel moved; neither may follow the device's own debit.
            if (applied) {
                assertNotEquals(FluidFuelLoaderStatus.PREPARING, status, label);
                assertNotEquals(FluidFuelLoaderStatus.LEDGER_UNAVAILABLE, status, label);
            } else {
                assertNotEquals(FluidFuelLoaderStatus.TRANSFERRING, status, label);
            }
        }
        assertEquals(Map.of(FluidFuelLoaderStatus.LEDGER_UNAVAILABLE, 24, FluidFuelLoaderStatus.ESCROW_PENDING, 33,
                FluidFuelLoaderStatus.PREPARING, 6, FluidFuelLoaderStatus.TRANSFERRING, 1), counts);
        // B3-S1: three unresolved creation-baseline outcomes leave the writer unavailable before any L1.
        assertEquals(FluidFuelLoaderStatus.LEDGER_UNAVAILABLE, EscrowLoaderAdmission.progress(false, false, true, true, false, true));
        // The review's trigger: a bind-time readback fails while a debited fill is held.
        assertEquals(FluidFuelLoaderStatus.ESCROW_PENDING, EscrowLoaderAdmission.progress(true, true, false, false, false, false));
        // A pending baseline or an UNKNOWN L1 is still PREPARING; an UNKNOWN write after the debit is ESCROW_PENDING.
        assertEquals(FluidFuelLoaderStatus.PREPARING, EscrowLoaderAdmission.progress(false, false, true, false, false, true));
        assertEquals(FluidFuelLoaderStatus.ESCROW_PENDING, EscrowLoaderAdmission.progress(true, true, true, false, false, true));
        assertEquals(FluidFuelLoaderStatus.TRANSFERRING, EscrowLoaderAdmission.progress(true, true, true, false, false, false));
    }

    @Test void theFairQueueVisitsInArrivalOrderOncePerDeviceWithOneCapturePerTick() {
        var queue = new EscrowFillQueue(); var visited = new ArrayList<Integer>();
        for (int i = 0; i < 12; i++) {
            int device = i; boolean captures = i == 2 || i == 9;
            assertTrue(queue.offer(EscrowTestFixtures.id(i), () -> { visited.add(device); return captures; }));
        }
        assertTrue(queue.offer(EscrowTestFixtures.id(0), () -> fail("A waiting device is never queued twice")));
        assertEquals(12, queue.size());
        // The first capture ends the tick; refusals before it do not.
        assertEquals(3, queue.visit()); assertEquals(List.of(0, 1, 2), visited);
        // At most eight visits per tick when nothing captures; visited entries leave whatever their outcome.
        visited.clear(); assertEquals(7, queue.visit()); assertEquals(List.of(3, 4, 5, 6, 7, 8, 9), visited);
        visited.clear(); assertEquals(2, queue.visit()); assertEquals(List.of(10, 11), visited);
        assertEquals(0, queue.size()); assertEquals(0, queue.visit());
        // A refused device offers again at its next paced attempt and joins the back of the queue.
        assertTrue(queue.offer(EscrowTestFixtures.id(1), () -> { visited.add(1); return false; }));
        assertTrue(queue.waiting(EscrowTestFixtures.id(1)));
    }

    @Test void theFairQueueIsBoundedAndTracksOnlyItsOwnPreparations() {
        var queue = new EscrowFillQueue();
        for (int i = 0; i < EscrowFillQueue.CAPACITY; i++) { assertTrue(queue.offer(EscrowTestFixtures.id(i), () -> false)); }
        assertFalse(queue.offer(EscrowTestFixtures.id(10_000), () -> false), "A full queue refuses; the device retries next interval");
        int visits = 0; while (queue.size() > 0) { visits += queue.visit(); }
        assertEquals(EscrowFillQueue.CAPACITY, visits);
        assertFalse(queue.preparing());
        queue.preparing(EscrowTestFixtures.id(1)); queue.preparing(EscrowTestFixtures.id(2)); assertTrue(queue.preparing());
        queue.resolved(EscrowTestFixtures.id(1)); assertTrue(queue.preparing());
        queue.resolved(EscrowTestFixtures.id(2)); assertFalse(queue.preparing());
        queue.offer(EscrowTestFixtures.id(3), () -> false); queue.preparing(EscrowTestFixtures.id(4)); queue.clear();
        assertEquals(0, queue.size()); assertFalse(queue.preparing());
    }

    @Test void theFairQueueRequiresItsOwnerThread() throws Exception {
        var queue = new EscrowFillQueue(); var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try { queue.offer(EscrowTestFixtures.id(1), () -> false); } catch (Throwable refused) { failure.set(refused); }
        });
        thread.start(); thread.join();
        assertInstanceOf(IllegalArgumentException.class, failure.get()); assertEquals(0, queue.size());
    }
}
