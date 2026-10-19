package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * The checked receipt writer (W27A-DESIGN section 2, "Writer"): {@link EscrowLedgerWriter}'s state machine with a typed
 * candidate. The owner thread only captures an immutable {@link BayReceiptRoot}; encoding, the round-trip check, the
 * digest, staging, force, readback and replacement run on the IO thread. A retry moves exactly the first attempt's
 * bytes. One captured generation and one successor; completions are published on the owner thread.
 */
public final class BayReceiptWriter implements AutoCloseable {
    /** GUARD_REFUSED: the request's own guard refused before any replacement; it never counts as an IO failure. */
    public enum Outcome { COMMITTED, FAILED_BEFORE_REPLACE, UNKNOWN, GUARD_REFUSED }
    /** The encoded bytes of a generation, fixed by its first attempt; the IO thread never mutates them. */
    record Candidate(CompoundTag payload, String digest) { }
    public record Completion(long generation, @Nullable String digest, Outcome outcome, BayReceiptRoot root,
                             @Nullable Candidate candidate) { }
    private record Request(Supplier<BayReceiptRoot> capture, Consumer<Completion> callback, BooleanSupplier beforeReplace) { }
    @FunctionalInterface interface DrainWait { void sleep(long nanos) throws InterruptedException; }
    static final int DRAIN_UNKNOWN_ATTEMPTS = 3, RETRY_TICKS = 200, START_TICKS = 10, UNAVAILABLE_FAILURES = 3;
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private final Thread owner = Thread.currentThread();
    private final Path file;
    private final UUID storeId;
    private final CheckedSavedDataFile.Committer committer;
    private final ExecutorService io;
    private final ArrayBlockingQueue<Completion> completions = new ArrayBlockingQueue<>(1);
    private Request active, successor;
    private BayReceiptRoot captured;
    private Candidate exact;
    private boolean running, closed, draining, uncertain, baselineFailed;
    private int failures, drainUnknown;
    private long retryAt, lastGeneration, durableGeneration, tick, lastIoStart = -START_TICKS;
    private String durableDigest; // Null until this session commits or the startup baseline proves the file.

