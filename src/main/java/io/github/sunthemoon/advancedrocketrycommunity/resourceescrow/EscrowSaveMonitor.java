package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.ChunkDataEvent;

/** Only actively protected slots are monitored; ordinary world's saves do not grow a global map. */
public final class EscrowSaveMonitor {
    public record Slot(String level, boolean entities, long chunk) { }
    private static final class Watch {
        private final AtomicLong generation = new AtomicLong();
        private int references;
    }
    private final Map<Slot, Watch> slots = new HashMap<>();
    private ChunkDataEvent.Save ownEvent;
    public AtomicLong watch(String level, boolean entities, ChunkPos pos) {
        Slot slot = new Slot(level, entities, pos.toLong());
        EscrowTags.require(slots.containsKey(slot) || slots.size() < 128, "Escrow monitor capacity exceeded");
        Watch watch = slots.computeIfAbsent(slot, key -> new Watch()); watch.references++;
        // The new own capture excludes its own Save event, not an earlier capture's proof of this slot.
        watch.generation.incrementAndGet(); return watch.generation;
    }
    public void release(String level, boolean entities, ChunkPos pos) {
        Slot slot = new Slot(level, entities, pos.toLong()); Watch watch = slots.get(slot);
        if (watch != null && --watch.references == 0) { slots.remove(slot); }
    }
    public void observed(String level, boolean entities, ChunkPos pos) {
        Watch watch = slots.get(new Slot(level, entities, pos.toLong()));
        if (watch != null) { watch.generation.incrementAndGet(); }
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
    public void clear() { slots.clear(); }
}
