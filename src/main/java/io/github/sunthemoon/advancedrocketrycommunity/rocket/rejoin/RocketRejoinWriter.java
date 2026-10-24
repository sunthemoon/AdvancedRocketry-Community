package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;

/** Fixed-target writer: one active immutable root and one successor; no world access on its IO thread. */
final class RocketRejoinWriter {
    record Capture(long generation, RocketRejoinRoot root) { }
    record Completion(Capture capture, Outcome outcome, CheckedJournalCheckpoint.Acknowledgement acknowledged) { }
    private final Thread owner = Thread.currentThread();
    private final Path file;
    private final CheckedSavedDataPolicy policy;
    private final Function<Path, CheckedSavedDataFile.Presence> presence;
    private final CheckedJournalCheckpoint disk;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "ARCE-rocket-rejoins"); thread.setDaemon(true); return thread;
    });
    private final ArrayBlockingQueue<Completion> completed = new ArrayBlockingQueue<>(1);
    private Capture active, successor;
    private long tick, retryAt, lastGeneration;
    private boolean running, closed, draining;
    // IO-thread ownership: an UNKNOWN first move can leave only this exact attempted candidate on disk.
    private boolean ownsFile;
    private String attemptedDigest;

    RocketRejoinWriter(Path file, CheckedSavedDataPolicy policy, boolean present,
                       Function<Path, CheckedSavedDataFile.Presence> presence, CheckedSavedDataPolicy.DirectoryForce force) {
        this.file = file; this.policy = policy; this.presence = presence;
        this.ownsFile = present; this.disk = new CheckedJournalCheckpoint(force, RocketRejoinCodec.DEPTH);
    }
    boolean request(long generation, RocketRejoinRoot root) {
        owner();
        if (closed || draining || active != null && successor != null) { return false; }
        if (generation <= lastGeneration) { throw new IllegalArgumentException("Rejoin writer generation did not advance"); }
        lastGeneration = generation;
        var capture = new Capture(generation, root);
        if (active == null) { active = capture; start(); } else { successor = capture; }
        return true;
    }
    java.util.List<RocketRejoinRoot> pendingRoots() {
        owner();
        return active == null ? java.util.List.of() : successor == null ? java.util.List.of(active.root())
                : java.util.List.of(active.root(), successor.root());
    }
    private void start() {
        if (running || active == null || tick < retryAt) { return; }
        running = true; Capture capture = active;
        io.execute(() -> {
            Outcome outcome; var previous = disk.outcome();
            try {
                CompoundTag payload = RocketRejoinCodec.encode(capture.root()); String candidateDigest = digest(payload);
                boolean[] ownershipRejected = {false}, provedAbsent = {false};
                var guarded = policy.withCommitter((staged, target) -> {
                    if (!ownsFile) {
                        ownershipRejected[0] = true;
                        var state = presence.apply(target);
                        provedAbsent[0] = state == CheckedSavedDataFile.Presence.ABSENT;
                        if (state != CheckedSavedDataFile.Presence.ABSENT) {
                            boolean ownAttempt = state == CheckedSavedDataFile.Presence.PRESENT && attemptedDigest != null
                                    && policy.read(target).map(RocketRejoinWriter::digest).filter(attemptedDigest::equals).isPresent();
                            if (!ownAttempt) { throw new IOException("Rejoin file appeared or its presence is unknown before first commit"); }
                        }
                        attemptedDigest = candidateDigest;
                        ownershipRejected[0] = false;
                    }
                    policy.committer().commit(staged, target);
                });
                outcome = disk.write(file, guarded, capture.generation(), payload, Map.of());
                if (provedAbsent[0] && outcome == Outcome.NOT_COMMITTED) { attemptedDigest = null; }
                // Exact readback is not ownership: the checkpoint may see an identical foreign file after our guard refused.
                if (ownershipRejected[0]) { outcome = disk.unknown(); }
                else if (outcome == Outcome.COMMITTED) { ownsFile = true; attemptedDigest = null; }
                else if (previous == Outcome.UNKNOWN) { outcome = disk.unknown(); }
            } catch (RuntimeException invalid) {
                outcome = previous == Outcome.UNKNOWN ? disk.unknown() : disk.refused();
            } catch (Throwable unknown) { outcome = disk.unknown(); }
            completed.add(new Completion(capture, outcome, disk.acknowledged().orElse(null)));
        });
    }
    void tick(long now, Consumer<Completion> publish) {
        owner(); tick = now;
        var result = completed.poll();
        if (result != null) {
            if (active != result.capture()) { throw new IllegalStateException("Foreign rejoin completion"); }
            running = false;
            if (result.outcome() == Outcome.COMMITTED) { active = successor; successor = null; retryAt = 0; }
            else { retryAt = tick + 200; }
            publish.accept(result);
        }
        start();
    }
    /** Same final-attempt count as the transfer writer. Failed close never clears the owner's dirty root. */
    void close(Consumer<Completion> publish) {
        owner(); if (closed) { return; } draining = true;
        int[] failures = {0}; long started = System.nanoTime(), firstTick = Math.max(tick, retryAt);
        try {
            while (active != null) {
                tick(firstTick + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), result -> {
                    publish.accept(result);
                    if (result.outcome() == Outcome.COMMITTED) { failures[0] = 0; }
                    else if (++failures[0] >= 3) { active = null; successor = null; }
                });
                Thread.sleep(1);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Rejoin drain interrupted", interrupted);
        } finally {
            closed = true; io.shutdown();
            try { while (!io.awaitTermination(1, TimeUnit.SECONDS)) { /* Retain world ownership until IO ends. */ } }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        }
    }
    private static String digest(CompoundTag tag) {
        RocketRejoinCodec.bounds(tag);
        return CanonicalNbtDigest.of(tag, RocketRejoinCodec.ROOT_BYTES, RocketRejoinCodec.DEPTH);
    }
    private void owner() { if (Thread.currentThread() != owner) { throw new IllegalStateException("Rejoin writer requires its owner thread"); } }
}
