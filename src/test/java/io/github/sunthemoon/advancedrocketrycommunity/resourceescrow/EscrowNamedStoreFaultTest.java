package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowNamedStoreFaultTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void selectsOnlyNamedE5StartSourceAndNotTheHealthyControlOrRecovery() {
        var slot = EscrowStoreFault.SLOT;
        assertTrue(EscrowStoreFault.namedSelected(true, EscrowLoaderNamedProbe.E5, "start", "minecraft:overworld", slot));
        assertFalse(EscrowStoreFault.namedSelected(false, EscrowLoaderNamedProbe.E5, "start", "minecraft:overworld", slot));
        for (var cut : new String[] {null, "", EscrowStoreFault.CASE, "LF_M_NAMED_CROSS_K2"}) {
            assertFalse(EscrowStoreFault.namedSelected(true, cut, "start", "minecraft:overworld", slot));
        }
        for (var scenario : new String[] {null, "", "recover"}) {
            assertFalse(EscrowStoreFault.namedSelected(true, EscrowLoaderNamedProbe.E5, scenario, "minecraft:overworld", slot));
        }
        assertFalse(EscrowStoreFault.namedSelected(true, EscrowLoaderNamedProbe.E5, "start", "minecraft:the_nether", slot));
        assertFalse(EscrowStoreFault.namedSelected(true, EscrowLoaderNamedProbe.E5, "start", "minecraft:overworld", new ChunkPos(5, 5)));
    }

    @Test void threeRetainedFailuresWaitForRealStoresAndTheFourthIsUnchanged() {
        var fault = new EscrowStoreFault(); var slot = EscrowStoreFault.SLOT;
        for (int i = 0; i < 3; i++) {
            var stored = new CompletableFuture<Void>();
            assertSame(stored, fault.observedNamed(false, slot, stored));
            var retained = fault.observedNamed(true, slot, stored);
            assertNotSame(stored, retained); assertFalse(retained.isDone());
            stored.complete(null); assertFalse(stored.isCompletedExceptionally());
            assertInstanceOf(java.io.IOException.class, assertThrows(CompletionException.class, retained::join).getCause());
        }
        var fourth = new CompletableFuture<Void>();
        assertSame(fourth, fault.observedNamed(true, slot, fourth));
        assertFalse(fourth.isDone());
    }
}
