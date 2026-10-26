package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(60)
class EscrowPinnedBarrierTest {
    private static final String LEVEL = "minecraft:overworld";
    private static final ChunkPos SLOT = new ChunkPos(0, 0);
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void otherSlotsStoreAndForceJoinDelayReadbackWithoutAnotherGovernedCapture() throws Exception {
        var monitor = new EscrowSaveMonitor(); var lifetime = new AtomicBoolean(true);
        var store = new EscrowChunkStore(null, monitor); var gate = new CompletableFuture<Void>();
        var storeGate = new CompletableFuture<Void>(); var forces = new AtomicInteger(); var reads = new AtomicInteger();
        var results = new ArrayList<EscrowBarrier.Result>(); var ordinaryResults = new ArrayList<EscrowBarrier.Result>();
        try (var worker = new IOWorker(directory, false, "w151") {
            @Override public CompletableFuture<Void> synchronize(boolean flush) {
                forces.incrementAndGet(); return super.synchronize(flush).thenCompose(done -> gate);
            }
        }; var barrier = new EscrowBarrier(label -> { }, lifetime)) {
            var handle = monitor.captureWatch(LEVEL, true, SLOT, lifetime);
            var data = new CompoundTag(); data.putInt("value", 7);
            var saved = worker.store(SLOT, data); saved.get(10, TimeUnit.SECONDS);
            var captured = new EscrowChunkStore.Capture(saved, worker, directory, SLOT, handle.generation(),
                    handle.capturedGeneration(), 10, () -> { }, handle::release);
            assertTrue(barrier.stageForTests(LEVEL, false, captured, root -> root.getInt("value") == 7, 0,
                    label -> { if (label.equals("BARRIER_READBACK_BEFORE")) { reads.incrementAndGet(); } }, results::add));
            var other = new ChunkPos(1, 0); var ordinaryHandle = monitor.ordinaryWatch(LEVEL, other, () -> true);
            var diskStore = worker.store(other, data); diskStore.get(10, TimeUnit.SECONDS);
            var otherSaved = diskStore.thenCompose(done -> storeGate);
            var ordinary = new EscrowChunkStore.Capture(otherSaved, worker, directory, other, ordinaryHandle.generation(),
                    ordinaryHandle.capturedGeneration(), 10, () -> { }, ordinaryHandle::release);
            assertTrue(barrier.stageForTests(LEVEL, true, ordinary, root -> true, 1, label -> { }, ordinaryResults::add));
            for (int tick = 2; tick < 100; tick++) {
                refused(barrier, store, tick); barrier.tick(tick);
            }
            assertTrue(results.isEmpty()); assertEquals(0, forces.get()); assertEquals(0, reads.get());
            storeGate.complete(null);
            assertEquals(1, forces.get());
            for (int tick = 100; tick < 200; tick++) { refused(barrier, store, tick); barrier.tick(tick); }
            assertTrue(results.isEmpty()); assertEquals(0, reads.get());
            gate.complete(null);
            await(barrier, () -> results.size() == 1 && ordinaryResults.size() == 1);
            assertEquals(1, reads.get()); assertEquals(1, forces.get()); assertTrue(results.get(0).confirmed());
            assertEquals(handle.capturedGeneration(), handle.generation().get());
            // Completed callback releases pending-byte charge, not the retained proof's pin.
            for (int tick = 500; tick < 600; tick++) { refused(barrier, store, tick); }
            barrier.quiesce(); assertTrue(results.get(0).proof().valid()); assertFalse(monitor.ordinaryAvailable(LEVEL, SLOT));
            barrier.close(); assertFalse(results.get(0).proof().valid()); assertTrue(monitor.ordinaryAvailable(LEVEL, SLOT));
            results.get(0).proof().release(); ordinaryResults.get(0).proof().release();
        } finally { gate.complete(null); storeGate.complete(null); }
    }

    @Test void failedStoreFailedReadbackStaleCallbackAndQuiesceReleaseOnlyTheirHandles() throws Exception {
        for (String failure : java.util.List.of("store", "force", "readback", "callback", "quiesce")) {
            var monitor = new EscrowSaveMonitor(); var lifetime = new AtomicBoolean(true);
            var handle = monitor.captureWatch(LEVEL, true, SLOT, lifetime); var results = new ArrayList<EscrowBarrier.Result>();
            Path folder = directory.resolve(failure);
            try (var worker = new IOWorker(folder, false, "w151-" + failure) {
                @Override public CompletableFuture<Void> synchronize(boolean flush) {
                    return failure.equals("force") ? CompletableFuture.failedFuture(new java.io.IOException("force")) : super.synchronize(flush);
                }
            }; var barrier = new EscrowBarrier(label -> { }, lifetime)) {
                var saved = failure.equals("store") ? CompletableFuture.<Void>failedFuture(new java.io.IOException("store"))
                        : worker.store(SLOT, new CompoundTag());
                var capture = new EscrowChunkStore.Capture(saved, worker, folder, SLOT, handle.generation(),
                        handle.capturedGeneration(), 0, () -> { }, handle::release);
                assertTrue(barrier.stageForTests(LEVEL, false, capture, root -> !failure.equals("readback"), 0,
                        label -> { if (failure.equals("callback") && label.equals("BARRIER_READBACK_AFTER")) {
                            handle.generation().incrementAndGet();
                        } }, results::add));
                if (failure.equals("quiesce")) { barrier.quiesce(); }
                else {
                    await(barrier, () -> !results.isEmpty());
                    assertFalse(results.get(0).confirmed());
                    assertEquals(switch (failure) {
                        case "store" -> EscrowReason.BARRIER_IO;
                        case "force" -> EscrowReason.BARRIER_FORCE;
                        case "callback" -> EscrowReason.SUPERSEDING_SAVE;
                        default -> EscrowReason.BARRIER_UNSTABLE;
                    }, results.get(0).reason());
                }
                assertTrue(monitor.ordinaryAvailable(LEVEL, SLOT), failure);
                var next = monitor.captureWatch(LEVEL, true, SLOT, lifetime); handle.release();
                assertFalse(monitor.ordinaryAvailable(LEVEL, SLOT)); next.release();
            }
        }
    }

    private static void refused(EscrowBarrier barrier, EscrowChunkStore store, long tick) {
        assertFalse(barrier.beginOrdinary(store, LEVEL, SLOT, root -> fail(), tick, label -> fail(), result -> fail()));
    }
    private static void await(EscrowBarrier barrier, java.util.function.BooleanSupplier done) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        for (long tick = 200; !done.getAsBoolean(); tick++) {
            assertTrue(System.nanoTime() < end, "Barrier did not complete"); barrier.tick(tick); Thread.sleep(5);
        }
    }
}
