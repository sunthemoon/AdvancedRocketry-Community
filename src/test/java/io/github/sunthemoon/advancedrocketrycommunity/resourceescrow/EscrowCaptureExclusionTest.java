package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCaptureExclusionTest {
    private static final String LEVEL = "minecraft:overworld";
    private static final ChunkPos SLOT = new ChunkPos(3, 4);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void continuousOrdinaryDemandDoesNotSpendBudgetOrSupersedeTheGovernedWatch() {
        var monitor = new EscrowSaveMonitor();
        // watch is the baseline governed capture's exact generation acquisition.
        var generation = monitor.watch(LEVEL, true, SLOT); long captured = generation.get();
        var store = new EscrowChunkStore(null, monitor);
        var labels = new ArrayList<String>(); var replies = new ArrayList<EscrowBarrier.Result>();
        try (var barrier = new EscrowBarrier()) {
            for (int tick = 0; tick < 100; tick++) {
                assertFalse(barrier.beginOrdinary(store, LEVEL, SLOT, root -> fail("Deferred serialization"),
                        tick, labels::add, replies::add), "Protected ordinary capture was admitted at " + tick);
                assertTrue(barrier.availableOrdinary(LEVEL, tick), "Deferral spent capture budget");
                assertEquals(captured, generation.get());
            }
            assertTrue(labels.isEmpty()); assertTrue(replies.isEmpty());
            assertEquals(0, barrier.ioFailures() + barrier.refusals() + barrier.supersedingSaves());
            monitor.release(LEVEL, true, SLOT);
            // With no Level this deliberately fails at serialization, but only after admission.
            assertTrue(barrier.beginOrdinary(store, LEVEL, SLOT, root -> true, 100, labels::add, replies::add));
            assertEquals(1, labels.size()); assertEquals(1, replies.size());
            assertEquals(EscrowReason.BARRIER_VETO, replies.get(0).reason());
        }
    }

    @Test void authoritativeAdmissionRejectsAStaleHintBeforeReserving() {
        var monitor = new EscrowSaveMonitor(); var store = new EscrowChunkStore(null, monitor);
        try (var barrier = new EscrowBarrier()) {
            assertTrue(barrier.availableOrdinary(store, LEVEL, SLOT, 0));
            var pin = monitor.captureWatch(LEVEL, true, SLOT, new AtomicBoolean(true));
            assertFalse(barrier.beginOrdinary(store, LEVEL, SLOT, root -> fail(), 0, label -> fail(), result -> fail()));
            assertNull(monitor.ordinaryWatch(LEVEL, SLOT, () -> fail("Pin reached reservation")));
            assertTrue(barrier.availableOrdinary(LEVEL, 0));
            pin.release();
            assertTrue(barrier.availableOrdinary(store, LEVEL, SLOT, 0));
        }
    }

    @Test void lastJointChildOwnsThePinAndReleasesAreIdempotent() {
        var monitor = new EscrowSaveMonitor(); var lifetime = new AtomicBoolean(true);
        var handle = monitor.captureWatch(LEVEL, true, SLOT, lifetime);
        var original = proof(handle, lifetime);
        var a = EscrowTestFixtures.operation(1); var b = EscrowTestFixtures.operation(2);
        var children = new ArrayList<>(EscrowBarrier.splitProof(original, List.of(
                new EscrowBarrier.Target(a.destination(), null, root -> true),
                new EscrowBarrier.Target(b.destination(), null, root -> true))).values());
        children.get(1).release(); children.get(1).release();
        assertFalse(monitor.ordinaryAvailable(LEVEL, SLOT)); assertTrue(children.get(0).valid());
        children.get(0).release(); children.get(0).release(); original.release();
        assertTrue(monitor.ordinaryAvailable(LEVEL, SLOT));
        var next = monitor.captureWatch(LEVEL, true, SLOT, lifetime);
        handle.release();
        assertFalse(monitor.ordinaryAvailable(LEVEL, SLOT)); next.release();
    }

    @Test void genuineSavesAndNewGovernedCapturesStillInvalidateAndOldReleasesDoNotUnpinNewOnes() {
        var monitor = new EscrowSaveMonitor(); var lifetime = new AtomicBoolean(true);
        var old = monitor.captureWatch(LEVEL, true, SLOT, lifetime); var first = proof(old, lifetime);
        monitor.observed(LEVEL, true, SLOT);
        assertFalse(first.valid()); assertTrue(monitor.ordinaryAvailable(LEVEL, SLOT));
        var newer = monitor.captureWatch(LEVEL, true, SLOT, lifetime); var second = proof(newer, lifetime);
        first.release(); assertFalse(monitor.ordinaryAvailable(LEVEL, SLOT)); assertTrue(second.valid());
        var newest = monitor.captureWatch(LEVEL, true, SLOT, lifetime);
        assertFalse(second.valid()); second.release();
        assertFalse(monitor.ordinaryAvailable(LEVEL, SLOT)); newest.release();
        assertTrue(monitor.ordinaryAvailable(LEVEL, SLOT));
    }

    @Test void levelChunkAndTerrainAreIndependentAndClearDoesNotLetOldHandlesReleaseRecreatedWatches() {
        var monitor = new EscrowSaveMonitor(); var lifetime = new AtomicBoolean(true);
        var old = monitor.captureWatch(LEVEL, true, SLOT, lifetime); var original = proof(old, lifetime);
        var otherLevel = monitor.ordinaryWatch("moon", SLOT, () -> true);
        var otherChunk = monitor.ordinaryWatch(LEVEL, new ChunkPos(4, 4), () -> true);
        var terrain = monitor.captureWatch(LEVEL, false, SLOT, lifetime);
        monitor.observed(LEVEL, false, SLOT);
        assertTrue(original.valid()); assertNotNull(otherLevel); assertNotNull(otherChunk);
        otherLevel.release(); otherChunk.release(); terrain.release();
        monitor.clear(); assertFalse(original.valid());
        var recreated = monitor.captureWatch(LEVEL, true, SLOT, lifetime);
        original.release(); assertFalse(monitor.ordinaryAvailable(LEVEL, SLOT));
        recreated.release(); assertTrue(monitor.ordinaryAvailable(LEVEL, SLOT));
    }

    @Test void aFailedOrdinaryCaptureReleasesItsOwnWatchWithoutChangingQuotaSemantics() {
        var monitor = new EscrowSaveMonitor(); var store = new EscrowChunkStore(null, monitor);
        try (var barrier = new EscrowBarrier()) {
            for (int tick = 0; tick < 4; tick++) {
                assertTrue(barrier.beginOrdinary(store, LEVEL, SLOT, root -> true, tick, label -> { },
                        result -> assertEquals(EscrowReason.BARRIER_VETO, result.reason())));
            }
            assertFalse(barrier.availableOrdinary(LEVEL, 4));
            // All 128 monitored slots remain available after those failed serializations.
            var handles = new ArrayList<EscrowSaveMonitor.Handle>();
            for (int i = 0; i < 128; i++) { handles.add(monitor.captureWatch(LEVEL, true, new ChunkPos(i, 0), new AtomicBoolean(true))); }
            assertThrows(EscrowChunkStore.CaptureBounds.class,
                    () -> monitor.captureWatch(LEVEL, true, new ChunkPos(128, 0), new AtomicBoolean(true)));
            handles.forEach(EscrowSaveMonitor.Handle::release);
        }
    }

    @Test void refusedBudgetDoesNotAcquireAWatchOrIncrementAnExistingGeneration() {
        var monitor = new EscrowSaveMonitor();
        var ordinary = monitor.ordinaryWatch(LEVEL, SLOT, () -> true);
        assertNull(monitor.ordinaryWatch(LEVEL, SLOT, () -> false));
        assertEquals(ordinary.capturedGeneration(), ordinary.generation().get()); ordinary.release();
    }

    static EscrowBarrier.Proof proof(EscrowSaveMonitor.Handle handle, AtomicBoolean lifetime) {
        var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, Path.of("."), SLOT,
                handle.generation(), handle.capturedGeneration(), 0, () -> { }, handle::release);
        return new EscrowBarrier.Proof(null, capture, new AtomicBoolean(true), lifetime);
    }
}
