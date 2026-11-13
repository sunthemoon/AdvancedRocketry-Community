package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowAccountingReconciliationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    static EscrowLedger root(EscrowNativeBinding.Admission... captures) {
        var root = EscrowAccountingCaptureTest.empty();
        for (var capture : captures) { root = EscrowAccountingCapture.append(root, List.of(), capture); }
        return root;
    }
    static Map<EscrowEndpoint.Key, EscrowPrefix.State> states(EscrowLedger root, boolean source, boolean destination) {
        var states = new HashMap<EscrowEndpoint.Key, EscrowPrefix.State>();
        EscrowPrefix.chains(root.records()).forEach((key, chain) -> {
            boolean applied = key.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY ? source : destination;
            var endpoint = chain.get(applied ? chain.size() - 1 : 0).endpoint();
            states.put(key, new EscrowPrefix.State(root.id(), applied ? endpoint.afterEpoch() : endpoint.beforeEpoch(),
                    applied ? endpoint.afterSignature() : endpoint.beforeSignature(),
                    applied ? chain.stream().map(EscrowPrefix.Link::marker).toList() : List.of()));
        });
        return states;
    }
    static Map<EscrowEndpoint.Key, Map<UUID, EscrowFluidAccount.Vector>> vectors(EscrowLedger root, boolean source, boolean destination) {
        var vectors = new HashMap<EscrowEndpoint.Key, Map<UUID, EscrowFluidAccount.Vector>>();
        for (var chain : EscrowPrefix.chains(root.records()).values()) {
            boolean applied = chain.get(0).role().equals("SOURCE") ? source : destination;
            var link = chain.get(applied ? chain.size() - 1 : 0); var base = EscrowAccountingReconciliation.baseline(root, link.operation());
            var vector = link.role().equals("SOURCE") ? applied ? base.sourceAfter() : base.sourceBefore()
                    : applied ? base.destinationAfter() : base.destinationBefore();
            var values = new HashMap<UUID, EscrowFluidAccount.Vector>();
            for (var member : chain) { values.put(member.operation().id(), vector); }
            vectors.put(link.endpoint().key(), Map.copyOf(values));
        }
        return Map.copyOf(vectors);
    }

    @Test void allFourPartialApplicationsStayKnownButOnlyBothCanComplete() {
        for (var admission : List.of(EscrowAccountingCaptureTest.fuel(false).admission(),
                EscrowAccountingCaptureTest.cargo(true), EscrowAccountingCaptureTest.cargo(false))) {
            var root = root(admission); var op = admission.operation();
            for (boolean source : List.of(false, true)) {
                for (boolean destination : List.of(false, true)) {
                    var states = new HashMap<EscrowEndpoint.Key, EscrowPrefix.State>();
                    for (var endpoint : List.of(op.source(), op.destination())) {
                        boolean applied = endpoint.equals(op.source()) ? source : destination;
                        states.put(endpoint.key(), new EscrowPrefix.State(root.id(), applied ? endpoint.afterEpoch() : endpoint.beforeEpoch(),
                                applied ? endpoint.afterSignature() : endpoint.beforeSignature(), applied ? List.of(new EscrowPrefix.Link(op, endpoint,
                                endpoint.equals(op.source()) ? "SOURCE" : "DESTINATION").marker()) : List.of()));
                    }
                    var base = admission.account().baseline();
                    var values = Map.of(op.source().key(), Map.of(op.id(), source ? base.sourceAfter() : base.sourceBefore()),
                            op.destination().key(), Map.of(op.id(), destination ? base.destinationAfter() : base.destinationBefore()));
                    assertTrue(EscrowAccountingReconciliation.matches(root, Set.of(op.id()), states, values, false));
                    assertEquals(source && destination, EscrowAccountingReconciliation.matches(root, Set.of(op.id()), states, values, true));
                    assertEquals(admission.account(), root.fluidAccounts().get(0));
                }
            }
        }
    }

    @Test void chainedFuelFrontiersArePrefixNormalizedBeforeAndAfterPartialRetirement() {
        var a = EscrowAccountingCaptureTest.fuel(false).admission(); var b = EscrowAccountingCaptureTest.fuel(true).admission();
        var root = root(a, b); var states = states(root, true, true); var values = vectors(root, true, true);
        assertTrue(EscrowAccountingReconciliation.matches(root, Set.of(a.operation().id(), b.operation().id()), states, values, true));
        assertTrue(EscrowAccountingReconciliation.matches(root, Set.of(a.operation().id()), states, values, true));
        var remaining = EscrowFloors.retire(root, Set.of(a.operation().id()), states);
        assertEquals(List.of(b.account()), remaining.fluidAccounts());
        assertTrue(EscrowAccountingReconciliation.matches(remaining, Set.of(b.operation().id()), states(remaining, true, true),
                vectors(remaining, true, true), true));
        assertTrue(EscrowFloors.retire(remaining, Set.of(b.operation().id()), states(remaining, true, true)).fluidAccounts().isEmpty());
        // The second projected baseline includes the first fill even when neither has applied yet.
        assertTrue(EscrowAccountingReconciliation.matches(root, Set.of(a.operation().id(), b.operation().id()), states(root, false, false),
                vectors(root, false, false), false));
    }

    @Test void retiredLeadingMarkersWaitForC8WithoutChangingAnyFluidOrTheSurvivingAccount() {
        var a = EscrowAccountingCaptureTest.fuel(false); var b = EscrowAccountingCaptureTest.fuel(true);
        var remaining = root(a.admission(), b.admission()).retire(Set.of(a.admission().operation().id()));
        var op = b.admission().operation(); var endpoint = op.rocket(); var envelope = b.after();
        var wrapper = RocketEscrowSection.decode(envelope.getCompound("wrapper"));
        var state = new EscrowPrefix.State(remaining.id(), wrapper.epoch(), endpoint.afterSignature(), wrapper.markers());
        var chain = EscrowPrefix.chains(remaining.records()).get(endpoint.key());
        var refusal = assertThrows(EscrowReconciliationView.Refusal.class, () -> EscrowReconciliationView.requireBeforeAccounting(
                remaining, endpoint, chain, state, envelope, null));
        assertEquals(EscrowReason.NONE, refusal.reason);
        var pruned = envelope.copy(); var markers = List.of(wrapper.markers().get(1));
        pruned.put("wrapper", new RocketEscrowSection(remaining.id(), 2, markers).encode());
        assertDoesNotThrow(() -> EscrowReconciliationView.requireBeforeAccounting(remaining, endpoint, chain,
                new EscrowPrefix.State(remaining.id(), 2, endpoint.afterSignature(), markers), pruned, null));
        assertEquals(EscrowAccountingCapture.nativeVector(op, endpoint, envelope), EscrowAccountingCapture.nativeVector(op, endpoint, pruned));
        assertEquals(List.of(b.admission().account()), remaining.fluidAccounts());
    }

    @Test void numericC2MismatchIssuesNoPermitAndBecomesComponentLocalStickyRepair() {
        var f = EscrowAccountingCaptureTest.fuel(false); var root = root(f.admission()); var op = f.admission().operation();
        var base = f.admission().account().baseline();
        var wrong = new EscrowFluidAccount.Baseline(base.scope(), List.of(new EscrowFluidAccount.Entry(base.moved(), 5001, 0, -100, 100)));
        var badOp = op.accounting(wrong);
        var held = root.accounting(List.of(badOp), List.of(new EscrowFluidAccount.Row(op.id(), wrong, null)));
        var saved = EscrowCodec.encode(held); var device = f.deviceBefore().copy();
        var refusal = assertThrows(EscrowReconciliationView.Refusal.class,
                () -> EscrowReconciliationAction.issue(held, badOp, f.deviceBefore(), false, () -> true));
        assertEquals(EscrowReason.ACCOUNTING_MISMATCH, refusal.reason); assertEquals(device, f.deviceBefore());
        var data = ResourceEscrowSavedData.load(saved);
        try (var writer = new EscrowLedgerWriter(directory.resolve("held.dat"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.holdAccounting(held.records()); coordinator.holdAccounting(held.records());
            assertEquals(EscrowReason.ACCOUNTING_MISMATCH, coordinator.currentReason(badOp));
            assertTrue(coordinator.locked(op.source().key())); assertTrue(coordinator.locked(op.destination().key()));
            assertThrows(IllegalArgumentException.class, () -> coordinator.transition(op.id(), EscrowReason.NONE));
            assertEquals(saved, EscrowCodec.encode(coordinator.ledger())); assertTrue(writer.idle());
        }
        assertTrue(EscrowAccountingReconciliation.matches(root, Set.of(op.id()), states(root, true, true), vectors(root, true, true), true));
    }

    @Test void repairWarningsAreOncePerLiveOperationAtTheLedgerCeiling() {
        var operations = java.util.stream.LongStream.rangeClosed(1, EscrowLimits.OPERATIONS)
                .mapToObj(n -> EscrowTestFixtures.distributedOwner(EscrowTestFixtures.operation(n))).toList();
        var root = EscrowTestFixtures.ledger(operations.toArray(EscrowOperation[]::new));
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var logs = new StartupLogLines("ARCE_ESCROW_ACCOUNTING_HELD ");
             var writer = new EscrowLedgerWriter(directory.resolve("warnings.dat"), 0);
             var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.holdAccounting(operations); coordinator.holdAccounting(operations);
            for (var op : operations) { coordinator.transition(op.id(), EscrowReason.ACCOUNTING_MISMATCH); }
            assertEquals(64, logs.lines.size());
            for (var op : operations) { assertEquals(1, logs.lines.stream().filter(line -> line.equals(
                    "WARN ARCE_ESCROW_ACCOUNTING_HELD op=" + op.id())).count()); }
            assertTrue(writer.idle());
        }
    }

    @Test void healthyComponentStillValidatesBesideAHeldNumericComponent() {
        var first = EscrowAccountingCaptureTest.fuel(false).admission();
        var healthy = EscrowTestFixtures.distributedOwner(EscrowSettlementCapacityTest.operation(3, 77, 0, true));
        var baseline = EscrowAccountingFormatTest.baseline(healthy); healthy = healthy.accounting(baseline);
        var root = EscrowAccountingCapture.append(root(first), List.of(healthy),
                Map.of(healthy.id(), new EscrowFluidAccount.Row(healthy.id(), baseline, null)));
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(directory.resolve("independent.dat"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.holdAccounting(List.of(first.operation()));
            assertEquals(EscrowReason.NONE, coordinator.currentReason(healthy));
            assertTrue(EscrowAccountingReconciliation.matches(root, Set.of(healthy.id()), states(root, true, true), vectors(root, true, true), true));
            assertTrue(writer.idle());
        }
    }

    @Test void root3PreparationQueuesTheExactRowButTheUnapprovedWriterFormatStaysClosed() {
        var f = EscrowAccountingCaptureTest.fuel(false); var root = EscrowAccountingCaptureTest.empty();
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(directory.resolve("queued.dat"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            assertEquals(EscrowCoordinator.Transfer.QUEUED, coordinator.prepareTransfer(f.admission(), ignored -> fail()));
            assertEquals(2, coordinator.nextSequence()); assertEquals(1, coordinator.admissionAuthority().pendingCount());
            var candidate = coordinator.pendingLedger(); assertEquals(List.of(f.admission().account()), candidate.fluidAccounts());
            assertEquals(List.of(f.admission().operation()), candidate.records());
            assertFalse(EscrowAccountingFormat.ENABLED);
            assertThrows(IllegalArgumentException.class, () -> EscrowLedgerWriter.Snapshot.of(candidate.generation(1), Set.of(f.admission().operation().id())));
            assertEquals(candidate, coordinator.pendingLedger()); assertEquals(root.id(), coordinator.ledger().id());
            assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
        }
    }

    @Test void root3FluidCannotRetireThroughTheModelOnlyProofApi() {
        var f = EscrowAccountingCaptureTest.fuel(false); var root = root(f.admission()); var before = EscrowCodec.encode(root);
        var data = ResourceEscrowSavedData.load(before);
        try (var writer = new EscrowLedgerWriter(directory.resolve("no-model-retirement.dat"), 0);
             var coordinator = new EscrowCoordinator(data, writer)) {
            assertFalse(coordinator.retire(Set.of(f.admission().operation().id()), Map.of(), ignored -> fail()));
            assertEquals(before, EscrowCodec.encode(coordinator.ledger())); assertTrue(writer.idle()); assertFalse(data.isDirty());
        }
    }

    @Test void sameTotalWrongFluidFailsLiveAndStableNumericReadbackForFuelAndBothCargoDirections() {
        var f = EscrowAccountingCaptureTest.fuel(false);
        checkReadback(root(f.admission()), f.admission().operation(), f.deviceAfter());
        for (boolean loading : List.of(true, false)) {
            var c = EscrowCargoEligibilityTest.sample(loading, 1000, -1); var admission = EscrowAccountingCaptureTest.cargo(loading);
            checkReadback(root(admission), admission.operation(), c.deviceAfter());
            var endpoint = admission.operation().rocket();
            var slot = EscrowReconciliationCheckpointTest.rocketSlot(endpoint, c.after());
            var changedEnvelope = c.after().copy();
            var wrongSnapshot = io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.CargoTestFixtures.snapshot(
                    new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000));
            changedEnvelope.put("snapshot", io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec.encode(wrongSnapshot));
            changedEnvelope.putString("content_hash", wrongSnapshot.contentHash());
            checkReadback(root(admission), admission.operation(), endpoint, slot,
                    EscrowReconciliationCheckpointTest.rocketSlot(endpoint, changedEnvelope));
        }
    }
    private void checkReadback(EscrowLedger root, EscrowOperation op, CompoundTag deviceAfter) {
        var endpoint = op.device(); var slot = slot(endpoint, deviceAfter);
        var changed = slot.copy(); var object = changed.getList("block_entities", 10).getCompound(0);
        var nativeRoot = object.getCompound(EscrowLoaderEndpoint.is(endpoint)
                ? io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot.KEY : EscrowCargoEligibility.DEVICE_ROOT);
        var tank = nativeRoot.getCompound("tank");
        if (tank.isEmpty()) {
            nativeRoot.put("tank", LoaderTank.of(ResourceLocation.tryParse("minecraft:lava"), null, 1).encode());
        } else { tank.putString("FluidName", "minecraft:lava"); }
        checkReadback(root, op, endpoint, slot, changed);
    }
    private void checkReadback(EscrowLedger root, EscrowOperation op, EscrowEndpoint endpoint, CompoundTag slot, CompoundTag changed) {
        var expected = EscrowSettlementDisk.ordinary(slot, endpoint, root.id(), null, List.of(op));
        var numeric = vectors(root, true, true);
        var wrong = EscrowSettlementDisk.ordinary(changed, endpoint, root.id(), null, List.of(op));
        assertNotEquals(expected.fluids(), wrong.fluids());
        var wrongLive = new HashMap<>(numeric); wrongLive.put(endpoint.key(), wrong.fluids());
        assertFalse(EscrowAccountingReconciliation.matches(root, Set.of(op.id()), states(root, true, true), wrongLive, true));
        var reader = new EscrowCargoReadback(root.id(), expected, null, List.of(op));
        assertFalse(reader.test(changed)); assertEquals(EscrowReason.ACCOUNTING_MISMATCH, reader.reason());
        assertTrue(CompletableFuture.supplyAsync(() -> reader.test(changed)).join());
        assertFalse(reader.confirmed()); assertEquals(EscrowReason.ACCOUNTING_MISMATCH, reader.reason());
        assertTrue(CompletableFuture.supplyAsync(() -> reader.test(slot)).join()); assertTrue(reader.confirmed());
    }
    static CompoundTag slot(EscrowEndpoint endpoint, CompoundTag envelope) {
        var slot = new CompoundTag(); slot.putInt("xPos", endpoint.x() >> 4); slot.putInt("zPos", endpoint.z() >> 4);
        var entries = new net.minecraft.nbt.ListTag(); entries.add(envelope.copy()); slot.put("block_entities", entries); return slot;
    }

    @Test void checkpointFreezesNumbersWithItsFrontiersAndNeedsJointTokensAndReadback() {
        var f = EscrowAccountingCaptureTest.fuel(false); var root = root(f.admission()); var op = f.admission().operation();
        var device = slot(op.device(), f.deviceAfter());
        var rocket = EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(), f.after());
        var states = states(root, true, true); var numeric = vectors(root, true, true);
        var observations = List.of(EscrowSettlementDisk.observe(device, op.device(), root.id()), EscrowSettlementDisk.observe(rocket, op.rocket(), root.id()));
        var lease = new AtomicBoolean(true);
        try (var checkpoint = new EscrowReconciliationCheckpoint(root, Set.of(op.id()), observations, lease::get, states, Map.of(), numeric)) {
            assertTrue(checkpoint.accountingValid()); assertFalse(checkpoint.proved());
            for (var endpoint : List.of(op.source(), op.destination())) {
                var slot = endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? rocket : device;
                var targets = checkpoint.targets(endpoint); var validator = targets.get(0).validator();
                assertTrue(validator.test(slot)); assertTrue(CompletableFuture.supplyAsync(() -> validator.test(slot)).join());
                var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory,
                        new ChunkPos(0, 0), new AtomicLong(1), 1, 0, () -> { }, () -> { });
                var proof = new EscrowBarrier.Proof(states.get(endpoint.key()), capture, new AtomicBoolean(true), new AtomicBoolean(true));
                checkpoint.accept(targets, new EscrowBarrier.JointResult(Map.of(endpoint.key(), proof), EscrowReason.NONE));
            }
            assertTrue(checkpoint.proved()); lease.set(false); assertFalse(checkpoint.proved());
        }
        assertEquals(1, root.fluidAccounts().size());
    }

    @Test void externalE2ReadbackRetainsTheTypedAccountAndItsBarrier() throws Exception {
        var f = EscrowAccountingCaptureTest.fuel(false); var root = root(f.admission()); var op = f.admission().operation();
        var before = EscrowCodec.encode(root); var device = slot(op.device(), f.deviceAfter());
        var rocket = EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(), f.after());
        var pos = new ChunkPos(op.device().x() >> 4, op.device().z() >> 4);
        var file = directory.resolve("r." + (pos.x >> 5) + "." + (pos.z >> 5) + ".mca");
        try (var region = new net.minecraft.world.level.chunk.storage.RegionFile(file, directory, true);
             var output = region.getChunkDataOutputStream(pos)) { net.minecraft.nbt.NbtIo.write(device, output); }
        try (var channel = java.nio.channels.FileChannel.open(file, java.nio.file.StandardOpenOption.READ,
                java.nio.file.StandardOpenOption.WRITE)) {
            var location = java.nio.ByteBuffer.allocate(4);
            channel.read(location, ((pos.x & 31) + (pos.z & 31) * 32) * 4L); location.flip();
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {(byte) 130}), (location.getInt() >>> 8) * 4096L + 4);
        }
        var refusal = assertThrows(AnvilRecordReader.Refusal.class, () -> AnvilRecordReader.read(directory, pos));
        assertEquals(EscrowReason.BARRIER_EXTERNAL, refusal.reason());
        var observations = List.of(EscrowSettlementDisk.observe(device, op.device(), root.id()),
                EscrowSettlementDisk.observe(rocket, op.rocket(), root.id()));
        try (var checkpoint = new EscrowReconciliationCheckpoint(root, Set.of(op.id()), observations, () -> true,
                states(root, true, true), Map.of(), vectors(root, true, true))) {
            var targets = checkpoint.targets(op.device());
            var failure = new EscrowBarrier.JointResult(Map.of(), EscrowBarrier.readbackReason(refusal));
            checkpoint.accept(targets, failure);
            assertEquals(EscrowReason.BARRIER_EXTERNAL, checkpoint.readbackReason(targets, failure));
            assertFalse(checkpoint.proved()); assertEquals(before, EscrowCodec.encode(checkpoint.root));
        }
    }

    @Test void migratedLegacyRecordHasNoC2OrCheckpointAndNewComponentAdmissionIsTypedAndEffectFree() {
        var f = EscrowAccountingCaptureTest.fuel(false); var a = f.admission().operation();
        var legacy = new EscrowOperation(a.id(), a.sequence(), a.owner(), a.createdTick(), a.phase(), a.reason(), a.payload(), a.payloadHash(),
                a.source(), a.destination(), a.guard(), a.kind(), a.settlement(), a.nativeBinding());
        var root = EscrowTestFixtures.ledger(legacy).accounting(List.of(legacy), List.of());
        assertTrue(EscrowAccountingReconciliation.legacyHeld(root, legacy));
        var refusal = assertThrows(EscrowReconciliationView.Refusal.class,
                () -> EscrowReconciliationAction.issue(root, legacy, f.deviceBefore(), false, () -> true));
        assertEquals(EscrowReason.UNKNOWN_BASELINE, refusal.reason);
        assertThrows(IllegalArgumentException.class, () -> EscrowFloors.retire(root, Set.of(legacy.id()), Map.of()));
        var next = EscrowAccountingCaptureTest.fuel(true).admission(); var saved = EscrowCodec.encode(root);
        var data = ResourceEscrowSavedData.load(saved);
        try (var writer = new EscrowLedgerWriter(directory.resolve("legacy.dat"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            assertEquals(EscrowCoordinator.Transfer.UNKNOWN_BASELINE, coordinator.prepareTransfer(next, ignored -> fail()));
            assertEquals(2, coordinator.nextSequence()); assertEquals(1, coordinator.admissionAuthority().pendingCount());
            assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty()); assertTrue(coordinator.pendingAdmissions().isEmpty());
            assertEquals(saved, EscrowCodec.encode(coordinator.ledger())); assertFalse(data.isDirty());
        }
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus.UNKNOWN_BASELINE,
                EscrowCargoRefusals.status(EscrowCoordinator.Transfer.UNKNOWN_BASELINE));
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus.UNKNOWN_BASELINE,
                EscrowLoaderAdmission.status(EscrowCoordinator.Transfer.UNKNOWN_BASELINE));
    }
}