    public BayReceiptWriter(Path file, UUID storeId, long generation, CheckedSavedDataFile.Committer committer) {
        this(file, storeId, generation, committer, Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ARCE-bay-receipts"); thread.setDaemon(true); return thread;
        }));
    }
    /** Unit-test seam: tests supply an IO thread they can await. The public constructor uses the thread above. */
    BayReceiptWriter(Path file, UUID storeId, long generation, CheckedSavedDataFile.Committer committer, ExecutorService io) {
        this.file = file.toAbsolutePath().normalize(); this.storeId = storeId; this.committer = committer; this.io = io;
        lastGeneration = generation; durableGeneration = generation;
    }

    /** The checked startup target supplies the durable authority; a differing file makes the store unavailable. */
    boolean baseline(BayReceiptRoot loaded) {
        owner(); EscrowTags.require(active == null && loaded.writerGeneration() == durableGeneration, "Invalid receipt baseline generation");
        if (baselineFailed) { return false; }
        try {
            durableDigest = BayReceiptBaseline.require(file, loaded); return true;
        } catch (RuntimeException failure) {
            baselineFailed = true;
            LOGGER.warn("ARCE_BAY_RECEIPTS_BASELINE_UNAVAILABLE generation={}; new receipts are refused until stopped-server whole-world repair",
                    durableGeneration);
            return false;
        }
    }
    public boolean request(Supplier<BayReceiptRoot> capture, Consumer<Completion> callback) { return request(capture, callback, () -> true); }
    /** The guard may read only immutable data and atomic proof tokens, never Minecraft objects. */
    public boolean request(Supplier<BayReceiptRoot> capture, Consumer<Completion> callback, BooleanSupplier beforeReplace) {
        owner();
        if (closed || draining || baselineFailed) { return false; }
        Request request = new Request(capture, callback, beforeReplace);
        if (active == null) { active = request; captureAndStart(); return true; }
        if (successor != null) { return false; }
        successor = request; return true;
    }
    private void captureAndStart() {
        BayReceiptRoot next;
        try {
            next = active.capture().get();
            EscrowTags.require(next.writerGeneration() > lastGeneration && next.storeId().equals(storeId), "Nonincreasing or foreign receipt generation");
        } catch (RuntimeException refused) {
            // A refused capture leaves no request behind, so a drain never waits on a generation that cannot start.
            active = null; captured = null; throw refused;
        }
        lastGeneration = next.writerGeneration(); captured = next; exact = null; uncertain = false; drainUnknown = 0; startIo();
    }
    private void startIo() {
        if (tick < Math.max(retryAt, lastIoStart + START_TICKS)) { return; }
        lastIoStart = tick; running = true;
        BayReceiptRoot root = captured; Candidate fixed = exact; BooleanSupplier beforeReplace = active.beforeReplace();
        boolean previouslyUncertain = uncertain; long durable = durableGeneration; String durableSignature = durableDigest;
        io.execute(() -> completions.add(write(root, fixed, beforeReplace, previouslyUncertain, durable, durableSignature)));
    }
    /** IO thread only. */
    private Completion write(BayReceiptRoot root, @Nullable Candidate fixed, BooleanSupplier beforeReplace,
                             boolean previouslyUncertain, long durable, String durableSignature) {
        Candidate candidate = fixed;
        if (candidate == null) {
            try {
                CompoundTag payload = BayReceiptCodec.encode(root);
                EscrowTags.require(root.equals(BayReceiptCodec.decode(payload)), "Receipt candidate does not round-trip");
                candidate = new Candidate(payload, BayReceiptCodec.digest(payload));
            } catch (RuntimeException invalid) {
                // Refused before staging; an earlier unknown outcome of this generation stays unknown.
                return new Completion(root.writerGeneration(), null,
                        previouslyUncertain ? Outcome.UNKNOWN : Outcome.FAILED_BEFORE_REPLACE, root, null);
            }
        }
        CompoundTag payload = candidate.payload();
        boolean[] attempted = {false}, refused = {false};
        var policy = new CheckedSavedDataPolicy(ManagedSavedDataType.BAY_RECEIPTS, (staged, target) -> {
            refused[0] = !beforeReplace.getAsBoolean();
            EscrowTags.require(!refused[0], "Proof invalidated before receipt replacement");
            attempted[0] = true; committer.commit(staged, target);
        });
        Outcome outcome;
        try {
            policy.replace(file, () -> payload); CheckedSavedDataPolicy.forceDirectory(file.getParent());
            outcome = Outcome.COMMITTED;
        } catch (RuntimeException | IOException failure) {
            outcome = attempted[0] || previouslyUncertain ? Outcome.UNKNOWN : refused[0] ? Outcome.GUARD_REFUSED : Outcome.FAILED_BEFORE_REPLACE;
            if (attempted[0] || previouslyUncertain) {
                try {
                    Optional<CompoundTag> disk = policy.read(file);
                    if (disk.filter(payload::equals).isPresent()) {
                        CheckedSavedDataPolicy.forceDirectory(file.getParent()); outcome = Outcome.COMMITTED;
                    } else if (refused[0] && previous(disk, policy.read(file), durable, durableSignature)) {
                        // A refused guard never moves this candidate, and the earlier ambiguous move cannot land later.
                        outcome = Outcome.FAILED_BEFORE_REPLACE;
                    }
                } catch (RuntimeException | IOException unreadable) { /* Keep this exact candidate for retry. */ }
            }
        }
        return new Completion(root.writerGeneration(), candidate.digest(), outcome, root, candidate);
    }
    private boolean previous(Optional<CompoundTag> disk, Optional<CompoundTag> again, long generation, String digest) {
        if (!disk.equals(again)) { return false; }
        if (disk.isEmpty()) { return generation == 0; }
        BayReceiptRoot durable = BayReceiptCodec.decode(disk.get());
        return durable.storeId().equals(storeId) && durable.writerGeneration() == generation
                && (digest == null || digest.equals(BayReceiptCodec.digest(disk.get())));
    }

    public void tick(long now) {
        owner(); tick = now; Completion completion = completions.poll();
        if (completion != null) { complete(completion); }
        if (active != null && captured != null && !running && tick >= retryAt) { startIo(); }
    }
    private void complete(Completion completion) {
        EscrowTags.require(captured != null && completion.generation() == captured.writerGeneration()
                && completion.root() == captured && (exact == null || exact.digest().equals(completion.digest())),
                "Stale, foreign or inexact receipt writer callback");
        if (exact == null) { exact = completion.candidate(); }
        running = false; boolean wasUnavailable = unavailable();
        if (completion.outcome() == Outcome.UNKNOWN) {
            if (!uncertain) {
                LOGGER.info("ARCE_BAY_RECEIPTS_WRITE_UNKNOWN generation={}; retrying the exact candidate every {} ticks",
                        completion.generation(), RETRY_TICKS);
            }
            uncertain = true; failures++; retryAt = tick + RETRY_TICKS; logAvailability(wasUnavailable, completion.generation());
            active.callback().accept(completion);
            if (draining && ++drainUnknown >= DRAIN_UNKNOWN_ATTEMPTS) { abandon(completion.generation()); }
            return;
        }
        if (uncertain) {
            LOGGER.info("ARCE_BAY_RECEIPTS_WRITE_RESOLVED generation={} outcome={}", completion.generation(), completion.outcome());
        }
        if (completion.outcome() == Outcome.COMMITTED) {
            failures = 0; durableGeneration = completion.generation(); durableDigest = completion.digest();
        } else if (completion.outcome() != Outcome.GUARD_REFUSED) { failures++; }
        uncertain = false; logAvailability(wasUnavailable, completion.generation());
        Request finished = active; captured = null; exact = null; active = successor; successor = null;
        // The store publishes the outcome (and cancels a definitely failed generation's mutations) before the
        // successor captures its RAM, so a successor never carries a cancelled mutation.
        finished.callback().accept(completion);
        if (active != null) { captureAndStart(); }
    }
    private void logAvailability(boolean wasUnavailable, long generation) {
        if (!wasUnavailable && unavailable()) {
            LOGGER.warn("ARCE_BAY_RECEIPTS_UNAVAILABLE failures={} generation={}; new receipts are refused until an exact acknowledgement",
                    failures, generation);
        } else if (wasUnavailable && !unavailable()) {
            LOGGER.info("ARCE_BAY_RECEIPTS_AVAILABLE generation={}", generation);
        }
    }
    /** Like a hard stop for this store: no further move and no callback. Restart validates the target file. */
    private void abandon(long generation) {
        LOGGER.warn("ARCE_BAY_RECEIPTS_DRAIN_ABANDONED generation={} attempts={}; restart uses the durable receipt file",
                generation, drainUnknown);
        active = null; successor = null; captured = null; exact = null;
    }

    /** The active captured generation, including an exact unknown candidate until it resolves; null when idle. */
    @Nullable BayReceiptRoot captured() { owner(); return captured; }
    public boolean unavailable() { owner(); return baselineFailed || failures >= UNAVAILABLE_FAILURES; }
    /** Unavailable through counted write failures, not a failed baseline, so a checked generation can recover it. */
    boolean recoverable() { owner(); return !baselineFailed && failures >= UNAVAILABLE_FAILURES; }
    public boolean idle() { owner(); return active == null; }
    boolean hasSuccessor() { owner(); return successor != null; }
    boolean unknown() { owner(); return uncertain; }
    public long lastGeneration() { owner(); return lastGeneration; }
    long durableGeneration() { owner(); return durableGeneration; }
    long lastIoStart() { owner(); return lastIoStart; }
    public void drain() { drain(System::nanoTime, TimeUnit.NANOSECONDS::sleep); }
    /** Unit-test seam: production always uses the monotonic JVM clock and an interruptible wait (W104's drain clock). */
    void drain(LongSupplier nanoTime, DrainWait wait) {
        owner(); draining = true; drainUnknown = 0;
        long firstTick = Math.max(tick, retryAt), started = nanoTime.getAsLong();
        // Only this bounded completion queue is pumped; no gameplay or new requests.
        while (active != null) {
            tick(firstTick + TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - started));
            try {
                long elapsed = nanoTime.getAsLong() - started;
                wait.sleep(1_000_000 - elapsed % 1_000_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("Bay receipt drain interrupted", interrupted);
            }
        }
    }
    @Override public void close() { owner(); if (closed) { return; } drain(); closed = true; join(); }
    /** Crash path: refuse requests, pump no callbacks and join the IO thread. The target file stays authoritative. */
    public void abort() {
        owner(); if (closed) { return; }
        closed = true; draining = true; active = null; successor = null; captured = null; exact = null; join(); completions.clear();
    }
    private void join() {
        io.shutdown();
        try { while (!io.awaitTermination(1, TimeUnit.SECONDS)) { /* Never release world ownership while IO is active. */ } }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Bay receipt writer requires its owner thread"); }
}
