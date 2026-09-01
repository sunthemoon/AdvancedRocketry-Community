package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentOperatingStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentSupplyResult;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.AtmosphereVolume;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CompletedVolumeScan;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CoordinatorTickReport;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.ScanScheduleResult;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.ScanScheduleStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeId;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeBounds;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeIndex;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanCoordinator;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Per-loaded-Level authority for bounded scan scheduling and provider election. */
public final class AtmosphereLevelService {
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator
            .comparingInt((BlockPos position) -> position.getX())
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ);

    private final ServerLevel level;
    private final int inspectionBudget;
    private final VolumeScanCoordinator coordinator;
    private final VolumeIndex index = new VolumeIndex();
    private final Map<BlockPos, VentState> vents = new LinkedHashMap<>();
    private final Map<VolumeId, BlockPos> breathableProviders = new HashMap<>();
    private final ArrayDeque<VolumePosition> dirtyQueue = new ArrayDeque<>();
    private final Set<VolumePosition> dirtySet = new HashSet<>();

    private boolean baseAtmosphereBreathable;
    private boolean exposedSkyIsOpen;
    private int lastTickInspections;
    private int lastPendingTasks;
    private long totalInspections;
    private long dirtyOverflows;
    private long completedServiceTicks;

    public AtmosphereLevelService(
            ServerLevel level,
            boolean baseAtmosphereBreathable,
            boolean exposedSkyIsOpen,
            int maxVolumeCells,
            int inspectionBudget
    ) {
        this.level = Objects.requireNonNull(level, "level");
        if (inspectionBudget <= 0
                || inspectionBudget > AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK) {
            throw new IllegalArgumentException("Invalid atmosphere inspection budget");
        }
        this.inspectionBudget = inspectionBudget;
        this.coordinator = new VolumeScanCoordinator(
                AtmosphereLimits.MAX_ACTIVE_SCAN_TASKS,
                maxVolumeCells
        );
        this.baseAtmosphereBreathable = baseAtmosphereBreathable;
        this.exposedSkyIsOpen = exposedSkyIsOpen;
    }

    public void updateEnvironment(boolean breathable, boolean skyIsOpen) {
        if (baseAtmosphereBreathable == breathable && exposedSkyIsOpen == skyIsOpen) {
            return;
        }
        baseAtmosphereBreathable = breathable;
        exposedSkyIsOpen = skyIsOpen;
        resetAllForRescan();
    }

    public boolean observeVent(OxygenVentBlockEntity vent) {
        Objects.requireNonNull(vent, "vent");
        BlockPos position = vent.getBlockPos().immutable();
        VentState existing = vents.get(position);
        if (existing != null) {
            existing.lastSeenGameTime = level.getGameTime();
            return true;
        }
        if (vents.size() >= AtmosphereLimits.MAX_TRACKED_VENTS) {
            vent.applySupply(VolumeScanOutcome.BUSY, false);
            return false;
        }
        vents.put(position, new VentState(position, level.getGameTime()));
        return true;
    }

    public void removeVent(BlockPos position) {
        VentState removed = vents.remove(position);
        if (removed != null && removed.volumeId != null) {
            breathableProviders.remove(removed.volumeId);
        }
        coordinator.cancelSeed(seed(position));
    }

    /** Invalidate authority synchronously, then queue bounded scan repair. */
    public void markDirty(BlockPos position) {
        Set<VolumePosition> affected = positionAndNeighbors(position);
        Set<VolumeId> invalidated = index.invalidateAround(affected);
        resetVolumes(invalidated);
        for (VentState state : vents.values()) {
            if (state.lastScanBounds != null
                    && affected.stream().anyMatch(state.lastScanBounds::contains)) {
                if (state.volumeId != null) {
                    breathableProviders.remove(state.volumeId);
                }
                state.reset(true);
            }
        }
        for (VolumePosition cell : affected) {
            if (dirtySet.contains(cell)) {
                continue;
            }
            if (dirtySet.size() >= AtmosphereLimits.MAX_DIRTY_POSITIONS) {
                dirtyOverflows++;
                dirtyQueue.clear();
                dirtySet.clear();
                resetAllForRescan();
                return;
            }
            dirtySet.add(cell);
            dirtyQueue.addLast(cell);
        }
    }

    public void onChunkUnload(int chunkX, int chunkZ) {
        Set<VolumeId> invalidated = index.invalidateWhere(
                position -> inChunk(position, chunkX, chunkZ)
        );
        resetVolumes(invalidated);
        Set<VolumePosition> cancelled = coordinator.cancelWhere(
                position -> inChunk(position, chunkX, chunkZ)
        );
        resetSeeds(cancelled);
        Iterator<Map.Entry<BlockPos, VentState>> iterator = vents.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, VentState> entry = iterator.next();
            if (entry.getKey().getX() >> 4 == chunkX && entry.getKey().getZ() >> 4 == chunkZ) {
                if (entry.getValue().volumeId != null) {
                    breathableProviders.remove(entry.getValue().volumeId);
                }
                iterator.remove();
            }
        }
    }

    public int onChunkLoad(int chunkX, int chunkZ) {
        return coordinator.resumePendingWhere(position -> inChunk(position, chunkX, chunkZ));
    }

    public AtmosphereLevelMetrics tick() {
        pruneUnobservedVents();
        processDirtyQueue();
        breathableProviders.clear();

        if (baseAtmosphereBreathable) {
            coordinator.clear();
            index.clear();
            for (VentState state : vents.values()) {
                OxygenVentBlockEntity vent = loadedVent(state.position);
                if (vent != null) {
                    vent.applySupply(VolumeScanOutcome.SEALED, false);
                }
                state.reset(false);
            }
            lastTickInspections = 0;
            lastPendingTasks = 0;
            completedServiceTicks++;
            return metrics();
        }

        scheduleRequiredScans();
        CoordinatorTickReport report = coordinator.tick(
                new ServerLevelVolumeWorldView(level, exposedSkyIsOpen),
                inspectionBudget
        );
        lastTickInspections = report.inspections();
        lastPendingTasks = report.pendingTasks();
        totalInspections += report.inspections();
        acceptCompletedScans();
        applyVentSupply();
        completedServiceTicks++;
        return metrics();
    }

    public BreathabilityState breathabilityAt(BlockPos position) {
        if (baseAtmosphereBreathable) {
            return BreathabilityState.BREATHABLE;
        }
        VolumePosition cell = fromBlockPos(position);
        Optional<AtmosphereVolume> volume = index.find(cell);
        if (volume.isPresent()) {
            BlockPos providerPosition = breathableProviders.get(volume.orElseThrow().id());
            OxygenVentBlockEntity provider = providerPosition == null
                    ? null
                    : loadedVent(providerPosition);
            if (provider != null
                    && provider.status() == VentOperatingStatus.ACTIVE
                    && provider.canSupplyAtmosphere()) {
                return BreathabilityState.BREATHABLE;
            }
        }
        return coordinator.isScanningPosition(cell)
                ? BreathabilityState.PENDING
                : BreathabilityState.VACUUM;
    }

    public boolean baseAtmosphereBreathable() {
        return baseAtmosphereBreathable;
    }

    public AtmosphereLevelMetrics metrics() {
        return new AtmosphereLevelMetrics(
                vents.size(),
                breathableProviders.size(),
                coordinator.activeTaskCount(),
                lastPendingTasks,
                index.volumeCount(),
                index.cellCount(),
                dirtySet.size(),
                lastTickInspections,
                totalInspections,
                dirtyOverflows,
                completedServiceTicks
        );
    }

    public Optional<AtmosphereVolume> volumeAt(BlockPos position) {
        return index.find(fromBlockPos(position));
    }

    public void clear() {
        vents.clear();
        dirtyQueue.clear();
        dirtySet.clear();
        breathableProviders.clear();
        coordinator.clear();
        index.clear();
        lastTickInspections = 0;
        lastPendingTasks = 0;
        completedServiceTicks = 0L;
    }

    private void pruneUnobservedVents() {
        long gameTime = level.getGameTime();
        Iterator<Map.Entry<BlockPos, VentState>> iterator = vents.entrySet().iterator();
        while (iterator.hasNext()) {
            VentState state = iterator.next().getValue();
            if (state.lastSeenGameTime >= gameTime && loadedVent(state.position) != null) {
                continue;
            }
            coordinator.cancelSeed(seed(state.position));
            if (state.volumeId != null) {
                breathableProviders.remove(state.volumeId);
            }
            iterator.remove();
        }
    }

    private void processDirtyQueue() {
        if (dirtyQueue.isEmpty()) {
            return;
        }
        Set<VolumePosition> batch = new LinkedHashSet<>();
        while (!dirtyQueue.isEmpty()
                && batch.size() < AtmosphereLimits.MAX_DIRTY_POSITIONS_PER_TICK) {
            VolumePosition position = dirtyQueue.removeFirst();
            dirtySet.remove(position);
            batch.add(position);
        }
        resetSeeds(coordinator.cancelAround(batch));
    }

    private void scheduleRequiredScans() {
        for (VentState state : vents.values()) {
            if (!state.needsScan) {
                continue;
            }
            VolumePosition seed = seed(state.position);
            Optional<AtmosphereVolume> existing = index.find(seed);
            if (existing.isPresent()) {
                AtmosphereVolume volume = existing.orElseThrow();
                state.attach(volume.id(), volume.bounds());
                continue;
            }
            ScanScheduleResult scheduled = coordinator.schedule(seed);
            if (scheduled.status() == ScanScheduleStatus.BUSY) {
                state.outcome = VolumeScanOutcome.BUSY;
                continue;
            }
            state.needsScan = false;
            state.outcome = VolumeScanOutcome.SCANNING;
        }
    }

    private void acceptCompletedScans() {
        for (CompletedVolumeScan completed : coordinator.drainCompleted()) {
            VolumeScanOutcome outcome = completed.result().outcome();
            VolumeId volumeId = null;
            if (completed.sealedVolume().isPresent()) {
                AtmosphereVolume volume = completed.sealedVolume().orElseThrow();
                for (VolumeId evicted : index.put(volume)) {
                    resetVolumes(Set.of(evicted));
                }
                volumeId = volume.id();
            }
            for (VolumePosition completedSeed : completed.seeds()) {
                VentState state = stateForSeed(completedSeed);
                if (state == null) {
                    continue;
                }
                state.needsScan = false;
                state.outcome = outcome;
                state.volumeId = volumeId;
                state.lastScanBounds = completed.result().bounds()
                        .orElseGet(() -> VolumeBounds.single(completed.result().seed()));
            }
        }
    }

    private void applyVentSupply() {
        Map<VolumeId, List<VentState>> byVolume = new HashMap<>();
        for (VentState state : vents.values()) {
            OxygenVentBlockEntity vent = loadedVent(state.position);
            if (vent == null) {
                continue;
            }
            if (state.volumeId == null || index.find(state.volumeId).isEmpty()) {
                if (state.volumeId != null) {
                    state.reset(true);
                }
                VolumeScanOutcome outcome = coordinator.outcomeForSeed(seed(state.position))
                        .orElse(state.outcome);
                vent.applySupply(outcome, false);
                continue;
            }
            byVolume.computeIfAbsent(state.volumeId, ignored -> new ArrayList<>()).add(state);
        }

        for (Map.Entry<VolumeId, List<VentState>> entry : byVolume.entrySet()) {
            List<VentState> candidates = entry.getValue();
            candidates.sort(Comparator.comparing(state -> state.position, POSITION_ORDER));
            VentState elected = candidates.stream()
                    .filter(state -> {
                        OxygenVentBlockEntity vent = loadedVent(state.position);
                        return vent != null && vent.canSupplyAtmosphere();
                    })
                    .findFirst()
                    .orElse(candidates.get(0));
            for (VentState state : candidates) {
                OxygenVentBlockEntity vent = loadedVent(state.position);
                if (vent == null) {
                    continue;
                }
                VentSupplyResult result = vent.applySupply(
                        VolumeScanOutcome.SEALED,
                        state == elected
                );
                if (state == elected && result.breathable()) {
                    breathableProviders.put(entry.getKey(), state.position);
                }
            }
        }
    }

    private void resetVolumes(Set<VolumeId> volumeIds) {
        if (volumeIds.isEmpty()) {
            return;
        }
        for (VolumeId volumeId : volumeIds) {
            breathableProviders.remove(volumeId);
        }
        for (VentState state : vents.values()) {
            if (state.volumeId != null && volumeIds.contains(state.volumeId)) {
                state.reset(true);
            }
        }
    }

    private void resetSeeds(Set<VolumePosition> seeds) {
        for (VolumePosition cancelledSeed : seeds) {
            VentState state = stateForSeed(cancelledSeed);
            if (state != null) {
                state.reset(true);
            }
        }
    }

    private void resetAllForRescan() {
        breathableProviders.clear();
        coordinator.clear();
        index.clear();
        for (VentState state : vents.values()) {
            state.reset(true);
        }
    }

    private VentState stateForSeed(VolumePosition target) {
        for (VentState state : vents.values()) {
            if (seed(state.position).equals(target)) {
                return state;
            }
        }
        return null;
    }

    private OxygenVentBlockEntity loadedVent(BlockPos position) {
        if (!level.hasChunkAt(position)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        return blockEntity instanceof OxygenVentBlockEntity vent && !vent.isRemoved()
                ? vent
                : null;
    }

    private static Set<VolumePosition> positionAndNeighbors(BlockPos position) {
        VolumePosition center = fromBlockPos(position);
        Set<VolumePosition> affected = new LinkedHashSet<>();
        affected.add(center);
        affected.addAll(center.neighbors());
        return Set.copyOf(affected);
    }

    private static VolumePosition seed(BlockPos position) {
        return new VolumePosition(position.getX(), position.getY() + 1, position.getZ());
    }

    private static VolumePosition fromBlockPos(BlockPos position) {
        return new VolumePosition(position.getX(), position.getY(), position.getZ());
    }

    private static boolean inChunk(VolumePosition position, int chunkX, int chunkZ) {
        return position.x() >> 4 == chunkX && position.z() >> 4 == chunkZ;
    }

    private static final class VentState {
        private final BlockPos position;
        private long lastSeenGameTime;
        private boolean needsScan = true;
        private VolumeScanOutcome outcome = VolumeScanOutcome.SCANNING;
        private VolumeId volumeId;
        private VolumeBounds lastScanBounds;

        private VentState(BlockPos position, long lastSeenGameTime) {
            this.position = position;
            this.lastSeenGameTime = lastSeenGameTime;
        }

        private void attach(VolumeId id, VolumeBounds bounds) {
            needsScan = false;
            outcome = VolumeScanOutcome.SEALED;
            volumeId = id;
            lastScanBounds = bounds;
        }

        private void reset(boolean rescan) {
            needsScan = rescan;
            outcome = VolumeScanOutcome.SCANNING;
            volumeId = null;
            lastScanBounds = null;
        }
    }
}
