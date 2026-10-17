package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** W11b3 B3-E: prepareFill's typed results as player status, the disabled production switch and the fair queue. */
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
