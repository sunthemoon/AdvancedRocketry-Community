package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W98 (W96-R5): ordinary source-retirement proofs share the barrier inside a sub-quota and a silent sink. */
class EscrowOrdinaryProofTest {
    private static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether", END = "minecraft:the_end";

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void ordinarySubQuotaLeavesGovernedHeadroomPerLevelAndOverall() {
        var budget = new EscrowBarrierBudget();
        for (int tick = 0; tick < 4; tick++) { assertTrue(budget.reserveOrdinary(OVERWORLD, tick)); budget.captured(0); }
        assertFalse(budget.availableOrdinary(OVERWORLD, 4), "A fifth ordinary capture in one Level batch");
        assertFalse(budget.reserveOrdinary(OVERWORLD, 4));
        assertTrue(budget.available(OVERWORLD, true, 4), "Governed entity captures keep the Level's headroom");
        for (int tick = 4; tick < 8; tick++) { assertTrue(budget.reserveOrdinary(NETHER, tick)); budget.captured(0); }
        assertEquals(8, budget.ordinaryPending());
        assertFalse(budget.reserveOrdinary(END, 8), "A ninth pending ordinary capture");
        assertTrue(budget.reserve(END, true, 8), "Governed proofs keep the other 24 pending slots");
        budget.captured(0);
        assertEquals(9, budget.pending());
        budget.releaseOrdinary(0);
        assertEquals(7, budget.ordinaryPending()); assertEquals(8, budget.pending());
        assertTrue(budget.reserveOrdinary(END, 9)); budget.captured(0);
        budget.releaseOrdinary(0);
        assertFalse(budget.reserveOrdinary(OVERWORLD, 19), "The Level's batch still holds four ordinary captures");
        assertTrue(budget.reserveOrdinary(OVERWORLD, 20), "A new batch window resets the Level's ordinary count");
    }

    @Test void ordinaryCapturesCountInTheSharedTickAndPerLevelQuotas() {
        var budget = new EscrowBarrierBudget();
        assertTrue(budget.reserveOrdinary(OVERWORLD, 0)); budget.captured(0);
        assertFalse(budget.reserve(OVERWORLD, true, 0), "The one capture per tick is shared");
        assertFalse(budget.availableOrdinary(NETHER, 0));
        for (int tick = 1; tick < 4; tick++) { assertTrue(budget.reserveOrdinary(OVERWORLD, tick)); budget.captured(0); }
        for (int tick = 4; tick < 16; tick++) { assertTrue(budget.reserve(OVERWORLD, true, tick)); budget.captured(0); }
        assertFalse(budget.reserve(OVERWORLD, true, 16), "Ordinary captures count in the Level's 16 entity captures");
        assertTrue(budget.reserve(OVERWORLD, false, 16), "Terrain captures are unaffected");
    }

    @Test void anOrdinaryAttemptReportsOnlyToItsSinkAndCountsNoGovernedFailure() {
        var governed = new ArrayList<String>(); var ordinary = new ArrayList<String>();
        var results = new ArrayList<EscrowBarrier.Result>();
        try (var barrier = new EscrowBarrier(governed::add)) {
            for (int tick = 0; tick < 4; tick++) {
                // A refused capture releases its ordinary reservation and reports the reason only to the attempt.
                assertTrue(barrier.beginOrdinary(null, OVERWORLD, new ChunkPos(0, 0), root -> true, tick, ordinary::add, results::add));
            }
            assertFalse(barrier.availableOrdinary(OVERWORLD, 4));
            assertFalse(barrier.beginOrdinary(null, OVERWORLD, new ChunkPos(0, 0), root -> true, 4, ordinary::add, results::add),
                    "The per-Level sub-quota refuses before any capture");
            assertTrue(barrier.availableOrdinary(NETHER, 5), "Released attempts free the ordinary pending quota");
            assertEquals(4, results.size());
            assertTrue(results.stream().allMatch(result -> !result.confirmed() && result.reason() == EscrowReason.BARRIER_VETO));
            assertEquals(java.util.Collections.nCopies(4, "BARRIER_STORE_BEFORE"), ordinary);
            assertTrue(governed.isEmpty(), "Governed probes observed an ordinary attempt");
            assertEquals(0, barrier.refusals() + barrier.ioFailures() + barrier.supersedingSaves(),
                    "Ordinary failures changed the governed counters");
            barrier.tick(5);
            assertTrue(governed.isEmpty());
        }
    }
}
