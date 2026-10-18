package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoFixtures.*;
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

class EscrowCargoCapacityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;

    @Test void realEncodersMeasureBothDirectionsFiveMarkersWorstActorReasonAndNestedPayloadLimits() {
        for (var kind : KINDS) {
            int longest = 0;
            for (int length = 3; length <= 128; length++) {
                try { operation(kind, 100, 99, 4, -1, "a:" + "b".repeat(length - 2)); longest = length; }
                catch (IllegalArgumentException refused) { break; }
            }
            assertTrue(longest > 0 && longest < 128, "The reference envelope, not the individual ID limit, decides eligibility");
            final String level = "a:" + "b".repeat(longest - 2);
            assertThrows(IllegalArgumentException.class, () -> operation(kind, 100, 99, 4, -1, "a:" + "b".repeat(126)));
            for (int retained = 0; retained <= 4; retained++) {
                int low = 0, high = 8_192;
                while (low < high) {
                    int mid = (low + high + 1) / 2;
                    if (fits(kind, retained, mid, level)) { low = mid; } else { high = mid - 1; }
                }
                assertTrue(low > 0); assertFalse(fits(kind, retained, low + 1, level));
                var op = operation(kind, 100, 99, retained, low, level).withReason(EscrowReason.JOURNAL_GUARD_MISMATCH);
                var root = ledger(op);
                System.out.println("CARGO_SIZE kind=" + kind + " retained=" + retained + " total_markers=" + (retained + 1)
                        + " level_chars=" + longest + " max_blob=" + low + " five_marker_preview=true");
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
                // At least the next rejected candidate must reach the complete-operation check, not a raised payload bound.
                final int nextBlob = low + 1; final int retainedCount = retained;
                var next = operation(kind, 100, 99, retainedCount, nextBlob, level);
                assertTrue(BoundedNbt.fits(next.payload(), 8_192, 12, 384));
                var empty = new EscrowLedger(id(500), 99, 0, List.of(), List.of(), List.of());
                var data = ResourceEscrowSavedData.load(EscrowCodec.encode(empty));
                try (var writer = new EscrowLedgerWriter(temp.resolve(kind + "-" + retained), 0); var coordinator = new EscrowCoordinator(data, writer)) {
                    assertEquals(EscrowCoordinator.Fill.ESCROW_FULL, coordinator.prepareTransfer(EscrowCargoAdmissionTest.capture(next, retained), ignored -> fail()));
                    assertEquals(100, coordinator.nextSequence()); assertFalse(data.isDirty()); assertEquals(empty, coordinator.ledger());
                    assertEquals(0, coordinator.admissionAuthority().pendingCount()); assertTrue(writer.capturedOperations().isEmpty());
                }
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
                        if (result == EscrowCoordinator.Fill.QUEUED) { admitted.add(op); }
                        else { assertEquals(EscrowCoordinator.Fill.ESCROW_FULL, result); refused = op; break; }
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
                        System.out.println("CARGO_R9 kind=" + kind + " cargo=60 floors=29 terminal_slots=64 before_after=" + reservedBefore
                                + " retirement_before_after=" + EscrowSettlementCapacity.reservedBytes(afterRetirement));
                        coordinator.tick(220); assertTrue(coordinator.beginSettlement(intent, results::add));
                        io.tickUntil(coordinator::tick, 221, writer::idle); assertEquals(List.of(true), results);
                        assertEquals(61, coordinator.ledger().records().size());
                    }
                    System.out.println("CARGO_RESERVATION kind=" + kind + " floors=" + floors + " admitted=" + admitted.size()
                            + " next_ceiling=" + ceiling + " preview_only=" + previewOnly + " headroom=" + EscrowSettlementCapacity.FLOOR_RETIREMENT_BYTES);
                }
            }
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
            System.out.println("CARGO_TABLES kind=" + kind + " heads=64 arms=64 reserved_before_after=" + reserved);
            report("full_tables_root", EscrowCodec.encode(full)); report("head", heads.get(0).encode()); report("armed", arms.get(0).encode());
        }
    }
    static void report(String name, CompoundTag tag) {
        var size = BoundedNbt.measure(tag, Integer.MAX_VALUE, 64, Integer.MAX_VALUE);
        assertNotNull(size); assertEquals(RocketNbtSize.uncompressedBytes(tag), size.bytes());
        System.out.println("CARGO_ENCODE " + name + " bytes=" + size.bytes() + " depth=" + depth(tag) + " nodes=" + size.nodes());
    }
    private static int depth(Tag tag) {
        int result = 0;
        if (tag instanceof CompoundTag compound) { for (String key : compound.getAllKeys()) { result = Math.max(result, depth(compound.get(key))); } }
        else if (tag instanceof net.minecraft.nbt.ListTag list) { for (var child : list) { result = Math.max(result, depth(child)); } }
        return result + 1;
    }
}
