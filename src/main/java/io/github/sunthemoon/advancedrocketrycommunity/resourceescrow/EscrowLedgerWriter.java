package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;

/** One captured generation and one successor request. All state/capture/callbacks belong to the owner thread. */
public final class EscrowLedgerWriter implements AutoCloseable {
    /**
     * GUARD_REFUSED (R17 item 4): the request's own guard refused before any replacement was attempted. It is not an IO
     * failure, so it never counts towards {@link #unavailable()}. A refusal while an earlier outcome is unknown still
     * resolves through readback as FAILED_BEFORE_REPLACE or UNKNOWN.
     */
    public enum Outcome { COMMITTED, FAILED_BEFORE_REPLACE, UNKNOWN, GUARD_REFUSED }
    public record Snapshot(EscrowLedger ledger, CompoundTag payload, String digest, Set<UUID> admissions) {
        public Snapshot {
            EscrowTags.bound(payload, EscrowLimits.ROOT_BYTES, 24, 131_072);
            EscrowTags.require(ledger.equals(EscrowCodec.decode(payload))
                    && digest.equals(EscrowCodec.digest(payload)), "Snapshot identity/digest mismatch");
            payload = payload.copy(); admissions = Set.copyOf(admissions);
            EscrowTags.require(admissions.size() <= EscrowLimits.OPERATIONS, "Too many writer requests");
            EscrowTags.require(ledger.records().stream().map(EscrowOperation::id).collect(java.util.stream.Collectors.toSet())
                    .containsAll(admissions), "Admission acknowledgement is absent from this generation");
        }
        @Override public CompoundTag payload() { return payload.copy(); }
        public static Snapshot of(EscrowLedger ledger, Set<UUID> admissions) {
            CompoundTag payload = EscrowCodec.encode(ledger);
            return new Snapshot(ledger, payload, EscrowCodec.digest(payload), admissions);
        }
    }
    public record Completion(long generation, String digest, Outcome outcome, Snapshot snapshot) { }
    private record Request(Supplier<Snapshot> capture, Consumer<Completion> callback, BooleanSupplier beforeReplace, long requestedAt) { }
    /** Shutdown stops retrying one unresolved candidate after this many further unknown outcomes. */
    static final int DRAIN_UNKNOWN_ATTEMPTS = 3;
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private final Thread owner = Thread.currentThread();
    private final Path file;
    private final CheckedSavedDataFile.Committer committer;
    private final java.util.function.Consumer<String> checkpoint;
    private final ExecutorService io;
    private final ArrayBlockingQueue<Completion> completions = new ArrayBlockingQueue<>(1);
    private Request active;
    private Request successor;
    private Snapshot captured;
    private boolean running;
    private boolean closed;
    private boolean draining;
    private boolean uncertain;
    private boolean baselineFailed;
    private int failures;
    private int drainUnknown;
    private long retryAt;
    private long lastGeneration;
    private long durableGeneration;
    private String durableDigest; // Null until this session commits; the loaded generation is matched by ID and number.
    private long tick;
    private long lastIoStart = -10;
    private EscrowTimingSink timings = EscrowTimingSink.NONE;
    void timings(EscrowTimingSink sink) { owner(); timings = sink; }
    void timing(String phase, long nanos) { owner(); timings.sample(phase, nanos); }
    /** Release-test observation includes encode, digest and the unchanged snapshot validation/copy. */
    Snapshot snapshot(EscrowLedger ledger, Set<UUID> admissions) {
        owner();
        if (timings == EscrowTimingSink.NONE) { return Snapshot.of(ledger, admissions); }
        long start = System.nanoTime();
        var result = Snapshot.of(ledger, admissions);
        timings.encoded(result, System.nanoTime() - start);
        return result;
    }

