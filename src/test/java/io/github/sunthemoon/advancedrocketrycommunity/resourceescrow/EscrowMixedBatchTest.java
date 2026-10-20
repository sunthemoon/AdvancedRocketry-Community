package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * W116 (W98-R10): in a barrier batch with governed and ordinary attempts, an ordinary own-store or force failure fails
 * only the ordinary attempts and never counts in the governed {@code ioFailures}; a governed failure still fails the
 * whole batch. Real {@link IOWorker}s write the region files that the readback reads.
 */
@Timeout(60)
class EscrowMixedBatchTest {
    private static final String LEVEL = "minecraft:overworld";
    @TempDir Path directory;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void anOrdinaryStoreFailureLeavesTheGovernedAttemptToConfirmUncounted() throws Exception {
        var governedWorker = worker(directory.resolve("governed"), false);
        try (var barrier = new EscrowBarrier()) {
            var governed = new ArrayList<EscrowBarrier.Result>(); var ordinary = new ArrayList<EscrowBarrier.Result>();
            assertTrue(barrier.stageForTests(LEVEL, false, stored(governedWorker, directory.resolve("governed"), new ChunkPos(0, 0)),
                    EscrowMixedBatchTest::marked, 0, label -> { }, governed::add));
            assertTrue(barrier.stageForTests(LEVEL, true, failedStore(governedWorker, directory.resolve("governed"), new ChunkPos(1, 0)),
                    root -> true, 1, label -> { }, ordinary::add));
            await(barrier, () -> governed.size() + ordinary.size() == 2);
            assertTrue(governed.get(0).confirmed(), "An ordinary store failure failed the governed attempt: " + governed.get(0).reason());
            assertEquals(EscrowReason.BARRIER_IO, ordinary.get(0).reason());
            assertEquals(0, barrier.ioFailures(), "An ordinary store failure counted in the governed IO failures");
            governed.get(0).proof().release();
        } finally { governedWorker.close(); }
    }

    @Test void anOrdinaryWorkersForceFailureLeavesTheGovernedAttemptToConfirmUncounted() throws Exception {
        var governedWorker = worker(directory.resolve("governed"), false);
        var ordinaryWorker = worker(directory.resolve("ordinary"), true);
        try (var barrier = new EscrowBarrier()) {
            var governed = new ArrayList<EscrowBarrier.Result>(); var ordinary = new ArrayList<EscrowBarrier.Result>();
            var labels = new ArrayList<String>();
            assertTrue(barrier.stageForTests(LEVEL, false, stored(governedWorker, directory.resolve("governed"), new ChunkPos(0, 0)),
                    EscrowMixedBatchTest::marked, 0, labels::add, governed::add));
            assertTrue(barrier.stageForTests(LEVEL, true, stored(ordinaryWorker, directory.resolve("ordinary"), new ChunkPos(1, 0)),
                    EscrowMixedBatchTest::marked, 1, label -> { }, ordinary::add));
            await(barrier, () -> governed.size() + ordinary.size() == 2);
            assertTrue(governed.get(0).confirmed(), "An ordinary worker's force failure failed the governed attempt: " + governed.get(0).reason());
            assertEquals(EscrowReason.BARRIER_FORCE, ordinary.get(0).reason());
            assertEquals(0, barrier.ioFailures(), "An ordinary force failure counted in the governed IO failures");
            assertEquals(List.of("BARRIER_STORE_AFTER", "BARRIER_FORCE_BEFORE", "K6", "BARRIER_FORCE_AFTER",
                    "BARRIER_READBACK_BEFORE", "BARRIER_READBACK_AFTER"), labels, "The governed checkpoint's labels");
            governed.get(0).proof().release();
        } finally { governedWorker.close(); ordinaryWorker.close(); }
    }

