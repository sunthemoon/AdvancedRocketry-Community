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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowReconciliationCheckpointTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void readbackUsesCompleteCurrentFrontierAndBindingNotIndividualOrHistoricalDigests() {
        var f = EscrowReconciliationBindingBoundaryTest.sample();
        var root = f.ledger(); var op = f.a(); var slot = rocketSlot(op.destination(), f.after());
        try (var checkpoint = checkpoint(root, op, slot, new AtomicBoolean(true))) {
            var validate = checkpoint.validator(op.destination().key());
            assertTrue(validate.test(slot));
            assertFalse(validate.test(rocketSlot(op.destination(), f.first())), "An obsolete individual AFTER is not the frozen frontier");
            var frame = slot.copy(); frame.getList("Entities", 10).getCompound(0).putBoolean("OnGround", true);
            assertTrue(validate.test(frame), "R15 observation does not compare the historical mutable envelope");
            for (String field : List.of("owner_id", "assembly_transaction_id")) {
                var changed = slot.copy(); changed.getList("Entities", 10).getCompound(0).getCompound("RocketEntityData")
                        .putUUID(field, EscrowTestFixtures.id(9999));
                assertFalse(validate.test(changed), field);
            }
            var duplicate = slot.copy(); duplicate.getList("Entities", 10).add(slot.getList("Entities", 10).getCompound(0).copy());
            assertFalse(validate.test(duplicate));
            var missing = slot.copy(); missing.put("Entities", new ListTag()); assertFalse(validate.test(missing));
            var wrongSlot = slot.copy(); wrongSlot.putIntArray("Position", new int[] {999, 999}); assertFalse(validate.test(wrongSlot));
            var foreign = slot.copy(); foreign.getList("Entities", 10).getCompound(0).getCompound("RocketEntityData")
                    .getCompound("resource_escrow").putUUID("ledger_id", EscrowTestFixtures.id(9999));
            assertFalse(validate.test(foreign));
            var forged = slot.copy(); forged.getList("Entities", 10).getCompound(0).getCompound("RocketEntityData")
                    .getCompound("resource_escrow").getList("markers", 10).getCompound(0).putUUID("op_id", EscrowTestFixtures.id(9999));
            assertFalse(validate.test(forged));
            var wrongFlight = slot.copy(); wrongFlight.getList("Entities", 10).getCompound(0).getCompound("RocketEntityData")
                    .getCompound("flight_data").putUUID("logical_rocket_id", EscrowTestFixtures.id(9999));
            assertFalse(validate.test(wrongFlight), "The flight and wrapper must name the same logical rocket");
        }
        var remaining = root.retire(Set.of(op.id()));
        var pruned = f.after().copy();
        pruned.put("wrapper", new RocketEscrowSection(root.id(), 2, List.of(new EscrowPrefix.Link(f.b(), f.b().destination(), "DESTINATION").marker())).encode());
        var afterPruning = rocketSlot(f.b().destination(), pruned);
        try (var checkpoint = checkpoint(remaining, f.b(), afterPruning, new AtomicBoolean(true))) {
            assertTrue(checkpoint.validator(f.b().destination().key()).test(afterPruning));
            assertFalse(checkpoint.validator(f.b().destination().key()).test(slot), "An unpruned stale marker is not the new frontier");
        }
    }

    @Test void noCheckpointForOneSidedOrBeforeFrontiersOrLostJournal() {
        var f = EscrowReconciliationBindingBoundaryTest.sample(); var root = f.ledger(); var op = f.a();
        var source = EscrowSettlementDisk.observe(sourceSlot(op, root), op.source(), root.id());
        assertThrows(IllegalArgumentException.class, () -> new EscrowReconciliationCheckpoint(root, Set.of(op.id()), List.of(source), () -> true));
        assertThrows(IllegalArgumentException.class, () -> checkpoint(root, op, rocketSlot(op.destination(), f.first()), new AtomicBoolean(true)));
        assertThrows(IllegalArgumentException.class, () -> checkpoint(root, op, rocketSlot(op.destination(), f.after()), new AtomicBoolean(false)));
    }

    @Test void bothLiveTokensAndExactLeaseAreRequiredAndEveryWatchIsReleased() {
        var f = EscrowReconciliationBindingBoundaryTest.sample(); var lease = new AtomicBoolean(true);
        var released = new AtomicInteger(); var generation = new AtomicLong(1);
        try (var checkpoint = checkpoint(f.ledger(), f.a(), rocketSlot(f.a().destination(), f.after()), lease)) {
            var sourceTargets = checkpoint.targets(f.a().source()); var destinationTargets = checkpoint.targets(f.a().destination());
            assertEquals(1, sourceTargets.size()); assertEquals(1, destinationTargets.size());
            checkpoint.accept(sourceTargets, proofs(sourceTargets, generation, released));
            assertFalse(checkpoint.proved(), "One-sided readback is not joint proof");
            checkpoint.accept(destinationTargets, proofs(destinationTargets, generation, released)); assertTrue(checkpoint.proved());
            generation.incrementAndGet(); assertFalse(checkpoint.proved()); checkpoint.discardInvalid();
            assertEquals(2, released.get()); assertTrue(checkpoint.proofs.isEmpty());
            checkpoint.accept(sourceTargets, proofs(sourceTargets, generation, released));
            checkpoint.accept(destinationTargets, proofs(destinationTargets, generation, released)); assertTrue(checkpoint.proved());
            lease.set(false); assertFalse(checkpoint.proved()); checkpoint.discardInvalid(); assertEquals(4, released.get());
            checkpoint.accept(sourceTargets, proofs(sourceTargets, generation, released)); assertEquals(5, released.get());
        }
    }

    @Test void samePhysicalSlotHasOneTargetGroupAndLateCallbackCannotResurrectAClosedCheckpoint() {
        var f = EscrowReconciliationBindingBoundaryTest.sample(); var root = f.ledger();
        var observations = new ArrayList<EscrowSettlementIntent.Observation>();
        observations.add(EscrowSettlementDisk.observe(sourceSlot(f.a(), root), f.a().source(), root.id()));
        observations.add(EscrowSettlementDisk.observe(sourceSlot(f.b(), root), f.b().source(), root.id()));
        observations.add(EscrowSettlementDisk.observe(rocketSlot(f.a().destination(), f.after()), f.a().destination(), root.id()));
        var checkpoint = new EscrowReconciliationCheckpoint(root, Set.of(f.a().id(), f.b().id()), observations, () -> true);
        var targets = checkpoint.targets(f.a().source()); assertEquals(2, targets.size());
        var released = new AtomicInteger(); checkpoint.close();
        checkpoint.accept(targets, proofs(targets, new AtomicLong(1), released));
        assertEquals(2, released.get()); assertFalse(checkpoint.proved()); assertTrue(checkpoint.proofs.isEmpty());
    }

    private static EscrowReconciliationCheckpoint checkpoint(EscrowLedger root, EscrowOperation op, CompoundTag slot, AtomicBoolean lease) {
        return new EscrowReconciliationCheckpoint(root, Set.of(op.id()), List.of(
                EscrowSettlementDisk.observe(sourceSlot(op, root), op.source(), root.id()),
                EscrowSettlementDisk.observe(slot, op.destination(), root.id())), lease::get);
    }

    private static EscrowBarrier.JointResult proofs(List<EscrowBarrier.Target> targets, AtomicLong generation, AtomicInteger released) {
        var proofs = new HashMap<EscrowEndpoint.Key, EscrowBarrier.Proof>();
        for (var target : targets) {
            var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, Path.of("."), new ChunkPos(0, 0),
                    generation, generation.get(), 0, () -> { }, released::incrementAndGet);
            proofs.put(target.endpoint().key(), new EscrowBarrier.Proof(target.frontier(), capture, new AtomicBoolean(true), new AtomicBoolean(true)));
        }
        return new EscrowBarrier.JointResult(proofs, EscrowReason.NONE);
    }

    static CompoundTag rocketSlot(EscrowEndpoint endpoint, CompoundTag envelope) {
        var root = new CompoundTag(); root.putIntArray("Position", new int[] {endpoint.x(), endpoint.z()});
        var entity = new CompoundTag(); entity.putUUID("UUID", envelope.getUUID("physical")); entity.putString("id", endpoint.type());
        entity.put("Pos", envelope.getList("pos", 6).copy());
        var data = new CompoundTag(); data.putInt("schema_version", 3); data.putUUID("assembly_transaction_id", envelope.getUUID("logical"));
        data.putUUID("owner_id", envelope.getUUID("owner")); data.put("snapshot", envelope.getCompound("snapshot").copy());
        data.put("flight_data", envelope.getCompound("flight").copy()); data.put("resource_escrow", envelope.getCompound("wrapper").copy());
        entity.put("RocketEntityData", data); var entities = new ListTag(); entities.add(entity); root.put("Entities", entities); return root;
    }

    private static CompoundTag sourceSlot(EscrowOperation op, EscrowLedger root) {
        var endpoint = op.source(); var tag = new CompoundTag(); tag.putString("id", endpoint.type());
        tag.putInt("x", endpoint.x()); tag.putInt("y", endpoint.y()); tag.putInt("z", endpoint.z());
        var fixture = new CompoundTag(); fixture.putUUID("identity", endpoint.identity()); fixture.putInt("amount", 4900); fixture.putInt("capacity", 16000);
        var persistent = new CompoundTag(); persistent.put("arce_escrow_fixture", fixture);
        EscrowTestEndpoints.putState(persistent, new EscrowPrefix.State(root.id(), 1, endpoint.afterSignature(),
                List.of(new EscrowPrefix.Link(op, endpoint, "SOURCE").marker())), false);
        tag.put("ForgeData", persistent); var slot = new CompoundTag(); slot.putInt("xPos", endpoint.x() >> 4); slot.putInt("zPos", endpoint.z() >> 4);
        var objects = new ListTag(); objects.add(tag); slot.put("block_entities", objects); return slot;
    }
}
