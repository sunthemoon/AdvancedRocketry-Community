package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternSize;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Bounded reverse index from loaded-world changes to known controller footprints. */
public final class MultiblockFootprintIndex {
    public static final int MAX_CONTROLLER_LIMIT = 65_536;
    public static final int MAX_CELL_REFERENCE_LIMIT = 4_194_304;

    private final int maxControllers;
    private final int maxCellReferences;
    private final Map<MultiblockControllerKey, Set<BlockPos>> footprints = new HashMap<>();
    private final Map<CellKey, Set<MultiblockControllerKey>> byCell = new HashMap<>();
    private final Map<ChunkKey, Set<MultiblockControllerKey>> byChunk = new HashMap<>();
    private int cellReferenceCount;

    public MultiblockFootprintIndex(int maxControllers, int maxCellReferences) {
        if (maxControllers < 1 || maxControllers > MAX_CONTROLLER_LIMIT) {
            throw new IllegalArgumentException("footprint controller capacity is outside its hard limit");
        }
        if (maxCellReferences < PatternSize.MAX_CELLS
                || maxCellReferences > MAX_CELL_REFERENCE_LIMIT) {
            throw new IllegalArgumentException("footprint cell capacity is outside its hard limit");
        }
        this.maxControllers = maxControllers;
        this.maxCellReferences = maxCellReferences;
    }

    /** Replaces one footprint atomically; false means the previous footprint remains active. */
    public boolean replace(MultiblockControllerKey controller, Set<BlockPos> cells) {
        Objects.requireNonNull(controller, "controller");
        Set<BlockPos> bounded = immutableCells(cells);
        if (!bounded.contains(controller.position())) {
            throw new IllegalArgumentException("controller footprint must contain its controller position");
        }
        Set<BlockPos> previous = footprints.get(controller);
        if (previous == null && footprints.size() >= maxControllers) {
            return false;
        }
        int previousSize = previous == null ? 0 : previous.size();
        if ((long) cellReferenceCount - previousSize + bounded.size() > maxCellReferences) {
            return false;
        }

        if (previous != null) {
            removeReferences(controller, previous);
        }
        footprints.put(controller, bounded);
        addReferences(controller, bounded);
        return true;
    }

    public void remove(MultiblockControllerKey controller) {
        Set<BlockPos> previous = footprints.remove(Objects.requireNonNull(controller, "controller"));
        if (previous != null) {
            removeReferences(controller, previous);
        }
    }

    public Set<MultiblockControllerKey> controllersAt(ResourceKey<Level> level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        return Set.copyOf(byCell.getOrDefault(new CellKey(level, position), Set.of()));
    }

    public Set<MultiblockControllerKey> controllersInChunk(
            ResourceKey<Level> level,
            int chunkX,
            int chunkZ
    ) {
        Objects.requireNonNull(level, "level");
        return Set.copyOf(byChunk.getOrDefault(new ChunkKey(level, chunkX, chunkZ), Set.of()));
    }

    public Set<MultiblockControllerKey> controllers() {
        return Set.copyOf(footprints.keySet());
    }

    public boolean containsController(MultiblockControllerKey controller) {
        return footprints.containsKey(Objects.requireNonNull(controller, "controller"));
    }

    public int controllerCount() {
        return footprints.size();
    }

    public int cellReferenceCount() {
        return cellReferenceCount;
    }

    public void clear() {
        footprints.clear();
        byCell.clear();
        byChunk.clear();
        cellReferenceCount = 0;
    }

    private void addReferences(MultiblockControllerKey controller, Set<BlockPos> cells) {
        Set<ChunkKey> chunks = new LinkedHashSet<>();
        for (BlockPos position : cells) {
            byCell.computeIfAbsent(new CellKey(controller.level(), position), ignored -> new LinkedHashSet<>())
                    .add(controller);
            chunks.add(ChunkKey.from(controller.level(), position));
        }
        chunks.forEach(chunk -> byChunk.computeIfAbsent(chunk, ignored -> new LinkedHashSet<>())
                .add(controller));
        cellReferenceCount += cells.size();
    }

    private void removeReferences(MultiblockControllerKey controller, Set<BlockPos> cells) {
        Set<ChunkKey> chunks = new LinkedHashSet<>();
        for (BlockPos position : cells) {
            CellKey cell = new CellKey(controller.level(), position);
            removeValue(byCell, cell, controller);
            chunks.add(ChunkKey.from(controller.level(), position));
        }
        chunks.forEach(chunk -> removeValue(byChunk, chunk, controller));
        cellReferenceCount -= cells.size();
    }

    private static <K> void removeValue(
            Map<K, Set<MultiblockControllerKey>> index,
            K key,
            MultiblockControllerKey controller
    ) {
        Set<MultiblockControllerKey> values = index.get(key);
        if (values != null && values.remove(controller) && values.isEmpty()) {
            index.remove(key);
        }
    }

    private static Set<BlockPos> immutableCells(Set<BlockPos> cells) {
        Objects.requireNonNull(cells, "cells");
        if (cells.isEmpty() || cells.size() > PatternSize.MAX_CELLS) {
            throw new IllegalArgumentException("controller footprint exceeds the pattern cell limit");
        }
        LinkedHashSet<BlockPos> copy = new LinkedHashSet<>();
        for (BlockPos position : cells) {
            copy.add(Objects.requireNonNull(position, "footprint position").immutable());
        }
        if (copy.size() != cells.size()) {
            throw new IllegalArgumentException("controller footprint contains duplicate positions");
        }
        return Set.copyOf(copy);
    }

    private record CellKey(ResourceKey<Level> level, BlockPos position) {
        private CellKey {
            Objects.requireNonNull(level, "level");
            position = Objects.requireNonNull(position, "position").immutable();
        }
    }

    private record ChunkKey(ResourceKey<Level> level, int x, int z) {
        private ChunkKey {
            Objects.requireNonNull(level, "level");
        }

        private static ChunkKey from(ResourceKey<Level> level, BlockPos position) {
            return new ChunkKey(
                    level,
                    Math.floorDiv(position.getX(), 16),
                    Math.floorDiv(position.getZ(), 16)
            );
        }
    }
}
