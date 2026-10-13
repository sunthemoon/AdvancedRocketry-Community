package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
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
    public enum Outcome { COMMITTED, FAILED_BEFORE_REPLACE, UNKNOWN }
    public record Snapshot(EscrowLedger ledger, CompoundTag payload, String digest, Set<UUID> admissions) {
        public Snapshot {
            EscrowTags.bound(payload, EscrowLimits.ROOT_BYTES, 24, 131_072);
            EscrowTags.require(ledger.equals(EscrowCodec.decode(payload))
                    && digest.equals(RocketNativeResources.signature(payload)), "Snapshot identity/digest mismatch");
            payload = payload.copy(); admissions = Set.copyOf(admissions);
            EscrowTags.require(admissions.size() <= EscrowLimits.OPERATIONS, "Too many writer requests");
            EscrowTags.require(ledger.records().stream().map(EscrowOperation::id).collect(java.util.stream.Collectors.toSet())
                    .containsAll(admissions), "Admission acknowledgement is absent from this generation");
        }
        @Override public CompoundTag payload() { return payload.copy(); }
        public static Snapshot of(EscrowLedger ledger, Set<UUID> admissions) {
            CompoundTag payload = EscrowCodec.encode(ledger);
            return new Snapshot(ledger, payload, RocketNativeResources.signature(payload), admissions);
        }
    }
    public record Completion(long generation, String digest, Outcome outcome, Snapshot snapshot) { }
    private record Request(Supplier<Snapshot> capture, Consumer<Completion> callback, BooleanSupplier beforeReplace) { }
    /** Shutdown stops retrying one unresolved candidate after this many further unknown outcomes. */
    static final int DRAIN_UNKNOWN_ATTEMPTS = 3;
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
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
    private boolean uncertain;
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

    public EscrowLedgerWriter(Path file, long generation) {
        this(file, generation, CheckedSavedDataFile::atomicMove);
    }
    public EscrowLedgerWriter(Path file, long generation, CheckedSavedDataFile.Committer committer) {
        this(file, generation, committer, cut -> { });
    }
    public EscrowLedgerWriter(Path file, long generation, CheckedSavedDataFile.Committer committer,
                              java.util.function.Consumer<String> checkpoint) {
        this.file = file; this.lastGeneration = generation; this.durableGeneration = generation; this.committer = committer;
        this.checkpoint = checkpoint;
    }
    public boolean request(Supplier<Snapshot> capture, Consumer<Completion> callback) {
        return request(capture, callback, () -> true);
    }
    /** Guard may read only immutable data and atomic proof tokens, never Minecraft objects. */
    public boolean request(Supplier<Snapshot> capture, Consumer<Completion> callback, BooleanSupplier beforeReplace) {
        owner();
        if (closed || draining) { return false; }
        Request request = new Request(capture, callback, beforeReplace);
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
        timings.sample("ledger_capture_main", System.nanoTime() - started);
        EscrowTags.require(next.ledger().writerGeneration() > lastGeneration, "Nonincreasing writer generation");
        lastGeneration = next.ledger().writerGeneration(); captured = next; uncertain = false; drainUnknown = 0; startIo();
    }
    private void startIo() {
        if (tick < Math.max(retryAt, lastIoStart + 10)) { return; }
        lastIoStart = tick;
        running = true; Snapshot snapshot = captured; BooleanSupplier beforeReplace = active.beforeReplace();
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
                outcome = attempted[0] || previouslyUncertain ? Outcome.UNKNOWN : Outcome.FAILED_BEFORE_REPLACE;
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
            timings.sample("ledger_io", System.nanoTime() - started);
            completions.add(new Completion(snapshot.ledger().writerGeneration(), snapshot.digest(), outcome, snapshot));
        });
    }
    public void tick(long tick) {
        owner(); this.tick = tick; Completion completion = completions.poll();
        if (completion != null) {
            EscrowTags.require(captured != null && completion.generation() == captured.ledger().writerGeneration()
                    && completion.digest().equals(captured.digest()), "Stale or foreign writer callback");
            running = false;
            if (completion.outcome() == Outcome.UNKNOWN) {
                uncertain = true; failures++; retryAt = tick + 200; active.callback().accept(completion);
                checkpoint.accept("WRITE_UNKNOWN");
                if (draining && ++drainUnknown >= DRAIN_UNKNOWN_ATTEMPTS) { abandon(completion.generation()); }
            } else {
                Request finished = active;
                if (completion.outcome() == Outcome.COMMITTED) {
                    failures = 0; durableGeneration = completion.generation(); durableDigest = completion.digest();
                } else { failures++; }
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
    private static boolean previous(Optional<CompoundTag> disk, Optional<CompoundTag> again, long generation, String digest, UUID ledger) {
        if (!disk.equals(again)) { return false; }
        if (disk.isEmpty()) { return generation == 0; }
        EscrowLedger durable = EscrowCodec.decode(disk.get());
        return durable.id().equals(ledger) && durable.writerGeneration() == generation
                && (digest == null || digest.equals(RocketNativeResources.signature(disk.get())));
    }
    /** Like a hard stop for this ledger: no further move and no callback. Restart validates the target file. */
    private void abandon(long generation) {
        LOGGER.warn("Escrow ledger shutdown stopped retrying generation {} after {} unknown outcomes; restart uses the durable ledger file",
                generation, drainUnknown);
        active = null; successor = null; captured = null;
    }
    public boolean containsCaptured(UUID admission) { owner(); return captured != null && captured.admissions().contains(admission); }
    public boolean unavailable() { owner(); return failures >= 3; }
    public boolean idle() { owner(); return active == null; }
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
