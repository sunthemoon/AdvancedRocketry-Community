package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.DirtyEnqueueResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockControllerKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockDirtyQueue;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFootprintIndex;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Server-lifecycle owner for bounded Rolling Machine indexing and dirty validation. */
public final class RollingMachineManager {
    public static final int MAX_TRACKED_CONTROLLERS = 16_384;
    public static final int MAX_TRACKED_CELL_REFERENCES = 1_000_000;
    public static final int MAX_CONTROLLERS_PER_TICK = 32;
    public static final int MAX_CELLS_PER_TICK = 8_192;
    public static final int MAX_PROCESSES_PER_TICK = 256;

    private final MultiblockPatternCatalogManager patterns;
    private final MultiblockFootprintIndex footprints = new MultiblockFootprintIndex(
            MAX_TRACKED_CONTROLLERS,
            MAX_TRACKED_CELL_REFERENCES
    );
    private final MultiblockDirtyQueue dirty = new MultiblockDirtyQueue(
            MAX_TRACKED_CONTROLLERS,
            MAX_CONTROLLERS_PER_TICK,
            MAX_CELLS_PER_TICK
    );
    private final LinkedHashSet<MultiblockControllerKey> dirtyRetries = new LinkedHashSet<>();
    private final LinkedHashSet<MultiblockControllerKey> reindexPending = new LinkedHashSet<>();
    private final LinkedHashSet<MultiblockControllerKey> processReady = new LinkedHashSet<>();
    private long observedCatalogGeneration = -1L;

    public RollingMachineManager(MultiblockPatternCatalogManager patterns) {
        this.patterns = java.util.Objects.requireNonNull(patterns, "patterns");
    }

    public void observeController(ServerLevel level, RollingMachineBlockEntity controller) {
        MultiblockControllerKey key = key(level, controller.getBlockPos());
        if (!replaceFootprint(key, controller)) {
            controller.invalidateMissingDefinition(level);
            AdvancedRocketryCommunity.LOGGER.error(
                    "Rolling Machine footprint limit rejected controller at {} in {}",
                    key.position(),
                    key.level().location()
            );
            return;
        }
        enqueue(key);
        enqueueProcess(key);
    }

    public void removeController(ServerLevel level, RollingMachineBlockEntity controller) {
        MultiblockControllerKey key = key(level, controller.getBlockPos());
        controller.unbindForRemoval(level);
        footprints.remove(key);
        dirtyRetries.remove(key);
        reindexPending.remove(key);
        processReady.remove(key);
    }

    public void markDirty(ServerLevel level, BlockPos changedPosition) {
        MultiblockControllerKey direct = key(level, changedPosition);
        for (MultiblockControllerKey controller : footprints.controllersAt(
                level.dimension(),
                changedPosition
        )) {
            enqueue(controller);
        }
        if (level.hasChunkAt(changedPosition)) {
            BlockEntity blockEntity = level.getBlockEntity(changedPosition);
            if (blockEntity instanceof RollingMachineBlockEntity controller
                    && !footprints.containsController(direct)) {
                observeController(level, controller);
            }
        }
    }

    public void onChunkChanged(ServerLevel level, int chunkX, int chunkZ) {
        footprints.controllersInChunk(level.dimension(), chunkX, chunkZ).forEach(this::enqueue);
    }

    public void onChunkUnloading(ServerLevel level, int chunkX, int chunkZ) {
        for (MultiblockControllerKey key : footprints.controllersInChunk(
                level.dimension(),
                chunkX,
                chunkZ
        )) {
            if (!level.hasChunkAt(key.position())) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(key.position());
            if (blockEntity instanceof RollingMachineBlockEntity controller) {
                controller.markWaitingForUnload();
            }
        }
    }

    public void markProcessReady(ServerLevel level, BlockPos controllerPosition) {
        MultiblockControllerKey key = key(level, controllerPosition);
        if (footprints.containsController(key)) {
            enqueueProcess(key);
        }
    }

    public void onRecipesReloaded() {
        for (MultiblockControllerKey key : footprints.controllers()) {
            enqueueProcess(key);
        }
    }

    public void tick(MinecraftServer server) {
        scheduleCatalogRefresh();
        processReindex(server);
        retryDirtyQueue();
        dirty.tick(controller -> validateLoaded(server, controller));
        processReady(server);
    }

    public int trackedControllerCount() {
        return footprints.controllerCount();
    }

    public int pendingValidationCount() {
        return dirty.pendingCount() + dirtyRetries.size();
    }

    public int pendingProcessCount() {
        return processReady.size();
    }

    public void clear() {
        footprints.clear();
        dirty.clear();
        dirtyRetries.clear();
        reindexPending.clear();
        processReady.clear();
        observedCatalogGeneration = -1L;
    }

    private void scheduleCatalogRefresh() {
        long generation = patterns.status().generation();
        if (generation == observedCatalogGeneration) {
            return;
        }
        observedCatalogGeneration = generation;
        reindexPending.addAll(footprints.controllers());
    }

    private void processReindex(MinecraftServer server) {
        Iterator<MultiblockControllerKey> iterator = reindexPending.iterator();
        int processed = 0;
        while (iterator.hasNext() && processed < MAX_CONTROLLERS_PER_TICK) {
            MultiblockControllerKey key = iterator.next();
            iterator.remove();
            processed++;
            ServerLevel level = server.getLevel(key.level());
            if (level == null || !level.hasChunkAt(key.position())) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(key.position());
            if (blockEntity instanceof RollingMachineBlockEntity controller) {
                observeController(level, controller);
            } else {
                footprints.remove(key);
            }
        }
    }

