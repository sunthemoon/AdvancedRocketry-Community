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
        var ledgerIo = new AwaitedLedgerIo();
        var writer = ledgerIo.writer(directory.resolve(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 1,
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile::atomicMove);
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
            assertTrue(coordinator.retire(Set.of(a.id()), proofs, outcomes::add)); EscrowLedgerWriterTest.poll(ledgerIo, writer, writer::idle);
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
    /** Review W11b1 finding 2, probe "stale": a bank frozen before op1's queued L1 cannot retire op1. */
    @Test void aFrontierFrozenBeforeAQueuedAdmissionCannotRetireThatAdmission() throws Exception {
        var op0 = EscrowTestFixtures.operation(1, 99, 0); var op1 = EscrowTestFixtures.operation(2, 99, 1);
        var ledger = EscrowTestFixtures.ledger(op0).generation(1);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(ledger)); var replies = new ArrayList<Boolean>();
        var ledgerIo = new AwaitedLedgerIo();
        var writer = ledgerIo.writer(directory.resolve(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 1,
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile::atomicMove);
        try (var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.prepareTest(op1, replies::add);
            var bank = op0.destination().key(); var staleBank = prefix(List.of(op0), bank, 1);
            coordinator.freeze(op0.source().key(), prefix(List.of(op0), op0.source().key(), 1));
            assertTrue(coordinator.admitting(bank));
            assertThrows(IllegalArgumentException.class, () -> coordinator.freeze(bank, staleBank));
            assertFalse(coordinator.frozen(bank));
            coordinator.tick(10); assertTrue(coordinator.commitPreparations()); EscrowLedgerWriterTest.poll(ledgerIo, writer, () -> !replies.isEmpty());
            assertEquals(List.of(true), replies); assertEquals(List.of(op0, op1), coordinator.ledger().records()); assertFalse(coordinator.admitting(bank));
            assertThrows(IllegalArgumentException.class, () -> coordinator.freeze(bank, staleBank));
            // Defense in depth: even a frozen stale bank frontier cannot cover op1.
            var records = coordinator.ledger().records(); var both = Set.of(op0.id(), op1.id());
            var frontiers = new HashMap<EscrowEndpoint.Key, EscrowPrefix.State>(); var proofs = new HashMap<EscrowEndpoint.Key, EscrowBarrier.Proof>();
            for (var key : List.of(op0.source().key(), op1.source().key(), bank)) { frontiers.put(key, prefix(records, key, key.equals(bank) ? 2 : 1)); }
            frontiers.put(bank, staleBank); frontiers.forEach((key, state) -> proofs.put(key, proof(state)));
            var staleFrontiers = Map.copyOf(frontiers); var staleProofs = Map.copyOf(proofs);
            var refused = assertThrows(IllegalArgumentException.class, () -> EscrowCoordinator.requireCoveringProofs(records, both, staleProofs, staleFrontiers));
            assertTrue(refused.getMessage().contains("covering"));
            EscrowCoordinator.requireCoveringProofs(records, Set.of(op0.id()), staleProofs, staleFrontiers);
            // The complete AFTER bank frontier proves both operations and retires them together.
            frontiers.put(bank, prefix(records, bank, 2)); proofs.put(bank, proof(frontiers.get(bank)));
            frontiers.forEach(coordinator::freeze); var outcomes = new ArrayList<Boolean>();
            coordinator.tick(20); assertTrue(coordinator.retire(both, proofs, outcomes::add)); EscrowLedgerWriterTest.poll(ledgerIo, writer, writer::idle);
            assertEquals(List.of(true), outcomes); assertTrue(coordinator.ledger().records().isEmpty());
        }
    }
    /** Review W11b1 finding 7: veto and bounds refusals are not IO failures, and a full monitor is a bound. */
    @Test void barrierCaptureReasonsSeparateRefusalsFromSupersession() {
        assertEquals(EscrowReason.SUPERSEDING_SAVE, EscrowBarrier.captureReason(new EscrowChunkStore.SupersededCapture()));
        assertEquals(EscrowReason.BARRIER_BOUNDS, EscrowBarrier.captureReason(new EscrowChunkStore.CaptureBounds("Entity capture limit exceeded")));
        assertEquals(EscrowReason.BARRIER_VETO, EscrowBarrier.captureReason(new IllegalArgumentException("Captured endpoint identity/frontier mismatch")));
        assertEquals(EscrowReason.BARRIER_UNSTABLE, EscrowBarrier.readbackReason(new java.io.IOException("Anvil record changed during readback")));
        var monitor = new EscrowSaveMonitor();
        for (int i = 0; i < 128; i++) { monitor.watch("minecraft:overworld", false, new ChunkPos(i, 0)); }
        var full = assertThrows(EscrowChunkStore.CaptureBounds.class, () -> monitor.watch("minecraft:overworld", false, new ChunkPos(128, 0)));
        assertEquals(EscrowReason.BARRIER_BOUNDS, EscrowBarrier.captureReason(full));
        for (EscrowReason reason : List.of(EscrowReason.BARRIER_VETO, EscrowReason.BARRIER_EXTERNAL, EscrowReason.BARRIER_BOUNDS)) {
            assertTrue(reason.retryableProofFailure() && !reason.manual()); // Same STALLED handling; distinct persisted reason.
        }
    }
    private EscrowBarrier.Proof proof(EscrowPrefix.State state) {
        var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory,
                new ChunkPos(0, 0), new AtomicLong(1), 1, 0, () -> { }, () -> { });
        return new EscrowBarrier.Proof(state, capture, new AtomicBoolean(true), new AtomicBoolean(true));
    }
    private static EscrowPrefix.State prefix(List<EscrowOperation> records, EscrowEndpoint.Key key, int applied) {
        var links = EscrowPrefix.chains(records).get(key).subList(0, applied); var last = links.get(applied - 1).endpoint();
        return new EscrowPrefix.State(EscrowTestFixtures.id(500), last.afterEpoch(), last.afterSignature(), links.stream().map(EscrowPrefix.Link::marker).toList());
    }
    private static EscrowPrefix.State frontier(EscrowEndpoint endpoint, EscrowOperation op) {
        return new EscrowPrefix.State(EscrowTestFixtures.id(500), endpoint.afterEpoch(), endpoint.afterSignature(),
                List.of(new EscrowPrefix.Marker(op.id(), op.sequence(), endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? "DESTINATION" : "SOURCE", endpoint.afterEpoch())));
    }
}
