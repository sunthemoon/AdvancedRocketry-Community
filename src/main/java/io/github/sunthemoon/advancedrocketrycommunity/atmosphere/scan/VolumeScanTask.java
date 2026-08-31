package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Resumable breadth-first scan with hard per-step and total-volume limits. */
public final class VolumeScanTask {
    private final VolumePosition seed;
    private final int maxVolumeCells;
    private final ArrayDeque<VolumePosition> queue = new ArrayDeque<>();
    private final Set<VolumePosition> enqueued = new LinkedHashSet<>();
    private final Set<VolumePosition> cells = new LinkedHashSet<>();

    private VolumeScanOutcome outcome = VolumeScanOutcome.SCANNING;
    private VolumeBounds bounds;
    private VolumePosition pendingPosition;
    private long totalInspections;

    public VolumeScanTask(VolumePosition seed) {
        this(seed, AtmosphereLimits.MAX_VOLUME_CELLS);
    }

    public VolumeScanTask(VolumePosition seed, int maxVolumeCells) {
        this.seed = Objects.requireNonNull(seed, "seed");
        if (maxVolumeCells <= 0 || maxVolumeCells > AtmosphereLimits.MAX_VOLUME_CELLS) {
            throw new IllegalArgumentException(
                    "Volume limit must be between 1 and " + AtmosphereLimits.MAX_VOLUME_CELLS
            );
        }
        this.maxVolumeCells = maxVolumeCells;
        queue.add(seed);
        enqueued.add(seed);
    }

    /**
     * Inspect at most {@code inspectionBudget} queued cells.
     *
     * @return the exact number of world observations performed
     */
    public int step(VolumeWorldView world, int inspectionBudget) {
        Objects.requireNonNull(world, "world");
        if (inspectionBudget <= 0
                || inspectionBudget > AtmosphereLimits.MAX_TASK_INSPECTIONS_PER_TICK) {
            throw new IllegalArgumentException(
                    "Inspection budget must be between 1 and "
                            + AtmosphereLimits.MAX_TASK_INSPECTIONS_PER_TICK
            );
        }
        if (outcome != VolumeScanOutcome.SCANNING) {
            return 0;
        }

        int inspected = 0;
        while (inspected < inspectionBudget && !queue.isEmpty()) {
            VolumePosition position = queue.removeFirst();
            CellObservation observation = Objects.requireNonNull(
                    world.observe(position),
                    "world observation"
            );
            inspected++;
            totalInspections++;

            if (observation == CellObservation.UNLOADED) {
                pendingPosition = position;
                outcome = VolumeScanOutcome.PENDING;
                break;
            }
            if (observation == CellObservation.OPEN) {
                outcome = VolumeScanOutcome.OPEN;
                break;
            }
            if (observation == CellObservation.SEALED) {
                continue;
            }

            if (cells.contains(position)) {
                continue;
            }
            if (cells.size() >= maxVolumeCells) {
                outcome = VolumeScanOutcome.TOO_LARGE;
                break;
            }
            cells.add(position);
            bounds = bounds == null ? VolumeBounds.single(position) : bounds.include(position);
            for (VolumePosition neighbor : position.neighbors()) {
                if (enqueued.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }

        if (outcome == VolumeScanOutcome.SCANNING && queue.isEmpty()) {
            outcome = VolumeScanOutcome.SEALED;
        }
        return inspected;
    }

    public boolean resumePending() {
        if (outcome != VolumeScanOutcome.PENDING) {
            return false;
        }
        queue.addFirst(pendingPosition);
        pendingPosition = null;
        outcome = VolumeScanOutcome.SCANNING;
        return true;
    }

    public boolean cancel() {
        if (outcome.terminal()) {
            return false;
        }
        pendingPosition = null;
        queue.clear();
        outcome = VolumeScanOutcome.CANCELLED;
        return true;
    }

    public VolumeScanOutcome outcome() {
        return outcome;
    }

    public boolean hasDiscovered(VolumePosition position) {
        return cells.contains(position);
    }

    public VolumeScanResult snapshot() {
        return new VolumeScanResult(
                seed,
                outcome,
                cells,
                Optional.ofNullable(bounds),
                totalInspections,
                queue.size(),
                Optional.ofNullable(pendingPosition)
        );
    }
}