    private void retryDirtyQueue() {
        Iterator<MultiblockControllerKey> iterator = dirtyRetries.iterator();
        int attempted = 0;
        while (iterator.hasNext() && attempted < MAX_CONTROLLERS_PER_TICK) {
            MultiblockControllerKey key = iterator.next();
            attempted++;
            DirtyEnqueueResult result = dirty.enqueue(key, patternCellCount());
            if (result != DirtyEnqueueResult.QUEUE_FULL) {
                iterator.remove();
            } else {
                break;
            }
        }
    }

    private void validateLoaded(MinecraftServer server, MultiblockControllerKey key) {
        ServerLevel level = server.getLevel(key.level());
        if (level == null || !level.hasChunkAt(key.position())) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(key.position());
        if (!(blockEntity instanceof RollingMachineBlockEntity controller)) {
            footprints.remove(key);
            processReady.remove(key);
            return;
        }
        Optional<MultiblockPatternDefinition> definition = definition();
        if (definition.isEmpty()) {
            controller.invalidateMissingDefinition(level);
            processReady.remove(key);
            return;
        }
        try {
            controller.revalidate(level, definition.orElseThrow());
            if (controller.formationState() == MultiblockFormationState.FORMED) {
                enqueueProcess(key);
            } else {
                processReady.remove(key);
            }
        } catch (RuntimeException exception) {
            if (dirtyRetries.size() < MAX_TRACKED_CONTROLLERS) {
                dirtyRetries.add(key);
            }
            AdvancedRocketryCommunity.LOGGER.error(
                    "Rolling Machine validation failed at {} in {}",
                    key.position(),
                    key.level().location(),
                    exception
            );
        }
    }

    private boolean replaceFootprint(
            MultiblockControllerKey key,
            RollingMachineBlockEntity controller
    ) {
        Optional<MultiblockPatternDefinition> definition = definition();
        if (definition.isEmpty()) {
            return footprints.replace(key, Set.of(key.position()));
        }
        try {
            return footprints.replace(key, footprint(controller, definition.orElseThrow()));
        } catch (ArithmeticException exception) {
            return footprints.replace(key, Set.of(key.position()));
        }
    }

    private Set<BlockPos> footprint(
            RollingMachineBlockEntity controller,
            MultiblockPatternDefinition definition
    ) {
        PatternPosition controllerWorld = position(controller.getBlockPos());
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        definition.cells().keySet().forEach(local -> {
            PatternPosition world = controller.controllerState().selectedTransform().localToWorld(
                    local,
                    definition.controllerAnchor(),
                    controllerWorld
            );
            cells.add(new BlockPos(world.x(), world.y(), world.z()));
        });
        return Set.copyOf(cells);
    }

    private void enqueue(MultiblockControllerKey key) {
        DirtyEnqueueResult result = dirty.enqueue(key, patternCellCount());
        if (result == DirtyEnqueueResult.QUEUE_FULL
                && dirtyRetries.size() < MAX_TRACKED_CONTROLLERS) {
            dirtyRetries.add(key);
        }
    }

    private void enqueueProcess(MultiblockControllerKey key) {
        if (processReady.size() < MAX_TRACKED_CONTROLLERS || processReady.contains(key)) {
            processReady.add(key);
        } else {
            AdvancedRocketryCommunity.LOGGER.error(
                    "Rolling Machine process queue limit rejected controller at {} in {}",
                    key.position(),
                    key.level().location()
            );
        }
    }

    private void processReady(MinecraftServer server) {
        Iterator<MultiblockControllerKey> iterator = processReady.iterator();
        List<MultiblockControllerKey> requeue = new ArrayList<>();
        int processed = 0;
        while (iterator.hasNext() && processed < MAX_PROCESSES_PER_TICK) {
            MultiblockControllerKey key = iterator.next();
            iterator.remove();
            processed++;
            ServerLevel level = server.getLevel(key.level());
            if (level == null || !level.hasChunkAt(key.position())) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(key.position());
            if (!(blockEntity instanceof RollingMachineBlockEntity controller)) {
                footprints.remove(key);
                continue;
            }
            try {
                if (controller.tickProcess(level)) {
                    requeue.add(key);
                }
            } catch (RuntimeException exception) {
                controller.requireRecoveryAfterUnexpectedTickFailure();
                AdvancedRocketryCommunity.LOGGER.error(
                        "Rolling Machine process tick failed at {} in {}",
                        key.position(),
                        key.level().location(),
                        exception
                );
            }
        }
        requeue.forEach(this::enqueueProcess);
    }

    private int patternCellCount() {
        return definition().map(value -> value.size().volume()).orElse(1);
    }

    private Optional<MultiblockPatternDefinition> definition() {
        return patterns.current().flatMap(catalog -> catalog.get(RollingMachineIds.MACHINE.toString()));
    }

    private static MultiblockControllerKey key(ServerLevel level, BlockPos position) {
        return new MultiblockControllerKey(level.dimension(), position);
    }

    private static PatternPosition position(BlockPos position) {
        return new PatternPosition(position.getX(), position.getY(), position.getZ());
    }
}
