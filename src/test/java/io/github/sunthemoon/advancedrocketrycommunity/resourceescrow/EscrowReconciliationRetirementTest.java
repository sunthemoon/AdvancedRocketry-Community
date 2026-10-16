package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowReconciliationRetirementTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void exactPartialAcknowledgementRetainsLaterMemberAndInstallsItsCompleteNamedFloor() throws Exception {
        try (var f = new Fixture(true, CheckedSavedDataFile::atomicMove, cut -> { })) {
            try (var request = f.request(() -> true)) {
                assertTrue(f.coordinator.retireNative(request, f.replies::add));
                assertTrue(f.replies.isEmpty()); f.assertHeld();
                f.io.tickUntil(f.coordinator::tick, 11, f.writer::idle);
                assertEquals(1, f.replies.size()); var ack = f.replies.get(0);
                assertTrue(ack.committed()); assertEquals(f.base.id(), ack.ledger());
                assertEquals(Set.of(f.a.id()), ack.members()); assertEquals(2, ack.generation());
                assertEquals(EscrowCodec.digest(EscrowCodec.encode(f.coordinator.ledger())), ack.digest());
                assertEquals(List.of(f.b), f.coordinator.ledger().records());
                assertEquals(f.base.issuedThrough(), f.coordinator.ledger().issuedThrough());
                assertFalse(f.coordinator.locked(f.a.source().key()));
                assertTrue(f.coordinator.locked(f.b.destination().key()));
                assertFalse(f.coordinator.frozen(f.b.destination().key()));
                assertEquals(2, f.releases.get()); assertFalse(request.proved());
                var floor = f.coordinator.ledger().floors().get(0);
                assertEquals(f.a.guard().transfer(), floor.getUUID("transfer_id"));
                assertEquals(f.a.destination().identity(), floor.getUUID("entity_id"));
                assertEquals(2, floor.getLong("resource_epoch"));
                assertEquals(f.b.destination().afterSignature(), floor.getString("signature"));
                assertEquals(f.b.sequence(), floor.getLong("highest_seq"));
                assertEquals(f.coordinator.ledger(), f.disk());
                assertFalse(f.coordinator.retireNative(request, f.replies::add), "An old request cannot report a second success");
            }
        }
    }

    @Test void noNativeRetirementWithMissingProofLostLeaseOrDifferentRoot() throws Exception {
        try (var f = new Fixture(false, CheckedSavedDataFile::atomicMove, cut -> { })) {
            var lease = new AtomicBoolean(true);
            try (var request = f.request(lease::get)) {
                lease.set(false); assertFalse(f.coordinator.retireNative(request, f.replies::add));
                lease.set(true); request.proofs.get(f.a.source().key()).release();
                assertFalse(f.coordinator.retireNative(request, f.replies::add));
                assertTrue(f.replies.isEmpty()); f.assertHeld();
            }
            try (var old = f.request(() -> true)) {
                f.data.setDirty(); f.coordinator.autosave(); f.io.tickUntil(f.coordinator::tick, 11, f.writer::idle);
                f.coordinator.tick(30);
                assertFalse(f.coordinator.retireNative(old, f.replies::add));
                assertTrue(f.replies.isEmpty()); f.assertHeld();
            }
        }
    }

    @Test void aLeaseLostAtCaptureDoesNotRetireOrBecomeAnEndpointException() throws Exception {
        try (var f = new Fixture(false, CheckedSavedDataFile::atomicMove, cut -> { })) {
            var armed = new AtomicBoolean(false); var reads = new AtomicInteger();
            try (var request = f.request(() -> !armed.get() || reads.incrementAndGet() == 1)) {
                armed.set(true);
                assertTrue(f.coordinator.retireNative(request, f.replies::add));
                f.io.tickUntil(f.coordinator::tick, 11, f.writer::idle);
                assertEquals(1, f.replies.size()); assertFalse(f.replies.get(0).committed());
                assertEquals(f.base, f.disk()); f.assertHeld();
            }
        }
    }

    @Test void leaseOrEitherTokenLostImmediatelyBeforeReplacementRefusesL2() throws Exception {
        for (int fault = 0; fault < 3; fault++) {
            var lease = new AtomicBoolean(true); var generations = new ArrayList<AtomicLong>(); int selected = fault;
            try (var f = new Fixture(false, CheckedSavedDataFile::atomicMove, cut -> {
                if (cut.equals("K1")) {
                    if (selected == 0) { lease.set(false); } else { generations.get(selected - 1).incrementAndGet(); }
                }
            }); var request = f.request(lease::get)) {
                generations.addAll(f.generations);
                assertTrue(f.coordinator.retireNative(request, f.replies::add));
                f.io.tickUntil(f.coordinator::tick, 11, f.writer::idle);
                assertEquals(1, f.replies.size()); assertFalse(f.replies.get(0).committed());
                assertEquals(f.base, f.disk()); f.assertHeld();
            }
        }
    }

    @Test void unknownKeepsExactCandidateAndLocksUntilOneFinalAcknowledgement() throws Exception {
        var moves = new AtomicInteger(); var unknown = new AtomicBoolean();
        try (var f = new Fixture(false, (staged, target) -> {
            if (moves.incrementAndGet() == 1) { throw new java.nio.file.AccessDeniedException(target.toString()); }
            CheckedSavedDataFile.atomicMove(staged, target);
        }, cut -> { if (cut.equals("WRITE_UNKNOWN")) { unknown.set(true); } }); var request = f.request(() -> true)) {
            assertTrue(f.coordinator.retireNative(request, f.replies::add));
            long tick = f.io.tickUntil(f.coordinator::tick, 11, unknown::get);
            assertTrue(unknown.get()); assertTrue(f.replies.isEmpty()); f.assertHeld();
            assertEquals(f.base, f.disk()); assertEquals(2, f.writer.lastGeneration());
            f.io.tickUntil(f.coordinator::tick, tick, f.writer::idle);
            assertEquals(2, moves.get()); assertEquals(1, f.replies.size()); assertTrue(f.replies.get(0).committed());
            assertEquals(2, f.replies.get(0).generation()); assertEquals(2, f.releases.get());
        }
    }

    @Test void unknownWithSupersededJournalResolvesOldRootWithoutUnlockingOrSuccess() throws Exception {
        var unknown = new AtomicBoolean(); var lease = new AtomicBoolean(true); var moves = new AtomicInteger();
        try (var f = new Fixture(false, (staged, target) -> {
            moves.incrementAndGet(); throw new java.nio.file.AccessDeniedException(target.toString());
        }, cut -> { if (cut.equals("WRITE_UNKNOWN")) { unknown.set(true); } }); var request = f.request(lease::get)) {
            assertTrue(f.coordinator.retireNative(request, f.replies::add));
            long tick = f.io.tickUntil(f.coordinator::tick, 11, unknown::get);
            assertTrue(unknown.get()); assertTrue(f.replies.isEmpty()); f.assertHeld();
            lease.set(false); f.io.tickUntil(f.coordinator::tick, tick, f.writer::idle);
            assertTrue(f.writer.idle()); assertEquals(1, moves.get());
            assertEquals(1, f.replies.size()); assertFalse(f.replies.get(0).committed());
            assertEquals(f.base, f.disk()); f.assertHeld();
        }
    }

    @Test void aLaterSettlementReleasesObsoleteOrdinaryFreezesOnlyAtExactO4() throws Exception {
        try (var f = new Fixture(false, CheckedSavedDataFile::atomicMove, cut -> { }); var request = f.request(() -> true)) {
            f.coordinator.transition(f.a.id(), EscrowReason.OTHER_STATE); f.coordinator.persistTransitions();
            long tick = f.io.tickUntil(f.coordinator::tick, 11, f.writer::idle); f.coordinator.tick(tick += 10);
            var observations = new ArrayList<>(request.ends.values());
            observations.add(new EscrowSettlementIntent.Observation(f.b.source(), new EscrowPrefix.State(f.base.id(), 1,
                    f.b.source().afterSignature(), List.of(new EscrowPrefix.Link(f.b, f.b.source(), "SOURCE").marker())), true, 4900));
            var preview = EscrowSettlementIntent.preview(f.coordinator.ledger(), f.a.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                    EscrowTestFixtures.id(700), "operator", tick, observations);
            var intent = preview.confirm(preview.actor(), preview.digest(), tick, observations);
            assertTrue(f.coordinator.beginSettlement(intent, assertTrueResult -> assertTrue(assertTrueResult)));
            tick = f.io.tickUntil(f.coordinator::tick, tick + 1, f.writer::idle); f.coordinator.tick(tick += 10);
            var proofs = new HashMap<EscrowEndpoint.Key, EscrowBarrier.Proof>();
            for (var observation : intent.checkpointed()) {
                var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory, new ChunkPos(0, 0),
                        new AtomicLong(1), 1, 0, () -> { }, () -> { });
                proofs.put(observation.endpoint().key(), new EscrowBarrier.Proof(observation.state(), capture, new AtomicBoolean(true), new AtomicBoolean(true)));
            }
            assertTrue(f.coordinator.frozen(f.a.source().key()) && f.coordinator.frozen(f.a.destination().key()));
            assertTrue(f.coordinator.settlementWrite(intent, proofs, false, () -> true, committed -> assertTrue(committed)));
            tick = f.io.tickUntil(f.coordinator::tick, tick + 1, f.writer::idle); f.coordinator.tick(tick += 10);
            assertTrue(f.coordinator.frozen(f.a.source().key()) && f.coordinator.frozen(f.a.destination().key()));
            assertTrue(f.coordinator.settlementWrite(intent, proofs, true, () -> true, committed -> {
                assertTrue(committed);
                assertFalse(f.coordinator.frozen(f.a.source().key()) || f.coordinator.frozen(f.a.destination().key()));
            }));
            f.io.tickUntil(f.coordinator::tick, tick + 1, f.writer::idle);
            assertFalse(f.coordinator.logicalLocked(f.a.guard().rocket()));
            proofs.values().forEach(EscrowBarrier.Proof::release);
        }
    }

    private final class Fixture implements AutoCloseable {
        final AwaitedLedgerIo io = new AwaitedLedgerIo();
        final Path file = directory.resolve(java.util.UUID.randomUUID().toString()).resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        final ResourceEscrowSavedData data;
        final EscrowCoordinator coordinator;
        final EscrowLedgerWriter writer;
        final EscrowLedger base;
        final EscrowOperation a, b;
        final net.minecraft.nbt.CompoundTag rocketSlot;
        final ArrayList<EscrowCoordinator.RetirementAcknowledgement> replies = new ArrayList<>();
        final ArrayList<AtomicLong> generations = new ArrayList<>();
        final AtomicInteger releases = new AtomicInteger();

        Fixture(boolean named, CheckedSavedDataFile.Committer committer, java.util.function.Consumer<String> checkpoint) throws Exception {
            var sample = EscrowReconciliationBindingBoundaryTest.sample(); var original = sample.a(); b = sample.b();
            a = named ? new EscrowOperation(original.id(), original.sequence(), original.owner(), original.createdTick(), original.phase(),
                    original.reason(), original.payload(), original.payloadHash(), original.source(), original.destination(),
                    new EscrowGuard(original.guard().rocket(), EscrowTestFixtures.id(750), 1, EscrowTestFixtures.hash(750)),
                    original.kind(), original.settlement(), original.nativeBinding()) : original;
            var initial = new EscrowLedger(sample.ledger().id(), sample.ledger().issuedThrough(), 1, List.of(a, b), List.of(), List.of());
            java.nio.file.Files.createDirectory(file.getParent());
            CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW).replace(file, () -> EscrowCodec.encode(initial));
            data = ResourceEscrowSavedData.load(EscrowCodec.encode(initial)); base = data.ledger();
            writer = io.writer(file, 1, committer, checkpoint); coordinator = new EscrowCoordinator(data, writer); coordinator.tick(10);
            rocketSlot = EscrowReconciliationCheckpointTest.rocketSlot(a.destination(), sample.after());
        }
        EscrowReconciliationCheckpoint request(BooleanSupplier lease) {
            var source = new EscrowSettlementIntent.Observation(a.source(), new EscrowPrefix.State(base.id(), 1,
                    a.source().afterSignature(), List.of(new EscrowPrefix.Link(a, a.source(), "SOURCE").marker())), true, 4900);
            var request = new EscrowReconciliationCheckpoint(base, Set.of(a.id()), List.of(source,
                    EscrowSettlementDisk.observe(rocketSlot, a.destination(), base.id())), lease);
            for (var endpoint : List.of(a.source(), a.destination())) {
                var targets = request.targets(endpoint); var proofs = new HashMap<EscrowEndpoint.Key, EscrowBarrier.Proof>();
                for (var target : targets) {
                    var generation = new AtomicLong(1); generations.add(generation);
                    var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory, new ChunkPos(0, 0),
                            generation, 1, 0, () -> { }, releases::incrementAndGet);
                    proofs.put(target.endpoint().key(), new EscrowBarrier.Proof(target.frontier(), capture, new AtomicBoolean(true), new AtomicBoolean(true)));
                    coordinator.freeze(target.endpoint().key(), target.frontier());
                }
                request.accept(targets, new EscrowBarrier.JointResult(proofs, EscrowReason.NONE));
            }
            return request;
        }
        EscrowLedger disk() throws Exception {
            return EscrowCodec.decode(CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW).read(file).orElseThrow());
        }
        void assertHeld() {
            assertEquals(List.of(a, b), coordinator.ledger().records());
            assertTrue(coordinator.frozen(a.source().key()) && coordinator.frozen(a.destination().key()));
            assertTrue(coordinator.locked(a.source().key()) && coordinator.locked(a.destination().key()));
        }
        @Override public void close() { coordinator.close(); }
    }
}
