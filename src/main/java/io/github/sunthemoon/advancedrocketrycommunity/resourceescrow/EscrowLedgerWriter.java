package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;

/** One captured generation and one successor request. All state/capture/callbacks belong to the owner thread. */
public final class EscrowLedgerWriter implements AutoCloseable {
    public enum Outcome { COMMITTED, FAILED_BEFORE_REPLACE, UNKNOWN }
    public record Snapshot(EscrowLedger ledger, CompoundTag payload, String digest, Set<UUID> admissions) {
        public Snapshot {
            EscrowTags.bound(payload, EscrowLimits.ROOT_BYTES, 24, 131_072);
            EscrowTags.require(ledger.equals(EscrowCodec.decode(payload))
                    && digest.equals(RocketNativeResources.signature(payload)), "Snapshot identity/digest mismatch");
            payload = payload.copy(); admissions = Set.copyOf(admissions);
            EscrowTags.require(admissions.size() <= EscrowLimits.OPERATIONS, "Too many writer requests");
        }
        @Override public CompoundTag payload() { return payload.copy(); }
        public static Snapshot of(EscrowLedger ledger, Set<UUID> admissions) {
            CompoundTag payload = EscrowCodec.encode(ledger);
            return new Snapshot(ledger, payload, RocketNativeResources.signature(payload), admissions);
        }
    }
    public record Completion(long generation, String digest, Outcome outcome, Snapshot snapshot) { }
    private record Request(Supplier<Snapshot> capture, Consumer<Completion> callback) { }
    private final Thread owner = Thread.currentThread();
    private final Path file;
    private final CheckedSavedDataFile.Committer committer;
    private final java.util.function.Consumer<String> checkpoint;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ARCE-escrow-ledger"); thread.setDaemon(true); return thread;
    });
    private final ArrayBlockingQueue<Completion> completions = new ArrayBlockingQueue<>(1);
    private Request active;
    private Request successor;
    private Snapshot captured;
    private boolean running;
    private boolean closed;
    private boolean draining;
    private int failures;
    private long retryAt;
    private long lastGeneration;

    public EscrowLedgerWriter(Path file, long generation) {
        this(file, generation, CheckedSavedDataFile::atomicMove);
    }
    public EscrowLedgerWriter(Path file, long generation, CheckedSavedDataFile.Committer committer) {
        this(file, generation, committer, cut -> { });
    }
    public EscrowLedgerWriter(Path file, long generation, CheckedSavedDataFile.Committer committer,
                              java.util.function.Consumer<String> checkpoint) {
        this.file = file; this.lastGeneration = generation; this.committer = committer;
        this.checkpoint = checkpoint;
    }
    public boolean request(Supplier<Snapshot> capture, Consumer<Completion> callback) {
        owner();
        if (closed || draining) { return false; }
        Request request = new Request(capture, callback);
        if (active == null) { active = request; captureAndStart(); return true; }
        if (successor != null) { return false; }
        successor = request; return true;
    }
    private void captureAndStart() {
        Snapshot next;
        try { next = active.capture().get(); }
        catch (RuntimeException refused) {
            active = null; captured = null;
            throw refused;
        }
        EscrowTags.require(next.ledger().writerGeneration() > lastGeneration, "Nonincreasing writer generation");
        lastGeneration = next.ledger().writerGeneration(); captured = next; startIo();
    }
    private void startIo() {
        running = true; Snapshot snapshot = captured;
        io.execute(() -> {
            boolean[] attempted = {false}; Outcome outcome;
            var policy = new CheckedSavedDataPolicy(ManagedSavedDataType.RESOURCE_ESCROW, (staged, target) -> {
                if (snapshot.ledger().writerGeneration() > 1) {
                    checkpoint.accept(snapshot.ledger().records().isEmpty() ? "K8" : "K1");
                }
                attempted[0] = true; committer.commit(staged, target);
                if (snapshot.ledger().writerGeneration() > 1) {
                    checkpoint.accept(snapshot.ledger().records().isEmpty() ? "K9" : "K2");
                }
            });
            try {
                policy.replace(file, snapshot::payload); CheckedSavedDataPolicy.forceDirectory(file.getParent());
                outcome = Outcome.COMMITTED;
            } catch (RuntimeException | IOException failure) {
                outcome = attempted[0] ? Outcome.UNKNOWN : Outcome.FAILED_BEFORE_REPLACE;
                if (attempted[0]) {
                    try {
                        if (policy.read(file).filter(snapshot.payload()::equals).isPresent()) {
                            CheckedSavedDataPolicy.forceDirectory(file.getParent()); outcome = Outcome.COMMITTED;
                        }
                    } catch (RuntimeException | IOException unreadable) { /* Keep this exact candidate for retry. */ }
                }
            }
            completions.add(new Completion(snapshot.ledger().writerGeneration(), snapshot.digest(), outcome, snapshot));
        });
    }
    public void tick(long tick) {
        owner(); Completion completion = completions.poll();
        if (completion != null) {
            EscrowTags.require(captured != null && completion.generation() == captured.ledger().writerGeneration()
                    && completion.digest().equals(captured.digest()), "Stale or foreign writer callback");
            running = false;
            if (completion.outcome() == Outcome.UNKNOWN) {
                failures++; retryAt = tick + 200; active.callback().accept(completion);
            } else {
                Request finished = active;
                if (completion.outcome() == Outcome.COMMITTED) { failures = 0; } else { failures++; }
                captured = null; active = successor; successor = null;
                // Successful authority is published before successor capture; failure cancellation is delayed
                // until after capture, so a successor that still contains an admission keeps its reservation.
                if (completion.outcome() == Outcome.COMMITTED) {
                    finished.callback().accept(completion);
                    if (active != null) { captureAndStart(); }
                } else {
                    if (active != null) { captureAndStart(); }
                    finished.callback().accept(completion);
                }
            }
        }
        if (active != null && captured != null && !running && tick >= retryAt) { startIo(); }
    }
    public boolean containsCaptured(UUID admission) { owner(); return captured != null && captured.admissions().contains(admission); }
    public boolean unavailable() { owner(); return failures >= 3; }
    public boolean idle() { owner(); return active == null; }
    public long lastGeneration() { owner(); return lastGeneration; }
    public void drain() {
        owner(); draining = true;
        long tick = retryAt;
        // Only this bounded completion queue is pumped; no gameplay, applies or new barriers.
        while (active != null) {
            tick(tick++);
            try { Thread.sleep(1); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("Escrow shutdown drain interrupted", interrupted);
            }
        }
    }
    @Override public void close() {
        owner(); if (closed) { return; } drain(); closed = true; io.shutdown();
        try { while (!io.awaitTermination(1, TimeUnit.SECONDS)) { /* Never release world ownership while IO is active. */ } }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Escrow writer requires its owner thread"); }
}
