package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowCargoContentionProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void admissionDiagnosticsAreBoundedAndInertOutsideTheStartCase() {
        assertTrue(EscrowCargoContentionProbe.diagnosticTick(true, EscrowCargoContentionProbe.E5, false, 100));
        assertFalse(EscrowCargoContentionProbe.diagnosticTick(false, EscrowCargoContentionProbe.E5, false, 100));
        assertFalse(EscrowCargoContentionProbe.diagnosticTick(true, EscrowCargoContentionProbe.E5, true, 100));
        assertFalse(EscrowCargoContentionProbe.diagnosticTick(true, EscrowCargoContentionProbe.E3, false, 100));
        assertFalse(EscrowCargoContentionProbe.diagnosticTick(true, null, false, 100));
        assertFalse(EscrowCargoContentionProbe.diagnosticTick(true, EscrowCargoContentionProbe.E5, false, 101));
    }
    @Test void fixtureSelectionAndClosedCacheGateAreInert() {
        for (String name : new String[] {EscrowCargoContentionProbe.E3, EscrowCargoContentionProbe.E5}) {
            assertTrue(EscrowCargoContentionProbe.selected(true, name));
            assertFalse(EscrowCargoContentionProbe.selected(false, name));
        }
        for (String name : new String[] {null, "", "FC_UNLOAD_E3_CACHE_CLOSE", "IC_LOAD_E5_STALLED", "FC_LOAD_E3"}) {
            assertFalse(EscrowCargoContentionProbe.selected(true, name));
        }
        assertEquals(0, EscrowCargoContentionProbe.closeOldest(false, null));
        assertEquals(0, EscrowCargoContentionProbe.injectCacheClose(false, null));
    }
    @Test void injectionReseedsAfterAnInterveningEndpointReadAndFailsOnlyItsOwnStore(@TempDir Path directory) throws Exception {
        try (var worker = new IOWorker(directory, false, "w138-r66-cache") { }) {
            var own = new ChunkPos(4, 4);
            var before = new CompoundTag(); before.putInt("value", 5_000);
            worker.store(own, before).join(); worker.synchronize(true).join();
            EscrowCargoContentionProbe.seedCache(worker);
            assertEquals(before, worker.loadAsync(own).join().orElseThrow());
            // The original early-seed fixture reaches this guard, not the close injection.
            var stale = assertThrows(IllegalArgumentException.class, () -> EscrowCargoContentionProbe.closeOldest(true, worker));
            assertEquals("E3 cache fixture changed before its own store", stale.getMessage());

            assertEquals(256, EscrowCargoContentionProbe.injectCacheClose(true, worker));
            var after = before.copy(); after.putInt("value", 4_000);
            var failure = assertThrows(CompletionException.class, () -> worker.store(own, after).join());
            assertInstanceOf(ClosedChannelException.class, failure.getCause());
            assertEquals(before, worker.loadAsync(own).join().orElseThrow(), "Failed own store must preserve the baseline");
            worker.store(own, after).join(); worker.synchronize(true).join();
            assertEquals(after, worker.loadAsync(own).join().orElseThrow(), "The close injection must occur only once");
        }
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