    @Test void aGovernedStoreFailureStillFailsTheWholeMixedBatchAndCountsOnce() throws Exception {
        var shared = worker(directory.resolve("shared"), false);
        try (var barrier = new EscrowBarrier()) {
            var governed = new ArrayList<EscrowBarrier.Result>(); var ordinary = new ArrayList<EscrowBarrier.Result>();
            assertTrue(barrier.stageForTests(LEVEL, false, failedStore(shared, directory.resolve("shared"), new ChunkPos(0, 0)),
                    root -> true, 0, label -> { }, governed::add));
            assertTrue(barrier.stageForTests(LEVEL, true, stored(shared, directory.resolve("shared"), new ChunkPos(1, 0)),
                    EscrowMixedBatchTest::marked, 1, label -> { }, ordinary::add));
            await(barrier, () -> governed.size() + ordinary.size() == 2);
            assertEquals(EscrowReason.BARRIER_IO, governed.get(0).reason());
            assertEquals(EscrowReason.BARRIER_IO, ordinary.get(0).reason(), "An ordinary attempt still fails on any failure in its batch");
            assertEquals(1, barrier.ioFailures(), "Only the governed attempt counts");
        } finally { shared.close(); }
    }

    @Test void anAllGovernedBatchStillFailsEveryAttemptOnOneStoreFailure() throws Exception {
        var shared = worker(directory.resolve("shared"), false);
        try (var barrier = new EscrowBarrier()) {
            var results = new ArrayList<EscrowBarrier.Result>();
            assertTrue(barrier.stageForTests(LEVEL, false, failedStore(shared, directory.resolve("shared"), new ChunkPos(0, 0)),
                    root -> true, 0, label -> { }, results::add));
            assertTrue(barrier.stageForTests(LEVEL, false, stored(shared, directory.resolve("shared"), new ChunkPos(1, 0)),
                    EscrowMixedBatchTest::marked, 1, label -> { }, results::add));
            await(barrier, () -> results.size() == 2);
            assertTrue(results.stream().allMatch(result -> result.reason() == EscrowReason.BARRIER_IO), "Unchanged all-governed outcome");
            assertEquals(2, barrier.ioFailures());
        } finally { shared.close(); }
    }

    /** Ticks the barrier on this owner thread past its 20-tick batch until the callbacks have run, within a wall-clock bound. */
    private static void await(EscrowBarrier barrier, java.util.function.BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        for (long tick = 2; !done.getAsBoolean(); tick++) {
            assertTrue(System.nanoTime() < deadline, "The batch did not complete");
            barrier.tick(tick);
            if (tick > 20) { Thread.sleep(5); }
        }
    }

    private static boolean marked(CompoundTag root) { return root.getInt("w116_marker") == 7; }

    private static EscrowChunkStore.Capture stored(IOWorker worker, Path folder, ChunkPos chunk) {
        var tag = new CompoundTag(); tag.putInt("w116_marker", 7); tag.put("Entities", new ListTag());
        return capture(worker.store(chunk, tag), worker, folder, chunk);
    }

    private static EscrowChunkStore.Capture failedStore(IOWorker worker, Path folder, ChunkPos chunk) {
        return capture(CompletableFuture.failedFuture(new IOException("Injected own-store failure")), worker, folder, chunk);
    }

    private static EscrowChunkStore.Capture capture(CompletableFuture<Void> stored, IOWorker worker, Path folder, ChunkPos chunk) {
        return new EscrowChunkStore.Capture(stored, worker, folder, chunk, new AtomicLong(1), 1, 0, () -> { }, () -> { });
    }

    /** A real worker over {@code folder}; with {@code failingForce} its force fails, as an unwritable disk would. */
    private static IOWorker worker(Path folder, boolean failingForce) {
        return failingForce ? new IOWorker(folder, false, "w116-ordinary") {
            @Override public CompletableFuture<Void> synchronize(boolean flush) {
                return CompletableFuture.failedFuture(new IOException("Injected force failure"));
            }
        } : new IOWorker(folder, false, "w116-governed") { };
    }
}
