package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.TagType;
import net.minecraft.nbt.visitors.SkipAll;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-073: a read that a pending chunk write serves is a copy, so changing it cannot change what is written. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IOWorkerPendingReadGameTests {
    // Far from every GameTest structure, inside the world border, and both in region file r.31250.31250.
    private static final ChunkPos GATE = new ChunkPos(1_000_000, 1_000_000);
    private static final ChunkPos PROBE = new ChunkPos(1_000_001, 1_000_000);
    private static final long WAIT_SECONDS = 5;

    private IOWorkerPendingReadGameTests() { }

    @GameTest(template = "empty", batch = "ioworker_pending_read", timeoutTicks = 100)
    public static void readOfAPendingWriteIsACopy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.getServer().isSameThread(), "The IO worker fixture needs the server thread");
        if (!(level.getChunkSource().chunkMap.chunkScanner() instanceof IOWorker worker)) {
            throw new IllegalStateException("The level's chunk storage is not backed by an IOWorker");
        }
        CompoundTag stored = chunkTag(PROBE);
        CompoundTag expected = stored.copy();
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean held = new AtomicBoolean();
        AtomicBoolean timedOut = new AtomicBoolean();
        try {
            // The worker runs its queued tasks in order and its disk writes only when that queue is empty. Holding
            // it inside a scan queues the store and the read back to back, so the read finds the write pending.
            worker.store(GATE, chunkTag(GATE));
            CompletableFuture<Void> scan = worker.scanChunk(GATE, new HoldingVisitor(release, held, timedOut));
            CompletableFuture<Void> write = worker.store(PROBE, stored);
            CompletableFuture<Optional<CompoundTag>> pendingRead = worker.loadAsync(PROBE);
            release.countDown();
            await(scan);
            helper.assertTrue(held.get() && !timedOut.get(), "The scan did not hold the worker while the read was queued");

            CompoundTag read = await(pendingRead).orElseThrow(() -> new IllegalStateException("No pending data"));
            helper.assertTrue(read.equals(expected), "The pending read differs from the stored tag");
            helper.assertTrue(read != stored, "The pending read returned the tag that is being written");
            // ChunkStorage.upgradeChunkTag puts __context on the read tag in the same way.
            read.putString("__context", "changed by the reader");
            read.getCompound("probe").putInt("value", -1);
            helper.assertTrue(stored.equals(expected), "Changing the read changed the pending tag");

            await(write);
            await(worker.synchronize(true));
            CompoundTag written = await(worker.loadAsync(PROBE)).orElseThrow(
                    () -> new IllegalStateException("The flushed chunk is missing"));
            helper.assertTrue(written.equals(expected), "The written chunk contains the reader's change");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("IO worker fixture interrupted", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("IO worker fixture failed", failure);
        } finally {
            release.countDown();
            // Null writes clear both far-away chunks from the region file again.
            worker.store(GATE, null);
            worker.store(PROBE, null);
        }
        helper.succeed();
    }

    private static CompoundTag chunkTag(ChunkPos pos) {
        CompoundTag probe = new CompoundTag();
        probe.putInt("value", 73);
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        root.putInt("xPos", pos.x);
        root.putInt("zPos", pos.z);
        root.putString("Status", "minecraft:empty");
        root.put("probe", probe);
        return root;
    }

    private static <T> T await(CompletableFuture<T> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /** Holds the IO thread at the scanned root tag until the fixture has queued its store and read. */
    private record HoldingVisitor(CountDownLatch release, AtomicBoolean held, AtomicBoolean timedOut)
            implements SkipAll {
        @Override
        public StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type) {
            held.set(true);
            try {
                timedOut.set(!release.await(WAIT_SECONDS, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                timedOut.set(true);
            }
            return StreamTagVisitor.ValueResult.HALT;
        }
    }
}
