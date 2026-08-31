package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** Round-robin task coordinator with collision-based connected-component merge. */
public final class VolumeScanCoordinator {
    private final int maxTasks;
    private final int maxVolumeCells;
    private final LinkedHashMap<Long, CoordinatedTask> tasks = new LinkedHashMap<>();
    private final Map<VolumePosition, Long> seedOwners = new HashMap<>();
    private final Map<VolumePosition, Long> cellOwners = new HashMap<>();
    private final List<CompletedVolumeScan> completed = new ArrayList<>();

    private long nextTaskId = 1L;
    private int roundRobinOffset;

    public VolumeScanCoordinator() {
        this(AtmosphereLimits.MAX_ACTIVE_SCAN_TASKS, AtmosphereLimits.MAX_VOLUME_CELLS);
    }

    public VolumeScanCoordinator(int maxTasks, int maxVolumeCells) {
        if (maxTasks <= 0 || maxTasks > AtmosphereLimits.MAX_ACTIVE_SCAN_TASKS) {
            throw new IllegalArgumentException("Invalid active-task limit");
        }
        if (maxVolumeCells <= 0 || maxVolumeCells > AtmosphereLimits.MAX_VOLUME_CELLS) {
            throw new IllegalArgumentException("Invalid volume-cell limit");
        }
        this.maxTasks = maxTasks;
        this.maxVolumeCells = maxVolumeCells;
    }

    public ScanScheduleResult schedule(VolumePosition seed) {
        Objects.requireNonNull(seed, "seed");
        Long existingSeed = seedOwners.get(seed);
        if (existingSeed != null && tasks.containsKey(existingSeed)) {
            return new ScanScheduleResult(ScanScheduleStatus.ATTACHED, OptionalLong.of(existingSeed));
        }
        Long existingCell = cellOwners.get(seed);
        if (existingCell != null && tasks.containsKey(existingCell)) {
            CoordinatedTask task = tasks.get(existingCell);
            task.seeds.add(seed);
            seedOwners.put(seed, existingCell);
            return new ScanScheduleResult(ScanScheduleStatus.ATTACHED, OptionalLong.of(existingCell));
        }
        if (tasks.size() >= maxTasks) {
            return new ScanScheduleResult(ScanScheduleStatus.BUSY, OptionalLong.empty());
        }

        long id = nextTaskId++;
        CoordinatedTask task = new CoordinatedTask(
                new VolumeScanTask(seed, maxVolumeCells),
                new LinkedHashSet<>(Set.of(seed))
        );
        tasks.put(id, task);
        seedOwners.put(seed, id);
        return new ScanScheduleResult(ScanScheduleStatus.STARTED, OptionalLong.of(id));
    }

    public CoordinatorTickReport tick(VolumeWorldView world, int levelInspectionBudget) {
        Objects.requireNonNull(world, "world");
        if (levelInspectionBudget <= 0
                || levelInspectionBudget > AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK) {
            throw new IllegalArgumentException("Invalid per-level inspection budget");
        }
        if (tasks.isEmpty()) {
            return new CoordinatorTickReport(0, 0, 0, 0, 0);
        }

        List<Long> ids = new ArrayList<>(tasks.keySet());
        int start = Math.floorMod(roundRobinOffset, ids.size());
        int inspected = 0;
        int completedBefore = completed.size();
        int merged = 0;
        for (int index = 0; index < ids.size() && inspected < levelInspectionBudget; index++) {
            long id = ids.get((start + index) % ids.size());
            CoordinatedTask coordinated = tasks.get(id);
            if (coordinated == null || coordinated.task.outcome() != VolumeScanOutcome.SCANNING) {
                continue;
            }
            int taskBudget = Math.min(
                    AtmosphereLimits.MAX_TASK_INSPECTIONS_PER_TICK,
                    levelInspectionBudget - inspected
            );
            VolumeScanStep step = coordinated.task.advance(world, taskBudget);
            inspected += step.inspections();
            MergeResult mergeResult = claim(id, step.newCells());
            merged += mergeResult.mergedTasks;
            if (!mergeResult.currentSurvived) {
                continue;
            }
            CoordinatedTask surviving = tasks.get(id);
            if (surviving != null && surviving.task.outcome().terminal()) {
                complete(id, surviving);
            }
        }
        roundRobinOffset = ids.isEmpty() ? 0 : (start + 1) % ids.size();
        int pending = (int) tasks.values().stream()
                .filter(task -> task.task.outcome() == VolumeScanOutcome.PENDING)
                .count();
        return new CoordinatorTickReport(
                inspected,
                tasks.size(),
                pending,
                completed.size() - completedBefore,
                merged
        );
    }

