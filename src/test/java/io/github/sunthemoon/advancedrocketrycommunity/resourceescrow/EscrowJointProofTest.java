package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowJointProofTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;
    @Test void jointTargetLimitAcceptsAll128AndRefusesOverflowDuplicatesAndOtherSlots() {
        var targets = new ArrayList<EscrowBarrier.Target>();
        for (int i = 1; i <= 128; i++) {
            var op = EscrowTestFixtures.operation(i);
            targets.add(new EscrowBarrier.Target(op.destination(), frontier(op.destination(), op), root -> true));
        }
        try (var barrier = new EscrowBarrier()) {
            barrier.quiesce(); // Exercise validation without a live store or changing capture quotas.
            assertFalse(barrier.beginJoint(null, targets, 0, result -> fail("Quiesced capture")));
            var overflow = new ArrayList<>(targets); overflow.add(targets.get(0));
            assertThrows(IllegalArgumentException.class, () -> barrier.beginJoint(null, overflow, 0, result -> { }));
            assertThrows(IllegalArgumentException.class, () -> barrier.beginJoint(null, List.of(), 0, result -> { }));
            assertThrows(IllegalArgumentException.class, () -> barrier.beginJoint(null, List.of(targets.get(0), targets.get(0)), 0, result -> { }));
            var other = EscrowTestFixtures.operation(129);
            assertThrows(IllegalArgumentException.class, () -> barrier.beginJoint(null, List.of(targets.get(0),
                    new EscrowBarrier.Target(other.source(), frontier(other.source(), other), root -> true)), 0, result -> { }));
        }
        AtomicInteger releases = new AtomicInteger();
        var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory,
                new ChunkPos(0, 0), new AtomicLong(1), 1, 0, () -> { }, releases::incrementAndGet);
        var base = new EscrowBarrier.Proof(targets.get(0).frontier(), capture, new AtomicBoolean(true), new AtomicBoolean(true));
        var proofs = EscrowBarrier.splitProof(base, targets); assertEquals(128, proofs.size());
        var tokens = new ArrayList<>(proofs.values());
        for (int i = 0; i < 127; i++) { tokens.get(i).release(); assertEquals(0, releases.get()); }
        tokens.get(127).release(); assertEquals(1, releases.get());
    }
    @Test void jointLeaseReleasesItsWatchOnlyAfterEveryEndpointAndSupersedesAllTokens() {
        var a = EscrowTestFixtures.operation(1); var b = EscrowTestFixtures.operation(2);
        AtomicInteger releases = new AtomicInteger(); AtomicLong generation = new AtomicLong(1);
        var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory,
                new ChunkPos(0, 0), generation, 1, 0, () -> { }, releases::incrementAndGet);
        var base = new EscrowBarrier.Proof(frontier(a.source(), a), capture, new AtomicBoolean(true), new AtomicBoolean(true));
        var targets = List.of(new EscrowBarrier.Target(a.source(), base.frontier(), root -> true),
                new EscrowBarrier.Target(b.source(), frontier(b.source(), b), root -> true));
        var proofs = EscrowBarrier.splitProof(base, targets);
        proofs.get(a.source().key()).release(); assertEquals(0, releases.get()); assertTrue(proofs.get(b.source().key()).valid());
        generation.incrementAndGet(); assertFalse(proofs.get(b.source().key()).valid());
        proofs.get(b.source().key()).release(); proofs.get(b.source().key()).release(); assertEquals(1, releases.get());
    }
    @Test void partialPrefixRetirementKeepsEpochAndLaterMarkersButRequiresFreshBankProof() throws Exception {
        var a = EscrowTestFixtures.operation(1, 99, 0); var b = EscrowTestFixtures.operation(2, 99, 1);
        var ledger = EscrowTestFixtures.ledger(a, b).generation(1);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(ledger));
        var writer = new EscrowLedgerWriter(directory.resolve(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 1);
        try (var coordinator = new EscrowCoordinator(data, writer)) {
            var proofs = new HashMap<EscrowEndpoint.Key, EscrowBarrier.Proof>();
            for (var chain : EscrowPrefix.chains(ledger.records()).values()) {
                var last = chain.get(chain.size() - 1); var state = new EscrowPrefix.State(ledger.id(), last.endpoint().afterEpoch(),
                        last.endpoint().afterSignature(), chain.stream().map(EscrowPrefix.Link::marker).toList());
                var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory,
                        new ChunkPos(0, 0), new AtomicLong(1), 1, 0, () -> { }, () -> { });
                coordinator.freeze(last.endpoint().key(), state);
                proofs.put(last.endpoint().key(), new EscrowBarrier.Proof(state, capture, new AtomicBoolean(true), new AtomicBoolean(true)));
            }
            var bank = proofs.get(a.destination().key()); var outcomes = new ArrayList<Boolean>();
            assertThrows(IllegalArgumentException.class, () -> coordinator.retire(Set.of(b.id()), proofs, outcomes::add));
            assertTrue(coordinator.retire(Set.of(a.id()), proofs, outcomes::add)); EscrowLedgerWriterTest.poll(writer, writer::idle);
            assertEquals(List.of(true), outcomes); assertEquals(List.of(b), coordinator.ledger().records());
            assertTrue(coordinator.locked(b.destination().key())); assertFalse(coordinator.locked(a.source().key()));
            assertFalse(bank.valid()); assertFalse(coordinator.frozen(b.destination().key()));
            var pruned = EscrowTestEndpoints.prune(bank.frontier(), Set.of(a.id()));
            assertEquals(2, pruned.epoch()); assertEquals(bank.frontier().signature(), pruned.signature());
            assertEquals(List.of(b.id()), pruned.markers().stream().map(EscrowPrefix.Marker::operation).toList());
            assertEquals(EscrowPrefix.Classification.AFTER, EscrowPrefix.classify(ledger.id(), EscrowPrefix.chains(List.of(b)).get(b.destination().key()), pruned));
            coordinator.freeze(b.destination().key(), pruned); coordinator.tick(10);
            assertThrows(IllegalArgumentException.class, () -> coordinator.retire(Set.of(b.id()), proofs, outcomes::add));
        }
    }
    private static EscrowPrefix.State frontier(EscrowEndpoint endpoint, EscrowOperation op) {
        return new EscrowPrefix.State(EscrowTestFixtures.id(500), endpoint.afterEpoch(), endpoint.afterSignature(),
                List.of(new EscrowPrefix.Marker(op.id(), op.sequence(), endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? "DESTINATION" : "SOURCE", endpoint.afterEpoch())));
    }
}
