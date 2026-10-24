package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrdinaryTerrainCaptureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void terrainFacadeUsesTheOrdinaryProofWithoutAFabricatedEscrowEndpoint() throws Exception {
        var method = EscrowBarrier.class.getMethod("beginOrdinaryTerrain", EscrowChunkStore.class, String.class,
                ChunkPos.class, Predicate.class, long.class, Consumer.class, Consumer.class);
        assertEquals(boolean.class, method.getReturnType());
        assertNotNull(EscrowBarrier.class.getMethod("availableOrdinaryTerrain", EscrowChunkStore.class,
                String.class, ChunkPos.class, long.class));
    }

    @Test void oneCapturePerServerTickIsSharedAcrossBothOrdinaryTypesAndGovernedWork() throws Exception {
        var budget = new EscrowBarrierBudget();
        assertTrue(reserve(budget, "one", false, 0)); budget.captured(64);
        assertFalse(reserve(budget, "two", true, 0));
        assertFalse(budget.reserve("three", true, 0));
        assertTrue(reserve(budget, "one", true, 1)); budget.captured(64);
        assertEquals(2, budget.ordinaryPending()); assertEquals(2, budget.pending()); assertEquals(128, budget.bytes());
    }

    @Test void fourPerLevelAndEightPendingAreSharedRatherThanMultipliedBySlotType() throws Exception {
        var budget = new EscrowBarrierBudget();
        for (int i = 0; i < 4; i++) { assertTrue(reserve(budget, "one", i % 2 == 0, i)); budget.captured(64); }
        assertFalse(reserve(budget, "one", true, 4)); assertFalse(reserve(budget, "one", false, 4));
        for (int i = 4; i < 8; i++) { assertTrue(reserve(budget, "other" + i, i % 2 == 0, i)); budget.captured(64); }
        assertFalse(reserve(budget, "free", false, 8));
        assertEquals(8, budget.ordinaryPending());
        budget.releaseOrdinary(64);
        assertTrue(reserve(budget, "one", false, 20)); budget.captured(64);
        assertEquals(8, budget.pending());
    }

    @Test void eachTypeStillPaysItsExistingSixteenPerLevelBatchLimit() throws Exception {
        for (boolean entity : new boolean[] {false, true}) {
            var budget = new EscrowBarrierBudget();
            for (int i = 0; i < 16; i++) {
                assertTrue(budget.reserve("one", entity, i)); budget.captured(64); budget.release(64);
            }
            assertFalse(reserve(budget, "one", entity, 16));
            assertTrue(reserve(budget, "one", !entity, 16));
        }
    }

    @Test void terrainAndEntityWatchesUseTheirOwnSlotGenerationWithinTheSameMonitor() throws Exception {
        var monitor = new EscrowSaveMonitor(); var pos = new ChunkPos(2, -3);
        var entity = monitor.captureWatch("one", true, pos, new AtomicBoolean(true));
        var terrain = watch(monitor, false, pos);
        assertNotNull(terrain);
        assertNull(watch(monitor, true, pos), "Ordinary entity capture must not supersede a governed entity pin");
        long entityGeneration = entity.generation().get();
        long terrainGeneration = terrain.generation().get();
        monitor.observed("one", false, pos);
        assertEquals(entityGeneration, entity.generation().get());
        assertEquals(terrainGeneration + 1, terrain.generation().get());
        terrain.release(); entity.release();
    }

    // Reflection keeps the pre-implementation regression compilable while testing the absent narrow facade.
    private static boolean reserve(EscrowBarrierBudget budget, String level, boolean entity, long tick) throws Exception {
        return (boolean) EscrowBarrierBudget.class.getDeclaredMethod("reserveOrdinary", String.class, boolean.class, long.class)
                .invoke(budget, level, entity, tick);
    }
    private static EscrowSaveMonitor.Handle watch(EscrowSaveMonitor monitor, boolean entity, ChunkPos pos) throws Exception {
        return (EscrowSaveMonitor.Handle) EscrowSaveMonitor.class.getDeclaredMethod("ordinaryWatch", String.class,
                boolean.class, ChunkPos.class, BooleanSupplier.class).invoke(monitor, "one", entity, pos, (BooleanSupplier) () -> true);
    }
}
