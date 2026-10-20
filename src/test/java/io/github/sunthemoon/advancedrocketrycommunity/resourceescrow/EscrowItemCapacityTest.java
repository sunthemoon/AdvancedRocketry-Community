package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowItemCapacityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); EscrowItemStackCodecTest.bootstrap(); }
    @TempDir Path temp;

    @Test void realEncodersMeasureBothDirectionsFiveMarkersWorstActorReasonAndNestedPayloadLimits() {
        for (var kind : KINDS) {
            int longest = 0;
            for (int length = 3; length <= 128; length++) {
                try { operation(kind, 100, 99, 4, -1, "a:" + "b".repeat(length - 2)); longest = length; }
                catch (IllegalArgumentException refused) { break; }
            }
            assertTrue(longest > 0 && longest < 128, "The reference envelope, not the individual ID limit, decides eligibility");
            assertThrows(IllegalArgumentException.class, () -> operation(kind, 100, 99, 4, -1, "a:" + "b".repeat(126)));
            final String longestReferenceLevel = "a:" + "b".repeat(longest - 2);
            // A locally valid 512-byte reference is not an assertion that the five-marker receipt fits.
            var widest = operation(kind, 100, 99, 4, -1, longestReferenceLevel);
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(ledger(widest), widest.id()));
            var empty = new EscrowLedger(id(500), 99, 0, List.of(), List.of(), List.of());
            var widestData = ResourceEscrowSavedData.load(EscrowCodec.encode(empty));
            try (var writer = new EscrowLedgerWriter(temp.resolve(kind + "-widest"), 0); var coordinator = new EscrowCoordinator(widestData, writer)) {
                assertEquals(EscrowCoordinator.Transfer.ESCROW_FULL, coordinator.prepareTransfer(EscrowCargoAdmissionTest.capture(widest, 4), ignored -> fail()));
                assertEquals(100, coordinator.nextSequence()); assertEquals(0, coordinator.admissionAuthority().pendingCount());
                assertEquals(empty, coordinator.ledger()); assertFalse(widestData.isDirty()); assertTrue(writer.capturedOperations().isEmpty());
            }
            int admittedLength = longest;
            while (admittedLength >= 3 && !fits(kind, 4, 0, "a:" + "b".repeat(admittedLength - 2))) { admittedLength--; }
            assertTrue(admittedLength >= "minecraft:overworld".length());
            System.out.println("ITEM_REFERENCE kind=" + kind + " local_max_level_chars=" + longest + " local_max_refused=true"
                    + " five_marker_max_level_chars=" + admittedLength);
            final String level = "a:" + "b".repeat(admittedLength - 2);
            for (int retained = 0; retained <= 4; retained++) {
                int low = 0, high = 8_192;
                while (low < high) {
                    int mid = (low + high + 1) / 2;
                    if (fits(kind, retained, mid, level)) { low = mid; } else { high = mid - 1; }
                }
                assertTrue(low > 0); assertFalse(fits(kind, retained, low + 1, level));
                var op = operation(kind, 100, 99, retained, low, level).withReason(EscrowReason.JOURNAL_GUARD_MISMATCH);
                var root = ledger(op);
                System.out.println("ITEM_SIZE kind=" + kind + " retained=" + retained + " total_markers=" + (retained + 1)
                        + " level_chars=" + admittedLength + " max_blob=" + low + " five_marker_preview=true");
                report("payload", op.payload()); report("rocket_reference", EscrowCodec.endpoint(op.rocket()));
                report("device_reference", EscrowCodec.endpoint(op.device())); report("unsettled_operation", EscrowCodec.operation(op));
                for (int missing = 0; missing <= 2; missing++) {
                    var intent = intent(root, op, retained, missing, false); var o1 = intent.freeze(root);
                    var o3 = EscrowSettlementTombstones.install(o1, intent); var o4 = EscrowSettlementTombstones.release(o3, intent, 7);
                    assertEquals(o4, EscrowCodec.decode(EscrowCodec.encode(o4)));
                    report("intent_" + missing, intent.encode()); report("settled_operation_" + missing, EscrowCodec.operation(o1.records().get(0)));
                    report("receipt_" + missing, EscrowReceiptCodec.encode(o3.tombstones().get(0))); report("O3_root_" + missing, EscrowCodec.encode(o3));
                    report("O4_root_" + missing, EscrowCodec.encode(o4));
                    o4.rocketAuthorities().forEach(head -> report("READY", head.encode()));
                }
                if (retained == 0) {
                    var neutral = intent(root, op, 0, 0, true);
                    report("neutral_intent", neutral.encode());
                    report("neutral_operation", EscrowCodec.operation(neutral.freeze(root).records().get(0)));
                    report("neutral_receipt", EscrowReceiptCodec.encode(EscrowSettlementTombstones.create(op, neutral)));
                }
                // The byte maximum is local for this item fixture; it is not a raised outer allowance.
                final int nextBlob = low + 1; final int retainedCount = retained;
                var unchanged = stack(nextBlob); var original = unchanged.copy();
                EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.CARGO_TOO_LARGE,
                        () -> operation(kind, 100, 99, retainedCount, nextBlob, level));
                assertEquals(original, unchanged);

            }
        }
    }
    private static boolean fits(EscrowOperation.Kind kind, int retained, int blob, String level) {
        try {
            var op = operation(kind, 100, 99, retained, blob, level);
            EscrowSettlementCapacity.preflight(ledger(op), op.id());
            // Preview checks the worst five-marker O1 even when the captured wrapper retains fewer markers.
            var repair = op.withReason(EscrowReason.JOURNAL_GUARD_MISMATCH); var root = ledger(repair);
            for (int missing = 0; missing <= 2; missing++) { intent(root, repair, retained, missing, false).freeze(root); }
            return true;
        } catch (IllegalArgumentException refused) { return false; }
    }

    @Test void framedPayloadAndUnsettledOperationCanFitWhileEmbeddedO1ExceedsTheNodeBound() {
        for (var kind : KINDS) {
            var original = operation(kind, 1, 99); var stack = stack(-1); var tag = new CompoundTag();
            for (int i = 0; i < 350; i++) { tag.putByte("n" + i, (byte) 1); }
            stack.put("tag", tag); var payload = new EscrowItemCargoPayload(cursor(), stack, 0, 0).encode();
            assertTrue(BoundedNbt.fits(payload, 8_192, 12, 384));
            var op = new EscrowOperation(original.id(), 1, original.owner(), 1, original.phase(), original.reason(), payload,
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources.signature(payload),
                    original.source(), original.destination(), original.guard(), original.kind(), original.settlement(), original.nativeBinding());
            assertTrue(BoundedNbt.fits(EscrowCodec.operation(op), 12_288, 16, 512));
            var root = ledger(); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            try (var writer = new EscrowLedgerWriter(temp.resolve(kind + "-nodes"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
                assertEquals(EscrowCoordinator.Transfer.ESCROW_FULL, coordinator.prepareTransfer(EscrowCargoAdmissionTest.capture(op, 0), ignored -> fail()));
                assertEquals(1, coordinator.nextSequence()); assertEquals(root, coordinator.ledger()); assertFalse(data.isDirty());
                assertEquals(0, coordinator.admissionAuthority().pendingCount()); assertTrue(writer.capturedOperations().isEmpty());
            }
        }
    }

    @Test void r6HeadroomAndR8CeilingChargesRefuseBeforeReservationAndStillPermitR9Retirement() throws Exception {
        for (var kind : KINDS) {
            for (int floors : List.of(0, 29)) {
                var base = EscrowCombinedReservationTest.retainedFloors(floors);
                var data = ResourceEscrowSavedData.load(EscrowCodec.encode(base)); var io = new AwaitedLedgerIo();
                var file = java.nio.file.Files.createDirectories(temp.resolve(kind + "-floors-" + floors)).resolve(
                        ManagedSavedDataType.RESOURCE_ESCROW.fileName());
                try (var writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove);
                        var coordinator = new EscrowCoordinator(data, writer)) {
                    var admitted = new ArrayList<EscrowOperation>(); EscrowOperation refused = null;
                    for (int i = 0; i < 64; i++) {
                        var op = operation(kind, coordinator.nextSequence(), 40_000 + i);
                        var result = coordinator.prepareTransfer(EscrowCargoAdmissionTest.capture(op, 0), ignored -> { });
                        if (result == EscrowCoordinator.Transfer.QUEUED) { admitted.add(op); }
                        else { assertEquals(EscrowCoordinator.Transfer.ESCROW_FULL, result); refused = op; break; }
                    }
                    assertNotNull(refused); assertEquals(floors == 0 ? 62 : 60, admitted.size());
                    assertEquals(refused.sequence(), coordinator.nextSequence()); assertEquals(admitted.size(), coordinator.admissionAuthority().pendingCount());
                    assertEquals(base, coordinator.ledger()); assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
                    var all = new ArrayList<>(admitted); all.add(refused); var prospective = base.append(all);
                    long ceiling = EscrowSettlementCapacity.reservedBytes(prospective);
                    long previewOnly = ceiling - (EscrowLimits.OP_BYTES + 2_048L) + previewBytes(refused);
                    assertTrue(ceiling + EscrowSettlementCapacity.FLOOR_RETIREMENT_BYTES > EscrowLimits.ROOT_BYTES);
                    if (floors == 29) { assertTrue(ceiling <= EscrowLimits.ROOT_BYTES, "R6 alone rejects the next cargo"); }
                    else { assertTrue(previewOnly + EscrowSettlementCapacity.FLOOR_RETIREMENT_BYTES <= EscrowLimits.ROOT_BYTES, "R8 must charge ceilings, not preview bytes"); }
                    assertTrue(coordinator.commitPreparations()); io.tickUntil(coordinator::tick, 1, writer::idle);
                    if (floors == 29) {
                        var intent = floorIntent(coordinator.ledger(), 0); var results = new ArrayList<Boolean>();
                        var terminal = coordinator.ledger();
                        for (int i = 1; i <= 64; i++) {
                            terminal = EscrowFlightAuthority.arm(terminal, EscrowFlightAuthorityTest.launch(terminal, i), 2);
                            var arm = terminal.armedFlights().get(terminal.armedFlights().size() - 1);
                            terminal = EscrowFlightAuthority.finish(terminal, arm, EscrowFlightTables.Outcome.CANCELLED,
                                    arm.source(), arm.epoch(), hash(2), hash(3));
                        }
                        var beforeRetirement = EscrowSettlementCapacity.floorRetirement(terminal, intent, coordinator.nextSequence(), 220);
                        long reservedBefore = EscrowSettlementCapacity.reservedBytes(terminal);
                        for (var arm : List.copyOf(terminal.armedFlights())) { terminal = EscrowFlightAuthority.prune(terminal, arm); }
                        assertEquals(reservedBefore, EscrowSettlementCapacity.reservedBytes(terminal));
                        var afterRetirement = EscrowSettlementCapacity.floorRetirement(terminal, intent, coordinator.nextSequence(), 220);
                        assertEquals(EscrowSettlementCapacity.reservedBytes(beforeRetirement), EscrowSettlementCapacity.reservedBytes(afterRetirement));
                        System.out.println("ITEM_R9 kind=" + kind + " cargo=60 floors=29 terminal_slots=64 before_after=" + reservedBefore
                                + " retirement_before_after=" + EscrowSettlementCapacity.reservedBytes(afterRetirement));
                        coordinator.tick(220); assertTrue(coordinator.beginSettlement(intent, results::add));
                        io.tickUntil(coordinator::tick, 221, writer::idle); assertEquals(List.of(true), results);
                        assertEquals(61, coordinator.ledger().records().size());
                    }
                    System.out.println("ITEM_RESERVATION kind=" + kind + " floors=" + floors + " admitted=" + admitted.size()
                            + " next_ceiling=" + ceiling + " preview_only=" + previewOnly + " headroom=" + EscrowSettlementCapacity.FLOOR_RETIREMENT_BYTES);
                }
            }
        }
    }
    @Test void embeddedO1NodeIntersectionIsMeasuredAtItsExactBoundary() {
        for (var kind : KINDS) {
            int max = 5;
            for (int nodes = 6; nodes < 384; nodes++) {
                try {
                    var op = withNodes(kind, nodes);
                    EscrowSettlementCapacity.preflight(ledger(op), op.id()); max = nodes;
                } catch (IllegalArgumentException refused) { break; }
            }
            assertTrue(max > 5 && max < 375);
            var last = withNodes(kind, max); var next = withNodes(kind, max + 1);
            assertTrue(BoundedNbt.fits(next.payload(), 8_192, 12, 384));
            assertTrue(BoundedNbt.fits(EscrowCodec.operation(next), 12_288, 16, 512));
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(ledger(last), last.id()));
            var root = new EscrowLedger(id(500), 99, 0, List.of(), List.of(), List.of());
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            try (var writer = new EscrowLedgerWriter(temp.resolve(kind + "-node-edge"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
                assertEquals(EscrowCoordinator.Transfer.ESCROW_FULL,
                        coordinator.prepareTransfer(EscrowItemAdmissionTest.capture(next, 4), ignored -> fail()));
                assertEquals(root, coordinator.ledger()); assertEquals(100, coordinator.nextSequence());
                assertEquals(0, coordinator.admissionAuthority().pendingCount()); assertFalse(data.isDirty());
                assertTrue(writer.capturedOperations().isEmpty());
            }
            var repair = last.withReason(EscrowReason.JOURNAL_GUARD_MISMATCH);
            System.out.println("ITEM_NODE_EDGE kind=" + kind + " stack_nodes=" + max);
            report("node_edge_payload", last.payload());
            report("node_edge_O1_operation", EscrowCodec.operation(repair.settle(intent(ledger(repair), repair, 4, 0, false))));
        }
    }
    private static EscrowOperation withNodes(EscrowOperation.Kind kind, int nodes) {
        var original = operation(kind, 100, 99, 4, -1, "minecraft:overworld");
        var payload = new EscrowItemCargoPayload(cursor(), EscrowItemStackCodecTest.nodes(nodes), 0, 0).encode();
        return new EscrowOperation(original.id(), original.sequence(), original.owner(), original.createdTick(), original.phase(), original.reason(),
                payload, io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources.signature(payload),
                original.source(), original.destination(), original.guard(), original.kind(), original.settlement(), original.nativeBinding());
    }
    @Test void simultaneous128CharacterItemIdAnd4096ByteStackFitButLocalDepthAndNodesDoNot() {
        var max = stack(0); max.putString("id", "w110:" + "i".repeat(123));
        max.getCompound("tag").putByteArray("blob", new byte[4_096 - RocketNbtSize.uncompressedBytes(max)]);
        assertEquals(4_096, RocketNbtSize.uncompressedBytes(max)); assertDoesNotThrow(() -> EscrowItemStackCodec.decode(max));
        for (var kind : KINDS) {
            var original = operation(kind, 100, 99, 4, -1, "minecraft:overworld");
            var payload = new EscrowItemCargoPayload(cursor(), max, 0, 0).encode();
            var op = new EscrowOperation(original.id(), original.sequence(), original.owner(), original.createdTick(), original.phase(), original.reason(),
                    payload, io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources.signature(payload),
                    original.source(), original.destination(), original.guard(), original.kind(), original.settlement(), original.nativeBinding());
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(ledger(op), op.id()));
            System.out.println("ITEM_LOCAL_MAX kind=" + kind + " id_chars=128 stack_bytes=4096 count=64 markers=5");
            report("local_max_payload", op.payload());
        }
        for (var maxLocal : List.of(EscrowItemStackCodecTest.depth(16), EscrowItemStackCodecTest.nodes(512))) {
            assertDoesNotThrow(() -> EscrowItemStackCodec.decode(maxLocal));
            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.CARGO_TOO_LARGE,
                    () -> new EscrowItemCargoPayload(cursor(), maxLocal, 0, 0));
        }
    }
    private static long previewBytes(EscrowOperation candidate) {
        // Five retained/candidate markers in the actual observation, with the same identities and payload.
        var op = operation(candidate.kind(), candidate.sequence(), candidate.guard().rocket().getLeastSignificantBits(), 4, -1,
                candidate.rocket().level()).withReason(EscrowReason.JOURNAL_GUARD_MISMATCH);
        var intent = intent(ledger(op), op, 4, 0, false);
        return RocketNbtSize.uncompressedBytes(EscrowCodec.operation(op.settle(intent))) - 3L
                + RocketNbtSize.uncompressedBytes(EscrowReceiptCodec.encode(EscrowSettlementTombstones.create(op, intent))) - 3L;
    }

    @Test void fullFlightTablesKeepTheirFixedReservationWhenTerminalEntriesArePruned() {
        for (var kind : KINDS) {
            var receipts = new ArrayList<CompoundTag>(); var heads = new ArrayList<EscrowFlightTables.Head>(); var arms = new ArrayList<EscrowFlightTables.Armed>();
            for (int i = 1; i <= 64; i++) {
                var op = operation(kind, i, 20_000 + i).withReason(EscrowReason.OTHER_STATE); var local = ledger(op);
                var intent = intent(local, op, 0, 0, false);
                var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(local), intent), intent, 1);
                receipts.add(released.tombstones().get(0)); var old = released.rocketAuthorities().get(0);
                var head = new EscrowFlightTables.Head(old.logical(), old.owner(), old.anchor(), 1, old.physical(), old.epoch(), old.kind(),
                        old.signature(), old.invariant(), i, i, EscrowFlightTables.Outcome.HANDED_OFF); heads.add(head);
                arms.add(new EscrowFlightTables.Armed(id(500), head.logical(), head.owner(), head.physical(), Long.MAX_VALUE, id(3_000 + i), i, 2,
                        hash(i), hash(i), hash(i), hash(i), id(4_000 + i), new EscrowGuard(head.logical(), id(5_000 + i), Long.MAX_VALUE, hash(i)),
                        head.anchor(), 1, EscrowFlightTables.Outcome.CANCELLED));
            }
            var full = new EscrowLedger(id(500), 64, 2, List.of(), List.of(), receipts, 64, heads, arms);
            assertEquals(full, EscrowCodec.decode(EscrowCodec.encode(full))); EscrowSettlementCapacity.preflight(full);
            long reserved = EscrowSettlementCapacity.reservedBytes(full); var pruned = full;
            for (var arm : arms) { pruned = EscrowFlightAuthority.prune(pruned, arm); }
            assertEquals(reserved, EscrowSettlementCapacity.reservedBytes(pruned));
            assertTrue(RocketNbtSize.uncompressedBytes(EscrowCodec.encode(pruned)) < RocketNbtSize.uncompressedBytes(EscrowCodec.encode(full)));
            System.out.println("ITEM_TABLES kind=" + kind + " heads=64 arms=64 reserved_before_after=" + reserved);
            report("full_tables_root", EscrowCodec.encode(full)); report("head", heads.get(0).encode()); report("armed", arms.get(0).encode());
        }
    }
    static void report(String name, CompoundTag tag) {
        var size = BoundedNbt.measure(tag, Integer.MAX_VALUE, 64, Integer.MAX_VALUE);
        assertNotNull(size); assertEquals(RocketNbtSize.uncompressedBytes(tag), size.bytes());
        System.out.println("ITEM_ENCODE " + name + " bytes=" + size.bytes() + " depth=" + depth(tag) + " nodes=" + size.nodes());
    }
    private static int depth(Tag tag) {
        int result = 0;
        if (tag instanceof CompoundTag compound) { for (String key : compound.getAllKeys()) { result = Math.max(result, depth(compound.get(key))); } }
        else if (tag instanceof net.minecraft.nbt.ListTag list) { for (var child : list) { result = Math.max(result, depth(child)); } }
        return result + 1;
    }
}
