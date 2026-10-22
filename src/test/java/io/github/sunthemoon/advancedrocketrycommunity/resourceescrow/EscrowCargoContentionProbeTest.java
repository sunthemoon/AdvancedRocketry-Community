package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoContentionProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void fixtureSelectionAndClosedCacheGateAreInert() {
        for (String name : new String[] {EscrowCargoContentionProbe.E3, EscrowCargoContentionProbe.E5}) {
            assertTrue(EscrowCargoContentionProbe.selected(true, name));
            assertFalse(EscrowCargoContentionProbe.selected(false, name));
        }
        for (String name : new String[] {null, "", "FC_UNLOAD_E3_CACHE_CLOSE", "IC_LOAD_E5_STALLED", "FC_LOAD_E3"}) {
            assertFalse(EscrowCargoContentionProbe.selected(true, name));
        }
        assertEquals(0, EscrowCargoContentionProbe.closeOldest(false, null));
    }
    @Test void threeFailuresRequireTheExactCargoCaseAndNeverApplyOnRestartOrAnotherSlot() {
        String name = EscrowCargoContentionProbe.E5; var at = new ChunkPos(4, 4);
        assertTrue(EscrowStoreFault.namedSelected(true, name, "start", "minecraft:overworld", at));
        assertFalse(EscrowStoreFault.namedSelected(false, name, "start", "minecraft:overworld", at));
        assertFalse(EscrowStoreFault.namedSelected(true, name, "recover", "minecraft:overworld", at));
        assertFalse(EscrowStoreFault.namedSelected(true, name, "start", "minecraft:the_nether", at));
        assertFalse(EscrowStoreFault.namedSelected(true, name, "start", "minecraft:overworld", new ChunkPos(5, 5)));
        var fault = new EscrowStoreFault();
        for (int i = 0; i < 3; i++) {
            var original = new CompletableFuture<Void>(); var retained = fault.observedNamed(true, at, original);
            assertFalse(retained.isDone()); original.complete(null);
            assertTrue(retained.isCompletedExceptionally()); assertFalse(original.isCompletedExceptionally());
        }
        var fourth = new CompletableFuture<Void>(); assertSame(fourth, fault.observedNamed(true, at, fourth));
    }
}
