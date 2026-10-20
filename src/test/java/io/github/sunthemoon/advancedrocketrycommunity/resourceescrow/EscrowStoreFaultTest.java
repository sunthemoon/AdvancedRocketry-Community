package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W107C (R43 item 2): cut E1's own-store fault is inert with its gate closed and fires once, after the real store. */
class EscrowStoreFaultTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void gateNeedsHooksCaseStartScenarioOverworldAndTheFixtureSlot() {
        var slot = new ChunkPos(4, 4);
        assertTrue(EscrowStoreFault.selected(true, "LF_M_E1_OWN_STORE", "start", "minecraft:overworld", slot));
        assertFalse(EscrowStoreFault.selected(false, "LF_M_E1_OWN_STORE", "start", "minecraft:overworld", slot));
        for (String other : new String[] {null, "", "LF_M_E2_EXTERNAL", "LF_M_K6", "E1_OWN_STORE"}) {
            assertFalse(EscrowStoreFault.selected(true, other, "start", "minecraft:overworld", slot));
        }
        for (String scenario : new String[] {null, "recover", ""}) {
            assertFalse(EscrowStoreFault.selected(true, "LF_M_E1_OWN_STORE", scenario, "minecraft:overworld", slot));
        }
        assertFalse(EscrowStoreFault.selected(true, "LF_M_E1_OWN_STORE", "start", "minecraft:the_nether", slot));
        for (var chunk : new ChunkPos[] {new ChunkPos(4, 5), new ChunkPos(5, 4), new ChunkPos(0, 0), new ChunkPos(128, 128)}) {
            assertFalse(EscrowStoreFault.selected(true, "LF_M_E1_OWN_STORE", "start", "minecraft:overworld", chunk));
        }
    }

    @Test void closedGateReturnsTheSameFutureAndNeverCompletesIt() {
        // The production overload reads the properties; with none set it never touches its Level.
        assertNull(System.getProperty("advancedrocketrycommunity.escrowCase"));
        var fault = new EscrowStoreFault(); var stored = new CompletableFuture<Void>();
        assertSame(stored, fault.observed(null, "minecraft:overworld", new ChunkPos(4, 4), stored));
        assertSame(stored, fault.observed(false, new ChunkPos(4, 4), stored));
        assertFalse(stored.isDone());
        stored.complete(null);
        assertFalse(stored.isCompletedExceptionally());
        var later = new CompletableFuture<Void>();
        assertSame(later, fault.observed(false, new ChunkPos(4, 4), later), "A closed gate does not spend the one fault");
        var opened = fault.observed(true, new ChunkPos(4, 4), later);
        assertNotSame(later, opened);
    }

    @Test void openGateFailsOnlyTheRetainedFutureAfterTheRealStoreAndOnlyOnce() {
        var fault = new EscrowStoreFault(); var stored = new CompletableFuture<Void>();
        var retained = fault.observed(true, new ChunkPos(4, 4), stored);
        assertNotSame(stored, retained);
        assertFalse(retained.isDone(), "The retained future waits for vanilla's own write");
        stored.complete(null);
        assertFalse(stored.isCompletedExceptionally(), "The vanilla store itself succeeds unchanged");
        assertTrue(retained.isCompletedExceptionally());
        var failure = assertThrows(CompletionException.class, retained::join);
        assertInstanceOf(java.io.IOException.class, failure.getCause());
        var next = new CompletableFuture<Void>();
        assertSame(next, fault.observed(true, new ChunkPos(4, 4), next), "A second own store is an ordinary store");
    }

    @Test void aFailedRealStoreStillFailsTheRetainedFuture() {
        var fault = new EscrowStoreFault(); var stored = new CompletableFuture<Void>();
        var retained = fault.observed(true, new ChunkPos(4, 4), stored);
        stored.completeExceptionally(new java.io.IOException("vanilla"));
        assertTrue(retained.isCompletedExceptionally());
    }
}