    public boolean resumePending(VolumePosition seed) {
        Long id = seedOwners.get(seed);
        CoordinatedTask task = id == null ? null : tasks.get(id);
        return task != null && task.task.resumePending();
    }

    public boolean cancelSeed(VolumePosition seed) {
        Long id = seedOwners.remove(seed);
        CoordinatedTask coordinated = id == null ? null : tasks.get(id);
        if (coordinated == null) {
            return false;
        }
        coordinated.seeds.remove(seed);
        if (!coordinated.seeds.isEmpty()) {
            return true;
        }
        removeTask(id, false, 0L);
        return true;
    }

    public List<CompletedVolumeScan> drainCompleted() {
        List<CompletedVolumeScan> result = List.copyOf(completed);
        completed.clear();
        return result;
    }

    public int activeTaskCount() {
        return tasks.size();
    }

    public OptionalLong taskForSeed(VolumePosition seed) {
        Long id = seedOwners.get(seed);
        return id == null || !tasks.containsKey(id) ? OptionalLong.empty() : OptionalLong.of(id);
    }

    private MergeResult claim(long currentId, Set<VolumePosition> newCells) {
        int merged = 0;
        for (VolumePosition position : newCells) {
            Long seedOwner = seedOwners.get(position);
            if (seedOwner != null && seedOwner != currentId && tasks.containsKey(seedOwner)) {
                long survivor = merge(currentId, seedOwner);
                merged++;
                if (survivor != currentId) {
                    return new MergeResult(false, merged);
                }
            }
            Long cellOwner = cellOwners.get(position);
            if (cellOwner != null && cellOwner != currentId && tasks.containsKey(cellOwner)) {
                long survivor = merge(currentId, cellOwner);
                merged++;
                if (survivor != currentId) {
                    return new MergeResult(false, merged);
                }
            }
            cellOwners.put(position, currentId);
        }
        return new MergeResult(true, merged);
    }

    private long merge(long firstId, long secondId) {
        if (firstId == secondId) {
            return firstId;
        }
        long survivorId = Math.min(firstId, secondId);
        long removedId = Math.max(firstId, secondId);
        CoordinatedTask survivor = tasks.get(survivorId);
        CoordinatedTask removed = tasks.get(removedId);
        if (survivor == null || removed == null) {
            return survivor == null ? removedId : survivorId;
        }
        survivor.seeds.addAll(removed.seeds);
        for (VolumePosition seed : removed.seeds) {
            seedOwners.put(seed, survivorId);
        }
        removeTask(removedId, true, survivorId);
        return survivorId;
    }

    private void complete(long id, CoordinatedTask coordinated) {
        VolumeScanResult result = coordinated.task.snapshot();
        Optional<AtmosphereVolume> volume = result.outcome() == VolumeScanOutcome.SEALED
                && !result.cells().isEmpty()
                ? Optional.of(AtmosphereVolume.fromSealedResult(result))
                : Optional.empty();
        completed.add(new CompletedVolumeScan(coordinated.seeds, result, volume));
        removeTask(id, false, 0L);
    }

    private void removeTask(long id, boolean preserveSeeds, long replacementId) {
        CoordinatedTask removed = tasks.remove(id);
        if (removed == null) {
            return;
        }
        removed.task.cancel();
        if (!preserveSeeds) {
            for (VolumePosition seed : removed.seeds) {
                seedOwners.remove(seed, id);
            }
        } else {
            for (VolumePosition seed : removed.seeds) {
                seedOwners.put(seed, replacementId);
            }
        }
        Iterator<Map.Entry<VolumePosition, Long>> iterator = cellOwners.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() == id) {
                iterator.remove();
            }
        }
    }

    private record CoordinatedTask(VolumeScanTask task, Set<VolumePosition> seeds) {
    }

    private record MergeResult(boolean currentSurvived, int mergedTasks) {
    }
}
