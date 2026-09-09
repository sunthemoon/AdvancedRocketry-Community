package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MultiblockDirtyQueueTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void idleTicksPerformNoValidation() {
        MultiblockDirtyQueue queue = new MultiblockDirtyQueue(128, 8, 8_192);
        AtomicInteger validations = new AtomicInteger();

        for (int tick = 0; tick < 2_000; tick++) {
            DirtyTickResult result = queue.tick(ignored -> validations.incrementAndGet());
            assertEquals(0, result.processedControllers());
        }

        assertEquals(0, validations.get());
    }

    @Test
    void deduplicatesAndDrainsWithinControllerAndCellBudgets() {
        MultiblockDirtyQueue queue = new MultiblockDirtyQueue(4, 2, 4_096);
        MultiblockControllerKey first = key(1);
        MultiblockControllerKey second = key(2);
        MultiblockControllerKey third = key(3);
        assertEquals(DirtyEnqueueResult.ENQUEUED, queue.enqueue(first, 1_000));
        assertEquals(DirtyEnqueueResult.ALREADY_QUEUED, queue.enqueue(first, 2_000));
        assertEquals(DirtyEnqueueResult.ENQUEUED, queue.enqueue(second, 2_000));
        assertEquals(DirtyEnqueueResult.ENQUEUED, queue.enqueue(third, 1_000));
        List<MultiblockControllerKey> processed = new ArrayList<>();

        DirtyTickResult firstTick = queue.tick(processed::add);
        assertEquals(2, firstTick.processedControllers());
        assertEquals(4_000, firstTick.processedCells());
        assertEquals(List.of(first, second), processed);
        assertEquals(1, queue.pendingCount());

        DirtyTickResult secondTick = queue.tick(processed::add);
        assertEquals(1, secondTick.processedControllers());
        assertEquals(1_000, secondTick.processedCells());
        assertEquals(0, queue.pendingCount());
    }

    @Test
    void queueAndConfigurationLimitsFailClosed() {
        MultiblockDirtyQueue queue = new MultiblockDirtyQueue(1, 1, 4_096);
        assertEquals(DirtyEnqueueResult.ENQUEUED, queue.enqueue(key(1), 4_096));
        assertEquals(DirtyEnqueueResult.QUEUE_FULL, queue.enqueue(key(2), 1));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(key(3), 4_097));
        assertThrows(IllegalArgumentException.class, () -> new MultiblockDirtyQueue(1, 1, 4_095));
    }

    @Test
    void redirtyAndValidationFailureRemainQueuedForALaterTick() {
        MultiblockDirtyQueue queue = new MultiblockDirtyQueue(4, 2, 4_096);
        MultiblockControllerKey first = key(1);
        MultiblockControllerKey second = key(2);
        queue.enqueue(first, 1_000);
        queue.tick(controller -> queue.enqueue(controller, 1_500));
        assertEquals(1, queue.pendingCount());
        assertEquals(1_500, queue.tick(ignored -> { }).processedCells());

        queue.enqueue(first, 1_000);
        queue.enqueue(second, 1_000);
        assertThrows(IllegalStateException.class, () -> queue.tick(ignored -> {
            throw new IllegalStateException("retry");
        }));
        assertEquals(2, queue.pendingCount());
        List<MultiblockControllerKey> retried = new ArrayList<>();
        queue.tick(retried::add);
        assertTrue(retried.containsAll(List.of(first, second)));
    }

    private static MultiblockControllerKey key(int x) {
        return new MultiblockControllerKey(Level.OVERWORLD, new BlockPos(x, 64, 0));
    }
}
