package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockControllerKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockDirtyQueue;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFootprintIndex;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.DirtyEnqueueResult;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** One Level's bounded, event-driven structure lifecycle; the kernel supplies the index and fair queue. */
public final class SolarArrayManager {
    private static final int MAX_CONTROLLERS = 16_384;
    private final ServerLevel level;
    private final Map<BlockPos, SolarArrayBlockEntity> controllers = new LinkedHashMap<>();
    private final MultiblockFootprintIndex footprints = new MultiblockFootprintIndex(MAX_CONTROLLERS, MAX_CONTROLLERS * 66);
    private final MultiblockDirtyQueue dirty = new MultiblockDirtyQueue(MAX_CONTROLLERS, 32, 8_192);
    private final Set<BlockPos> retry = new LinkedHashSet<>();
    private boolean closed;

    SolarArrayManager(ServerLevel level) { this.level = level; }

    void observe(SolarArrayBlockEntity controller) {
        if (closed || controller.getLevel() != level) { return; }
        BlockPos position = controller.getBlockPos();
        if (!controllers.containsKey(position) && controllers.size() >= MAX_CONTROLLERS) {
            controller.invalidateStructure(SolarArrayRules.Structure.UNAVAILABLE); return;
        }
        var previous = controllers.put(position, controller);
        if (previous != null && previous != controller) { previous.invalidateStructure(SolarArrayRules.Structure.PENDING); }
        if (!footprints.replace(key(position), controller.footprint())) {
            controller.invalidateStructure(SolarArrayRules.Structure.UNAVAILABLE); return;
        }
        enqueue(controller);
    }

    void forget(SolarArrayBlockEntity controller) {
        if (!controllers.remove(controller.getBlockPos(), controller)) { return; }
        footprints.remove(key(controller.getBlockPos())); retry.remove(controller.getBlockPos());
        controller.invalidateStructure(SolarArrayRules.Structure.PENDING);
    }

    public void changed(BlockPos position) {
        if (closed) { return; }
        for (var key : footprints.controllersAt(level.dimension(), position)) {
            var controller = controllers.get(key.position());
            if (controller != null) { enqueue(controller); }
        }
    }

    void released(BlockPos plug) {
        if (closed) { return; }
        for (var key : footprints.controllersAt(level.dimension(), plug)) {
            var controller = controllers.get(key.position());
            if (controller != null && controller.structure() == SolarArrayRules.Structure.CONFLICT) { enqueue(controller); }
        }
    }

    public void chunkChanged(int x, int z, boolean unloading) {
        if (closed) { return; }
        for (var key : footprints.controllersInChunk(level.dimension(), x, z)) {
            var controller = controllers.get(key.position());
            if (controller != null) {
                controller.chunkState(x, z, unloading);
                enqueue(controller);
                if (unloading) { controller.invalidateStructure(SolarArrayRules.Structure.UNLOADED); }
            }
        }
    }

    public void reload() {
        if (!closed) { controllers.values().forEach(this::enqueue); }
    }

    private void enqueue(SolarArrayBlockEntity controller) {
        controller.invalidateStructure(SolarArrayRules.Structure.PENDING);
        // A state/facing change replaces the old footprint before validation.
        if (!footprints.replace(key(controller.getBlockPos()), controller.footprint())) {
            controller.invalidateStructure(SolarArrayRules.Structure.UNAVAILABLE); return;
        }
        if (dirty.enqueue(key(controller.getBlockPos()), SolarArrayLayout.CELL_COUNT) == DirtyEnqueueResult.QUEUE_FULL) {
            retry.add(controller.getBlockPos());
        }
    }

    public void tick() {
        if (closed || !level.getServer().isSameThread()) { return; }
        var iterator = retry.iterator();
        int budget = 32;
        while (iterator.hasNext() && budget-- > 0) {
            var position = iterator.next();
            if (dirty.enqueue(key(position), SolarArrayLayout.CELL_COUNT) == DirtyEnqueueResult.QUEUE_FULL) { break; }
            iterator.remove();
        }
        dirty.tick(key -> {
            var controller = controllers.get(key.position());
            if (controller != null && controller.physical()) { controller.revalidate(level); }
        });
    }

    public void close() {
        closed = true;
        controllers.values().forEach(controller -> controller.invalidateStructure(SolarArrayRules.Structure.UNAVAILABLE));
        controllers.clear(); footprints.clear(); dirty.clear(); retry.clear();
    }
    private MultiblockControllerKey key(BlockPos position) { return new MultiblockControllerKey(level.dimension(), position); }
}
