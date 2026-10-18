package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;

/** Own retained store -> same worker force -> stable disk read -> owner-thread confirmation. */
public final class EscrowBarrier implements AutoCloseable {
    public record Proof(EscrowPrefix.State frontier, EscrowChunkStore.Capture capture, AtomicBoolean active,
                        AtomicBoolean lifetime) {
        public boolean valid() { return active.get() && lifetime.get() && capture.generation().get() == capture.capturedGeneration(); }
        public void release() { if (active.compareAndSet(true, false)) { capture.releaseWatch().run(); } }
    }
    public record Result(Proof proof, EscrowReason reason) { public boolean confirmed() { return proof != null && proof.valid(); } }
    public record Target(EscrowEndpoint endpoint, EscrowPrefix.State frontier, Predicate<CompoundTag> validator) { }
    public record JointResult(Map<EscrowEndpoint.Key, Proof> proofs, EscrowReason reason) {
        public JointResult { proofs = Map.copyOf(proofs); }
        public boolean confirmed() { return !proofs.isEmpty() && proofs.values().stream().allMatch(Proof::valid); }
    }
    private record Pending(EscrowChunkStore.Capture capture, EscrowPrefix.State frontier,
                            Consumer<Result> callback, EscrowReason failure, boolean ordinary) { }
    /** W98 (W96-R5): each attempt reports to its own sink; an ordinary one never reaches the governed checkpoint. */
    private record Attempt(EscrowChunkStore.Capture capture, EscrowPrefix.State frontier,
                            Predicate<CompoundTag> validator, Consumer<Result> callback, Consumer<String> sink, boolean ordinary) { }
    private final Thread owner = Thread.currentThread();
    private final ExecutorService reader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ARCE-escrow-readback"); thread.setDaemon(true); return thread;
    });
    private final ArrayBlockingQueue<Pending> completions = new ArrayBlockingQueue<>(32);
    private final AtomicBoolean active;
    private final AtomicBoolean working = new AtomicBoolean(true);
    private final Object lifecycle = new Object();
    private final EscrowBarrierBudget budget = new EscrowBarrierBudget();
    private final Map<String, List<Attempt>> staged = new HashMap<>();
    private final Map<String, Long> nextForce = new HashMap<>();
    private final java.util.Set<EscrowChunkStore.Capture> outstanding = new java.util.HashSet<>();
    private int ioFailures;
    private int supersedingSaves;
    private int refusals;
    private final Consumer<String> checkpoint;
    private final EscrowTimingSink timings;
    public EscrowBarrier() { this(cut -> { }); }
    public EscrowBarrier(Consumer<String> checkpoint) { this(checkpoint, new AtomicBoolean(true)); }
    EscrowBarrier(Consumer<String> checkpoint, AtomicBoolean lifetime) { this(checkpoint, lifetime, EscrowTimingSink.NONE); }
    EscrowBarrier(Consumer<String> checkpoint, AtomicBoolean lifetime, EscrowTimingSink timings) {
        this.checkpoint = checkpoint; active = lifetime; this.timings = timings;
    }

    /** Read-only scheduling hint. begin still reserves atomically before any capture. */
    boolean available(EscrowEndpoint endpoint, long tick) {
        return available(endpoint.level(), endpoint.kind() == EscrowEndpoint.Kind.ENTITY, tick);
    }

    boolean available(String level, boolean entity, long tick) {
        owner(); return working.get() && budget.available(level, entity, tick);
    }

    /** One serialization/store/force/readback proves every frozen endpoint in the same Anvil slot. */
    public boolean beginJoint(EscrowChunkStore store, List<Target> targets, long tick, Consumer<JointResult> callback) {
        owner(); List<Target> frozen = List.copyOf(targets);
        EscrowTags.require(!frozen.isEmpty() && frozen.size() <= EscrowLimits.OPERATIONS * 2, "Joint endpoint limit exceeded");
        EscrowEndpoint first = frozen.get(0).endpoint();
        var keys = new java.util.HashSet<EscrowEndpoint.Key>();
        for (Target target : frozen) {
            EscrowEndpoint endpoint = target.endpoint();
            EscrowTags.require(keys.add(endpoint.key()) && sameSlot(first, endpoint), "Joint proof needs unique endpoints in one slot");
        }
        return begin(store, first, frozen.get(0).frontier(), root -> frozen.stream().allMatch(target -> target.validator().test(root)), tick, result -> {
            if (!result.confirmed()) { callback.accept(new JointResult(Map.of(), result.reason())); return; }
            callback.accept(new JointResult(splitProof(result.proof(), frozen), EscrowReason.NONE));
        });
    }
    static Map<EscrowEndpoint.Key, Proof> splitProof(Proof original, List<Target> targets) {
        AtomicInteger references = new AtomicInteger(targets.size());
        var old = original.capture();
        var shared = new EscrowChunkStore.Capture(old.stored(), old.worker(), old.directory(), old.chunk(), old.generation(),
                old.capturedGeneration(), old.bytes(), old.restoreDirty(), () -> { if (references.decrementAndGet() == 0) { original.release(); } });
        var result = new HashMap<EscrowEndpoint.Key, Proof>();
        targets.forEach(target -> result.put(target.endpoint().key(), new Proof(target.frontier(), shared, new AtomicBoolean(true), original.lifetime())));
        return Map.copyOf(result);
    }
    static boolean sameSlot(EscrowEndpoint first, EscrowEndpoint second) {
        boolean entity = first.kind() == EscrowEndpoint.Kind.ENTITY;
        return first.kind() == second.kind() && first.level().equals(second.level())
                && (entity ? first.x() == second.x() && first.z() == second.z()
                : first.x() >> 4 == second.x() >> 4 && first.z() >> 4 == second.z() >> 4);
    }

    public boolean begin(EscrowChunkStore store, EscrowEndpoint endpoint, EscrowPrefix.State frontier,
                         Predicate<CompoundTag> validator, long tick, Consumer<Result> callback) {
        owner();
        if (!working.get() || !budget.reserve(endpoint.level(), endpoint.kind() == EscrowEndpoint.Kind.ENTITY, tick)) { return false; }
        EscrowChunkStore.Capture capture;
        long started = System.nanoTime();
        checkpoint.accept("BARRIER_STORE_BEFORE");
        try { capture = store.captureAndStore(endpoint, validator); }
        catch (RuntimeException failure) {
            EscrowReason reason = captureReason(failure);
            budget.release(AnvilRecordReader.DECODED_BYTES); count(reason); callback.accept(new Result(null, reason)); return true;
        }
        finally { timings.sample("capture_main", System.nanoTime() - started); }
        budget.captured(capture.bytes());
        outstanding.add(capture);
        capture.stored().whenComplete((ignored, failure) -> timings.sample("own_store_io", System.nanoTime() - started));
        staged.computeIfAbsent(endpoint.level(), key -> new ArrayList<>())
                .add(new Attempt(capture, frontier, validator, callback, checkpoint, false));
        nextForce.putIfAbsent(endpoint.level(), tick + 20);
        return true;
    }

    /**
     * W98 (W96-R5): an ordinary source-retirement proof of one entity slot. It shares the Level's batch, force and
     * readback and every quota, within the ordinary sub-quota. Its labels go only to {@code sink}, and it adds no timing
     * sample or failure count, so governed probes and counters never observe it. No ledger frontier.
     */
    public boolean beginOrdinary(EscrowChunkStore store, String level, net.minecraft.world.level.ChunkPos slot,
                                 Predicate<CompoundTag> validator, long tick, Consumer<String> sink, Consumer<Result> callback) {
        owner();
        if (!working.get() || !budget.reserveOrdinary(level, tick)) { return false; }
        EscrowChunkStore.Capture capture;
        sink.accept("BARRIER_STORE_BEFORE");
        try { capture = store.captureEntitySlot(slot, validator); }
        catch (RuntimeException failure) {
            budget.releaseOrdinary(AnvilRecordReader.DECODED_BYTES); callback.accept(new Result(null, captureReason(failure))); return true;
        }
        budget.captured(capture.bytes());
        outstanding.add(capture);
        staged.computeIfAbsent(level, key -> new ArrayList<>()).add(new Attempt(capture, null, validator, callback, sink, true));
        nextForce.putIfAbsent(level, tick + 20);
        return true;
    }

    /** Read-only hint for {@link #beginOrdinary}; it still reserves atomically. */
    public boolean availableOrdinary(String level, long tick) { owner(); return working.get() && budget.availableOrdinary(level, tick); }

    private void forceBatch(List<Attempt> attempts) {
        // Each batch label goes once to every distinct sink; an all-governed batch reaches only the governed checkpoint.
        var sinks = attempts.stream().map(Attempt::sink).distinct().toList();
        boolean governed = attempts.stream().anyMatch(attempt -> !attempt.ordinary());
        // Keep every own future: synchronize's pending set is not a completed-error history.
        CompletableFuture.allOf(attempts.stream().map(attempt -> attempt.capture().stored())
                .toArray(CompletableFuture[]::new)).whenComplete((stored, storeFailure) -> {
            CompletableFuture<Void> forced;
            long forceStarted = System.nanoTime();
            synchronized (lifecycle) {
                if (!working.get()) { return; }
                if (storeFailure != null) {
                    attempts.forEach(attempt -> complete(attempt, EscrowReason.BARRIER_IO)); return;
                }
                sinks.forEach(sink -> sink.accept("BARRIER_STORE_AFTER"));
                sinks.forEach(sink -> sink.accept("BARRIER_FORCE_BEFORE"));
                sinks.forEach(sink -> sink.accept("K6"));
                // Quiesce cannot overtake a force submission, even when the own future completes off-thread.
                forced = CompletableFuture.allOf(attempts.stream().map(attempt -> attempt.capture().worker()).distinct()
                        .map(worker -> worker.synchronize(true)).toArray(CompletableFuture[]::new));
            }
            // A cache-wide force occurs at most once per original worker in this Level's 20-tick batch.
            forced.whenComplete((ignored, forceFailure) -> {
                        if (governed) { timings.sample("force_io", System.nanoTime() - forceStarted); }
                        if (!working.get()) { return; }
                        if (forceFailure != null) {
                            attempts.forEach(attempt -> complete(attempt, EscrowReason.BARRIER_FORCE)); return;
                        }
                        sinks.forEach(sink -> sink.accept("BARRIER_FORCE_AFTER"));
                        try { reader.execute(() -> attempts.forEach(this::readback)); }
                        catch (java.util.concurrent.RejectedExecutionException stopped) {
                            if (working.get()) { throw stopped; }
                        }
                    });
        });
    }
    private void readback(Attempt attempt) {
        if (!working.get()) { return; }
        var capture = attempt.capture(); EscrowReason reason = EscrowReason.NONE;
        long started = System.nanoTime();
        attempt.sink().accept("BARRIER_READBACK_BEFORE");
        try {
            if (capture.generation().get() != capture.capturedGeneration()) { reason = EscrowReason.SUPERSEDING_SAVE; }
            else if (!attempt.validator().test(AnvilRecordReader.read(capture.directory(), capture.chunk()).root())) {
                reason = EscrowReason.BARRIER_UNSTABLE;
            }
        } catch (Exception failureRead) { reason = readbackReason(failureRead); }
        if (reason == EscrowReason.NONE) { attempt.sink().accept("BARRIER_READBACK_AFTER"); }
        if (!attempt.ordinary()) { timings.sample("readback_io", System.nanoTime() - started); }
        complete(attempt, reason);
    }
    private void complete(Attempt attempt, EscrowReason reason) {
        if (working.get()) {
            completions.add(new Pending(attempt.capture(), attempt.frontier(), attempt.callback(), reason, attempt.ordinary()));
        }
    }
    public void tick(long tick) {
        owner();
        if (!working.get()) { return; }
        for (var entry : staged.entrySet()) {
            if (!entry.getValue().isEmpty() && tick >= nextForce.get(entry.getKey())) {
                List<Attempt> ready = List.copyOf(entry.getValue()); entry.getValue().clear();
                nextForce.put(entry.getKey(), tick + 20); forceBatch(ready);
            }
        }
        owner(); Pending result; int callbacks = 32;
        while (callbacks-- > 0 && (result = completions.poll()) != null) {
            if (result.ordinary()) { budget.releaseOrdinary(result.capture().bytes()); }
            else { budget.release(result.capture().bytes()); }
            EscrowReason reason = result.failure();
            outstanding.remove(result.capture());
            if (reason == EscrowReason.NONE && result.capture().generation().get() != result.capture().capturedGeneration()) {
                reason = EscrowReason.SUPERSEDING_SAVE;
            }
            if (reason == EscrowReason.NONE) {
                result.callback().accept(new Result(new Proof(result.frontier(), result.capture(), new AtomicBoolean(true), active), EscrowReason.NONE));
            } else {
                result.capture().restoreDirty().run();
                result.capture().releaseWatch().run();
                if (!result.ordinary()) { count(reason); }
                result.callback().accept(new Result(null, reason));
            }
        }
    }
    /** Design reason table: IO/force/unstable and superseding saves differ from veto/external/bounds refusals. */
    static EscrowReason captureReason(RuntimeException failure) {
        return failure instanceof EscrowChunkStore.SupersededCapture ? EscrowReason.SUPERSEDING_SAVE
                : failure instanceof EscrowChunkStore.CaptureBounds ? EscrowReason.BARRIER_BOUNDS : EscrowReason.BARRIER_VETO;
    }
    static EscrowReason readbackReason(Exception failure) {
        return failure instanceof AnvilRecordReader.Refusal refusal ? refusal.reason() : EscrowReason.BARRIER_UNSTABLE;
    }
    private void count(EscrowReason reason) {
        switch (reason) {
            case SUPERSEDING_SAVE -> supersedingSaves++;
            case BARRIER_VETO, BARRIER_EXTERNAL, BARRIER_BOUNDS -> refusals++;
            default -> ioFailures++;
        }
    }
    public int ioFailures() { return ioFailures; }
    public int supersedingSaves() { return supersedingSaves; }
    public int refusals() { return refusals; }
    /** Discard unfinished barriers without invalidating proofs held by already-frozen ledger generations. */
    public void quiesce() {
        owner(); synchronized (lifecycle) {
            working.set(false); completions.clear(); staged.clear(); reader.shutdown();
            outstanding.forEach(capture -> { capture.restoreDirty().run(); capture.releaseWatch().run(); }); outstanding.clear();
        }
    }
    @Override public void close() { owner(); quiesce(); active.set(false); }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Barrier requires its owner thread"); }
}