    public EscrowLedgerWriter(Path file, long generation) {
        this(file, generation, CheckedSavedDataFile::atomicMove);
    }
    public EscrowLedgerWriter(Path file, long generation, CheckedSavedDataFile.Committer committer) {
        this(file, generation, committer, cut -> { });
    }
    public EscrowLedgerWriter(Path file, long generation, CheckedSavedDataFile.Committer committer,
                              java.util.function.Consumer<String> checkpoint) {
        this(file, generation, committer, checkpoint, Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ARCE-escrow-ledger"); thread.setDaemon(true); return thread;
        }));
    }
    /** Unit-test seam: tests in this package supply an IO thread they can await. Every public constructor uses the thread above. */
    EscrowLedgerWriter(Path file, long generation, CheckedSavedDataFile.Committer committer,
                       java.util.function.Consumer<String> checkpoint, ExecutorService io) {
        this.file = file; this.lastGeneration = generation; this.durableGeneration = generation; this.committer = committer;
        this.checkpoint = checkpoint; this.io = io;
    }
    /** Checked startup target, not a staged candidate or an in-memory deserialization, supplies release authority. */
    boolean baseline(EscrowLedger loaded) {
        owner(); EscrowTags.require(active == null && loaded.writerGeneration() == durableGeneration, "Invalid baseline generation");
        if (baselineFailed) { return false; }
        try {
            durableDigest = EscrowLedgerBaseline.require(file, loaded); return true;
        } catch (RuntimeException failure) {
            baselineFailed = true;
            LOGGER.warn("ARCE_ESCROW_BASELINE_UNAVAILABLE generation={}; escrow actions refused until stopped-server whole-world repair", durableGeneration);
            return false;
        }
    }
    public boolean request(Supplier<Snapshot> capture, Consumer<Completion> callback) {
        return request(capture, callback, () -> true);
    }
    /** Guard may read only immutable data and atomic proof tokens, never Minecraft objects. */
    public boolean request(Supplier<Snapshot> capture, Consumer<Completion> callback, BooleanSupplier beforeReplace) {
        owner();
        if (closed || draining || baselineFailed) { return false; }
        Request request = new Request(capture, callback, beforeReplace, System.nanoTime());
        if (active == null) { active = request; captureAndStart(); return true; }
        if (successor != null) { return false; }
        successor = request; return true;
    }
    private void captureAndStart() {
        Snapshot next;
        long started = System.nanoTime();
        try { next = active.capture().get(); }
        catch (RuntimeException refused) {
            active = null; captured = null;
            throw refused;
        }
        long captureNanos = System.nanoTime() - started;
        timings.sample("ledger_capture_main", captureNanos);
        timings.captured(next, captureNanos);
        timings.queued(next.ledger().writerGeneration(), started - active.requestedAt());
        EscrowTags.require(next.ledger().writerGeneration() > lastGeneration, "Nonincreasing writer generation");
        lastGeneration = next.ledger().writerGeneration(); captured = next; uncertain = false; drainUnknown = 0; startIo();
    }
    private void startIo() {
        if (tick < Math.max(retryAt, lastIoStart + 10)) { return; }
        lastIoStart = tick;
        running = true; Snapshot snapshot = captured; BooleanSupplier beforeReplace = active.beforeReplace();
        timings.writing(snapshot.ledger().writerGeneration());
        boolean previouslyUncertain = uncertain; long durable = durableGeneration; String durableSignature = durableDigest;
        io.execute(() -> {
            long started = System.nanoTime();
            boolean[] attempted = {false}, refused = {false}; Outcome outcome;
            var policy = new CheckedSavedDataPolicy(ManagedSavedDataType.RESOURCE_ESCROW, (staged, target) -> {
                if (snapshot.ledger().writerGeneration() > 1) {
                    checkpoint.accept(snapshot.ledger().records().isEmpty() ? "K8" : "K1");
                }
                refused[0] = !beforeReplace.getAsBoolean();
                EscrowTags.require(!refused[0], "Proof invalidated before ledger replacement");
                attempted[0] = true; committer.commit(staged, target);
                if (snapshot.ledger().writerGeneration() > 1) {
                    checkpoint.accept(snapshot.ledger().records().isEmpty() ? "K9" : "K2");
                }
            });
            try {
                policy.replace(file, snapshot::payload); CheckedSavedDataPolicy.forceDirectory(file.getParent());
                outcome = Outcome.COMMITTED;
            } catch (RuntimeException | IOException failure) {
                outcome = attempted[0] || previouslyUncertain ? Outcome.UNKNOWN
                        : refused[0] ? Outcome.GUARD_REFUSED : Outcome.FAILED_BEFORE_REPLACE;
                if (attempted[0] || previouslyUncertain) {
                    try {
                        Optional<CompoundTag> disk = policy.read(file);
                        if (disk.filter(snapshot.payload()::equals).isPresent()) {
                            CheckedSavedDataPolicy.forceDirectory(file.getParent()); outcome = Outcome.COMMITTED;
                        } else if (refused[0] && previous(disk, policy.read(file), durable, durableSignature, snapshot.ledger().id())) {
                            // This writer never moves a candidate whose guard refused, and the earlier ambiguous move
                            // cannot land later. A stable readback of the last durable generation resolves it.
                            outcome = Outcome.FAILED_BEFORE_REPLACE;
                        }
                    } catch (RuntimeException | IOException unreadable) { /* Keep this exact candidate for retry. */ }
                }
            }
            long ioNanos = System.nanoTime() - started;
            timings.sample("ledger_io", ioNanos);
            timings.written(snapshot.ledger().writerGeneration(), ioNanos);
            completions.add(new Completion(snapshot.ledger().writerGeneration(), snapshot.digest(), outcome, snapshot));
        });
    }
    public void tick(long tick) {
        owner(); this.tick = tick; Completion completion = completions.poll();
        if (completion != null) {
            EscrowTags.require(captured != null && completion.generation() == captured.ledger().writerGeneration()
                    && completion.digest().equals(captured.digest()), "Stale or foreign writer callback");
            timings.acknowledged(completion.generation());
            running = false; boolean wasUnavailable = unavailable();
            if (completion.outcome() == Outcome.UNKNOWN) {
                if (!uncertain) {
                    LOGGER.info("Escrow ledger generation {} has an unknown replacement outcome; retrying the exact candidate", completion.generation());
                }
                uncertain = true; failures++; retryAt = tick + 200; logAvailability(wasUnavailable, completion.generation());
                active.callback().accept(completion);
                checkpoint.accept("WRITE_UNKNOWN");
                if (draining && ++drainUnknown >= DRAIN_UNKNOWN_ATTEMPTS) { abandon(completion.generation()); }
            } else {
                Request finished = active;
                if (uncertain) {
                    LOGGER.info("Escrow ledger generation {} resolved as {} after an unknown outcome", completion.generation(), completion.outcome());
                }
                if (completion.outcome() == Outcome.COMMITTED) {
                    failures = 0; durableGeneration = completion.generation(); durableDigest = completion.digest();
                } else if (completion.outcome() != Outcome.GUARD_REFUSED) { failures++; }
                uncertain = false; logAvailability(wasUnavailable, completion.generation());
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
    private void logAvailability(boolean wasUnavailable, long generation) {
        if (!wasUnavailable && unavailable()) {
            LOGGER.warn("Escrow ledger is unavailable after {} unresolved writes at generation {}; admission is refused until an exact acknowledgement",
                    failures, generation);
        } else if (wasUnavailable && !unavailable()) {
            LOGGER.info("Escrow ledger is available again at generation {}", generation);
        }
    }
    private static boolean previous(Optional<CompoundTag> disk, Optional<CompoundTag> again, long generation, String digest, UUID ledger) {
        if (!disk.equals(again)) { return false; }
        if (disk.isEmpty()) { return generation == 0; }
        EscrowLedger durable = EscrowCodec.decode(disk.get());
        return durable.id().equals(ledger) && durable.writerGeneration() == generation
                && (digest == null || digest.equals(EscrowCodec.digest(disk.get())));
    }
    /** Like a hard stop for this ledger: no further move and no callback. Restart validates the target file. */
    private void abandon(long generation) {
        LOGGER.warn("Escrow ledger shutdown stopped retrying generation {} after {} unknown outcomes; restart uses the durable ledger file",
                generation, drainUnknown);
        active = null; successor = null; captured = null;
    }
    public boolean containsCaptured(UUID admission) { owner(); return captured != null && captured.admissions().contains(admission); }
    /** Includes the exact unknown candidate until resolution; successor admissions remain in the coordinator queue. */
    java.util.List<EscrowOperation> capturedOperations() {
        owner(); return captured == null ? java.util.List.of() : captured.ledger().records();
    }
    public boolean unavailable() { owner(); return baselineFailed || failures >= 3; }
    boolean baselineAvailable() { owner(); return !baselineFailed; }
    public boolean idle() { owner(); return active == null; }
    boolean unknown() { owner(); return uncertain; }
    public long lastGeneration() { owner(); return lastGeneration; }
    long lastIoStart() { owner(); return lastIoStart; }
    public void drain() {
        owner(); draining = true; drainUnknown = 0;
        // Continue the server clock: lastIoStart holds a real tick, so restarting at retryAt (0 unless an UNKNOWN
        // happened) would wait out the cadence for as many iterations as the server's uptime in ticks.
        long tick = Math.max(this.tick, retryAt);
        // Only this bounded completion queue is pumped; no gameplay, applies or new barriers.
        while (active != null) {
            tick(tick++);
            try { Thread.sleep(1); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("Escrow shutdown drain interrupted", interrupted);
            }
        }
    }
    @Override public void close() {
        owner(); if (closed) { return; } drain(); closed = true; join();
    }
    /** Crash path: refuse requests, pump no callbacks and join the IO thread. The target file stays authoritative. */
    public void abort() {
        owner(); if (closed) { return; }
        closed = true; draining = true; active = null; successor = null; captured = null; join(); completions.clear();
    }
    private void join() {
        io.shutdown();
        try { while (!io.awaitTermination(1, TimeUnit.SECONDS)) { /* Never release world ownership while IO is active. */ } }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Escrow writer requires its owner thread"); }
}
