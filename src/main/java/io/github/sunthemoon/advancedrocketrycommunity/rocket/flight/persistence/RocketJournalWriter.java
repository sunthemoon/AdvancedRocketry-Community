package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** One immutable active snapshot and one successor; only the owner publishes acknowledgements. */
final class RocketJournalWriter implements AutoCloseable {
    record Snapshot(long sequence, List<RocketTransferRecord> records,
                    FirstEventOutbox firstMoonLanding) {
        Snapshot {
            if (sequence < 1 || records.size() > RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
                throw new IllegalArgumentException("Journal capture exceeds its fixed bound");
            }
            // Records, flights, snapshots and their nested resource values are immutable domain objects.
            // No NBT encoding, digest, world access or block traversal belongs in this capture.
            records = List.copyOf(records);
            // W44 section 2.4: the outbox is an immutable record with an immutable participant list, so capture stays O(1).
            java.util.Objects.requireNonNull(firstMoonLanding, "firstMoonLanding");
        }
        Snapshot(long sequence, List<RocketTransferRecord> records) {
            this(sequence, records, FirstEventOutbox.empty());
        }
    }
    record Completion(long sequence, RocketJournalCheckpoint.Outcome outcome,
                      RocketJournalCheckpoint.Acknowledgement acknowledged, long writerNanos) { }
    private record Request(Snapshot snapshot, CheckedSavedDataPolicy policy) { }
    static final int DRAIN_UNKNOWN_ATTEMPTS = 3;
    private final Thread owner = Thread.currentThread();
    private final Path file;
    private final RocketJournalCheckpoint disk = new RocketJournalCheckpoint();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "ARCE-rocket-journal"); thread.setDaemon(true); return thread;
    });
    private final ArrayBlockingQueue<Completion> completions = new ArrayBlockingQueue<>(1);
    private Request active, successor;
    private boolean running, closed, draining;
    private long tick, retryAt, lastSequence;
    private int drainUnknown;

    RocketJournalWriter(Path file) { this.file = file.toAbsolutePath().normalize(); }
    boolean request(Snapshot snapshot, CheckedSavedDataPolicy policy) {
        owner();
        if (closed || draining) { return false; }
        if (active != null && active.snapshot().sequence() == snapshot.sequence()) { return true; }
        if (successor != null && successor.snapshot().sequence() == snapshot.sequence()) { return true; }
        if (snapshot.sequence() < lastSequence) { throw new IllegalArgumentException("Journal generation moved backwards"); }
        if (active != null && successor != null) { return false; }
        lastSequence = snapshot.sequence();
        var request = new Request(snapshot, policy);
        if (active == null) { active = request; start(); } else { successor = request; }
        return true;
    }
    private void start() {
        if (running || active == null || tick < retryAt) { return; }
        running = true; Request request = active;
        io.execute(() -> {
            long started = System.nanoTime();
            RocketJournalCheckpoint.Outcome outcome;
            try {
                CompoundTag payload = RocketTransferSavedData.encode(request.snapshot().records(),
                        request.snapshot().firstMoonLanding(), new CompoundTag());
                var digests = new LinkedHashMap<UUID, String>();
                for (Tag raw : payload.getList("transfers", Tag.TAG_COMPOUND)) {
                    var record = (CompoundTag) raw;
                    digests.put(record.getUUID("transfer_id"), RocketJournalDigest.of(record,
                            RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES));
                }
                outcome = disk.write(file, request.policy(), request.snapshot().sequence(), payload, digests);
            } catch (RuntimeException refused) {
                // Encoding failed before replacement. Any previously unknown disk authority stays unknown.
                outcome = disk.outcome() == RocketJournalCheckpoint.Outcome.UNKNOWN
                        ? RocketJournalCheckpoint.Outcome.UNKNOWN : RocketJournalCheckpoint.Outcome.NOT_COMMITTED;
            } catch (Throwable unknown) {
                // An Error may occur after replacement. Never retain proof of the previous disk generation.
                outcome = disk.unknown();
            }
            completions.add(new Completion(request.snapshot().sequence(), outcome,
                    disk.acknowledged().orElse(null), System.nanoTime() - started));
        });
    }
    void tick(long now, Consumer<Completion> publish) {
        owner(); tick = now;
        Completion completion = completions.poll();
        if (completion != null) {
            if (active == null || completion.sequence() != active.snapshot().sequence()) {
                throw new IllegalStateException("Foreign journal completion");
            }
            running = false;
            if (completion.outcome() != RocketJournalCheckpoint.Outcome.COMMITTED) {
                retryAt = tick + 200;
                if (draining && ++drainUnknown >= DRAIN_UNKNOWN_ATTEMPTS) { active = null; successor = null; }
            } else {
                active = successor; successor = null; retryAt = 0; drainUnknown = 0;
            }
            publish.accept(completion);
        }
        start();
    }
    boolean idle() { owner(); return active == null; }
    void drain(Consumer<Completion> publish) {
        owner(); draining = true; drainUnknown = 0;
        // Continue the actual owner clock, including a long-lived server and failure backoff.
        long now = Math.max(tick, retryAt);
        while (!idle()) {
            tick(now++, publish);
            try { Thread.sleep(1); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("Journal drain interrupted", interrupted);
            }
        }
        draining = false;
    }
    void close(Consumer<Completion> publish) { owner(); if (!closed) { drain(publish); closed = true; join(); } }
    @Override public void close() { close(completion -> { }); }
    void abort() {
        owner(); if (closed) { return; }
        closed = true; active = null; successor = null; join(); completions.clear();
    }
    private void join() {
        io.shutdown();
        try { while (!io.awaitTermination(1, TimeUnit.SECONDS)) { /* Keep world ownership until IO stops. */ } }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
    private void owner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Journal writer requires its owner thread"); }
    }
}
