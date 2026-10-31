package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R192: native retirement cannot occupy the writer's uncaptured successor slot. */
class EscrowLoaderS6RetirementExclusionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void busyWriterRefusesNativeRetirementWithoutChangingAuthority() throws Exception { exercise("busy"); }
    @Test void nonemptyAdmissionQueueRefusesNativeRetirementWithoutChangingAuthority() throws Exception { exercise("queued"); }
    @Test void sameProvedCheckpointIsAdmittedWhenWriterAndQueueAreIdle() throws Exception { exercise("idle"); }

    private void exercise(String mode) throws Exception {
        var sample = EscrowReconciliationBindingBoundaryTest.sample();
        var op = sample.a();
        var initial = new EscrowLedger(sample.ledger().id(), sample.ledger().issuedThrough(), 1,
                List.of(op, sample.b()), List.of(), List.of());
        Path file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW).replace(file, () -> EscrowCodec.encode(initial));
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(initial));
        var io = new AwaitedLedgerIo();
        var writer = io.writer(file, 1, CheckedSavedDataFile::atomicMove);
        try (var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.tick(10);
            CompoundTag rocketSlot = EscrowReconciliationCheckpointTest.rocketSlot(op.destination(), sample.after());
            var source = new EscrowSettlementIntent.Observation(op.source(), new EscrowPrefix.State(data.ledger().id(), 1,
                    op.source().afterSignature(), List.of(new EscrowPrefix.Link(op, op.source(), "SOURCE").marker())), true, 4900);
            try (var request = new EscrowReconciliationCheckpoint(data.ledger(), Set.of(op.id()), List.of(source,
                    EscrowSettlementDisk.observe(rocketSlot, op.destination(), data.ledger().id())), () -> true)) {
                for (var endpoint : List.of(op.source(), op.destination())) {
                    var state = request.frontiers.get(endpoint.key());
                    var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null,
                            directory, new ChunkPos(0, 0), new AtomicLong(1), 1, 0, () -> { }, () -> { });
                    request.proofs.put(endpoint.key(), new EscrowBarrier.Proof(state, capture,
                            new AtomicBoolean(true), new AtomicBoolean(true)));
                    coordinator.freeze(endpoint.key(), state);
                }
                assertTrue(request.proved());
                if (mode.equals("busy")) {
                    assertTrue(writer.request(() -> writer.snapshot(data.ledger().generation(2), Set.of()), ignored -> { }));
                    assertFalse(writer.idle());
                    assertTrue(coordinator.pendingAdmissions().isEmpty());
                } else if (mode.equals("queued")) {
                    coordinator.enqueue(EscrowTestFixtures.operation(3, 99, 0), ignored -> { });
                    assertTrue(writer.idle());
                    assertEquals(1, coordinator.pendingAdmissions().size());
                }
                var before = EscrowCodec.encode(data.ledger());
                var markers = rocketSlot.copy();
                var frontiers = java.util.Map.copyOf(request.frontiers);
                var proofs = java.util.Map.copyOf(request.proofs);
                String writerBefore = writer.observation();
                long generation = writer.lastGeneration();
                var replies = new ArrayList<EscrowCoordinator.RetirementAcknowledgement>();
                boolean accepted = coordinator.retireNative(request, replies::add);
                assertEquals(mode.equals("idle"), accepted);
                if (!accepted) {
                    assertEquals(before, EscrowCodec.encode(data.ledger()));
                    assertEquals(generation, writer.lastGeneration());
                    // Completion arrival is asynchronous, but retirement must not create a successor.
                    assertEquals(writerBefore.contains("successor:true"), writer.observation().contains("successor:true"));
                    assertEquals(markers, rocketSlot);
                    assertEquals(frontiers, request.frontiers);
                    assertEquals(proofs, request.proofs);
                    assertTrue(request.proved());
                    assertTrue(coordinator.locked(op.source().key()) && coordinator.locked(op.destination().key()));
                    assertTrue(coordinator.frozen(op.source().key()) && coordinator.frozen(op.destination().key()));
                    assertTrue(replies.isEmpty());
                } else {
                    io.tickUntil(coordinator::tick, 11, writer::idle);
                    assertEquals(1, replies.size());
                    assertTrue(replies.get(0).committed());
                }
            }
        }
    }
}
