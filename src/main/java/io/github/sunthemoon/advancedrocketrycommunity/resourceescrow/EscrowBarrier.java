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
    private record Pending(EscrowChunkStore.Capture capture, EscrowPrefix.State frontier,
                            Consumer<Result> callback, EscrowReason failure) { }
    private record Attempt(EscrowChunkStore.Capture capture, EscrowPrefix.State frontier,
                            Predicate<CompoundTag> validator, Consumer<Result> callback) { }
    private final Thread owner = Thread.currentThread();
    private final ExecutorService reader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ARCE-escrow-readback"); thread.setDaemon(true); return thread;
    });
    private final ArrayBlockingQueue<Pending> completions = new ArrayBlockingQueue<>(32);
    private final AtomicBoolean active;
    private final AtomicBoolean working = new AtomicBoolean(true);
    private final Object lifecycle = new Object();
    private final Map<String, Batch> batches = new HashMap<>();
    private final Map<String, List<Attempt>> staged = new HashMap<>();
    private final Map<String, Long> nextForce = new HashMap<>();
    private int pending;
    private int bytes;
    private long captureTick = Long.MIN_VALUE;
    private int ioFailures;
    private int supersedingSaves;
    private final Consumer<String> checkpoint;
    public EscrowBarrier() { this(cut -> { }); }
    public EscrowBarrier(Consumer<String> checkpoint) { this(checkpoint, new AtomicBoolean(true)); }
    EscrowBarrier(Consumer<String> checkpoint, AtomicBoolean lifetime) { this.checkpoint = checkpoint; active = lifetime; }
    private record Batch(long started, int terrain, int entities) { }

    public boolean begin(EscrowChunkStore store, EscrowEndpoint endpoint, EscrowPrefix.State frontier,
                         Predicate<CompoundTag> validator, long tick, Consumer<Result> callback) {
        owner();
        if (!working.get() || pending >= 32 || captureTick == tick
                || bytes > EscrowLimits.CAPTURE_BYTES - AnvilRecordReader.DECODED_BYTES) { return false; }
        Batch batch = batches.get(endpoint.level());
        if (batch == null || tick - batch.started() >= 20) { batch = new Batch(tick, 0, 0); }
        boolean entity = endpoint.kind() == EscrowEndpoint.Kind.ENTITY;
        if ((entity ? batch.entities() : batch.terrain()) >= 16) { return false; }
        batches.put(endpoint.level(), new Batch(batch.started(), batch.terrain() + (entity ? 0 : 1), batch.entities() + (entity ? 1 : 0)));
        captureTick = tick;
        EscrowChunkStore.Capture capture;
        try { capture = store.captureAndStore(endpoint, validator); }
        catch (RuntimeException failure) { ioFailures++; callback.accept(new Result(null, EscrowReason.BARRIER_VETO)); return true; }
        pending++; bytes += capture.bytes();
        staged.computeIfAbsent(endpoint.level(), key -> new ArrayList<>()).add(new Attempt(capture, frontier, validator, callback));
        nextForce.putIfAbsent(endpoint.level(), tick + 20);
        return true;
    }
    private void forceBatch(List<Attempt> attempts) {
        // Keep every own future: synchronize's pending set is not a completed-error history.
        CompletableFuture.allOf(attempts.stream().map(attempt -> attempt.capture().stored())
                .toArray(CompletableFuture[]::new)).whenComplete((stored, storeFailure) -> {
            CompletableFuture<Void> forced;
            synchronized (lifecycle) {
                if (!working.get()) { return; }
                if (storeFailure != null) {
                    attempts.forEach(attempt -> complete(attempt.capture(), attempt.frontier(), attempt.callback(), EscrowReason.BARRIER_IO)); return;
                }
                checkpoint.accept("K6");
                // Quiesce cannot overtake a force submission, even when the own future completes off-thread.
                forced = CompletableFuture.allOf(attempts.stream().map(attempt -> attempt.capture().worker()).distinct()
                        .map(worker -> worker.synchronize(true)).toArray(CompletableFuture[]::new));
            }
            // A cache-wide force occurs at most once per original worker in this Level's 20-tick batch.
            forced.whenComplete((ignored, forceFailure) -> {
                        if (!working.get()) { return; }
                        if (forceFailure != null) {
                            attempts.forEach(attempt -> complete(attempt.capture(), attempt.frontier(), attempt.callback(), EscrowReason.BARRIER_FORCE)); return;
                        }
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
        try {
            if (capture.generation().get() != capture.capturedGeneration()) { reason = EscrowReason.SUPERSEDING_SAVE; }
            else if (!attempt.validator().test(AnvilRecordReader.read(capture.directory(), capture.chunk()).root())) {
                reason = EscrowReason.BARRIER_UNSTABLE;
            }
        } catch (Exception failureRead) { reason = EscrowReason.BARRIER_UNSTABLE; }
        complete(capture, attempt.frontier(), attempt.callback(), reason);
    }
    private void complete(EscrowChunkStore.Capture capture, EscrowPrefix.State frontier,
                          Consumer<Result> callback, EscrowReason reason) {
        if (working.get()) { completions.add(new Pending(capture, frontier, callback, reason)); }
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
        owner(); Pending result; int budget = 32;
        while (budget-- > 0 && (result = completions.poll()) != null) {
            pending--; bytes -= result.capture().bytes(); EscrowReason reason = result.failure();
            if (reason == EscrowReason.NONE && result.capture().generation().get() != result.capture().capturedGeneration()) {
                reason = EscrowReason.SUPERSEDING_SAVE;
            }
            if (reason == EscrowReason.NONE) {
                result.callback().accept(new Result(new Proof(result.frontier(), result.capture(), new AtomicBoolean(true), active), EscrowReason.NONE));
            } else {
                result.capture().restoreDirty().run();
                result.capture().releaseWatch().run();
                if (reason == EscrowReason.SUPERSEDING_SAVE) { supersedingSaves++; } else { ioFailures++; }
                result.callback().accept(new Result(null, reason));
            }
        }
    }
    public int ioFailures() { return ioFailures; }
    public int supersedingSaves() { return supersedingSaves; }
    /** Discard unfinished barriers without invalidating proofs held by already-frozen ledger generations. */
    public void quiesce() {
        owner(); synchronized (lifecycle) {
            working.set(false); completions.clear(); staged.clear(); reader.shutdown();
        }
    }
    @Override public void close() { owner(); quiesce(); active.set(false); }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Barrier requires its owner thread"); }
}
