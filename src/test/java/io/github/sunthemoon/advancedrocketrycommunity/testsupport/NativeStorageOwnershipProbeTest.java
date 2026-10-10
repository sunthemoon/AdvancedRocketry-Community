package io.github.sunthemoon.advancedrocketrycommunity.testsupport;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bounded native API observations, not a chunk-save race or durability proof. */
final class NativeStorageOwnershipProbeTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path directory;

    @Test
    @Timeout(60)
    void observeSubmittedAndLoadedReferencesWithoutMutatingPublishedTags() throws Exception {
        long callerThread = Thread.currentThread().getId();
        try (ProbeWorker worker = new ProbeWorker(directory)) {
            for (int index = 0; index < 4; index++) {
                ChunkPos position = new ChunkPos(index, 0);
                ObservedTag child = new ObservedTag();
                child.putInt("child_marker", 17 + index);
                ObservedTag submitted = new ObservedTag();
                submitted.putInt("root_marker", 11 + index);
                submitted.put("child", child);

                // Everything below observes references or reads values. No published tree is mutated.
                CompletableFuture<Void> stored = worker.store(position, submitted);
                Optional<CompoundTag> initial = await(worker.loadAsync(position));
                boolean initialRootIdentity = initial.orElse(null) == submitted;
                boolean initialChildIdentity = initial.isPresent()
                        && initial.get().get("child") == child;
                if (initial.isPresent()) {
                    assertMarkers(initial.get(), index);
                }
                await(stored);
                await(worker.synchronize(true));
                CompoundTag afterStore = await(worker.loadAsync(position)).orElseThrow();
                assertMarkers(afterStore, index);

                System.out.printf("NATIVE_STORAGE_OBSERVATION index=%d initial_present=%s "
                                + "initial_root_identity=%s initial_child_identity=%s "
                                + "after_root_identity=%s after_child_identity=%s "
                                + "root_copy_calls=%d child_copy_calls=%d "
                                + "root_write_calls=%d child_write_calls=%d "
                                + "root_write_on_caller=%s child_write_on_caller=%s%n",
                        index, initial.isPresent(), initialRootIdentity, initialChildIdentity,
                        afterStore == submitted, afterStore.get("child") == child,
                        submitted.copyCalls.get(), child.copyCalls.get(),
                        submitted.writeCalls.get(), child.writeCalls.get(),
                        submitted.firstWriteThread.get() == callerThread,
                        child.firstWriteThread.get() == callerThread);
            }
        }

        // Reopening crosses the worker lifetime, but says nothing about power-loss durability.
        try (ProbeWorker reopened = new ProbeWorker(directory)) {
            for (int index = 0; index < 4; index++) {
                assertMarkers(await(reopened.loadAsync(new ChunkPos(index, 0))).orElseThrow(), index);
            }
        }
        System.out.println("NATIVE_STORAGE_OBSERVATION workers_closed=true reopened_records=4");
    }

    private static void assertMarkers(CompoundTag tag, int index) {
        assertEquals(11 + index, tag.getInt("root_marker"));
        assertTrue(tag.contains("child", CompoundTag.TAG_COMPOUND));
        assertEquals(17 + index, tag.getCompound("child").getInt("child_marker"));
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }

    /** Protected construction is the only native API exposed by this test adapter. */
    private static final class ProbeWorker extends IOWorker {
        private ProbeWorker(Path directory) {
            super(directory, true, "arce-ownership-probe");
        }
    }

    /** Counters are outside NBT; callbacks delegate without changing its contents or scheduling. */
    private static final class ObservedTag extends CompoundTag {
        private final AtomicInteger copyCalls = new AtomicInteger();
        private final AtomicInteger writeCalls = new AtomicInteger();
        private final AtomicLong firstWriteThread = new AtomicLong(-1);

        @Override
        public CompoundTag copy() {
            copyCalls.incrementAndGet();
            return super.copy();
        }

        @Override
        public void write(DataOutput output) throws IOException {
            writeCalls.incrementAndGet();
            firstWriteThread.compareAndSet(-1, Thread.currentThread().getId());
            super.write(output);
        }
    }
}
