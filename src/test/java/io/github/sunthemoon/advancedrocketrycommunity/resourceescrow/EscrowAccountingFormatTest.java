package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowAccountingFormatTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static Baseline baseline(EscrowOperation op) {
        var fluid = op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? op.payload() : op.payload().getCompound("fluid");
        return new Baseline(op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? Scope.FUEL : Scope.CARGO,
                List.of(new Entry(EscrowFluidAccountCodec.key(fluid).value(), 5000, 0, -1000, 1000)));
    }
    static Row frozen(EscrowOperation op, Knowledge knowledge) {
        var base = baseline(op);
        return new Row(op.id(), base, new Frozen(knowledge, op.id(), List.of(op.id()),
                List.of(new FrozenEntry(base.moved(), OptionalLong.of(4000), OptionalLong.of(1000),
                        knowledge == Knowledge.KNOWN ? OptionalLong.of(0) : OptionalLong.empty(), op.id()))));
    }
    static EscrowLedger planned(EscrowOperation... old) {
        var ops = List.of(old).stream().map(op -> op.accounting(baseline(op))).toList();
        return ledger(old).accounting(ops, ops.stream().map(op -> new Row(op.id(), baseline(op), null)).toList());
    }
    static EscrowSettlementIntent intent(EscrowLedger root, List<Row> rows) {
        var ends = new java.util.LinkedHashMap<EscrowEndpoint.Key, EscrowEndpoint>();
        root.records().forEach(op -> { ends.put(op.source().key(), op.source()); ends.put(op.destination().key(), op.destination()); });
        var observed = ends.values().stream().map(end -> new EscrowSettlementIntent.Observation(end,
                new EscrowPrefix.State(root.id(), end.afterEpoch(), end.afterSignature(), List.of()), true,
                EscrowSignatureKind.settlement(end) == EscrowSignatureKind.ITEM_BUFFER_V1 ? 0 : 1000)).toList();
        var preview = EscrowSettlementIntent.preview(root, root.records().get(0).id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                id(700), "operator", 10, observed);
        if (!rows.isEmpty()) { preview = preview.accounting(rows); }
        return preview.confirm(preview.actor(), preview.digest(), 11, observed);
    }
    @Test void activationIsOffAndWriterCannotPublishTheNewForm() {
        assertFalse(EscrowAccountingFormat.ENABLED);
        var old = ledger(operation(1)); assertEquals(2, EscrowCodec.encode(old).getInt("schema_version"));
        assertEquals(old, EscrowLedgerWriter.Snapshot.of(old, java.util.Set.of()).ledger());
        assertThrows(IllegalArgumentException.class, () -> EscrowLedgerWriter.Snapshot.of(planned(operation(1)), java.util.Set.of()));
    }
    @Test void explicitRoot3RoundTripsPlannedO1O3AndO4() {
        var root = planned(operation(1).withReason(EscrowReason.OTHER_STATE));
        assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
        var rows = List.of(frozen(root.records().get(0), Knowledge.KNOWN)); var intent = intent(root, rows);
        assertEquals(3, intent.encode().getInt("schema_version")); assertEquals(intent, EscrowSettlementIntent.decode(intent.encode()));
        var o1 = intent.freeze(root, rows); var o3 = EscrowSettlementTombstones.install(o1, intent);
        var o4 = EscrowSettlementTombstones.release(o3, intent);
        for (var stage : List.of(o1, o3, o4)) { assertEquals(stage, EscrowCodec.decode(EscrowCodec.encode(stage))); }
        assertEquals(rows, o4.fluidAccounts()); assertEquals(4, o4.tombstones().get(0).getInt("schema_version"));
        var wire = EscrowReceiptCodec.encode(o4.tombstones().get(0)); assertEquals(32, wire.getByteArray("f1b").length);
        assertEquals("KNOWN", wire.getString("f7"));
        assertEquals(rows, o4.generation(9).fluidAccounts());
    }
    @Test void absentEmptyAndCanonicalTagIdentityAreStableAndAmountFree() {
        var fluid = operation(1).payload(); var absent = EscrowFluidAccountCodec.key(fluid).value();
        assertEquals("3ff4379359c41c1a93f27c5778afb851847878b8cc698b8fdb376e1494c8b4bb", absent.tagDigest());
        fluid.put("Tag", new CompoundTag()); var empty = EscrowFluidAccountCodec.key(fluid).value(); assertNotEquals(absent, empty);
        fluid.putInt("Amount", 1); assertEquals(empty, EscrowFluidAccountCodec.key(fluid).value());
        var a = new CompoundTag(); a.putInt("b", 2); a.putInt("a", 1); fluid.put("Tag", a);
        var first = EscrowFluidAccountCodec.key(fluid).value();
        var b = new CompoundTag(); b.putInt("a", 1); b.putInt("b", 2); fluid.put("Tag", b);
        assertEquals(first, EscrowFluidAccountCodec.key(fluid).value());
    }
    @Test void rowsUseTheAcceptedBoundsAndRefuseTheFifteenthMember() {
        assertEquals(new EscrowFluidAccountCodec.Limits(2048, 5, 65, 14), EscrowFluidAccountCodec.SAVED);
        assertTrue(EscrowFluidAccountCodec.encode(EscrowFluidAccountSizeTest.worstRow(14), EscrowFluidAccountCodec.SAVED).ok());
        assertFalse(EscrowFluidAccountCodec.encode(EscrowFluidAccountSizeTest.worstRow(15), EscrowFluidAccountCodec.SAVED).ok());
    }
    @Test void missingOrOrphanRowsAndWrongDigestsAreRefused() {
        var root = planned(operation(1)); var encoded = EscrowCodec.encode(root);
        var noList = encoded.copy(); noList.remove("fluid_accounts"); assertThrows(IllegalArgumentException.class, () -> EscrowCodec.decode(noList));
        var missing = encoded.copy(); missing.put("fluid_accounts", new net.minecraft.nbt.ListTag());
        assertThrows(IllegalArgumentException.class, () -> EscrowCodec.decode(missing));
        var wrong = encoded.copy(); wrong.getList("records", 10).getCompound(0).putByteArray("accounting_baseline", new byte[32]);
        assertThrows(IllegalArgumentException.class, () -> EscrowCodec.decode(wrong));
        assertThrows(IllegalArgumentException.class, () -> ledger(operation(1)).accounting(List.of(operation(1)), root.fluidAccounts()));
        assertThrows(IllegalArgumentException.class, () -> root.accounting(root.records(), List.of(root.fluidAccounts().get(0), root.fluidAccounts().get(0))));
        var malformed = encoded.copy(); malformed.put("fluid_accounts", net.minecraft.nbt.StringTag.valueOf("not a list"));
        assertThrows(IllegalArgumentException.class, () -> EscrowCodec.decode(malformed));
    }
    @Test void intentCannotOmitOrChangeTheAccountCommitment() {
        var root = planned(operation(1).withReason(EscrowReason.OTHER_STATE)); var rows = List.of(frozen(root.records().get(0), Knowledge.KNOWN));
        var plain = intent(root, List.of()); assertThrows(IllegalArgumentException.class, () -> plain.freeze(root, rows));
        var intent = intent(root, rows); var invalid = intent.encode(); invalid.remove("account_commitment");
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.decode(invalid));
        assertThrows(IllegalArgumentException.class, () -> intent.accounting(List.of()));
        var changed = intent.encode(); changed.putByteArray("account_commitment", new byte[32]);
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.decode(changed));
    }
    @Test void commitmentMustBePreviewedAndCannotChangeAfterConfirmation() {
        var root = planned(operation(1).withReason(EscrowReason.OTHER_STATE));
        var rows = List.of(frozen(root.records().get(0), Knowledge.KNOWN));
        var confirmed = intent(root, rows); var before = confirmed.encode();
        assertThrows(IllegalArgumentException.class, () -> confirmed.accounting(rows));
        assertEquals(before, confirmed.encode());
        var preview = EscrowSettlementIntent.preview(root, root.records().get(0).id(), confirmed.choice(), confirmed.actor(),
                confirmed.actorName(), confirmed.previewTick(), confirmed.observations());
        assertThrows(IllegalArgumentException.class, () -> preview.accounting(List.of()));
        var accounted = preview.accounting(rows);
        assertNotEquals(preview.digest(), accounted.digest()); assertEquals(-1, accounted.confirmedTick());
        assertThrows(IllegalArgumentException.class, () -> accounted.confirm(accounted.actor(), preview.digest(), 11, accounted.observations()));
        assertEquals(accounted.digest(), accounted.confirm(accounted.actor(), accounted.digest(), 11, accounted.observations()).digest());
    }
    @Test void rowlessItemComponentInRoot3KeepsItsSchema2IntentAndUnknownSchema3Receipt() {
        var op = EscrowItemFixtures.operation(EscrowOperation.Kind.ITEM_CARGO_LOAD, 1, 99).withReason(EscrowReason.JOURNAL_GUARD_MISMATCH);
        var root = ledger(op).accounting(List.of(op), List.of()); var intent = intent(root, List.of());
        assertEquals(2, intent.encode().getInt("schema_version")); assertNull(intent.accountCommitment());
        var o1 = intent.freeze(root); var o3 = EscrowSettlementTombstones.install(o1, intent); var o4 = EscrowSettlementTombstones.release(o3, intent);
        for (var stage : List.of(o1, o3, o4)) {
            assertEquals(3, stage.schema()); assertTrue(stage.fluidAccounts().isEmpty());
            assertEquals(stage, EscrowCodec.decode(EscrowCodec.encode(stage)));
        }
        assertEquals(3, o4.tombstones().get(0).getInt("schema_version"));
        assertEquals("UNKNOWN", o4.tombstones().get(0).getString("accounting_status"));
    }
    @Test void mixedItemAndLegacyFluidComponentsSelectTheirOwnReceiptVersions() {
        for (boolean item : List.of(true, false)) {
            var other = item ? EscrowItemFixtures.operation(EscrowOperation.Kind.ITEM_CARGO_UNLOAD, 2, 99, 0, -1, "minecraft:overworld")
                    : EscrowCargoFixtures.operation(EscrowOperation.Kind.FLUID_CARGO_UNLOAD, 2, 99);
            var original = operation(1).withReason(EscrowReason.OTHER_STATE); var s = original.source();
            var source = new EscrowEndpoint(s.kind(), s.level(), other.rocket().identity(), s.logicalRocket(), s.type(), s.x(), s.y(), s.z(),
                    s.unit(), s.beforeEpoch(), s.beforeSignature(), s.afterSignature());
            var fuel = new EscrowOperation(original.id(), 1, original.owner(), 1, original.phase(), original.reason(), original.payload(), original.payloadHash(),
                    source, original.destination(), original.guard()).accounting(baseline(original));
            var base = ledger(original, other).accounting(List.of(fuel, other), List.of(new Row(fuel.id(), baseline(fuel), null)));
            var rows = List.of(frozen(fuel, item ? Knowledge.KNOWN : Knowledge.UNKNOWN_BASELINE));
            var intent = intent(base, rows); var o1 = intent.freeze(base, rows);
            var o3 = EscrowSettlementTombstones.install(o1, intent); assertEquals(o3, EscrowCodec.decode(EscrowCodec.encode(o3)));
            assertEquals(3, intent.encode().getInt("schema_version"));
            var receipt = o3.tombstones().get(0); assertEquals(4, receipt.getInt("schema_version"));
            assertEquals(item ? "KNOWN" : "UNKNOWN", receipt.getString("accounting_status"));
            assertEquals(3, o3.tombstones().get(1).getInt("schema_version"));
            assertEquals(EscrowSettlementTombstones.create(other, intent), o3.tombstones().get(1));
            var wrong = new ArrayList<>(rows); wrong.add(new Row(other.id(), baseline(fuel), null));
            assertThrows(IllegalArgumentException.class, () -> base.accounting(base.records(), wrong));
            if (!item) {
                var known = List.of(frozen(fuel, Knowledge.KNOWN)); var falseIntent = intent(base, known);
                assertThrows(IllegalArgumentException.class, () -> falseIntent.freeze(base, known));
            }
        }
    }
    @Test void allReceiptLockAndReleasePredicatesAgreeAcrossBothSchemas() {
        var root = planned(operation(1).withReason(EscrowReason.OTHER_STATE)); var op = root.records().get(0);
        var rows = List.of(frozen(op, Knowledge.KNOWN)); var intent = intent(root, rows);
        var newReceipt = EscrowSettlementTombstones.create(op, intent, rows.get(0));
        for (boolean released : List.of(false, true)) {
            var next = newReceipt.copy(); next.putBoolean("released", released); next.putLong("release_generation", released ? 2 : 0);
            var old = next.copy(); old.putInt("schema_version", 3); old.remove("accounting_digest"); old.putString("accounting_status", "UNKNOWN");
            var a = new EscrowLedger(root.id(), 1, 1, List.of(), List.of(), List.of(old));
            var b = new EscrowLedger(root.id(), 1, 1, List.of(), List.of(), List.of(next), 0, List.of(), List.of(), 3, rows);
            assertEquals(EscrowSettlementTombstones.installed(a, intent), EscrowSettlementTombstones.installed(b, intent));
            assertEquals(EscrowSettlementTombstones.logicalLocked(a, op.guard().rocket()), EscrowSettlementTombstones.logicalLocked(b, op.guard().rocket()));
            assertEquals(EscrowSettlementTombstones.history(a, op.guard().rocket()), EscrowSettlementTombstones.history(b, op.guard().rocket()));
            for (var identity : List.of(op.rocket().identity(), id(9999))) {
                assertEquals(EscrowSettlementTombstones.identityLocked(a, op.guard().rocket(), identity), EscrowSettlementTombstones.identityLocked(b, op.guard().rocket(), identity));
                assertEquals(EscrowSettlementTombstones.minimumEpoch(a, op.guard().rocket(), identity), EscrowSettlementTombstones.minimumEpoch(b, op.guard().rocket(), identity));
                assertEquals(EscrowSettlementTombstones.stateLocked(a, op.guard().rocket(), identity, RocketEscrowSection.neutral(), hash(77), 0),
                        EscrowSettlementTombstones.stateLocked(b, op.guard().rocket(), identity, RocketEscrowSection.neutral(), hash(77), 0));
            }
            for (var endpoint : List.of(op.source(), op.destination())) {
                assertEquals(EscrowSettlementTombstones.endpointLocked(a, endpoint.key()), EscrowSettlementTombstones.endpointLocked(b, endpoint.key()));
            }
            assertEquals(EscrowSettlementTombstones.retiredIds(a.tombstones()), EscrowSettlementTombstones.retiredIds(b.tombstones()));
            assertEquals(EscrowSettlementTombstones.retiredTransfer(a, op.guard().rocket(), id(42)), EscrowSettlementTombstones.retiredTransfer(b, op.guard().rocket(), id(42)));
        }
        // Other schemas cannot reach lock predicates: the ledger's receipt boundary rejects them first.
        for (int schema : List.of(0, 1, 2, 5, Integer.MAX_VALUE)) {
            var invalid = newReceipt.copy(); invalid.putInt("schema_version", schema);
            assertFalse(EscrowSettlementTombstones.settlementReceipt(invalid));
            assertThrows(IllegalArgumentException.class, () -> new EscrowLedger(root.id(), 1, 1, List.of(), List.of(),
                    List.of(invalid), 0, List.of(), List.of(), 3, rows));
        }
    }
    @Test void reservationCountsAnO3AccountOnceAtItsFullCeiling() {
        var root = planned(operation(1).withReason(EscrowReason.OTHER_STATE)); var rows = List.of(frozen(root.records().get(0), Knowledge.KNOWN));
        var intent = intent(root, rows); var o1 = intent.freeze(root, rows); var o3 = EscrowSettlementTombstones.install(o1, intent);
        assertEquals(EscrowSettlementCapacity.reservedBytes(o1), EscrowSettlementCapacity.reservedBytes(o3));
        var old = ledger(operation(1));
        assertEquals(39 + 2048, EscrowSettlementCapacity.reservedBytes(root) - EscrowSettlementCapacity.reservedBytes(old));
    }
    @Test void legacyAdmissionRefusalLeavesTheProspectiveRootUnchanged() {
        var first = operation(1, 99, 0); var next = operation(2, 99, 1).accounting(baseline(operation(2, 99, 1)));
        var root = ledger(first, operation(2, 99, 1)).accounting(List.of(first, next), List.of(new Row(next.id(), baseline(next), null)));
        var encoded = EscrowCodec.encode(root);
        assertThrows(EscrowSettlementCapacity.StaleCapture.class, () -> EscrowSettlementCapacity.preflight(root, next.id()));
        assertEquals(encoded, EscrowCodec.encode(root)); assertEquals(0, root.writerGeneration()); assertEquals(2, root.issuedThrough());
    }
    @Test void invalidBaselineCannotFallBackToLegacyEncoding() {
        var op = operation(1); var base = baseline(op); var bad = new Baseline(Scope.FUEL,
                List.of(new Entry(base.moved(), 5000, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> op.accounting(bad));
        assertNull(op.accountingBaseline()); assertEquals(2, EscrowCodec.operation(op).getInt("schema_version"));
    }
}
