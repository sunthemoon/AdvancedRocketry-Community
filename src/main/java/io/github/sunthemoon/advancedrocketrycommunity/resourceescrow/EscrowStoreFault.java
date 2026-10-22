package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * W107C (Root decision R43 item 2): the one gated own-store IO fault of loader cut E1, at the barrier's own-store future
 * because no other seam exists. It is inert unless the release-test hooks, the case {@link #CASE} and the start scenario
 * are all set on a gated release server. Then the first own store of the fixture's source slot (overworld terrain chunk
 * 4,4) still goes to vanilla's IOWorker unchanged, and only the future that the barrier retains completes exceptionally,
 * after that write has finished. It changes no decision, order, saved format, proof or lease: the barrier's own reason
 * table reports BARRIER_IO. One fault per store, so a later proof of the same slot is an ordinary store.
 */
final class EscrowStoreFault {
    static final String CASE = "LF_M_E1_OWN_STORE";
    /** The loader matrix's source chunk (loader A at 66,100,64); no other slot is ever selected. */
    static final ChunkPos SLOT = new ChunkPos(4, 4);
    private final AtomicBoolean spent = new AtomicBoolean();
    private final AtomicInteger namedFailures = new AtomicInteger();

    /** The pure gate, so that a unit test pins every property and the exact slot. */
    static boolean selected(boolean hooks, String testCase, String scenario, String level, ChunkPos chunk) {
        return hooks && (CASE.equals(testCase) || EscrowCargoStorageProbe.E1.equals(testCase))
                && "start".equals(scenario) && "minecraft:overworld".equals(level) && SLOT.equals(chunk);
    }

    /** The terrain own store's future as the barrier must retain it; the same instance whenever the gate is closed. */
    CompletableFuture<Void> observed(ServerLevel level, String levelId, ChunkPos chunk, CompletableFuture<Void> stored) {
        if (namedSelected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), System.getProperty("advancedrocketrycommunity.escrowCase"),
                System.getProperty("advancedrocketrycommunity.escrowScenario"), levelId, chunk)
                && EscrowTestEndpoints.enabled(level.getServer())) { return observedNamed(true, chunk, stored); }
        return observed(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), System.getProperty("advancedrocketrycommunity.escrowCase"),
                System.getProperty("advancedrocketrycommunity.escrowScenario"), levelId, chunk)
                && EscrowTestEndpoints.enabled(level.getServer()), chunk, stored);
    }

    /** W125 uses the same seam for exactly three E5 source proofs; recovery uses repaired storage. */
    static boolean namedSelected(boolean hooks, String testCase, String scenario, String level, ChunkPos chunk) {
        return hooks && EscrowLoaderNamedProbe.E5.equals(testCase) && "start".equals(scenario)
                && "minecraft:overworld".equals(level) && SLOT.equals(chunk);
    }

    CompletableFuture<Void> observedNamed(boolean gate, ChunkPos chunk, CompletableFuture<Void> stored) {
        if (!gate) { return stored; }
        int failure = namedFailures.getAndUpdate(value -> Math.min(3, value + 1));
        if (failure == 3) { return stored; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_STORE_FAULT cut={} chunk={},{} failure={}",
                EscrowLoaderNamedProbe.E5, chunk.x, chunk.z, failure + 1);
        return stored.thenCompose(ignored -> CompletableFuture.failedFuture(new java.io.IOException("Release-test named own-store IO failure")));
    }

    /** At most one fault per store; the real store has always completed before the retained future fails. */
    CompletableFuture<Void> observed(boolean gate, ChunkPos chunk, CompletableFuture<Void> stored) {
        if (!gate || !spent.compareAndSet(false, true)) { return stored; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_STORE_FAULT cut={} chunk={},{}",
                System.getProperty("advancedrocketrycommunity.escrowCase", CASE), chunk.x, chunk.z);
        return stored.thenCompose(ignored -> CompletableFuture.failedFuture(new java.io.IOException("Release-test own-store IO failure")));
    }
}
