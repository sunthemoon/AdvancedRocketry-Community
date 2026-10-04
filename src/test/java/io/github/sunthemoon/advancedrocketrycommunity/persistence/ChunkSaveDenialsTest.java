package io.github.sunthemoon.advancedrocketrycommunity.persistence;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkSaveDenialsTest {
    @Test void ordinaryChunksAndOtherLevelInstancesRemainWritable() {
        var state = new ChunkSaveDenials();
        state.deny(11, "unsafe tank");
        assertEquals("unsafe tank", state.reason(11).orElseThrow());
        assertTrue(state.reason(12).isEmpty());
        assertTrue(new ChunkSaveDenials().reason(11).isEmpty());
    }

    @Test void repeatedRefusalPreservesFirstReasonWithoutGrowing() {
        var state = new ChunkSaveDenials();
        state.deny(11, "original");
        state.deny(11, "different serialized contents");
        assertEquals("original", state.reason(11).orElseThrow());
        assertEquals(1, state.size());
    }

    @Test void exactCapacityDoesNotBlockUnrelatedChunksButOverflowNeverEvicts() {
        var state = new ChunkSaveDenials();
        for (long chunk = 0; chunk < ChunkSaveDenials.MAX_CHUNKS; chunk++) { state.deny(chunk, "unsafe"); }
        assertEquals(ChunkSaveDenials.MAX_CHUNKS, state.size());
        assertTrue(state.reason(256).isEmpty());
        state.deny(256, "overflow");
        assertEquals(ChunkSaveDenials.MAX_CHUNKS, state.size());
        assertEquals("unsafe", state.reason(0).orElseThrow());
        assertTrue(state.reason(256).isPresent());
        assertTrue(state.reason(Long.MIN_VALUE).isPresent());
        assertThrows(IllegalStateException.class, () -> state.restoreFixture(0));
    }

    @Test void boundedReasonsRejectInvalidInputAndAcceptExactLimit() {
        var state = new ChunkSaveDenials();
        assertThrows(IllegalArgumentException.class, () -> state.deny(1, null));
        assertThrows(IllegalArgumentException.class, () -> state.deny(1, " "));
        assertThrows(IllegalArgumentException.class, () -> state.deny(1, "x".repeat(257)));
        state.deny(1, "x".repeat(256));
        assertEquals(256, state.reason(1).orElseThrow().length());
    }

    @Test void closedLifetimeCannotBecomeWritableOrRetainReferences() {
        var state = new ChunkSaveDenials();
        state.deny(1, "unsafe");
        state.close();
        assertEquals(0, state.size());
        assertTrue(state.reason(1).isPresent());
        assertTrue(state.reason(2).isPresent());
        state.deny(3, "later callback");
        assertEquals(0, state.size());
        assertTrue(state.reason(3).isPresent());
        assertThrows(IllegalStateException.class, () -> state.restoreFixture(1));
    }
}
