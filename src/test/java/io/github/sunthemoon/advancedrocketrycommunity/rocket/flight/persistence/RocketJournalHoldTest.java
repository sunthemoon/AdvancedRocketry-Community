package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;

class RocketJournalHoldTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void selectionWaitsForOnlineReleaseWithoutChangingPayload() throws Exception {
        var hold = new RocketJournalHold(5_000); var id = UUID.randomUUID();
        var before = root(id, "PREPARED"); var after = root(id, "COMMITTED"); var copy = after.copy();
        hold.select(id);
        var write = CompletableFuture.runAsync(() -> hold.beforeReplace(after, before));
        try {
            awaitWaiting(hold); assertFalse(write.isDone());
            assertThrows(IllegalStateException.class, () -> hold.select(UUID.randomUUID()));
            hold.release(); write.get(2, TimeUnit.SECONDS);
            assertEquals(copy, after); assertFalse(hold.waiting());
            hold.beforeReplace(after, before); // Single-use; a later generation cannot be held again.
        } finally { hold.release(); write.get(6, TimeUnit.SECONDS); }
    }

    @Test void boundAutomaticallyReleasesAndCannotBeRaised() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new RocketJournalHold(60_001));
        assertThrows(IllegalArgumentException.class, () -> new RocketJournalHold(0));
        var hold = new RocketJournalHold(40); var id = UUID.randomUUID(); hold.select(id);
        long start = System.nanoTime(); hold.beforeReplace(root(id, "PREPARED"), null);
        assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(40));
        assertFalse(hold.waiting()); hold.select(id); hold.release();
    }

    @Test void unrelatedAndIdenticalGenerationsDoNotWaitButRemovalDoes() throws Exception {
        var hold = new RocketJournalHold(5_000); var id = UUID.randomUUID(); hold.select(id);
        var before = root(id, "PREPARED");
        var unrelated = root(UUID.randomUUID(), "COMMITTED");
        hold.beforeReplace(unrelated, null); hold.beforeReplace(before.copy(), before);
        assertFalse(hold.waiting());
        var removal = CompletableFuture.runAsync(() -> hold.beforeReplace(new CompoundTag(), before));
        try { awaitWaiting(hold); assertFalse(removal.isDone()); }
        finally { hold.release(); removal.get(6, TimeUnit.SECONDS); }
    }

    @Test void releaseBeforeWriterArrivesDoesNotDelayIt() {
        var hold = new RocketJournalHold(5_000); var id = UUID.randomUUID();
        hold.select(id); hold.release(); hold.beforeReplace(root(id, "PREPARED"), null);
        assertFalse(hold.waiting());
    }

    @Test void interruptionDoesNotCancelOrPoisonTheCheckedCommit() throws Exception {
        var hold = new RocketJournalHold(5_000); var id = UUID.randomUUID(); hold.select(id);
        var committed = new java.util.concurrent.atomic.AtomicBoolean();
        var interruptedAfter = new java.util.concurrent.atomic.AtomicBoolean();
        var writer = new Thread(() -> {
            hold.beforeReplace(root(id, "PREPARED"), null);
            interruptedAfter.set(Thread.currentThread().isInterrupted()); committed.set(true);
        });
        writer.start();
        try {
            awaitWaiting(hold); writer.interrupt(); Thread.sleep(20);
            assertFalse(committed.get()); hold.release(); writer.join(2_000);
            assertTrue(committed.get()); assertFalse(interruptedAfter.get());
        } finally { hold.release(); writer.join(6_000); }
    }

    private static void awaitWaiting(RocketJournalHold hold) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!hold.waiting() && System.nanoTime() < deadline) { Thread.sleep(1); }
        assertTrue(hold.waiting());
    }
    private static CompoundTag root(UUID logical, String phase) {
        var root = new CompoundTag(); var records = new ListTag(); var record = new CompoundTag();
        record.putUUID("logical_rocket_id", logical); record.putString("phase", phase);
        records.add(record); root.put("transfers", records); return root;
    }
}
