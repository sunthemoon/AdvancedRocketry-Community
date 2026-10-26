package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EscrowSaveObservationTest {
    private static void attach(EscrowSaveMonitor monitor, Consumer<EscrowSaveMonitor.Observation> observer) throws Exception {
        // Unit tests do not invent a server to bypass the public release-test gate.
        var field = EscrowSaveMonitor.class.getDeclaredField("observer"); field.setAccessible(true); field.set(monitor, observer);
    }
    @Test void defaultCountersKeepTheirExistingSlotAndLifetimeSemantics() {
        var monitor = new EscrowSaveMonitor(); var pos = new ChunkPos(16, 9);
        var counter = monitor.watch("moon", true, pos);
        assertEquals(1, counter.get());
        monitor.observed("moon", false, pos); assertEquals(1, counter.get());
        assertSame(counter, monitor.watch("moon", true, pos)); assertEquals(2, counter.get());
        monitor.observed("moon", true, pos); assertEquals(3, counter.get());
        monitor.release("moon", true, pos); monitor.release("moon", true, pos);
        monitor.observed("moon", true, pos); assertEquals(3, counter.get());
        assertNotSame(counter, monitor.watch("moon", true, pos));
    }
    @Test void observerReportsExactCounterAndGenerationWithoutIncrementingIt() throws Exception {
        var monitor = new EscrowSaveMonitor(); var rows = new ArrayList<EscrowSaveMonitor.Observation>(); attach(monitor, rows::add);
        var pos = new ChunkPos(16, 9); var counter = monitor.watch("moon", true, pos);
        monitor.observed("moon", true, pos); monitor.watch("moon", true, pos);
        assertEquals(java.util.List.of("CAPTURE", "ORDINARY_STORE", "CAPTURE"), rows.stream().map(EscrowSaveMonitor.Observation::cause).toList());
        assertEquals(java.util.List.of(1L, 2L, 3L), rows.stream().map(EscrowSaveMonitor.Observation::generation).toList());
        assertTrue(rows.stream().allMatch(row -> row.counter() == counter)); assertEquals(3, counter.get());
    }
    @Test void observerFailureCannotInterruptSaveAndClearDetachesObserver() throws Exception {
        var monitor = new EscrowSaveMonitor(); var pos = new ChunkPos(16, 9);
        attach(monitor, value -> { throw new IllegalStateException("test observer failure"); });
        var counter = assertDoesNotThrow(() -> monitor.watch("moon", true, pos));
        monitor.observed("moon", true, pos); assertEquals(2, counter.get());
        var rows = new ArrayList<EscrowSaveMonitor.Observation>(); attach(monitor, rows::add);
        monitor.clear(); monitor.watch("moon", true, pos); assertTrue(rows.isEmpty());
    }
    @Test void everyOrdinaryStoreCanInvalidateANewCaptureAndAQuietIntervalDoesNot() {
        var monitor = new EscrowSaveMonitor(); var pos = new ChunkPos(16, 9);
        for (int attempt = 0; attempt < 100; attempt++) {
            var counter = monitor.watch("moon", true, pos); long captured = counter.get();
            monitor.observed("moon", true, pos); assertNotEquals(captured, counter.get());
            monitor.release("moon", true, pos);
        }
        var counter = monitor.watch("moon", true, pos); long captured = counter.get();
        assertEquals(captured, counter.get());
    }
}
