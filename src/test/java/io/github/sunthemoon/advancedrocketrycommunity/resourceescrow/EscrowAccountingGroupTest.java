package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Ownership remains provable from receipt-backed rows after all operations retire. */
class EscrowAccountingGroupTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static List<Row> sharedRows(UUID first, UUID second, Knowledge knowledge) {
        var key = Key.derive("minecraft:water", null).value();
        var source = knowledge == Knowledge.UNKNOWN_ENDPOINT ? OptionalLong.empty() : OptionalLong.of(4000);
        return List.of(
                new Row(first, new Baseline(Scope.FUEL, List.of(new Entry(key, 5000, 0, -1000, 1000))),
                        new Frozen(knowledge, first, List.of(first, second), List.of(new FrozenEntry(key, source,
                                OptionalLong.of(1900), knowledge == Knowledge.KNOWN ? OptionalLong.of(100) : OptionalLong.empty(), first)))),
                new Row(second, new Baseline(Scope.FUEL, List.of(new Entry(key, 5000, 1000, -1000, 1000))),
                        new Frozen(knowledge, first, List.of(), List.of(new FrozenEntry(key, source,
                                OptionalLong.of(1900), OptionalLong.empty(), first)))));
    }

    private static List<Row> reassign(List<Row> rows, boolean split) {
        UUID later = rows.get(1).operation();
        return rows.stream().map(row -> {
            UUID owner = split ? row.operation() : later;
            var old = row.frozen();
            return new Row(row.operation(), row.baseline(), new Frozen(old.knowledge(), old.groupOwner(), old.members(),
                    old.entries().stream().map(entry -> new FrozenEntry(entry.key(), entry.source(), entry.destination(),
                            old.knowledge() == Knowledge.KNOWN && owner.equals(row.operation())
                                    ? OptionalLong.of(100) : OptionalLong.empty(), owner)).toList()));
        }).toList();
    }

    @Test void firstEligibleMemberOwnsEachKeyRegardlessOfCallerOrUuidOrder() {
        for (var knowledge : List.of(Knowledge.KNOWN, Knowledge.UNKNOWN_BASELINE, Knowledge.UNKNOWN_ENDPOINT)) {
            var rows = sharedRows(id(5), id(4), knowledge);
            assertEquals(Status.OK, EscrowFluidAccount.verifyGroup(rows));
            assertEquals(Status.OK, EscrowFluidAccount.verifyGroup(List.of(rows.get(1), rows.get(0))));
            assertEquals(knowledge == Knowledge.KNOWN ? 1 : 0, rows.stream().flatMap(row -> row.frozen().entries().stream())
                    .filter(entry -> entry.adjustment().isPresent()).count());
            assertEquals(knowledge == Knowledge.KNOWN ? 100 : 0, rows.stream().flatMap(row -> row.frozen().entries().stream())
                    .mapToLong(entry -> entry.adjustment().orElse(0)).sum());
        }
    }

    @Test void twoIndividuallyValidSelfOwnersAreRefusedAsAGroup() {
        for (var knowledge : List.of(Knowledge.KNOWN, Knowledge.UNKNOWN_BASELINE, Knowledge.UNKNOWN_ENDPOINT)) {
            var split = reassign(sharedRows(id(5), id(4), knowledge), true);
            split.forEach(row -> assertEquals(Status.OK, row.status()));
            assertEquals(Status.INVALID_OWNER, EscrowFluidAccount.verifyGroup(split));
        }
    }

    @Test void consistentReassignmentToALaterEligibleMemberIsRefused() {
        for (var knowledge : List.of(Knowledge.KNOWN, Knowledge.UNKNOWN_BASELINE, Knowledge.UNKNOWN_ENDPOINT)) {
            var later = reassign(sharedRows(id(5), id(4), knowledge), false);
            later.forEach(row -> assertEquals(Status.OK, row.status()));
            assertEquals(Status.INVALID_OWNER, EscrowFluidAccount.verifyGroup(List.of(later.get(1), later.get(0))));
        }
    }

    @Test void receiptBackedGroupsStillRejectSplitAndLaterOwnersAfterOperationsRetire() {
        var rows = sharedRows(id(1), id(2), Knowledge.KNOWN);
        var first = operation(1, 99, 0).withReason(EscrowReason.OTHER_STATE).accounting(rows.get(0).baseline());
        var second = operation(2, 99, 1).accounting(rows.get(1).baseline());
        var root = ledger(operation(1, 99, 0), operation(2, 99, 1)).accounting(List.of(first, second), rows.stream()
                .map(row -> new Row(row.operation(), row.baseline(), null)).toList());
        var intent = EscrowAccountingFormatTest.intent(root, rows);
        var o1 = intent.freeze(root, rows);
        var o4 = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(o1, intent), intent);
        assertTrue(o4.records().isEmpty()); assertEquals(2, o4.tombstones().size());
        assertEquals(o4, EscrowCodec.decode(EscrowCodec.encode(o4)));
        for (boolean split : List.of(true, false)) {
            var malformed = reassign(rows, split);
            var encoded = EscrowCodec.encode(o4);
            encoded.put("fluid_accounts", EscrowFluidAccountCodec.encodeRows(malformed, EscrowFluidAccountCodec.SAVED).value());
            var receipts = new ListTag();
            for (var old : o4.tombstones()) {
                var receipt = old.copy();
                var row = malformed.stream().filter(r -> r.operation().equals(receipt.getUUID("op_id"))).findFirst().orElseThrow();
                receipt.putString("accounting_digest", EscrowFluidAccountCodec.digest(row, EscrowFluidAccountCodec.SAVED).value());
                var wire = EscrowReceiptCodec.encode(receipt);
                assertEquals(receipt, EscrowReceiptCodec.decode(wire));
                receipts.add(wire);
            }
            encoded.put("tombstones", receipts);
            var refused = assertThrows(IllegalArgumentException.class, () -> EscrowCodec.decode(encoded));
            assertEquals("Invalid account group", refused.getMessage(), "Digests match; ownership itself must be rejected");
        }
    }
}
