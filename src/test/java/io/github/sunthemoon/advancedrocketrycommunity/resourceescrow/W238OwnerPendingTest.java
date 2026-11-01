package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowAdmissionAuthority.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentReasons;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class W238OwnerPendingTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final UUID A = id(238_000), B = id(238_001);

    @Test void mixedConsumersChargeOneOwnerAndRefuseWithoutChargingOthers() throws Exception {
        try (var h = BayReceiptHarness.empty(temp)) {
            for (int n = 1; n <= 8; n++) {
                assertTrue(h.put(receipt(n, A, id(240_000), BayReceipt.Phase.INTENT)).queued());
                var pending = new Pending(id(100 + n), A, id(240_001), Consumer.PROPULSION);
                assertEquals(Result.RESERVED, reserve(h.authority, pending)); h.fluid.add(pending);
            }
            var before = BayReceiptCodec.encode(h.store().durable());
            long sequence = h.store().issuedThrough();
            assertEquals("OWNER_PENDING_FULL", check(h.authority, A).name());
            var refused = h.put(receipt(20, A, id(240_002), BayReceipt.Phase.INTENT));
            assertEquals("OWNER_PENDING_FULL", BayDeploymentReasons.admission(refused));
            assertFalse(refused.queued()); assertEquals(16, h.authority.pendingCount());
            assertEquals(8, h.authority.retainedFor(A)); assertEquals(sequence, h.store().issuedThrough());
            assertEquals(before, BayReceiptCodec.encode(h.store().durable()));
            assertEquals(Result.RESERVED, check(h.authority, B));
            assertTrue(h.put(receipt(21, B, id(240_003), BayReceipt.Phase.INTENT)).queued());
            assertEquals(Result.ALREADY_HELD, reserve(h.authority, h.fluid.get(0)));
            var first = h.fluid.get(0);
            assertEquals(Result.IDENTITY_CONFLICT, reserve(h.authority, new Pending(first.op(), B, first.rocket(), first.consumer())));
            h.settle();
            assertTrue(h.put(h.store().receipts().get(0).withPhase(BayReceipt.Phase.REPAIR, "INTERRUPTED", 2)).queued());
            h.settle(); assertEquals(17, h.authority.pendingCount());
        }
    }

    @Test void fourOwnersFillTheServerAndCapacityPrecedenceIsStable() throws Exception {
        try (var h = BayReceiptHarness.empty(temp)) {
            for (int n = 1; n <= 64; n++) {
                assertTrue(h.put(receipt(n, id(238_000 + (n - 1) / 16), id(240_000 + (n - 1) / 8), BayReceipt.Phase.INTENT)).queued());
            }
            assertEquals(64, h.authority.pendingCount());
            assertEquals(Result.SERVER_FULL, check(h.authority, id(238_004)));
            assertEquals(Result.SERVER_FULL, check(h.authority, A));
            var first = h.store().receipts().get(0);
            assertEquals(Result.ALREADY_HELD, h.reserve(first));
            h.settle();
            assertTrue(h.put(first.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 2)).queued());
            assertEquals(64, h.authority.pendingCount());
            h.settle(); assertEquals(63, h.authority.pendingCount());
            assertEquals(16, h.authority.retainedFor(A));
            assertEquals(Result.ROCKET_FULL, h.authority.checkReserve(id(999), A, id(240_001), Consumer.PROPULSION));
            assertEquals(Result.RESERVED, check(h.authority, A));
        }
    }

    @Test void rejectedRearmReacquiresTheOwnerSlotOnlyAfterExactAcknowledgement() throws Exception {
        try (var h = BayReceiptHarness.empty(temp)) {
            var rejected = receipt(1, A, id(240_000), BayReceipt.Phase.REJECTED);
            assertTrue(h.put(rejected).queued()); h.settle();
            for (int n = 2; n <= 17; n++) {
                assertTrue(h.put(receipt(n, A, id(240_001 + (n - 2) / 8), BayReceipt.Phase.INTENT)).queued());
            }
            h.settle(); var root = BayReceiptCodec.encode(h.store().durable());
            var rearm = rejected.withPhase(BayReceipt.Phase.INTENT, "NONE", 3);
            assertEquals("OWNER_PENDING_FULL", h.put(rearm).reservation().name());
            assertEquals(root, BayReceiptCodec.encode(h.store().durable()));
            assertEquals(17, h.authority.retainedFor(A)); assertEquals(16, h.authority.pendingCount());
            var first = h.store().receipts().get(1);
            assertTrue(h.put(first.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 4)).queued());
            assertEquals("OWNER_PENDING_FULL", h.put(rearm).reservation().name());
            h.settle(); assertTrue(h.put(rearm).queued()); h.settle();
            assertEquals(16, h.authority.pendingCount()); assertEquals(17, h.authority.retainedFor(A));
            assertEquals(rejected.op(), h.store().receipts().get(0).op());
        }
    }

    @Test void restartRebuildsBothCompleteIndexesAndGrandfathersOverCapFiles() throws Exception {
        for (int count : List.of(16, 24, 64)) {
            Path directory = java.nio.file.Files.createDirectory(temp.resolve("old-" + count));
            var rows = new ArrayList<BayReceipt>();
            for (int n = 1; n <= count; n++) { rows.add(receipt(n, A, id(240_000 + (n - 1) / 8), BayReceipt.Phase.INTENT)); }
            var file = directory.resolve(ManagedSavedDataType.BAY_RECEIPTS.fileName());
            CheckedSavedDataFile.replace(file, ManagedSavedDataType.BAY_RECEIPTS, () -> BayReceiptCodec.encode(root(rows)));
            var authority = new EscrowAdmissionAuthority(); var propulsion = authority.register(Consumer.PROPULSION);
            try (var h = new BayReceiptHarness(authority, directory)) {
                h.receipts.start(BayReceiptSavedData.load(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.BAY_RECEIPTS).orElseThrow()),
                        CheckedSavedDataFile.Presence.PRESENT, file, 0);
                assertEquals(Result.UNAVAILABLE, check(authority, A));
                propulsion.noFile(); assertEquals(count, authority.pendingCount());
                assertEquals(count == 64 ? "SERVER_FULL" : "OWNER_PENDING_FULL", check(authority, A).name());
                assertEquals(count == 64 ? Result.SERVER_FULL : Result.RESERVED, check(authority, B));
                assertEquals(rows, h.store().receipts());
                assertTrue(h.put(rows.get(0).withPhase(BayReceipt.Phase.REPAIR, "INTERRUPTED", 5)).queued());
                h.settle(); assertEquals(count, authority.pendingCount());
                for (int n = 0; n < count - 15; n++) { assertTrue(h.remove(h.store().receipts().get(0)).queued()); }
                h.settle(); assertEquals(15, authority.pendingCount());
                assertEquals(Result.RESERVED, check(authority, A));
                assertDoesNotThrow(() -> BayReceiptCodec.encode(h.store().durable()));
                assertEquals(15, BayReceiptCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.BAY_RECEIPTS).orElseThrow()).receipts().size());
            }
        }
    }

    @Test void unknownAndSuccessorCopiesChargeOnceAndReleaseOnlyOnExactRejection() throws Exception {
        try (var h = BayReceiptHarness.empty(temp)) {
            for (int n = 1; n <= 16; n++) {
                assertTrue(h.put(receipt(n, A, id(240_000 + (n - 1) / 8), BayReceipt.Phase.INTENT)).queued());
            }
            h.settle(); var first = h.store().receipts().get(0);
            h.committer.set(BayReceiptHarness.AMBIGUOUS);
            assertTrue(h.put(first.withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", 5)).queued());
            h.until(h.store().writer()::unknown);
            assertTrue(h.put(receipt(20, A, id(240_003), BayReceipt.Phase.REJECTED)).queued());
            assertTrue(h.store().writer().hasSuccessor());
            assertEquals(16, h.authority.pendingCount()); assertEquals(17, h.authority.retainedFor(A));
            assertEquals(Result.ALREADY_HELD, h.reserve(first));
            assertEquals(Result.OWNER_PENDING_FULL, check(h.authority, A));
            h.committer.set(CheckedSavedDataFile::atomicMove); h.settle();
            assertTrue(h.put(first.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 6)).queued());
            assertEquals(Result.OWNER_PENDING_FULL, check(h.authority, A));
            h.settle(); assertEquals(15, h.authority.pendingCount()); assertEquals(17, h.authority.retainedFor(A));
            assertEquals(Result.RESERVED, check(h.authority, A));
        }
    }

    @Test void restartRebuildsTheOwnerUnionFromRealLedgerAndBayFiles() throws Exception {
        var ops = java.util.stream.LongStream.rangeClosed(1, 8).mapToObj(EscrowTestFixtures::operation).toList();
        UUID storedOwner = ops.get(0).owner(); var rows = new ArrayList<BayReceipt>();
        for (int n = 1; n <= 8; n++) { rows.add(receipt(n, storedOwner, id(240_000), BayReceipt.Phase.INTENT)); }
        var ledgerFile = temp.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var bayFile = temp.resolve(ManagedSavedDataType.BAY_RECEIPTS.fileName());
        CheckedSavedDataFile.replace(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW,
                () -> EscrowCodec.encode(EscrowTestFixtures.ledger(ops.toArray(EscrowOperation[]::new))));
        CheckedSavedDataFile.replace(bayFile, ManagedSavedDataType.BAY_RECEIPTS, () -> BayReceiptCodec.encode(root(rows)));
        var authority = new EscrowAdmissionAuthority(); var propulsion = authority.register(Consumer.PROPULSION);
        try (var h = new BayReceiptHarness(authority, temp)) {
            h.receipts.start(BayReceiptSavedData.load(CheckedSavedDataFile.readPayload(bayFile, ManagedSavedDataType.BAY_RECEIPTS).orElseThrow()),
                    CheckedSavedDataFile.Presence.PRESENT, bayFile, 0);
            assertEquals(Result.UNAVAILABLE, check(authority, storedOwner));
            var data = ResourceEscrowSavedData.load(CheckedSavedDataFile.readPayload(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            try (var writer = new EscrowLedgerWriter(ledgerFile, 0); var coordinator = new EscrowCoordinator(data, writer, propulsion)) {
                assertEquals(16, authority.pendingCount()); assertEquals(Result.OWNER_PENDING_FULL, check(authority, storedOwner));
                assertEquals(Result.RESERVED, check(authority, B)); assertEquals(ops, coordinator.ledger().records());
                assertEquals(rows, h.store().receipts()); assertEquals(Result.ALREADY_HELD, reserve(authority, Pending.of(ops.get(0))));
            }
        }
    }

    /** W231 test list item 2: with both limits reached, ROCKET_FULL wins over OWNER_PENDING_FULL and changes nothing. */
    @Test void rocketFullTakesPrecedenceOverOwnerPendingFullWhenBothApply() throws Exception {
        try (var h = BayReceiptHarness.empty(temp)) {
            for (int n = 1; n <= 16; n++) {
                assertTrue(h.put(receipt(n, A, id(240_000 + (n - 1) / 8), BayReceipt.Phase.INTENT)).queued());
            }
            assertEquals(16, h.authority.pendingCount()); assertEquals(8, h.authority.pendingFor(id(240_001)));
            assertEquals(Result.OWNER_PENDING_FULL, h.authority.checkReserve(id(990_000), A, id(240_002), Consumer.PROPULSION));
            assertEquals(Result.ROCKET_FULL, h.authority.checkReserve(id(990_000), B, id(240_001), Consumer.PROPULSION));
            assertEquals(Result.ROCKET_FULL, h.authority.checkReserve(id(990_000), A, id(240_001), Consumer.PROPULSION));
            var before = BayReceiptCodec.encode(h.store().durable()); long sequence = h.store().issuedThrough();
            var refused = h.put(receipt(20, A, id(240_001), BayReceipt.Phase.INTENT));
            assertFalse(refused.queued()); assertEquals(Result.ROCKET_FULL, refused.reservation());
            assertEquals("ROCKET_PENDING_FULL", BayDeploymentReasons.admission(refused));
            assertEquals(Result.ROCKET_FULL, h.authority.tryReserve(id(990_000), A, id(240_001), Consumer.PROPULSION));
            assertEquals(16, h.authority.pendingCount()); assertEquals(sequence, h.store().issuedThrough());
            assertEquals(before, BayReceiptCodec.encode(h.store().durable()));
        }
    }

    /**
     * W231 test list item 6: a written escrow ledger with 24 PROPULSION records for one owner loads after a restart and
     * refuses only that owner's new work. A decision moves one held record to SETTLING_ACCEPT and an exact retirement
     * removes another; neither asks for a slot, and the saved file loads again with the same counts.
     */
    @Test void restartWithAnOverCapEscrowLedgerRefusesOnlyThatOwnerAndRecoversWithoutANewSlot() throws Exception {
        var ops = java.util.stream.LongStream.rangeClosed(1, 24).mapToObj(EscrowTestFixtures::operation).toList();
        UUID owner = ops.get(0).owner(); assertTrue(ops.stream().allMatch(op -> op.owner().equals(owner)));
        var file = temp.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        CheckedSavedDataFile.replace(file, ManagedSavedDataType.RESOURCE_ESCROW,
                () -> EscrowCodec.encode(EscrowTestFixtures.ledger(ops.toArray(EscrowOperation[]::new))));
        var authority = new EscrowAdmissionAuthority(); var propulsion = authority.register(Consumer.PROPULSION);
        assertEquals(Result.UNAVAILABLE, check(authority, B));
        var data = ResourceEscrowSavedData.load(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
        var io = new AwaitedLedgerIo(); var decided = ops.get(0); var retired = ops.get(1);
        try (var writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove);
                var coordinator = new EscrowCoordinator(data, writer, propulsion)) {
            assertEquals(ops, coordinator.ledger().records()); assertEquals(24, authority.pendingCount());
            assertEquals(Result.OWNER_PENDING_FULL, check(authority, owner)); assertEquals(Result.RESERVED, check(authority, B));
            assertEquals(Result.ALREADY_HELD, reserve(authority, Pending.of(decided)));
            long tick = 10;
            coordinator.transition(decided.id(), EscrowReason.OTHER_STATE);
            coordinator.tick(tick); coordinator.autosave(); io.awaitWrites(); coordinator.tick(++tick);
            assertEquals(EscrowReason.OTHER_STATE, coordinator.ledger().records().get(0).reason());
            var observed = EscrowSettlementIntentTest.observations(EscrowTestFixtures.ledger(decided), false);
            var preview = EscrowSettlementIntent.preview(coordinator.ledger(), decided.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                    B, "operator", tick, observed);
            var decisions = new ArrayList<Boolean>(); coordinator.tick(tick += 10);
            assertTrue(coordinator.beginSettlement(preview.confirm(B, preview.digest(), tick, observed), decisions::add));
            io.awaitWrites(); coordinator.tick(++tick); assertEquals(List.of(true), decisions);
            assertEquals(EscrowOperation.Phase.SETTLING_ACCEPT, coordinator.ledger().records().get(0).phase());
            assertEquals(24, authority.pendingCount()); assertEquals(Result.OWNER_PENDING_FULL, check(authority, owner));
            var proofs = new java.util.HashMap<EscrowEndpoint.Key, EscrowBarrier.Proof>();
            for (var chain : EscrowPrefix.chains(coordinator.ledger().records()).entrySet()) {
                if (!chain.getKey().equals(retired.source().key()) && !chain.getKey().equals(retired.destination().key())) { continue; }
                var last = chain.getValue().get(chain.getValue().size() - 1).endpoint();
                var state = new EscrowPrefix.State(coordinator.ledger().id(), last.afterEpoch(), last.afterSignature(),
                        chain.getValue().stream().map(EscrowPrefix.Link::marker).toList());
                coordinator.freeze(chain.getKey(), state);
                var capture = new EscrowChunkStore.Capture(java.util.concurrent.CompletableFuture.completedFuture(null), null, temp,
                        new net.minecraft.world.level.ChunkPos(0, 0), new java.util.concurrent.atomic.AtomicLong(1), 1, 0, () -> { }, () -> { });
                proofs.put(chain.getKey(), new EscrowBarrier.Proof(state, capture, new java.util.concurrent.atomic.AtomicBoolean(true),
                        new java.util.concurrent.atomic.AtomicBoolean(true)));
            }
            assertEquals(2, proofs.size());
            var retirements = new ArrayList<Boolean>(); coordinator.tick(tick += 10);
            assertTrue(coordinator.retire(java.util.Set.of(retired.id()), proofs, retirements::add));
            io.awaitWrites(); coordinator.tick(++tick); assertEquals(List.of(true), retirements);
            assertEquals(23, coordinator.ledger().records().size()); assertEquals(23, authority.pendingCount());
            assertEquals(Result.OWNER_PENDING_FULL, check(authority, owner)); assertEquals(Result.RESERVED, check(authority, B));
            assertEquals(coordinator.ledger(), EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
        }
        var restarted = new EscrowAdmissionAuthority(); var again = restarted.register(Consumer.PROPULSION);
        var reloaded = ResourceEscrowSavedData.load(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
        try (var writer = new EscrowLedgerWriter(file, reloaded.ledger().writerGeneration());
                var coordinator = new EscrowCoordinator(reloaded, writer, again)) {
            assertEquals(23, restarted.pendingCount()); assertEquals(Result.OWNER_PENDING_FULL, check(restarted, owner));
            assertEquals(Result.RESERVED, check(restarted, B));
            assertEquals(EscrowOperation.Phase.SETTLING_ACCEPT, coordinator.ledger().records().get(0).phase());
            assertTrue(coordinator.ledger().records().stream().noneMatch(op -> op.id().equals(retired.id())));
        }
    }

    private static Result check(EscrowAdmissionAuthority authority, UUID owner) {
        return authority.checkReserve(id(990_000), owner, id(990_001), Consumer.PROPULSION);
    }
    @Test void everyLoaderAndCargoKindUsesTheSameOwnerUnionAndTypedRefusal() throws Exception {
        var operations = new ArrayList<EscrowOperation>();
        operations.add(EscrowSettlementCapacityTest.operation(1, 99, 0, true));
        EscrowCargoFixtures.KINDS.forEach(kind -> operations.add(EscrowCargoFixtures.operation(kind, 1, 99)));
        EscrowItemFixtures.KINDS.forEach(kind -> operations.add(EscrowItemFixtures.operation(kind, 1, 99)));
        for (var operation : operations) {
            var root = EscrowTestFixtures.ledger(); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            var bays = new ArrayList<Pending>();
            for (int n = 0; n < 16; n++) { bays.add(new Pending(id(200 + n), operation.owner(), id(300 + n / 8), Consumer.BAY)); }
            try (var writer = new EscrowLedgerWriter(temp.resolve(operation.kind() + ".dat"), 0);
                    var coordinator = new EscrowCoordinator(data, writer)) {
                coordinator.admissionAuthority().registerIndex(Consumer.BAY, () -> new Index(true, bays));
                var payload = operation.payload(); var binding = operation.nativeBinding();
                var result = coordinator.prepareTransfer(new EscrowNativeBinding.Admission(operation, RocketEscrowSection.neutral()), ignored -> fail());
                assertEquals(EscrowCoordinator.Transfer.OWNER_PENDING_FULL, result, operation.kind().name());
                assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus.OWNER_PENDING_FULL,
                        EscrowCargoRefusals.status(result));
                assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus.OWNER_PENDING_FULL,
                        EscrowLoaderAdmission.status(result));
                assertEquals(root, coordinator.ledger()); assertFalse(data.isDirty());
                assertEquals(1, coordinator.nextSequence()); assertEquals(16, coordinator.admissionAuthority().pendingCount());
                assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
                assertEquals(payload, operation.payload()); assertEquals(binding, operation.nativeBinding());
            }
        }
    }

    @Test void floorPreviewReportsOwnerRefusalAndConfirmationDoesNotThrowOrChargeTheActor() throws Exception {
        var floor = EscrowTestFixtures.floor(300); floor.putUUID("transfer_id", EscrowTestFixtures.id(301));
        var root = new EscrowLedger(EscrowTestFixtures.id(500), 1, 0, List.of(), List.of(floor), List.of());
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", floor.getUUID("entity_id"),
                floor.getUUID("rocket_id"), "minecraft:armor_stand", 0, 0, 0, EscrowEndpoint.Unit.PRIMARY,
                1, floor.getString("signature"), floor.getString("signature"));
        var observations = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
        var preview = EscrowSettlementIntent.floorPreview(root, id(10_000), floor, B, "operator", 0, observations);
        var confirmed = preview.confirm(B, preview.digest(), 200, observations);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root)); var io = new AwaitedLedgerIo();
        var bays = new ArrayList<Pending>();
        for (int n = 0; n < 16; n++) { bays.add(new Pending(id(200 + n), A, id(400 + n / 8), Consumer.BAY)); }
        try (var writer = io.writer(temp.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0, CheckedSavedDataFile::atomicMove);
                var coordinator = new EscrowCoordinator(data, writer)) {
            var authority = coordinator.admissionAuthority(); authority.registerIndex(Consumer.BAY, () -> new Index(true, bays));
            var refusal = assertDoesNotThrow(() -> EscrowSettlementOperator.floorAdmission(root, authority, preview.members().get(0).id(), A, endpoint.logicalRocket()));
            assertEquals(Result.OWNER_PENDING_FULL, refusal);
            // W238-R1: a console or RCON operator, with no mod language loaded, reads W231's English sentence.
            var message = EscrowCommands.floorRefusal(refusal);
            assertEquals("OWNER_PENDING_FULL: The owner's pending operations are full; finish existing work before starting a new floor retirement.",
                    message.getString());
            var translated = assertInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class, message.getSiblings().get(0).getContents());
            assertEquals("message.advancedrocketrycommunity.escrow.floor_owner_pending_full", translated.getKey());
            assertEquals(english("message.advancedrocketrycommunity.escrow.floor_owner_pending_full"), translated.getFallback());
            coordinator.tick(200);
            assertFalse(assertDoesNotThrow(() -> coordinator.beginSettlement(confirmed, A, ignored -> fail())));
            assertEquals(root, coordinator.ledger()); assertEquals(2, coordinator.nextSequence()); assertFalse(data.isDirty());
            assertEquals(16, authority.pendingCount()); assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
            bays.remove(0); assertEquals(Result.RESERVED, EscrowSettlementOperator.floorAdmission(root, authority, preview.members().get(0).id(), A, endpoint.logicalRocket()));
            var results = new ArrayList<Boolean>();
            assertTrue(coordinator.beginSettlement(confirmed, A, results::add));
            io.tickUntil(coordinator::tick, 201, writer::idle); assertEquals(List.of(true), results);
            var stored = coordinator.ledger().records().get(0);
            assertEquals(A, stored.owner()); assertEquals(B, EscrowSettlementIntent.decode(stored.settlement()).actor());
            assertEquals(Result.ALREADY_HELD, reserve(authority, Pending.of(stored)));
            assertEquals(coordinator.ledger(), EscrowCodec.decode(EscrowCodec.encode(coordinator.ledger())));
        }
    }

    /** W238-R2: the command's floor admission keeps O1's order, so waiting is never suggested for full receipts. */
    @Test void floorAdmissionReportsPermanentReceiptsFullBeforeTheTransientOwnerRefusal() {
        var floor = EscrowTestFixtures.floor(300); floor.putUUID("transfer_id", EscrowTestFixtures.id(301));
        var receipts = new ArrayList<net.minecraft.nbt.CompoundTag>();
        for (int n = 0; n < 63; n++) { receipts.add(EscrowTestFixtures.receipt(n)); }
        // A decided record holds the 64th permanent receipt, so the preview's tombstone-count check alone would pass.
        var decided = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE);
        var undecided = EscrowTestFixtures.ledger(decided).replace(List.of(decided), List.of(floor), receipts);
        var observed = EscrowSettlementIntentTest.observations(EscrowTestFixtures.ledger(decided), false);
        var decision = EscrowSettlementIntent.preview(undecided, decided.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, B, "operator", 10, observed);
        var oneDecided = decision.confirm(B, decision.digest(), 11, observed).freeze(undecided);
        receipts.add(EscrowTestFixtures.receipt(63));
        var tombstones = new EscrowLedger(EscrowTestFixtures.id(500), 1, 0, List.of(), List.of(floor), receipts);
        var authority = new EscrowAdmissionAuthority(); authority.register(Consumer.PROPULSION).noFile();
        var bays = new ArrayList<Pending>();
        for (int n = 0; n < 16; n++) { bays.add(new Pending(id(200 + n), A, id(400 + n / 8), Consumer.BAY)); }
        authority.registerIndex(Consumer.BAY, () -> new Index(true, bays));
        UUID operation = id(10_000), logical = floor.getUUID("rocket_id");
        assertEquals(Result.OWNER_PENDING_FULL, EscrowSettlementOperator.floorAdmission(EscrowTestFixtures.ledger(), authority, operation, A, logical));
        assertEquals(63, oneDecided.tombstones().size()); assertEquals(64, tombstones.tombstones().size());
        for (var root : List.of(oneDecided, tombstones)) {
            assertEquals(64, EscrowSettlementIntent.reservedReceipts(root) + root.tombstones().size());
            var refused = assertThrows(IllegalArgumentException.class,
                    () -> EscrowSettlementOperator.floorAdmission(root, authority, operation, A, logical));
            assertEquals(EscrowSettlementIntent.RECEIPTS_FULL, refused.getMessage());
            assertEquals(16, authority.pendingCount());
        }
    }

    @Test void appendedCodesAndExactMessagesExistInBothGeneratedLanguages() throws Exception {
        assertEquals(6, Result.OWNER_PENDING_FULL.ordinal());
        assertEquals(35, io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayStatus.Reason.OWNER_PENDING_FULL.code());
        assertEquals(20, io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus.OWNER_PENDING_FULL.ordinal());
        assertEquals(27, io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus.OWNER_PENDING_FULL.ordinal());
        for (boolean chinese : List.of(false, true)) {
            var language = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(Path.of(
                    "src/generated/v1.8/resources/assets/advancedrocketrycommunity_v180/lang/" + (chinese ? "zh_cn" : "en_us") + ".json"))).getAsJsonObject();
            var messages = java.util.Map.of(
                    "screen.advancedrocketrycommunity.rocket_satellite_bay.reason.owner_pending_full", chinese
                            ? "你的待处理操作已达上限；请等待已有操作完成，或请管理员检查仍被持有的操作。"
                            : "Your pending operations are full; wait for earlier work to finish, or ask an administrator to inspect held operations.",
                    "message.advancedrocketrycommunity.fluid_fuel_loader.owner_pending_full", chinese
                            ? "你的待处理操作已达上限；未移动燃料。请等待已有操作完成，或联系管理员。"
                            : "Your pending operations are full; no fuel moved. Wait for earlier work or ask an administrator.",
                    "message.advancedrocketrycommunity.rocket_cargo.owner_pending_full", chinese
                            ? "你的待处理操作已达上限；未移动货物。请等待已有操作完成，或联系管理员。"
                            : "Your pending operations are full; no cargo moved. Wait for earlier work or ask an administrator.",
                    "message.advancedrocketrycommunity.escrow.floor_owner_pending_full", chinese
                            ? "该所有者的待处理操作已达上限；请先完成已有操作，再创建新的下限退役操作。"
                            : "The owner's pending operations are full; finish existing work before starting a new floor retirement.");
            messages.forEach((key, value) -> { assertTrue(language.has(key), key); assertEquals(value, language.get(key).getAsString()); });
        }
    }
    private static String english(String key) throws java.io.IOException {
        return com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(Path.of(
                "src/generated/v1.8/resources/assets/advancedrocketrycommunity_v180/lang/en_us.json"))).getAsJsonObject().get(key).getAsString();
    }
    private static Result reserve(EscrowAdmissionAuthority authority, Pending p) {
        return authority.tryReserve(p.op(), p.owner(), p.rocket(), p.consumer());
    }
}
