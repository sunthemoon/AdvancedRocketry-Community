package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.Map;
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
    private final Thread owner = Thread.currentThread();
    private final ExecutorService reader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ARCE-escrow-readback"); thread.setDaemon(true); return thread;
    });
    private final ArrayBlockingQueue<Pending> completions = new ArrayBlockingQueue<>(32);
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Map<String, Batch> batches = new HashMap<>();
    private int pending;
    private int bytes;
    private long captureTick = Long.MIN_VALUE;
    private int ioFailures;
    private int supersedingSaves;
    private final Consumer<String> checkpoint;
    public EscrowBarrier() { this(cut -> { }); }
    public EscrowBarrier(Consumer<String> checkpoint) { this.checkpoint = checkpoint; }
    private record Batch(long started, int terrain, int entities) { }

    public boolean begin(EscrowChunkStore store, EscrowEndpoint endpoint, EscrowPrefix.State frontier,
                         Predicate<CompoundTag> validator, long tick, Consumer<Result> callback) {
        owner();
        if (!active.get() || pending >= 32 || captureTick == tick
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
        // A successful later synchronize cannot hide a failed own-store future.
        capture.stored().whenComplete((ignored, failure) -> {
            if (!active.get()) { return; }
            if (failure != null) { complete(capture, frontier, callback, EscrowReason.BARRIER_IO); return; }
            checkpoint.accept("K6");
            capture.worker().synchronize(true).whenComplete((forced, forceFailure) -> {
                if (!active.get()) { return; }
                if (forceFailure != null) { complete(capture, frontier, callback, EscrowReason.BARRIER_FORCE); return; }
                reader.execute(() -> {
                    EscrowReason reason = EscrowReason.NONE;
                    try {
                        if (capture.generation().get() != capture.capturedGeneration()) { reason = EscrowReason.SUPERSEDING_SAVE; }
                        else if (!validator.test(AnvilRecordReader.read(capture.directory(), capture.chunk()).root())) {
                            reason = EscrowReason.BARRIER_UNSTABLE;
                        }
                    } catch (Exception failureRead) { reason = EscrowReason.BARRIER_UNSTABLE; }
                    complete(capture, frontier, callback, reason);
                });
            });
        });
        return true;
    }
    private void complete(EscrowChunkStore.Capture capture, EscrowPrefix.State frontier,
                          Consumer<Result> callback, EscrowReason reason) {
        if (active.get()) { completions.add(new Pending(capture, frontier, callback, reason)); }
    }
    public void tick() {
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
    @Override public void close() { owner(); active.set(false); completions.clear(); reader.shutdown(); }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Barrier requires its owner thread"); }
}
