package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.ChunkDataEvent;

/** Only actively protected slots are monitored; ordinary world's saves do not grow a global map. */
public final class EscrowSaveMonitor {
    public record Slot(String level, boolean entities, long chunk) { }
    /** Release-test observation only; the counter remains the authority for proof validity. */
    public record Observation(Slot slot, AtomicLong counter, long generation, String cause) { }
    private java.util.function.Consumer<Observation> observer;
    public void observerForTests(net.minecraft.server.MinecraftServer server, java.util.function.Consumer<Observation> value) {
        EscrowTestEndpoints.requireEnabled(server);
        observer = value;
    }
    private void observe(Slot slot, Watch watch, String cause) {
        if (observer == null) { return; }
        try { observer.accept(new Observation(slot, watch.generation, watch.generation.get(), cause)); }
        catch (RuntimeException failure) {
            // A diagnostic failure must not affect save/retry behavior. The diagnostic must report its missing evidence.
            observer = null;
            com.mojang.logging.LogUtils.getLogger().info("Escrow save observation disabled after diagnostic failure", failure);
        }
    }
    private static final class Watch {
        private final AtomicLong generation = new AtomicLong();
        private int references;
        private Handle pin;
    }
    /** Owner-thread ownership; a stale or repeated release cannot release a newer watch or pin. */
    final class Handle {
        private final Slot slot;
        private final Watch watch;
        private final long captured;
        private final AtomicBoolean lifetime;
        private boolean released;
        private Handle(Slot slot, Watch watch, long captured, AtomicBoolean lifetime) {
            this.slot = slot; this.watch = watch; this.captured = captured; this.lifetime = lifetime;
        }
        AtomicLong generation() { return watch.generation; }
        long capturedGeneration() { return captured; }
        void release() {
            if (released) { return; }
            released = true;
            if (slots.get(slot) != watch) { return; }
            if (watch.pin == this) { watch.pin = null; }
            if (--watch.references == 0) { slots.remove(slot); }
        }
        private boolean protects() { return !released && lifetime.get() && watch.generation.get() == captured; }
    }
    private final Map<Slot, Watch> slots = new HashMap<>();
    private ChunkDataEvent.Save ownEvent;
    Handle captureWatch(String level, boolean entities, ChunkPos pos, AtomicBoolean lifetime) {
        return acquire(new Slot(level, entities, pos.toLong()), entities, lifetime);
    }
    /** Check and reserve without calling a serializer, observer or checkpoint between them. */
    Handle ordinaryWatch(String level, ChunkPos pos, BooleanSupplier reserve) {
        if (!ordinaryAvailable(level, pos) || !reserve.getAsBoolean()) { return null; }
        return acquire(new Slot(level, true, pos.toLong()), false, null);
    }
    public boolean ordinaryAvailable(String level, ChunkPos pos) {
        Watch watch = slots.get(new Slot(level, true, pos.toLong()));
        return watch == null || watch.pin == null || !watch.pin.protects();
    }
    private Handle acquire(Slot slot, boolean protect, AtomicBoolean lifetime) {
        if (!slots.containsKey(slot) && slots.size() >= 128) { throw new EscrowChunkStore.CaptureBounds("Escrow monitor capacity exceeded"); }
        Watch watch = slots.computeIfAbsent(slot, key -> new Watch()); watch.references++;
        var handle = new Handle(slot, watch, watch.generation.incrementAndGet(), lifetime);
        if (protect) { watch.pin = handle; }
        // Publish the pin before diagnostics, which may observe a genuine nested save.
        observe(slot, watch, "CAPTURE"); return handle;
    }
    // Package-local compatibility helpers for the existing generation/observation unit fixtures.
    AtomicLong watch(String level, boolean entities, ChunkPos pos) {
        return captureWatch(level, entities, pos, new AtomicBoolean(true)).generation();
    }
    void release(String level, boolean entities, ChunkPos pos) {
        Slot slot = new Slot(level, entities, pos.toLong());
        Watch watch = slots.get(slot);
        if (watch != null && --watch.references == 0) { slots.remove(slot); }
    }
    public void observed(String level, boolean entities, ChunkPos pos) {
        Slot slot = new Slot(level, entities, pos.toLong());
        Watch watch = slots.get(slot);
        if (watch != null) { watch.generation.incrementAndGet(); observe(slot, watch, "ORDINARY_STORE"); }
    }
    public void observed(ChunkDataEvent.Save event) {
        if (event != ownEvent && event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            observed(level.dimension().location().toString(), false, event.getChunk().getPos());
        }
    }
    public void postOwn(ChunkDataEvent.Save event) {
        EscrowTags.require(ownEvent == null, "Reentrant barrier serialization"); ownEvent = event;
        try { net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event); } finally { ownEvent = null; }
    }
    public void clear() { slots.values().forEach(watch -> watch.generation.incrementAndGet()); slots.clear(); observer = null; }
}
