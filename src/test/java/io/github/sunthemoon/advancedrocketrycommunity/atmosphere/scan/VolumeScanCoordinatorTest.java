package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class VolumeScanCoordinatorTest {
    @Test
    void connectedSeedsMergeIntoOneStableVolume() {
        VolumeScanCoordinator coordinator = new VolumeScanCoordinator(8, 64);
        VolumePosition first = new VolumePosition(0, 0, 0);
        VolumePosition second = new VolumePosition(8, 0, 0);
        assertEquals(ScanScheduleStatus.STARTED, coordinator.schedule(first).status());
        assertEquals(ScanScheduleStatus.STARTED, coordinator.schedule(second).status());
        VolumeWorldView corridor = position -> position.y() == 0
                && position.z() == 0
                && position.x() >= 0
                && position.x() <= 8
                ? CellObservation.TRAVERSABLE
                : CellObservation.SEALED;

        int merged = runUntilIdle(coordinator, corridor, 4);
        List<CompletedVolumeScan> completed = coordinator.drainCompleted();

        assertTrue(merged >= 1, "The connected tasks never collided");
        assertEquals(1, completed.size());
        assertEquals(java.util.Set.of(first, second), completed.get(0).seeds());
        AtmosphereVolume volume = completed.get(0).sealedVolume().orElseThrow();
        assertEquals(9, volume.cells().size());
        assertEquals(VolumeIdentity.fromCells(volume.cells()), volume.id());
    }

    @Test
    void disconnectedSeedsProduceDistinctVolumes() {
        VolumeScanCoordinator coordinator = new VolumeScanCoordinator(4, 16);
        VolumePosition first = new VolumePosition(0, 0, 0);
        VolumePosition second = new VolumePosition(10, 0, 0);
        coordinator.schedule(first);
        coordinator.schedule(second);
        VolumeWorldView world = position -> position.equals(first) || position.equals(second)
                ? CellObservation.TRAVERSABLE
                : CellObservation.SEALED;

        runUntilIdle(coordinator, world, 16);
        List<CompletedVolumeScan> completed = coordinator.drainCompleted();

        assertEquals(2, completed.size());
        assertFalse(completed.get(0).sealedVolume().orElseThrow().id()
                .equals(completed.get(1).sealedVolume().orElseThrow().id()));
    }

    @Test
    void globalBudgetAndActiveTaskLimitAreNeverExceeded() {
        VolumeScanCoordinator coordinator = new VolumeScanCoordinator(2, 64);
        assertEquals(ScanScheduleStatus.STARTED, coordinator.schedule(new VolumePosition(0, 0, 0)).status());
        assertEquals(ScanScheduleStatus.STARTED, coordinator.schedule(new VolumePosition(100, 0, 0)).status());
        assertEquals(ScanScheduleStatus.BUSY, coordinator.schedule(new VolumePosition(200, 0, 0)).status());

        CoordinatorTickReport report = coordinator.tick(
                ignored -> CellObservation.TRAVERSABLE,
                17
        );

        assertEquals(17, report.inspections());
        assertEquals(2, report.activeTasks());
    }

    @Test
    void pendingTaskDoesNoWorkUntilExplicitResume() {
        AtomicBoolean loaded = new AtomicBoolean(false);
        VolumeScanCoordinator coordinator = new VolumeScanCoordinator(2, 8);
        VolumePosition seed = new VolumePosition(0, 0, 0);
        VolumePosition unloaded = new VolumePosition(1, 0, 0);
        coordinator.schedule(seed);
        VolumeWorldView world = position -> {
            if (position.equals(unloaded) && !loaded.get()) {
                return CellObservation.UNLOADED;
            }
            return position.equals(seed) || position.equals(unloaded)
                    ? CellObservation.TRAVERSABLE
                    : CellObservation.SEALED;
        };

        CoordinatorTickReport pending = coordinator.tick(world, 32);
        assertEquals(1, pending.pendingTasks());
        assertEquals(0, coordinator.tick(world, 32).inspections());

        loaded.set(true);
        assertTrue(coordinator.resumePending(seed));
        runUntilIdle(coordinator, world, 32);
        assertEquals(2, coordinator.drainCompleted().get(0).sealedVolume().orElseThrow().cells().size());
    }

    private static int runUntilIdle(
            VolumeScanCoordinator coordinator,
            VolumeWorldView world,
            int budget
    ) {
        int guard = 10_000;
        int merged = 0;
        while (coordinator.activeTaskCount() > 0 && guard-- > 0) {
            CoordinatorTickReport report = coordinator.tick(world, budget);
            assertTrue(report.inspections() <= budget);
            merged += report.mergedTasks();
        }
        assertTrue(guard > 0, "Coordinator did not become idle within the test guard");
        return merged;
    }
}
