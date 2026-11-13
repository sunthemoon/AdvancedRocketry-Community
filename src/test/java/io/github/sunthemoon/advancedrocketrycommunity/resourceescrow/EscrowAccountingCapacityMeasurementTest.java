package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Measurements only: unaccepted capacity numbers are printed, not substituted for historical pins. */
class EscrowAccountingCapacityMeasurementTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static int edge(IntPredicate fits, int high) {
        int low = 0;
        while (low < high) { int mid = (low + high + 1) >>> 1; if (fits.test(mid)) { low = mid; } else { high = mid - 1; } }
        assertTrue(low > 0); assertTrue(fits.test(low)); assertFalse(fits.test(low + 1)); return low;
    }
    private static EscrowLedger account(EscrowLedger root) {
        var ops = root.records().stream().map(op -> op.accounting(EscrowAccountingFormatTest.baseline(op))).toList();
        return root.accounting(ops, ops.stream().map(op -> new Row(op.id(), EscrowAccountingFormatTest.baseline(op), null)).toList());
    }
    private static Row frozen(EscrowOperation op, int missing) {
        var base = EscrowAccountingFormatTest.baseline(op);
        boolean sourceMissing = missing == (op.source().equals(op.rocket()) ? 1 : 2);
        boolean destinationMissing = missing == (op.destination().equals(op.rocket()) ? 1 : 2);
        return new Row(op.id(), base, new Frozen(missing == 0 ? Knowledge.KNOWN : Knowledge.UNKNOWN_ENDPOINT, op.id(), List.of(op.id()),
                List.of(new FrozenEntry(base.moved(), sourceMissing ? OptionalLong.empty() : OptionalLong.of(4000),
                        destinationMissing ? OptionalLong.empty() : OptionalLong.of(1000), missing == 0 ? OptionalLong.of(0) : OptionalLong.empty(), op.id()))));
    }
    private static boolean cargoFits(EscrowOperation op, int retained, boolean accounting) {
        try {
            var root = ledger(op); if (accounting) { root = account(root); op = root.records().get(0); }
            EscrowSettlementCapacity.preflight(root, op.id());
            var repair = op.withReason(EscrowReason.JOURNAL_GUARD_MISMATCH); root = root.replace(List.of(repair), root.floors(), root.tombstones());
            for (int missing = 0; missing <= 2; missing++) {
                var intent = EscrowCargoFixtures.intent(root, repair, retained, missing, false);
                if (accounting) {
                    var rows = List.of(frozen(repair, missing)); intent = intent.accounting(rows); intent.freeze(root, rows);
                } else { intent.freeze(root); }
            }
            return true;
        } catch (IllegalArgumentException refused) { return false; }
    }
    @Test void measureCargoBlobAnd512NodeEdgesWithoutRepinning() {
        for (var kind : EscrowCargoFixtures.KINDS) {
            for (int retained = 0; retained <= 4; retained++) {
                int count = retained;
                for (boolean accounting : List.of(false, true)) {
                    int bytes = edge(blob -> cargoFits(EscrowCargoFixtures.operation(kind, 100, 99, count, blob,
                            "a:" + "b".repeat(121)), count, accounting), 8000);
                    System.out.println("W362_MEASURE cargo kind=" + kind + " retained=" + retained + " schema=" + (accounting ? 3 : 2) + " blob=" + bytes);
                }
            }
            for (boolean accounting : List.of(false, true)) {
                int entries = edge(nodes -> {
                    try {
                        var original = EscrowCargoFixtures.operation(kind, 1, 99); var payload = original.payload();
                        var tag = new net.minecraft.nbt.CompoundTag(); for (int i = 0; i < nodes; i++) { tag.putByte("n" + i, (byte) 1); }
                        payload.getCompound("fluid").put("Tag", tag);
                        var op = new EscrowOperation(original.id(), 1, original.owner(), 1, original.phase(), original.reason(), payload,
                                RocketNativeResources.signature(payload), original.source(), original.destination(), original.guard(), original.kind(),
                                original.settlement(), original.nativeBinding());
                        return cargoFits(op, 0, accounting);
                    } catch (IllegalArgumentException refused) { return false; }
                }, 370);
                System.out.println("W362_MEASURE nodes kind=" + kind + " schema=" + (accounting ? 3 : 2) + " tag_entries=" + entries + " ceiling=512");
            }
        }
    }
    private static EscrowLedger mixed(int blob, boolean accounting) {
        var cargo = EscrowCargoFixtures.guarded(EscrowCargoFixtures.operation(EscrowOperation.Kind.FLUID_CARGO_UNLOAD,
                101, 99, 0, blob, "advancedrocketrycommunity:tau_ceti_g"));
        var fuel = EscrowReceiptChargeTest.fill(100, 98, 4, 4, "advancedrocketrycommunity:tau_ceti_g", true, -1); var s = fuel.source();
        var alias = new EscrowEndpoint(s.kind(), s.level(), cargo.rocket().identity(), s.logicalRocket(), s.type(), s.x(), s.y(), s.z(),
                s.unit(), s.beforeEpoch(), s.beforeSignature(), s.afterSignature());
        fuel = nativeModel(new EscrowOperation(fuel.id(), fuel.sequence(), fuel.owner(), fuel.createdTick(), fuel.phase(), fuel.reason(),
                fuel.payload(), fuel.payloadHash(), alias, fuel.destination(), fuel.guard(), fuel.kind(), fuel.settlement()), EscrowCargoFixtures.wrapper(4));
        var root = ledger(fuel, cargo); return accounting ? account(root) : root;
    }
    @Test void measureMixedComponentBlobWithoutRepinning() {
        for (boolean accounting : List.of(false, true)) {
            int blob = edge(bytes -> {
                try { var root = mixed(bytes, accounting); EscrowSettlementCapacity.preflight(root, root.records().get(1).id()); return true; }
                catch (IllegalArgumentException refused) { return false; }
            }, 8000);
            System.out.println("W362_MEASURE mixed schema=" + (accounting ? 3 : 2) + " blob=" + blob);
        }
    }
    @Test void measureLoaderFloorCountsAndReservationsWithBothCodecs() {
        for (int floors : List.of(255, 256)) {
            var base = EscrowCombinedReservationTest.retainedFloors(floors);
            var original = operation(floors + 1, 999, 0);
            var guard = new EscrowGuard(original.guard().rocket(), id(50_000), 1, hash(400));
            var op = new EscrowOperation(original.id(), original.sequence(), original.owner(), original.createdTick(), original.phase(), original.reason(),
                    original.payload(), original.payloadHash(), original.source(), original.destination(), guard);
            for (boolean accounting : List.of(false, true)) {
                var root = base.append(List.of(op)); if (accounting) { root = account(root); }
                var usage = EscrowFloors.usage(root, List.of());
                System.out.println("W362_MEASURE loader_floors schema=" + (accounting ? 3 : 2) + " seeded=" + floors + " used=" + usage.used()
                        + " reserved=" + usage.reserved() + " total=" + usage.total() + " reserved_bytes=" + EscrowSettlementCapacity.reservedBytes(root));
                var candidate = root;
                if (floors == 255) { assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(candidate, op.id())); }
                else { assertThrows(EscrowSettlementCapacity.ReservationFull.class, () -> EscrowSettlementCapacity.preflight(candidate, op.id())); }
            }
        }
        for (boolean accounting : List.of(false, true)) {
            var records = java.util.stream.LongStream.rangeClosed(1, 6).mapToObj(i -> operation(i, 99, i - 1)).toList();
            var root = ledger(records.toArray(EscrowOperation[]::new)); if (accounting) { root = account(root); }
            var candidate = root;
            assertThrows(EscrowSettlementCapacity.MarkerLimit.class,
                    () -> EscrowSettlementCapacity.requireAdmission(candidate, candidate.records().get(5), null));
            System.out.println("W362_MEASURE loader_marker schema=" + (accounting ? 3 : 2) + " admitted=5 refused=6");
        }
    }
}
