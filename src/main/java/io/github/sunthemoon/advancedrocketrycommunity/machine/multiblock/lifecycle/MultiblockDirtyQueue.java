package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternSize;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Deduplicated per-server queue with fixed controller and cell budgets per tick. */
public final class MultiblockDirtyQueue {
    public static final int MAX_PENDING_LIMIT = 65_536;
    public static final int MAX_CONTROLLERS_PER_TICK_LIMIT = 1_024;
    public static final int MAX_CELLS_PER_TICK_LIMIT = 65_536;

    private final int maxPending;
    private final int maxControllersPerTick;
    private final int maxCellsPerTick;
    private final LinkedHashMap<MultiblockControllerKey, Integer> pending = new LinkedHashMap<>();

    public MultiblockDirtyQueue(int maxPending, int maxControllersPerTick, int maxCellsPerTick) {
        if (maxPending < 1 || maxPending > MAX_PENDING_LIMIT) {
            throw new IllegalArgumentException("dirty queue capacity is outside its hard limit");
        }
        if (maxControllersPerTick < 1 || maxControllersPerTick > MAX_CONTROLLERS_PER_TICK_LIMIT) {
            throw new IllegalArgumentException("controller tick budget is outside its hard limit");
        }
        if (maxCellsPerTick < PatternSize.MAX_CELLS || maxCellsPerTick > MAX_CELLS_PER_TICK_LIMIT) {
            throw new IllegalArgumentException("cell tick budget must fit one maximum pattern");
        }
        this.maxPending = maxPending;
        this.maxControllersPerTick = maxControllersPerTick;
        this.maxCellsPerTick = maxCellsPerTick;
    }

    /** A QUEUE_FULL result requires the caller to stop that controller until it can retry. */
    public DirtyEnqueueResult enqueue(MultiblockControllerKey controller, int patternCells) {
        Objects.requireNonNull(controller, "controller");
        if (patternCells < 1 || patternCells > PatternSize.MAX_CELLS) {
            throw new IllegalArgumentException("queued pattern cell count exceeds the hard limit");
        }
        Integer previous = pending.get(controller);
        if (previous != null) {
            pending.put(controller, Math.max(previous, patternCells));
            return DirtyEnqueueResult.ALREADY_QUEUED;
        }
        if (pending.size() >= maxPending) {
            return DirtyEnqueueResult.QUEUE_FULL;
        }
        pending.put(controller, patternCells);
        return DirtyEnqueueResult.ENQUEUED;
    }

    public DirtyTickResult tick(Consumer<MultiblockControllerKey> validator) {
        Objects.requireNonNull(validator, "validator");
        int pendingBefore = pending.size();
        int selectedCells = 0;
        List<Map.Entry<MultiblockControllerKey, Integer>> selected = new ArrayList<>();
        Iterator<Map.Entry<MultiblockControllerKey, Integer>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext() && selected.size() < maxControllersPerTick) {
            Map.Entry<MultiblockControllerKey, Integer> next = iterator.next();
            if (selectedCells + next.getValue() > maxCellsPerTick) {
                break;
            }
            selected.add(Map.entry(next.getKey(), next.getValue()));
            selectedCells += next.getValue();
            iterator.remove();
        }
        int processedControllers = 0;
        int processedCells = 0;
        for (int index = 0; index < selected.size(); index++) {
            Map.Entry<MultiblockControllerKey, Integer> next = selected.get(index);
            try {
                validator.accept(next.getKey());
            } catch (RuntimeException exception) {
                for (int restore = index; restore < selected.size(); restore++) {
                    Map.Entry<MultiblockControllerKey, Integer> unprocessed = selected.get(restore);
                    pending.merge(unprocessed.getKey(), unprocessed.getValue(), Math::max);
                }
                throw exception;
            }
            processedControllers++;
            processedCells += next.getValue();
        }
        return new DirtyTickResult(pendingBefore, processedControllers, processedCells, pending.size());
    }

    public int pendingCount() {
        return pending.size();
    }

    public void clear() {
        pending.clear();
    }
}
