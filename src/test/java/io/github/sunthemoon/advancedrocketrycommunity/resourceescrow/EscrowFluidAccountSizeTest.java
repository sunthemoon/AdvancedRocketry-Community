package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Baseline;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Frozen;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.FrozenEntry;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Key;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Knowledge;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Row;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Scope;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Status;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccountCodec.Limits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W361 measurements for Root's decision row (W357 section 5). Rows are measured with the real account codec; the
 * existing objects are encoded by the real existing encoders, and the fields that Slice B will add are then added to
 * those encoded tags with the names and types of section 4.1. Nothing here changes an existing fixture or pin.
 */
class EscrowFluidAccountSizeTest {
    /** The new root header: a 17-byte longer epoch and the required, empty {@code fluid_accounts} list. */
    static final String NEW_EPOCH = "v1.8.0-resource-escrow-fluid-accounting";
    /** Section 4.1 does not name the intent's commitment field. This name is this slice's assumption. */
    static final String COMMITMENT = "account_commitment";
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    record Size(long bytes, int depth, int nodes) { }
    static Size size(CompoundTag tag) {
        var measured = BoundedNbt.measure(tag, Integer.MAX_VALUE, 64, Integer.MAX_VALUE);
        assertEquals(RocketNbtSize.uncompressedBytes(tag), measured.bytes());
        return new Size(measured.bytes(), depth(tag), measured.nodes());
    }
    private static int depth(Tag tag) {
        int result = 0;
        if (tag instanceof CompoundTag compound) { for (String key : compound.getAllKeys()) { result = Math.max(result, depth(compound.get(key))); } }
        else if (tag instanceof ListTag list) { for (var child : list) { result = Math.max(result, depth(child)); } }
        return result + 1;
    }

    /** Seven key entries with 128-character IDs, every optional field, and a member list of {@code members}. */
    static Row worstRow(int members) {
        var keys = new ArrayList<Key>();
        for (int index = 0; index < 5; index++) { keys.add(Key.derive("a:" + (char) ('a' + index) + "x".repeat(125), null).value()); }
        UUID op = id(1);
        var baseline = new Baseline(Scope.FUEL, List.of(new Entry(keys.get(0), 0, 2_048_000, 0, 0), new Entry(keys.get(1), 16_000, 0, -1_000, 1_000)));
        var entries = new ArrayList<FrozenEntry>();
        for (Key key : keys) {
            long source = 15_000, destination = 2_047_000;
            long loss = -(source - baseline.source(key) + destination - baseline.destination(key));
            entries.add(new FrozenEntry(key, OptionalLong.of(source), OptionalLong.of(destination), OptionalLong.of(loss), op));
        }
        var list = new ArrayList<UUID>(); for (int index = 0; index < members; index++) { list.add(id(1 + index)); }
        var row = new Row(op, baseline, new Frozen(Knowledge.KNOWN, op, list, entries));
        assertEquals(Status.OK, row.status()); return row;
    }

