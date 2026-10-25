package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class RocketReleaseWorldTest {
    @Test void nativeProofKeepsItsWatchGenerationAndLifetimeRatherThanBecomingABooleanSnapshot() {
        var generation = new AtomicLong(7); var lifetime = new AtomicBoolean(true); var releases = new AtomicInteger();
        var nativeProof = new EscrowBarrier.Proof(null, new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null),
                null, null, new ChunkPos(0, 0), generation, 7, 0, () -> { }, releases::incrementAndGet), new AtomicBoolean(true), lifetime);
        var proof = RocketReleaseWorld.readback(new EscrowBarrier.Result(nativeProof, EscrowReason.NONE), 1);
        assertNotNull(proof); assertTrue(proof.valid()); assertEquals(1, proof.copies());
        generation.incrementAndGet(); assertFalse(proof.valid());
        proof.close(); proof.close(); assertEquals(1, releases.get());
        lifetime.set(false); assertFalse(proof.valid());
    }

    @Test void failedOrInvalidReadbackNeverPublishesASeatProofAndReleasesItsWatch() {
        assertNull(RocketReleaseWorld.readback(new EscrowBarrier.Result(null, EscrowReason.BARRIER_IO), 0));
        var releases = new AtomicInteger();
        var nativeProof = new EscrowBarrier.Proof(null, new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null),
                null, null, new ChunkPos(0, 0), new AtomicLong(), 0, 0, () -> { }, releases::incrementAndGet),
                new AtomicBoolean(true), new AtomicBoolean(true));
        assertNull(RocketReleaseWorld.readback(new EscrowBarrier.Result(nativeProof, EscrowReason.NONE), -1));
        assertEquals(1, releases.get());
    }
}
