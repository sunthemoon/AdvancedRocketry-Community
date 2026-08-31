package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class VolumeScanTaskTest {
    @Test
    void sealedRoomCompletesIncrementallyWithinEveryStepBudget() {
        VolumeScanTask task = new VolumeScanTask(new VolumePosition(1, 1, 1));
        VolumeWorldView room = position -> inside(position, 0, 2)
                ? CellObservation.TRAVERSABLE
                : CellObservation.SEALED;

        int calls = 0;
        while (!task.outcome().terminal()) {
            int inspected = task.step(room, 5);
            assertTrue(inspected >= 1 && inspected <= 5);
            calls++;
        }

        VolumeScanResult result = task.snapshot();
        assertEquals(VolumeScanOutcome.SEALED, result.outcome());
        assertEquals(27, result.cells().size());
        assertEquals(new VolumeBounds(0, 0, 0, 2, 2, 2), result.bounds().orElseThrow());
        assertTrue(calls > 1, "The test did not exercise incremental execution");
        assertThrows(UnsupportedOperationException.class, () -> result.cells().clear());
    }

    @Test
    void explicitOpenBoundaryStopsTheScan() {
        VolumeScanTask task = new VolumeScanTask(new VolumePosition(0, 0, 0));
        VolumeWorldView opening = position -> position.x() >= 2
                ? CellObservation.OPEN
                : CellObservation.TRAVERSABLE;

        runToPauseOrCompletion(task, opening, 16);

        assertEquals(VolumeScanOutcome.OPEN, task.outcome());
        long inspections = task.snapshot().totalInspections();
        assertEquals(0, task.step(opening, 16));
        assertEquals(inspections, task.snapshot().totalInspections());
    }

    @Test
    void oversizedSpaceRetainsOnlyTheConfiguredMaximum() {
        VolumeScanTask task = new VolumeScanTask(new VolumePosition(0, 0, 0), 10);

        runToPauseOrCompletion(task, ignored -> CellObservation.TRAVERSABLE, 4);

        VolumeScanResult result = task.snapshot();
        assertEquals(VolumeScanOutcome.TOO_LARGE, result.outcome());
        assertEquals(10, result.cells().size());
        long inspections = result.totalInspections();
        assertEquals(0, task.step(ignored -> CellObservation.TRAVERSABLE, 4));
        assertEquals(inspections, task.snapshot().totalInspections());
    }

    @Test
    void unloadedCellSuspendsWithoutGrowingAndCanResume() {
        AtomicBoolean loaded = new AtomicBoolean(false);
        VolumeScanTask task = new VolumeScanTask(new VolumePosition(0, 0, 0));
        VolumePosition unavailable = new VolumePosition(1, 0, 0);
        VolumeWorldView world = position -> {
            if (position.equals(unavailable) && !loaded.get()) {
                return CellObservation.UNLOADED;
            }
            return inside(position, -1, 1)
                    ? CellObservation.TRAVERSABLE
                    : CellObservation.SEALED;
        };

        runToPauseOrCompletion(task, world, 8);
        VolumeScanResult pending = task.snapshot();
        assertEquals(VolumeScanOutcome.PENDING, pending.outcome());
        assertEquals(unavailable, pending.pendingPosition().orElseThrow());
        int discoveredBeforeResume = pending.cells().size();
        assertEquals(0, task.step(world, 8));
        assertEquals(discoveredBeforeResume, task.snapshot().cells().size());

        loaded.set(true);
        assertTrue(task.resumePending());
        runToPauseOrCompletion(task, world, 8);

        assertEquals(VolumeScanOutcome.SEALED, task.outcome());
        assertEquals(27, task.snapshot().cells().size());
        assertFalse(task.resumePending());
    }

    @Test
    void cancellationAndBudgetsAreStrict() {
        VolumeScanTask task = new VolumeScanTask(new VolumePosition(0, 0, 0));

        assertThrows(IllegalArgumentException.class, () -> task.step(
                ignored -> CellObservation.SEALED,
                AtmosphereLimits.MAX_TASK_INSPECTIONS_PER_TICK + 1
        ));
        assertThrows(IllegalArgumentException.class, () -> new VolumeScanTask(
                new VolumePosition(0, 0, 0),
                AtmosphereLimits.MAX_VOLUME_CELLS + 1
        ));
        assertTrue(task.cancel());
        assertEquals(VolumeScanOutcome.CANCELLED, task.outcome());
        assertFalse(task.cancel());
        assertEquals(0, task.step(ignored -> CellObservation.TRAVERSABLE, 1));
    }

    private static void runToPauseOrCompletion(
            VolumeScanTask task,
            VolumeWorldView world,
            int budget
    ) {
        int guard = 10_000;
        while (task.outcome() == VolumeScanOutcome.SCANNING && guard-- > 0) {
            task.step(world, budget);
        }
        assertTrue(guard > 0, "Scan did not terminate within the test guard");
    }

    private static boolean inside(VolumePosition position, int minimum, int maximum) {
        return position.x() >= minimum && position.x() <= maximum
                && position.y() >= minimum && position.y() <= maximum
                && position.z() >= minimum && position.z() <= maximum;
    }
}