    @Test void worstAccountRowBytesDepthAndNodes() {
        for (int members : new int[] {1, 5, 8, 14, 18, 19, 64}) {
            var tag = EscrowFluidAccountCodec.encode(worstRow(members), EscrowFluidAccountCodec.MAXIMUM).value();
            var size = size(tag);
            assertEquals(worstRow(members), EscrowFluidAccountCodec.decode(tag, EscrowFluidAccountCodec.MAXIMUM).value());
            System.out.println("ACCOUNT_SIZE worst_row members=" + members + " bytes=" + size.bytes() + " list_element_bytes=" + (size.bytes() - 3)
                    + " depth=" + size.depth() + " nodes=" + size.nodes());
            // The layout is linear in the member list and nothing else varies: 1,753 bytes and 16 per member.
            assertEquals(1_753 + 16L * members, size.bytes()); assertEquals(5, size.depth()); assertEquals(65, size.nodes());
        }
        // The exact bounds of the worst row are tight: one byte, one level or one node less is refused.
        var worst = worstRow(64); var tag = EscrowFluidAccountCodec.encode(worst, EscrowFluidAccountCodec.MAXIMUM).value();
        assertEquals(Status.OK, EscrowFluidAccountCodec.decode(tag, new Limits(2_777, 5, 65, 64)).status());
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.decode(tag, new Limits(2_776, 5, 65, 64)).status());
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.decode(tag, new Limits(2_777, 4, 65, 64)).status());
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.decode(tag, new Limits(2_777, 5, 64, 64)).status());
        // An ordinary one-key fuel row, planned and then frozen balanced, for comparison.
        var ordinary = ordinaryRow(id(1), EscrowFluidAccountTest.WATER);
        var planned = size(EscrowFluidAccountCodec.encode(new Row(id(1), ordinary.baseline(), null), EscrowFluidAccountCodec.MAXIMUM).value());
        var frozen = size(EscrowFluidAccountCodec.encode(ordinary, EscrowFluidAccountCodec.MAXIMUM).value());
        System.out.println("ACCOUNT_SIZE ordinary_row planned_bytes=" + planned.bytes() + " planned_nodes=" + planned.nodes()
                + " frozen_bytes=" + frozen.bytes() + " frozen_nodes=" + frozen.nodes());
    }

    /** A 1,000 mB fill of one key from a 5,000 mB tank into an empty bank, settled with a zero residual. */
    static Row ordinaryRow(UUID op, Key key) {
        var member = new EscrowFluidAccount.Member(op, 1, EscrowFluidAccountTest.device(10), EscrowFluidAccountTest.rocket(20, "FUEL"),
                Baseline.capture(Scope.FUEL, EscrowFluidAccount.Vector.of(key, 5_000).value(), EscrowFluidAccount.Vector.EMPTY, key, 1_000).value(), false);
        return EscrowFluidAccount.settle(List.of(member), EscrowFluidAccountTest.finals(EscrowFluidAccountTest.device(10),
                EscrowFluidAccount.Vector.of(key, 4_000).value(), EscrowFluidAccountTest.rocket(20, "FUEL"),
                EscrowFluidAccount.Vector.of(key, 1_000).value())).value().rows().get(op);
    }

    // ---- The largest real component: the most members whose frozen intent still fits one 12,288-byte operation. ----

    private static UUID rocketEntity(int rocket) { return id(2_000_000 + rocket); }

    /**
     * The smallest valid member: three-character IDs, a non-native rocket (no complete binding), eight fills per rocket
     * (the saved bound), and rockets joined by a cross-kind UUID alias, which is the only way past eight.
     */
    private static List<EscrowOperation> smallestComponent(int members) {
        var ops = new ArrayList<EscrowOperation>();
        for (int index = 0; index < members; index++) {
            int rocket = index / 8, epoch = index % 8; long sequence = index + 1;
            var payload = new CompoundTag(); payload.putString("FluidName", "a:b"); payload.putInt("Amount", 1);
            UUID device = epoch == 0 && rocket > 0 ? rocketEntity(rocket - 1) : id(1_000_000 + sequence);
            var source = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, "a:b", device, null, "a:b", 0, 0, 0, EscrowEndpoint.Unit.TANK,
                    0, hash(1), hash(2));
            var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "a:b", rocketEntity(rocket), id(3_000_000 + rocket), "a:b", 0, 0, 0,
                    EscrowEndpoint.Unit.TANK, epoch, hash(10 + epoch), hash(11 + epoch));
            var op = new EscrowOperation(id(sequence), sequence, id(99), 0, EscrowOperation.Phase.PREPARED, EscrowReason.NONE, payload,
                    RocketNativeResources.signature(payload), source, destination, new EscrowGuard(destination.logicalRocket(), null, 1, hash(400)));
            ops.add(index == 0 ? op.withReason(EscrowReason.OTHER_STATE) : op);
        }
        return ops;
    }

    /** Production-shaped members: the registered loader and rocket types, a real Level and complete native bindings. */
    private static List<EscrowOperation> nativeComponent(int members) {
        var ops = new ArrayList<EscrowOperation>();
        for (int index = 0; index < members; index++) {
            int rocket = index / 8, epoch = index % 8; long sequence = index + 1;
            var fill = EscrowReceiptChargeTest.fill(sequence, 5_000 + rocket, epoch, epoch, "minecraft:overworld", false, -1);
            if (epoch == 0 && rocket > 0) {
                var old = fill.source(); var previous = ops.get(index - 1).rocket();
                var device = new EscrowEndpoint(old.kind(), old.level(), previous.identity(), null, old.type(), old.x(), old.y(), old.z(), old.unit(),
                        old.beforeEpoch(), old.beforeSignature(), old.afterSignature());
                fill = nativeModel(new EscrowOperation(fill.id(), fill.sequence(), fill.owner(), fill.createdTick(), fill.phase(), fill.reason(),
                        fill.payload(), fill.payloadHash(), device, fill.destination(), fill.guard(), fill.kind(), fill.settlement()),
                        EscrowCargoFixtures.wrapper(0));
            }
            ops.add(index == 0 ? fill.withReason(EscrowReason.OTHER_STATE) : fill);
        }
        return ops;
    }

    private record Frozen1(EscrowSettlementIntent intent, EscrowLedger root) { }

    private static EscrowLedger accountingRoot(List<EscrowOperation> operations) {
        var rows = operations.stream().map(op -> {
            var key = EscrowFluidAccountCodec.key(op.payload()).value(); int quantity = op.payload().getInt("Amount");
            return new Row(op.id(), new Baseline(Scope.FUEL, List.of(new Entry(key, 5000,
                    op.destination().beforeEpoch() * quantity, -quantity, quantity))), null);
        }).toList();
        var ops = operations.stream().map(op -> op.accounting(rows.stream().filter(row -> row.operation().equals(op.id()))
                .findFirst().orElseThrow().baseline())).toList();
        return new EscrowLedger(id(500), ops.size(), 0, operations, List.of(), List.of()).accounting(ops, rows);
    }

    private static List<Row> frozenAccounts(EscrowLedger root, boolean present) {
        var members = new ArrayList<EscrowFluidAccount.Member>();
        var finals = new LinkedHashMap<EscrowFluidAccount.UnitRef, Optional<EscrowFluidAccount.Vector>>();
        for (var op : root.records()) {
            var base = root.fluidAccounts().stream().filter(row -> row.operation().equals(op.id())).findFirst().orElseThrow().baseline();
            var source = new EscrowFluidAccount.UnitRef(op.source().key(), "tank");
            var destination = new EscrowFluidAccount.UnitRef(op.destination().key(), "fuel");
            members.add(new EscrowFluidAccount.Member(op.id(), op.sequence(), source, destination, base, false));
            finals.put(source, present ? Optional.of(base.sourceAfter()) : Optional.empty());
            finals.put(destination, present ? Optional.of(base.destinationAfter()) : Optional.empty());
        }
        var settled = EscrowFluidAccount.settle(members, finals);
        assertEquals(Status.OK, settled.status());
        return root.records().stream().map(op -> settled.value().rows().get(op.id())).toList();
    }

    /** Runs the real preview, confirmation and O1 freeze; any refusal means the component does not fit. */
    private static Frozen1 freeze(List<EscrowOperation> ops, boolean present) {
        return freeze(ops, present, false);
    }
    private static Frozen1 freeze(List<EscrowOperation> ops, boolean present, boolean accounting) {
        var root = accounting ? accountingRoot(ops) : new EscrowLedger(id(500), ops.size(), 0, ops, List.of(), List.of());
        var rows = accounting ? frozenAccounts(root, present) : List.<Row>of();
        var observations = new ArrayList<EscrowSettlementIntent.Observation>();
        for (var chain : EscrowPrefix.chains(ops).values()) {
            var first = chain.get(0).endpoint(); var last = chain.get(chain.size() - 1).endpoint();
            if (!present) { observations.add(new EscrowSettlementIntent.Observation(first, null, false, 0)); continue; }
            boolean rocket = first.kind() == EscrowEndpoint.Kind.ENTITY;
            var state = new EscrowPrefix.State(root.id(), last.afterEpoch(), rocket ? hash(777) : last.afterSignature(),
                    chain.stream().map(EscrowPrefix.Link::marker).toList());
            observations.add(new EscrowSettlementIntent.Observation(first, state, true, Long.MAX_VALUE, EscrowSignatureKind.settlement(first),
                    rocket ? hash(778) : EscrowInvariant.reference(first)));
        }
        var preview = EscrowSettlementIntent.preview(root, ops.get(0).id(), present ? EscrowSettlementIntent.Choice.ACCEPT_CURRENT
                : EscrowSettlementIntent.Choice.ABANDON, id(700), "a", 0, observations);
        if (accounting) { preview = preview.accounting(rows); }
        var intent = preview.confirm(preview.actor(), preview.digest(), 200, observations);
        return new Frozen1(intent, intent.freeze(root, rows));
    }

    private static int largest(java.util.function.IntFunction<List<EscrowOperation>> build, boolean present, String label) {
        return largest(build, present, label, false);
    }
    private static int largest(java.util.function.IntFunction<List<EscrowOperation>> build, boolean present, String label, boolean accounting) {
        int largest = 0; String refusal = "none up to 64";
        for (int members = 1; members <= 64; members++) {
            try {
                // Include the baseline and commitment before testing the complete intent and operation envelopes.
                var frozen = freeze(build.apply(members), present, accounting);
                assertEquals(members, frozen.intent().members().size(), "One connected component");
                largest = members;
            } catch (IllegalArgumentException refused) { refusal = refused.getClass().getSimpleName() + ": " + refused.getMessage(); break; }
        }
        var fit = freeze(build.apply(largest), present, accounting); var intent = size(fit.intent().encode());
        long operation = 0; int nodes = 0;
        for (var op : fit.root().records()) {
            var encoded = size(EscrowCodec.operation(op)); operation = Math.max(operation, encoded.bytes()); nodes = Math.max(nodes, encoded.nodes());
        }
        System.out.println("ACCOUNT_SIZE largest_component format=" + (accounting ? "accounting" : "legacy")
                + " shape=" + label + " members=" + largest + " observations=" + fit.intent().observations().size()
                + " intent_bytes=" + intent.bytes() + " intent_nodes=" + intent.nodes() + " intent_depth=" + intent.depth()
                + " largest_operation_bytes=" + operation + " largest_operation_nodes=" + nodes + " next_refusal=\"" + refusal + "\"");
        return largest;
    }

    @Test void largestRealComponentWhoseIntentFitsAnOperation() {
        // Historical root-2 frozen-fit measurements, not a bound on unsettled components that can decode.
        int smallestMissing = largest(EscrowFluidAccountSizeTest::smallestComponent, false, "smallest-ids-all-missing");
        int smallestPresent = largest(EscrowFluidAccountSizeTest::smallestComponent, true, "smallest-ids-all-present");
        int nativeMissing = largest(EscrowFluidAccountSizeTest::nativeComponent, false, "native-ids-all-missing");
        int nativePresent = largest(EscrowFluidAccountSizeTest::nativeComponent, true, "native-ids-all-present");
        assertEquals(14, smallestMissing); assertEquals(9, smallestPresent); assertEquals(12, nativeMissing); assertEquals(8, nativePresent);
        // Historical layout at the largest frozen legacy component; not the new-format minimum.
        var row = EscrowFluidAccountCodec.encode(worstRow(smallestMissing), EscrowFluidAccountCodec.MAXIMUM).value(); var size = size(row);
        System.out.println("ACCOUNT_SIZE worst_real_row members=" + smallestMissing + " bytes=" + size.bytes() + " depth=" + size.depth()
                + " nodes=" + size.nodes() + " margin_to_2048=" + (2_048 - size.bytes()));
        assertEquals(1_977, size.bytes());
        // Preserve the old proposed-bound assertions; SAVED uses Root's conservative 2,048 / 14 decision.
        var proposed = new Limits(1_977, 5, 65, smallestMissing);
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.decode(row, new Limits(1_976, 5, 65, smallestMissing)).status());
        assertEquals(worstRow(smallestMissing), EscrowFluidAccountCodec.decode(row, proposed).value());
        var next = EscrowFluidAccountCodec.encode(worstRow(smallestMissing + 1), EscrowFluidAccountCodec.MAXIMUM).value();
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.decode(next, proposed).status());
        assertEquals(Status.CARDINALITY, EscrowFluidAccountCodec.decode(next, new Limits(4_096, 5, 65, smallestMissing)).status());
        assertEquals(1_993, size(next).bytes());
        // A ceiling of 2,048 bytes would hold the member list of at most 18 members.
        assertEquals(2_041, size(EscrowFluidAccountCodec.encode(worstRow(18), EscrowFluidAccountCodec.MAXIMUM).value()).bytes());
    }

    @Test void newFormatFrozenFitIncludesBaselineAndCommitmentInsideTheSearch() {
        int missing = largest(EscrowFluidAccountSizeTest::smallestComponent, false, "smallest-ids-all-missing", true);
        int present = largest(EscrowFluidAccountSizeTest::smallestComponent, true, "smallest-ids-all-present", true);
        int nativeMissing = largest(EscrowFluidAccountSizeTest::nativeComponent, false, "native-ids-all-missing", true);
        int nativePresent = largest(EscrowFluidAccountSizeTest::nativeComponent, true, "native-ids-all-present", true);
        assertEquals(13, missing); assertEquals(8, present); assertEquals(12, nativeMissing); assertEquals(8, nativePresent);
        var fit = freeze(smallestComponent(missing), false, true);
        assertEquals(3, fit.root().schema()); assertEquals(3, fit.intent().encode().getInt("schema_version"));
        assertEquals(fit.root(), EscrowCodec.decode(EscrowCodec.encode(fit.root())));
        var legacy = freeze(smallestComponent(missing), false);
        for (int index = 0; index < missing; index++) {
            var old = EscrowCodec.operation(legacy.root().records().get(index));
            var actual = EscrowCodec.operation(fit.root().records().get(index));
            assertEquals(size(schema3Operation(old)), size(actual), "Measured additions match the real new-format encoder");
            assertEquals(115, size(actual).bytes() - size(old).bytes());
        }
        assertEquals(11_762, fit.root().records().stream().mapToLong(op -> size(EscrowCodec.operation(op)).bytes()).max().orElseThrow());
        var worst = EscrowFluidAccountCodec.encode(worstRow(missing), EscrowFluidAccountCodec.SAVED).value();
        var measured = size(worst);
        long actualRow = fit.root().fluidAccounts().stream().mapToLong(row -> size(
                EscrowFluidAccountCodec.encode(row, EscrowFluidAccountCodec.SAVED).value()).bytes()).max().orElseThrow();
        System.out.println("ACCOUNT_SIZE worst_new_format_frozen_row members=" + missing + " bytes=" + measured.bytes()
                + " depth=" + measured.depth() + " nodes=" + measured.nodes() + " margin_to_2048=" + (2048 - measured.bytes())
                + " actual_minimal_fixture_row_bytes=" + actualRow);
        assertEquals(new Size(1961, 5, 65), measured);
        assertEquals(492, actualRow);
        assertEquals(new Limits(2048, 5, 65, 14), EscrowFluidAccountCodec.SAVED);
    }

    @Test void largerUnsettledAndLegacyComponentsDecodeEvenWhenFreezingIsRefused() {
        var ops = smallestComponent(15);
        var legacy = new EscrowLedger(id(500), 15, 0, ops, List.of(), List.of());
        var migrated = legacy.accounting(ops, List.of());
        for (var root : List.of(legacy, migrated)) {
            var decoded = EscrowCodec.decode(EscrowCodec.encode(root));
            assertEquals(root, decoded);
            assertEquals(15, EscrowSettlementIntent.component(decoded.records(), ops.get(0).id()).size());
        }
        assertThrows(IllegalArgumentException.class, () -> freeze(ops, false));
        var unsettled = accountingRoot(smallestComponent(14));
        assertEquals(unsettled, EscrowCodec.decode(EscrowCodec.encode(unsettled)));
        assertThrows(IllegalArgumentException.class, () -> freeze(smallestComponent(14), false, true));
        // This candidate passes all old-format checks; only the additions make its operation too large.
        var oldFit = freeze(smallestComponent(14), false);
        var grown = schema3Operation(EscrowCodec.operation(oldFit.root().records().get(0)));
        assertEquals(12_402, size(grown).bytes());
        assertFalse(BoundedNbt.fits(grown, EscrowLimits.OP_BYTES, 16, 512));
    }

    // ---- The new receipt, operation and root sizes of the W114 fixtures. ----

    private static EscrowOperation fixture(EscrowOperation.Kind kind, String level, boolean guarded, int retained) {
        if (!kind.cargo()) { return EscrowReceiptChargeTest.fill(100, 99, retained, retained, level, guarded, -1); }
        var op = EscrowCargoFixtures.operation(kind, 100, 99, retained, -1, level);
        return guarded ? EscrowCargoFixtures.guarded(op) : op;
    }

    /** W114's sizing intent: every marker of the rocket charged as DESTINATION, the longest new actor name. */
    private static EscrowSettlementIntent sizingIntent(EscrowLedger root, EscrowOperation op, int retained) {
        var observed = EscrowCargoFixtures.observations(root, op, retained, 0, false).stream().map(o -> {
            if (!o.endpoint().equals(op.rocket())) { return o; }
            var markers = o.state().markers().stream().map(m -> new EscrowPrefix.Marker(m.operation(), m.sequence(), "DESTINATION", m.epochAfter())).toList();
            return new EscrowSettlementIntent.Observation(o.endpoint(), new EscrowPrefix.State(root.id(), o.state().epoch(), o.state().signature(), markers),
                    o.bound(), o.quantity(), o.signatureKind(), o.invariantDigest());
        }).toList();
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, id(700), "~".repeat(16), 10, observed);
        return preview.confirm(preview.actor(), preview.digest(), 11, observed);
    }

    /** A schema-3 receipt in wire form with the schema-4 additions of section 4.1: {@code f1b} and the status. */
    static CompoundTag schema4Receipt(CompoundTag encodedSchema3, String status) {
        var tag = encodedSchema3.copy();
        assertEquals(3, tag.getInt("f0")); assertEquals("UNKNOWN", tag.getString("f7")); assertFalse(tag.contains("f1b"));
        tag.putInt("f0", 4); tag.putString("f7", status); tag.putByteArray("f1b", new byte[32]); return tag;
    }
    /** A settled operation with the two schema-3 additions: its baseline digest and its intent's commitment. */
    static CompoundTag schema3Operation(CompoundTag encodedSchema2) {
        var tag = encodedSchema2.copy(); tag.putByteArray("accounting_baseline", new byte[32]);
        if (tag.contains("settlement")) { tag.getCompound("settlement").putByteArray(COMMITMENT, new byte[32]); }
        return tag;
    }

    @Test void newReceiptAndOperationSizesOfTheW114Fixtures() {
        long worstFuel = 0, worstCargo = 0, newFuel = 0, newCargo = 0, worstOperation = 0, newOperation = 0; int cells = 0, receiptNodes = 0, operationNodes = 0;
        for (String level : EscrowReceiptChargeTest.LEVELS) {
            for (var kind : List.of(EscrowOperation.Kind.FLUID_CARGO_LOAD, EscrowOperation.Kind.FLUID_CARGO_UNLOAD, EscrowOperation.Kind.FLUID_PROPULSION_FILL)) {
                for (boolean guarded : List.of(false, true)) {
                    int retained = kind.cargo() ? 0 : 4; cells++;
                    var repair = fixture(kind, level, guarded, retained).withReason(EscrowReason.JOURNAL_GUARD_MISMATCH); var root = ledger(repair);
                    var intent = sizingIntent(root, repair, retained);
                    var old = EscrowReceiptCodec.encode(EscrowSettlementTombstones.create(repair, intent));
                    var unknown = size(schema4Receipt(old, "UNKNOWN")); var known = size(schema4Receipt(old, "KNOWN"));
                    // One 32-byte array under a three-character key is 42 bytes and one node; KNOWN is two bytes shorter.
                    assertEquals(size(old).bytes() + 42, unknown.bytes()); assertEquals(size(old).bytes() + 40, known.bytes());
                    assertEquals(size(old).nodes() + 1, unknown.nodes()); assertEquals(size(old).depth(), unknown.depth());
                    assertTrue(BoundedNbt.fits(schema4Receipt(old, "UNKNOWN"), 2_048, 16, 128), "The new receipt fits the unchanged receipt ceiling");
                    if (kind.cargo()) { worstCargo = Math.max(worstCargo, size(old).bytes()); newCargo = Math.max(newCargo, unknown.bytes()); }
                    else { worstFuel = Math.max(worstFuel, size(old).bytes()); newFuel = Math.max(newFuel, unknown.bytes()); }
                    receiptNodes = Math.max(receiptNodes, unknown.nodes());
                    var settled = EscrowCodec.operation(repair.settle(intent)); var grown = size(schema3Operation(settled));
                    assertEquals(size(settled).bytes() + 58 + 39 + COMMITMENT.length(), grown.bytes()); assertEquals(size(settled).nodes() + 2, grown.nodes());
                    assertTrue(BoundedNbt.fits(schema3Operation(settled), EscrowLimits.OP_BYTES, 16, 512));
                    worstOperation = Math.max(worstOperation, size(settled).bytes()); newOperation = Math.max(newOperation, grown.bytes());
                    operationNodes = Math.max(operationNodes, grown.nodes());
                }
            }
        }
        System.out.println("ACCOUNT_SIZE receipts cells=" + cells + " fuel_old=" + worstFuel + " fuel_new_unknown=" + newFuel + " fuel_new_known=" + (newFuel - 2)
                + " fuel_margin=" + (2_048 - newFuel) + " fluid_cargo_old=" + worstCargo + " fluid_cargo_new_unknown=" + newCargo
                + " fluid_cargo_new_known=" + (newCargo - 2) + " fluid_cargo_margin=" + (2_048 - newCargo) + " max_nodes=" + receiptNodes);
        System.out.println("ACCOUNT_SIZE operations cells=" + cells + " settled_old_max=" + worstOperation + " settled_new_max=" + newOperation
                + " added_baseline=58 added_commitment=" + (39 + COMMITMENT.length()) + " max_nodes=" + operationNodes);
        assertEquals(54, cells); assertEquals(1_833, worstFuel); assertEquals(1_875, newFuel); assertEquals(173, 2_048 - newFuel);
        // W114's 1,557-byte cargo pin is an item receipt, which stays schema 3; the worst fluid cargo receipt is 1,547 bytes.
        assertEquals(1_547, worstCargo); assertEquals(1_589, newCargo); assertEquals(459, 2_048 - newCargo);
        assertEquals(104, receiptNodes); assertEquals(5_158, worstOperation); assertEquals(5_273, newOperation); assertEquals(192, operationNodes);
    }

    /** The root header of schema 3 with {@code rows} account rows, added to a real encoded root. */
    static CompoundTag schema3Root(CompoundTag encodedRoot2, List<Row> rows) {
        var tag = encodedRoot2.copy(); tag.putInt("schema_version", 3); tag.putString("format_epoch", NEW_EPOCH);
        tag.put("fluid_accounts", EscrowFluidAccountCodec.encodeRows(rows, EscrowFluidAccountCodec.MAXIMUM).value()); return tag;
    }

    // ---- Pinned counts that the format moves. ----

    /** The admission rule of EscrowSettlementCapacity.preflight with one more charge per fluid operation. */
    private static int admitted(EscrowLedger base, List<EscrowOperation> candidates, long headerBytes, long rowCeiling) {
        int admitted = 0;
        for (int count = 1; count <= candidates.size(); count++) {
            var prospective = base.append(candidates.subList(0, count));
            long reserved = EscrowSettlementCapacity.reservedBytes(prospective) + headerBytes + rowCeiling * count;
            if (prospective.records().size() + 1 > EscrowLimits.OPERATIONS
                    || reserved + EscrowSettlementCapacity.FLOOR_RETIREMENT_BYTES > EscrowLimits.ROOT_BYTES) { break; }
            admitted = count;
        }
        return admitted;
    }

    @Test void concurrentCargoOperationsUnderEachRowCeiling() {
        // The header delta, measured on a real encoded root: 17 bytes of epoch and 22 bytes of empty list.
        var empty = EscrowCodec.encode(ledger());
        long header = size(schema3Root(empty, List.of())).bytes() - size(empty).bytes();
        assertEquals(39, header);
        for (var kind : EscrowCargoFixtures.KINDS) {
            for (int floors : List.of(0, 29)) {
                var base = EscrowCombinedReservationTest.retainedFloors(floors); var candidates = new ArrayList<EscrowOperation>();
                for (int index = 0; index < 64; index++) {
                    candidates.add(distributedOwner(EscrowCargoFixtures.operation(kind, base.issuedThrough() + 1 + index, 40_000 + index)));
                }
                // Control: the real preflight admits exactly today's pinned count, and the formula reproduces it.
                int real = 0;
                for (int count = 1; count <= 64; count++) {
                    var prospective = base.append(candidates.subList(0, count));
                    try { EscrowSettlementCapacity.preflight(prospective, candidates.get(count - 1).id()); real = count; }
                    catch (EscrowSettlementCapacity.ReservationFull full) { break; }
                }
                assertEquals(floors == 0 ? 62 : 60, real); assertEquals(real, admitted(base, candidates, 0, 0));
                var line = new StringBuilder("ACCOUNT_SIZE cargo_capacity kind=" + kind + " floors=" + floors + " admitted_today=" + real);
                var results = new LinkedHashMap<Integer, Integer>();
                for (int ceiling : new int[] {1_977, 2_048, 2_777, 4_096}) {
                    results.put(ceiling, admitted(base, candidates, header, ceiling));
                    line.append(" ceiling_").append(ceiling).append('=').append(results.get(ceiling));
                }
                System.out.println(line);
                // 62 becomes 55 and 60 becomes 53 under the proposed 1,977 bytes; 2,048 bytes gives the same counts.
                assertEquals(floors == 0 ? 55 : 53, results.get(1_977)); assertEquals(results.get(1_977), results.get(2_048));
                assertEquals(floors == 0 ? 52 : 50, results.get(2_777)); assertEquals(floors == 0 ? 48 : 47, results.get(4_096));
            }
        }
    }

    @Test void benchmarkCeilingFixtureWithAccountRows() {
        var root = EscrowFlightBenchmarkCeiling.create(); var encoded = EscrowCodec.encode(root);
        assertEquals(1_048_576, size(encoded).bytes());
        int blob = root.records().get(0).payload().getCompound("Tag").getByteArray("blob").length;
        int last = root.records().get(63).payload().getCompound("Tag").getByteArray("blob").length;
        // The same root in the new form: 64 settled schema-3 operations, 64 schema-4 receipts and 64 account rows.
        var rows = new ArrayList<Row>();
        for (var op : root.records()) { rows.add(ordinaryRow(op.id(), EscrowFluidAccountCodec.key(op.payload()).value())); }
        var grown = schema3Root(encoded, rows);
        var records = new ListTag(); for (var raw : grown.getList("records", 10)) { records.add(schema3Operation((CompoundTag) raw)); }
        var receipts = new ListTag(); for (var raw : grown.getList("tombstones", 10)) { receipts.add(schema4Receipt((CompoundTag) raw, "KNOWN")); }
        grown.put("records", records); grown.put("tombstones", receipts);
        long added = size(grown).bytes() - 1_048_576;
        long rowBytes = size(EscrowFluidAccountCodec.encode(rows.get(0), EscrowFluidAccountCodec.MAXIMUM).value()).bytes() - 3;
        assertEquals(39 + 64 * (58 + 39 + COMMITMENT.length() + 40 + rowBytes), added);
        // Every blob byte is one root byte, so the fixture's own search lands on the old total minus the added bytes.
        long total = 64L * blob + (last - blob) - added; long newBlob = Math.floorDiv(total, 64), remainder = Math.floorMod(total, 64);
        System.out.println("ACCOUNT_SIZE benchmark_ceiling root_bytes=1048576 records=64 floors=256 blob_today=" + blob + " last_extra_today=" + (last - blob)
                + " same_fixture_new_form_bytes=" + size(grown).bytes() + " added_bytes=" + added + " row_list_bytes=" + rowBytes
                + " blob_new=" + newBlob + " last_extra_new=" + remainder);
        assertEquals(7_625, blob); assertEquals(46, last - blob); assertEquals(32_807, added); assertEquals(357, rowBytes);
        assertEquals(7_113, newBlob); assertEquals(7, remainder);
        for (var raw : records) { assertTrue(BoundedNbt.fits((CompoundTag) raw, EscrowLimits.OP_BYTES, 16, 512)); }
        assertFalse(BoundedNbt.fits(grown, EscrowLimits.ROOT_BYTES, 24, 131_072), "As it stands the fixture no longer fits 1 MiB");
    }
}
