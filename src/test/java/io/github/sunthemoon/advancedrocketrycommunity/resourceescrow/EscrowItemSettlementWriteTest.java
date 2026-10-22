package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Item/load-role counterparts of the unchanged before-write proof and checked-journal-lease rules. */
class EscrowItemSettlementWriteTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    record Fixture(EscrowOperation op, EscrowSettlementIntent intent, EscrowLedger root) { }
    static Fixture fixture(boolean load, boolean release, int missing) {
        var f = EscrowItemReconciliationFixture.create(load); var op = f.op().withReason(EscrowReason.OTHER_STATE);
        var rocket = missing == 1 ? new EscrowSettlementIntent.Observation(op.rocket(), null, false, 0)
                : EscrowItemSettlementObservationTest.rocket(f, f.after());
        var device = missing == 2 ? new EscrowSettlementIntent.Observation(op.device(), null, false, 0)
                : EscrowItemSettlementObservationTest.device(f, f.deviceAfter());
        var root = EscrowItemSettlementProofTest.held(f, op);
        var intent = EscrowItemSettlementProofTest.intent(root, op, List.of(rocket, device));
        var frozen = intent.freeze(root);
        return new Fixture(op, intent, (release ? EscrowSettlementTombstones.install(frozen, intent) : frozen).generation(1));
    }

    @Test void eitherMissingProofWrongProjectionOrInvalidatedProofRefusesBeforeO3AndO4() {
        for (boolean load : List.of(true, false)) {
            for (boolean release : List.of(false, true)) {
                for (int missing = 0; missing < 3; missing++) {
                    var f = fixture(load, release, missing); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(f.root()));
                    var file = directory.resolve(load + "-" + release + "-" + missing + ".dat");
                    try (var writer = new EscrowLedgerWriter(file, 1); var coordinator = new EscrowCoordinator(data, writer)) {
                        coordinator.tick(10);
                        for (var observation : f.intent().checkpointed()) {
                            var partial = proofs(f.intent()); partial.remove(observation.endpoint().key());
                            assertThrows(IllegalArgumentException.class,
                                    () -> coordinator.settlementWrite(f.intent(), partial, release, () -> true, ignored -> fail()));
                        }
                        var wrong = proofs(f.intent()); var key = f.op().rocket().key(); var old = wrong.get(key);
                        wrong.put(key, new EscrowBarrier.Proof(new EscrowPrefix.State(f.root().id(), 1,
                                f.op().rocket().afterSignature(), List.of()), old.capture(), old.active(), old.lifetime()));
                        assertThrows(IllegalArgumentException.class,
                                () -> coordinator.settlementWrite(f.intent(), wrong, release, () -> true, ignored -> fail()));
                        var invalid = proofs(f.intent()); invalid.get(key).capture().generation().incrementAndGet();
                        assertThrows(IllegalArgumentException.class,
                                () -> coordinator.settlementWrite(f.intent(), invalid, release, () -> true, ignored -> fail()));
                        assertEquals(f.root(), coordinator.ledger()); assertTrue(writer.idle());
                        assertFalse(java.nio.file.Files.exists(file));
                    }
                }
            }
        }
    }

    @Test void proofOrJournalLeaseLossAtTheWriterBoundaryRetainsTheExactIntentAndReceipts() {
        for (boolean load : List.of(true, false)) {
            for (boolean release : List.of(false, true)) {
                for (boolean invalidateProof : List.of(true, false)) {
                    var f = fixture(load, release, 0); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(f.root()));
                    var proofs = proofs(f.intent()); var lease = new AtomicBoolean(true);
                    var calls = new java.util.ArrayList<Boolean>(); var io = new AwaitedLedgerIo();
                    var file = directory.resolve(load + "-" + release + "-" + invalidateProof + ".dat");
                    try (var writer = io.writer(file, 1, CheckedSavedDataFile::atomicMove, cut -> {
                        if (invalidateProof) { proofs.get(f.op().rocket().key()).capture().generation().incrementAndGet(); }
                        else { lease.set(false); }
                    }); var coordinator = new EscrowCoordinator(data, writer)) {
                        coordinator.tick(10);
                        assertTrue(coordinator.settlementWrite(f.intent(), proofs, release, lease::get, calls::add));
                        io.tickUntil(coordinator::tick, 11, writer::idle);
                        assertTrue(writer.idle()); assertEquals(List.of(false), calls);
                        assertEquals(f.root(), coordinator.ledger()); assertFalse(java.nio.file.Files.exists(file));
                        assertEquals(f.intent().encode(), coordinator.ledger().records().get(0).settlement());
                        assertEquals(release ? 1 : 0, coordinator.ledger().tombstones().size());
                        assertTrue(coordinator.ledger().rocketAuthorities().isEmpty());
                    }
                }
            }
        }
    }

    private Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs(EscrowSettlementIntent intent) {
        var result = new HashMap<EscrowEndpoint.Key, EscrowBarrier.Proof>();
        for (var observation : intent.checkpointed()) {
            var frontier = observation.missing() ? new EscrowPrefix.State(intent.ledger(), 0,
                    EscrowSettlementTombstones.observationDigest(observation), List.of()) : observation.state();
            var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory, new ChunkPos(0, 0),
                    new AtomicLong(1), 1, 0, () -> { }, () -> { });
            result.put(observation.endpoint().key(), new EscrowBarrier.Proof(frontier, capture, new AtomicBoolean(true), new AtomicBoolean(true)));
        }
        return result;
    }
}
