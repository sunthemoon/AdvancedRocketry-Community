package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowAccountingDecodeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static void roundTrip(EscrowLedger root) {
        assertDoesNotThrow(() -> EscrowSettlementCapacity.requireReservations(root));
        assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
    }

    @Test void decodeChecksReservationsWithoutConstructingHypotheticalOperatorChoices() {
        var op = EscrowCargoFixtures.operation(EscrowOperation.Kind.FLUID_CARGO_LOAD, 1, 99, 0, 7_500,
                "a:" + "b".repeat(121));
        var root = EscrowAccountingFormatTest.planned(op);
        // The current saved form and fixed reservations fit; a hypothetical embedded O1 does not.
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(root));
        roundTrip(root);
        assertFalse(EscrowAccountingFormat.ENABLED);
        assertThrows(IllegalArgumentException.class, () -> EscrowLedgerWriter.Snapshot.of(root, Set.of()));
    }

    @Test void reasonL2FloorGrowthO1O3O4AndFloorCollectionRemainDecodable() {
        var original = operation(1); var guard = new EscrowGuard(original.guard().rocket(), id(900), 1, hash(400));
        var named = new EscrowOperation(original.id(), 1, original.owner(), 1, original.phase(), original.reason(),
                original.payload(), original.payloadHash(), original.source(), original.destination(), guard);
        var l1 = EscrowAccountingFormatTest.planned(named);
        EscrowSettlementCapacity.preflight(l1, named.id()); roundTrip(l1);
        var repair = l1.replace(List.of(l1.records().get(0).withReason(EscrowReason.OTHER_STATE)), l1.floors(), l1.tombstones());
        roundTrip(repair);
        var rows = List.of(EscrowAccountingFormatTest.frozen(repair.records().get(0), Knowledge.KNOWN));
        var intent = EscrowAccountingFormatTest.intent(repair, rows);
        var o1 = intent.freeze(repair, rows); var o3 = EscrowSettlementTombstones.install(o1, intent);
        var o4 = EscrowSettlementTombstones.release(o3, intent);
        for (var root : List.of(o1, o3, o4)) { roundTrip(root); assertEquals(rows, root.fluidAccounts()); }
        var endpoint = named.rocket();
        var l2 = EscrowFloors.retire(l1, Set.of(named.id()), Map.of(endpoint.key(),
                new EscrowPrefix.State(l1.id(), endpoint.afterEpoch(), endpoint.afterSignature(), List.of())));
        roundTrip(l2); assertTrue(l2.fluidAccounts().isEmpty()); assertEquals(1, l2.floors().size());
        assertTrue(EscrowSettlementCapacity.reservedBytes(l2) <= EscrowSettlementCapacity.reservedBytes(l1));
        var collected = EscrowFloors.collect(l2, List.of(), ignored -> true);
        roundTrip(collected); assertTrue(collected.floors().isEmpty());
    }

    private static EscrowLedger operations() {
        var ops = java.util.stream.LongStream.rangeClosed(1, 55).mapToObj(i ->
                EscrowCargoFixtures.operation(EscrowOperation.Kind.FLUID_CARGO_LOAD, i, 40_000 + i)).toArray(EscrowOperation[]::new);
        return EscrowAccountingFormatTest.planned(ops);
    }

    private static EscrowLedger receiptsAndFloors() {
        var receipts = new ArrayList<CompoundTag>(); var rows = new ArrayList<Row>();
        for (int i = 1; i <= 64; i++) {
            var root = EscrowAccountingFormatTest.planned(operation(i).withReason(EscrowReason.OTHER_STATE));
            var frozen = List.of(EscrowAccountingFormatTest.frozen(root.records().get(0), Knowledge.KNOWN));
            var intent = EscrowAccountingFormatTest.intent(root, frozen);
            var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(root, frozen), intent), intent);
            receipts.addAll(released.tombstones()); rows.addAll(frozen);
        }
        var floors = EscrowCombinedReservationTest.retainedFloors(256);
        return new EscrowLedger(id(500), Math.max(64, floors.issuedThrough()), 0, List.of(), floors.floors(), receipts,
                0, List.of(), List.of(), 3, rows);
    }

    @Test void acceptedCountLimitsRoundTripAndArithmeticExhaustionStillRefusesDecode() {
        var ops = operations(); assertEquals(55, ops.records().size());
        EscrowSettlementCapacity.preflight(ops, ops.records().get(54).id()); roundTrip(ops);
        var terminal = receiptsAndFloors();
        assertEquals(64, terminal.tombstones().size()); assertEquals(64, terminal.fluidAccounts().size());
        assertEquals(256, terminal.floors().size()); roundTrip(terminal);
        // Count limits are separate: 55 pending plus 64 distinct permanent receipts cannot be admitted together.
        var extra = operation(65); var base = EscrowAccountingFormatTest.baseline(extra); extra = extra.accounting(base);
        var rows = new ArrayList<>(terminal.fluidAccounts()); rows.add(new Row(extra.id(), base, null));
        var exhausted = new EscrowLedger(terminal.id(), Math.max(65, terminal.issuedThrough()), 0, List.of(extra),
                terminal.floors(), terminal.tombstones(), 0, List.of(), List.of(), 3, rows);
        assertThrows(EscrowSettlementCapacity.ReservationFull.class, () -> EscrowCodec.decode(EscrowCodec.encode(exhausted)));
        var byteFull = EscrowFlightBenchmarkCeiling.create(true);
        assertThrows(EscrowSettlementCapacity.ReservationFull.class, () -> EscrowCodec.decode(EscrowCodec.encode(byteFull)));
    }

    @Test void measureDecodeAndFullPreviewAtTheAcceptedLimits() {
        for (var root : List.of(operations(), receiptsAndFloors())) {
            var encoded = EscrowCodec.encode(root);
            for (int warmup = 0; warmup < 2; warmup++) { EscrowCodec.decode(encoded); EscrowSettlementCapacity.preflight(root); }
            int samples = 5; long start = System.nanoTime();
            for (int i = 0; i < samples; i++) { assertEquals(root, EscrowCodec.decode(encoded)); }
            long decodeNanos = System.nanoTime() - start; start = System.nanoTime();
            for (int i = 0; i < samples; i++) { EscrowSettlementCapacity.preflight(root); }
            long previewNanos = System.nanoTime() - start;
            System.out.println("W362_R446_DECODE operations=" + root.records().size() + " receipts=" + root.tombstones().size()
                    + " floors=" + root.floors().size() + " samples=" + samples + " decode_mean_ms="
                    + decodeNanos / (samples * 1_000_000.0) + " full_preflight_mean_ms=" + previewNanos / (samples * 1_000_000.0));
        }
    }
}
