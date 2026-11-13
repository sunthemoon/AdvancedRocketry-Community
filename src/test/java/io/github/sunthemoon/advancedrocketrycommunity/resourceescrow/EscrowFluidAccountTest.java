package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Application;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Baseline;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Delta;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Frozen;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.FrozenEntry;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Key;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Knowledge;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Member;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Row;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Scope;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Settlement;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Status;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Unit;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.UnitRef;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Vector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** W361: the pure account model of W357 sections 2 and 3. No Minecraft bootstrap is needed. */
class EscrowFluidAccountTest {
    static final String EMPTY_TAG_DIGEST = "0".repeat(64), OTHER_TAG_DIGEST = "1".repeat(64);
    static final Key WATER = key("minecraft:water", null), OXYGEN = key("advancedrocketrycommunity:oxygen", null),
            HYDROGEN = key("advancedrocketrycommunity:hydrogen", null), LAVA = key("minecraft:lava", null),
            MILK = key("minecraft:milk", null), TAGGED_WATER = key("minecraft:water", OTHER_TAG_DIGEST);

    static Key key(String fluid, String tag) { return Key.derive(fluid, tag).value(); }
    static UUID id(long value) { return new UUID(77, value); }
    static Vector vector(Object... pairs) {
        var units = new ArrayList<Unit>();
        for (int index = 0; index < pairs.length; index += 2) { units.add(new Unit((Key) pairs[index], ((Number) pairs[index + 1]).longValue())); }
        return Vector.aggregate(units).value();
    }
    static UnitRef device(long value) {
        return new UnitRef(new EscrowEndpoint.Key(EscrowEndpoint.Kind.BLOCK_ENTITY, "minecraft:overworld", id(value), null,
                "advancedrocketrycommunity:fluid_fuel_loader", 0, 64, 0, EscrowEndpoint.Unit.TANK), "TANK");
    }
    static UnitRef rocket(long value, String scope) {
        return new UnitRef(new EscrowEndpoint.Key(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", id(value), id(value + 1_000),
                "advancedrocketrycommunity:rocket", 0, 0, 0, EscrowEndpoint.Unit.PRIMARY), scope);
    }
    static Baseline baseline(Scope scope, Vector source, Vector destination, Key moved, long quantity) {
        var result = Baseline.capture(scope, source, destination, moved, quantity);
        assertEquals(Status.OK, result.status()); return result.value();
    }
    static Map<UnitRef, Optional<Vector>> finals(Object... pairs) {
        var map = new HashMap<UnitRef, Optional<Vector>>();
        for (int index = 0; index < pairs.length; index += 2) { map.put((UnitRef) pairs[index], Optional.ofNullable((Vector) pairs[index + 1])); }
        return map;
    }
    /** One fuel fill of {@code quantity} water from a 5,000 mB loader into an empty rocket. */
    private static Settlement single(long quantity, Vector source, Vector destination) {
        var member = new Member(id(1), 1, device(10), rocket(20, "FUEL"),
                baseline(Scope.FUEL, vector(WATER, 5_000), Vector.EMPTY, WATER, quantity), false);
        var settled = EscrowFluidAccount.settle(List.of(member), finals(device(10), source, rocket(20, "FUEL"), destination));
        assertEquals(Status.OK, settled.status()); return settled.value();
    }

    @Test void keysSeparateFluidsTagsAndAnAbsentTagFromAnEmptyOne() {
        assertNotEquals(key("minecraft:water", null), key("minecraft:water", EMPTY_TAG_DIGEST));
        assertNotEquals(key("minecraft:water", EMPTY_TAG_DIGEST), TAGGED_WATER);
        assertNotEquals(WATER.tagDigest(), LAVA.tagDigest(), "The envelope digest covers the fluid ID");
        assertEquals(WATER, key("minecraft:water", null));
        assertTrue(OXYGEN.compareTo(WATER) < 0, "Ordered by ID first");
        var same = List.of(WATER, TAGGED_WATER).stream().sorted().toList();
        assertTrue(same.get(0).tagDigest().compareTo(same.get(1).tagDigest()) < 0, "Then by digest");
        for (String invalid : new String[] {"water", "Minecraft:water", "minecraft:wa ter", "minecraft:" + "w".repeat(119), ""}) {
            assertEquals(Status.INVALID_KEY, Key.derive(invalid, null).status(), invalid);
        }
        assertEquals(Status.OK, Key.derive("minecraft:" + "w".repeat(118), null).status());
        assertEquals(Status.MALFORMED_DIGEST, Key.derive("minecraft:water", "abc").status());
        assertEquals(Status.MALFORMED_DIGEST, new Key("minecraft:water", "A".repeat(64)).status());
        assertEquals(Status.INVALID_KEY, new Key("water", WATER.tagDigest()).status());
    }

    @Test void amountsAreAggregatedPerEndpointAndKeyAcrossBothBanks() {
        // The same key in both banks is one amount; a move between the banks leaves the account unchanged.
        var split = vector(WATER, 600, WATER, 400); var moved = vector(WATER, 1_000, null, 0);
        assertEquals(split, moved); assertEquals(1_000, split.amount(WATER)); assertEquals(java.util.Set.of(WATER), split.keys());
        var banks = vector(HYDROGEN, 700, OXYGEN, 300);
        assertEquals(700, banks.amount(HYDROGEN)); assertEquals(300, banks.amount(OXYGEN)); assertEquals(0, banks.amount(WATER));
        // Empty units are a known zero, not a synthetic fluid.
        assertEquals(Vector.EMPTY, vector(null, 0, null, 0)); assertEquals(Vector.EMPTY, vector(WATER, 0));
        assertTrue(Vector.EMPTY.keys().isEmpty());
        assertEquals(Status.INVALID_KEY, Vector.aggregate(List.of(new Unit(null, 5))).status());
        assertEquals(Status.NEGATIVE_AMOUNT, Vector.aggregate(List.of(new Unit(WATER, -1))).status());
        assertEquals(Status.OVERFLOW, Vector.aggregate(List.of(new Unit(WATER, Long.MAX_VALUE), new Unit(WATER, 1))).status());
        assertEquals(Status.MALFORMED_DIGEST, Vector.aggregate(List.of(new Unit(new Key("minecraft:water", "x"), 1))).status());
    }

    @Test void baselineCapturesOneMovedKeyOfOneToOneThousandMillibuckets() {
        for (long quantity : new long[] {1, 1_000}) {
            var baseline = baseline(Scope.FUEL, vector(WATER, 5_000), vector(WATER, 250, OXYGEN, 40), WATER, quantity);
            assertEquals(WATER, baseline.moved()); assertEquals(quantity, baseline.quantity());
            assertEquals(new Delta(WATER, -quantity), baseline.sourceDelta()); assertEquals(new Delta(WATER, quantity), baseline.destinationDelta());
            assertEquals(2, baseline.entries().size());
            assertEquals(new Entry(OXYGEN, 0, 40, 0, 0), baseline.entries().get(0));
            assertEquals(new Entry(WATER, 5_000, 250, -quantity, quantity), baseline.entries().get(1));
            assertEquals(vector(WATER, 5_000 - quantity), baseline.sourceAfter());
            assertEquals(vector(WATER, 250 + quantity, OXYGEN, 40), baseline.destinationAfter());
            assertEquals(Status.OK, baseline.verifyPlan(vector(WATER, 5_000 - quantity), vector(OXYGEN, 40, WATER, 250 + quantity)));
            // A changed collateral unit or a different moved amount is not the plan.
            assertEquals(Status.INVALID_PLAN, baseline.verifyPlan(vector(WATER, 5_000 - quantity), vector(OXYGEN, 39, WATER, 250 + quantity)));
            assertEquals(Status.INVALID_PLAN, baseline.verifyPlan(vector(WATER, 5_000 - quantity), vector(OXYGEN, 40, WATER, 251 + quantity)));
            assertEquals(Status.INVALID_PLAN, baseline.verifyPlan(vector(WATER, 5_000 - quantity, LAVA, 1), baseline.destinationAfter()));
        }
        var source = vector(WATER, 500);
        assertEquals(Status.INVALID_PLAN, Baseline.capture(Scope.FUEL, source, Vector.EMPTY, WATER, 0).status());
        assertEquals(Status.INVALID_PLAN, Baseline.capture(Scope.FUEL, vector(WATER, 5_000), Vector.EMPTY, WATER, 1_001).status());
        assertEquals(Status.INVALID_PLAN, Baseline.capture(Scope.FUEL, source, Vector.EMPTY, WATER, 501).status());
        assertEquals(Status.INVALID_PLAN, Baseline.capture(Scope.FUEL, source, Vector.EMPTY, OXYGEN, 1).status());
        assertEquals(Status.INVALID_KEY, Baseline.capture(Scope.FUEL, source, Vector.EMPTY, null, 1).status());
        assertEquals(Status.OVERFLOW, Baseline.capture(Scope.FUEL, source, vector(WATER, Long.MAX_VALUE), WATER, 1).status());
    }

    @Test void baselineCardinalityAndCanonicalFormAreRefused() {
        // Fuel: two baseline keys. Cargo: one.
        assertEquals(Status.CARDINALITY, Baseline.capture(Scope.FUEL, vector(WATER, 5), vector(OXYGEN, 1, HYDROGEN, 1), WATER, 1).status());
        assertEquals(Status.CARDINALITY, Baseline.capture(Scope.CARGO, vector(WATER, 5), vector(OXYGEN, 1), WATER, 1).status());
        assertEquals(Status.OK, Baseline.capture(Scope.CARGO, vector(WATER, 5), vector(WATER, 9), WATER, 1).status());
        assertEquals(Status.CARDINALITY, new Baseline(Scope.FUEL, List.of()).status());
        var moved = new Entry(WATER, 5, 0, -1, 1);
        assertEquals(Status.DUPLICATE_KEY, new Baseline(Scope.FUEL, List.of(moved, new Entry(WATER, 0, 3, 0, 0))).status());
        assertEquals(Status.UNSORTED_KEYS, new Baseline(Scope.FUEL, List.of(moved, new Entry(OXYGEN, 0, 3, 0, 0))).status());
        assertEquals(Status.OK, new Baseline(Scope.FUEL, List.of(new Entry(OXYGEN, 0, 3, 0, 0), moved)).status());
        assertEquals(Status.NEGATIVE_AMOUNT, new Baseline(Scope.FUEL, List.of(new Entry(WATER, 5, -1, -1, 1))).status());
        assertEquals(Status.NEGATIVE_AMOUNT, new Baseline(Scope.FUEL, List.of(new Entry(WATER, -5, 0, -1, 1))).status());
        // The planned deltas of every key sum to zero.
        assertEquals(Status.INCORRECT_BALANCE, new Baseline(Scope.FUEL, List.of(new Entry(WATER, 5, 0, -1, 2))).status());
        assertEquals(Status.INCORRECT_BALANCE, new Baseline(Scope.FUEL, List.of(new Entry(OXYGEN, 0, 3, 0, 1), moved)).status());
        assertEquals(Status.INVALID_PLAN, new Baseline(Scope.FUEL, List.of(new Entry(WATER, 5, 0, 1, -1))).status(), "The source loses");
        assertEquals(Status.INVALID_PLAN, new Baseline(Scope.FUEL, List.of(new Entry(WATER, 5, 0, 0, 0))).status(), "Nothing moves");
        assertEquals(Status.INVALID_PLAN, new Baseline(Scope.FUEL, List.of(new Entry(OXYGEN, 9, 0, -1, 1), moved)).status(), "Two keys move");
        assertEquals(Status.INVALID_PLAN, new Baseline(Scope.FUEL, List.of(new Entry(OXYGEN, 0, 0, 0, 0), moved)).status(), "An empty collateral key");
        assertEquals(Status.INVALID_PLAN, new Baseline(Scope.FUEL, List.of(new Entry(WATER, 5_000, 0, -1_001, 1_001))).status());
        assertEquals(Status.OVERFLOW, new Baseline(Scope.FUEL, List.of(new Entry(WATER, 5, 0, Long.MIN_VALUE, Long.MIN_VALUE))).status());
    }

    @Test void allFourPartialApplicationsKeepTheOutstandingIdentityForBothQuantities() {
        for (long quantity : new long[] {1, 1_000}) {
            var baseline = baseline(Scope.FUEL, vector(WATER, 5_000), vector(OXYGEN, 300), WATER, quantity);
            long[] expectedOutstanding = {0, quantity, -quantity, 0};
            for (Application application : Application.values()) {
                boolean source = application == Application.SOURCE_ONLY || application == Application.BOTH;
                boolean destination = application == Application.DESTINATION_ONLY || application == Application.BOTH;
                var observedSource = source ? baseline.sourceAfter() : baseline.sourceBefore();
                var observedDestination = destination ? baseline.destinationAfter() : baseline.destinationBefore();
                assertEquals(Optional.of(application), EscrowFluidAccount.classify(baseline, observedSource, observedDestination));
                long outstanding = EscrowFluidAccount.outstanding(baseline, application);
                assertEquals(expectedOutstanding[application.ordinal()], outstanding);
                assertEquals(application == Application.SOURCE_ONLY || application == Application.DESTINATION_ONLY, application.halfApplied());
                // dS + dD + outstanding = 0 for the moved key; every other key has no obligation and no residual.
                assertEquals(0, EscrowFluidAccount.residual(baseline, WATER, observedSource, observedDestination).value() + outstanding);
                assertEquals(0, EscrowFluidAccount.residual(baseline, OXYGEN, observedSource, observedDestination).value());
            }
            // A changed collateral key, a wrong amount or a foreign fluid is none of the four combinations.
            assertEquals(Optional.empty(), EscrowFluidAccount.classify(baseline, baseline.sourceAfter(), vector(OXYGEN, 299, WATER, quantity)));
            assertEquals(Optional.empty(), EscrowFluidAccount.classify(baseline, baseline.sourceAfter(), vector(OXYGEN, 300, WATER, quantity + 1)));
            assertEquals(Optional.empty(), EscrowFluidAccount.classify(baseline, baseline.sourceAfter(), vector(OXYGEN, 300, LAVA, quantity)));
        }
        assertEquals(-250, EscrowFluidAccount.delta(750, 1_000).value()); assertEquals(250, EscrowFluidAccount.delta(1_000, 750).value());
        assertEquals(Status.NEGATIVE_AMOUNT, EscrowFluidAccount.delta(-1, 0).status());
    }

    @Test void gainsLossesAndAnAcceptedHalfAppliedTransferAreSignedDiscrepancies() {
        // The design's examples: baseline source 5,000 and destination 0, 1,000 mB planned.
        var complete = single(1_000, vector(WATER, 4_000), vector(WATER, 1_000));
        assertEquals(Knowledge.KNOWN, complete.knowledge()); assertEquals(Map.of(WATER, 0L), complete.discrepancy()); assertTrue(complete.balanced());
        var loss = single(1_000, vector(WATER, 4_000), vector(WATER, 900));
        assertEquals(Map.of(WATER, 100L), loss.discrepancy()); assertFalse(loss.balanced());
        // Destination-only: the outstanding obligation -1,000 is frozen as a gain of 1,000, never as a completed transfer.
        var baseline = loss.rows().get(id(1)).baseline();
        var destinationOnly = single(1_000, vector(WATER, 5_000), vector(WATER, 1_000));
        assertEquals(Map.of(WATER, -1_000L), destinationOnly.discrepancy()); assertFalse(destinationOnly.balanced());
        assertEquals(Optional.of(Application.DESTINATION_ONLY), EscrowFluidAccount.classify(baseline, vector(WATER, 5_000), vector(WATER, 1_000)));
        assertEquals(-1_000, EscrowFluidAccount.outstanding(baseline, Application.DESTINATION_ONLY));
        // Source-only: a loss of +q.
        var sourceOnly = single(1_000, vector(WATER, 4_000), Vector.EMPTY);
        assertEquals(Map.of(WATER, 1_000L), sourceOnly.discrepancy());
        assertEquals(EscrowFluidAccount.outstanding(baseline, Application.SOURCE_ONLY), sourceOnly.discrepancy().get(WATER));
        var one = single(1, vector(WATER, 4_999), Vector.EMPTY);
        assertEquals(Map.of(WATER, 1L), one.discrepancy());
        // The frozen row stores the observed amounts and the one signed adjustment, and its own identity holds.
        var row = loss.rows().get(id(1));
        assertEquals(List.of(new FrozenEntry(WATER, OptionalLong.of(4_000), OptionalLong.of(900), OptionalLong.of(100), id(1))), row.frozen().entries());
        assertEquals(List.of(id(1)), row.frozen().members()); assertEquals(Status.OK, row.status());
        assertFalse(row.frozen().entries().get(0).shared(id(1)));
        var wrong = new Row(id(1), row.baseline(), new Frozen(Knowledge.KNOWN, id(1), List.of(id(1)),
                List.of(new FrozenEntry(WATER, OptionalLong.of(4_000), OptionalLong.of(900), OptionalLong.of(0), id(1)))));
        assertEquals(Status.INCORRECT_BALANCE, wrong.status(), "A completed transfer cannot be claimed over a residual");
    }

    @Test void theSameTotalInADifferentFluidIsALossAndAGainNotABalance() {
        // 100 mB of water replaced by 100 mB of oxygen: the scalar total is unchanged.
        var settled = single(1_000, vector(WATER, 4_000), vector(WATER, 900, OXYGEN, 100));
        assertEquals(Map.of(WATER, 100L, OXYGEN, -100L), settled.discrepancy()); assertFalse(settled.balanced());
        var entries = settled.rows().get(id(1)).frozen().entries();
        assertEquals(List.of(OXYGEN, WATER), entries.stream().map(FrozenEntry::key).toList());
        assertEquals(OptionalLong.of(-100), entries.get(0).adjustment()); assertEquals(OptionalLong.of(100), entries.get(1).adjustment());
        // A different tag is a different fluid too, including a tag that appears where none was.
        var retagged = single(1_000, vector(WATER, 4_000), vector(TAGGED_WATER, 1_000));
        assertEquals(1_000L, retagged.discrepancy().get(WATER)); assertEquals(-1_000L, retagged.discrepancy().get(TAGGED_WATER));
        var emptyTag = key("minecraft:water", EMPTY_TAG_DIGEST);
        var absentAgainstEmpty = single(1_000, vector(WATER, 4_000), vector(emptyTag, 1_000));
        assertEquals(1_000L, absentAgainstEmpty.discrepancy().get(WATER)); assertEquals(-1_000L, absentAgainstEmpty.discrepancy().get(emptyTag));
    }

    @Test void twoFuelBanksAreOneAggregatedDestinationPerKey() {
        // Hydrogen into the primary bank while the oxidizer bank holds oxygen; the rocket vector covers both banks.
        var before = vector(HYDROGEN, 200, OXYGEN, 300);
        var member = new Member(id(1), 1, device(10), rocket(20, "FUEL"), baseline(Scope.FUEL, vector(HYDROGEN, 5_000), before, HYDROGEN, 1_000), false);
        var settled = EscrowFluidAccount.settle(List.of(member), finals(device(10), vector(HYDROGEN, 4_000),
                rocket(20, "FUEL"), vector(HYDROGEN, 1_200, OXYGEN, 300))).value();
        assertTrue(settled.balanced()); assertEquals(Map.of(HYDROGEN, 0L, OXYGEN, 0L), settled.discrepancy());
        // A fuel-for-oxidizer substitution keeps the total and is still a discrepancy of both keys.
        var substituted = EscrowFluidAccount.settle(List.of(member), finals(device(10), vector(HYDROGEN, 4_000),
                rocket(20, "FUEL"), vector(HYDROGEN, 200, OXYGEN, 1_300))).value();
        assertEquals(Map.of(HYDROGEN, 1_000L, OXYGEN, -1_000L), substituted.discrepancy());
        // The same key in both banks: its split between the banks is invisible to the account (section 2.1).
        var both = new Member(id(2), 2, device(11), rocket(21, "FUEL"),
                baseline(Scope.FUEL, vector(WATER, 5_000), vector(WATER, 100, WATER, 50), WATER, 1_000), false);
        for (var split : List.of(vector(WATER, 1_100, WATER, 50), vector(WATER, 150, WATER, 1_000))) {
            assertTrue(EscrowFluidAccount.settle(List.of(both), finals(device(11), vector(WATER, 4_000), rocket(21, "FUEL"), split)).value().balanced());
        }
    }

    @Test void bothCargoDirectionsBalanceAndKeepTheirOneKeyScope() {
        var tank = rocket(20, "CARGO:1,2,3");
        var load = new Member(id(1), 1, device(10), tank, baseline(Scope.CARGO, vector(WATER, 16_000), vector(WATER, 2_000), WATER, 1_000), false);
        var loaded = EscrowFluidAccount.settle(List.of(load), finals(device(10), vector(WATER, 15_000), tank, vector(WATER, 3_000))).value();
        assertTrue(loaded.balanced());
        // Unload: the rocket is the source of the transfer.
        var unload = new Member(id(2), 2, tank, device(11), baseline(Scope.CARGO, vector(WATER, 3_000), Vector.EMPTY, WATER, 1_000), false);
        var unloaded = EscrowFluidAccount.settle(List.of(unload), finals(tank, vector(WATER, 2_000), device(11), vector(WATER, 1_000))).value();
        assertTrue(unloaded.balanced());
        assertEquals(new FrozenEntry(WATER, OptionalLong.of(2_000), OptionalLong.of(1_000), OptionalLong.of(0), id(2)),
                unloaded.rows().get(id(2)).frozen().entries().get(0));
        var shortUnload = EscrowFluidAccount.settle(List.of(unload), finals(tank, vector(WATER, 2_000), device(11), vector(WATER, 400))).value();
        assertEquals(Map.of(WATER, 600L), shortUnload.discrepancy());
        // Cargo keeps three keys at most: the baseline key and one foreign key per tank.
        assertEquals(Status.OK, EscrowFluidAccount.settle(List.of(unload), finals(tank, vector(LAVA, 1), device(11), vector(OXYGEN, 1))).status());
        var fuel = new Member(id(3), 3, device(12), rocket(22, "FUEL"), baseline(Scope.FUEL, vector(WATER, 5_000), vector(OXYGEN, 1), WATER, 1), false);
        assertEquals(Status.OK, EscrowFluidAccount.settle(List.of(fuel), finals(device(12), vector(LAVA, 1), rocket(22, "FUEL"),
                vector(HYDROGEN, 1, MILK, 1))).status(), "Five keys: two baseline keys and three observed ones");
    }

    @Test void aMissingQuantityIsUnknownAndNeverZero() {
        var missingDestination = single(1_000, vector(WATER, 4_000), null);
        assertEquals(Knowledge.UNKNOWN_ENDPOINT, missingDestination.knowledge()); assertFalse(missingDestination.balanced());
        assertTrue(missingDestination.discrepancy().isEmpty(), "No loss is computed from an unknown contribution");
        var entry = missingDestination.rows().get(id(1)).frozen().entries().get(0);
        assertEquals(OptionalLong.of(4_000), entry.source(), "The known side is kept");
        assertEquals(OptionalLong.empty(), entry.destination()); assertEquals(OptionalLong.empty(), entry.adjustment());
        assertThrows(java.util.NoSuchElementException.class, () -> entry.destination().getAsLong());
        // The planned transfer stays in the baseline as the liability, separate from the unknown loss.
        assertEquals(1_000, missingDestination.rows().get(id(1)).baseline().quantity());
        var missingBoth = single(1_000, null, null);
        assertEquals(Knowledge.UNKNOWN_ENDPOINT, missingBoth.knowledge());
        // An observation that was never supplied is a refusal, not a missing endpoint.
        var member = new Member(id(1), 1, device(10), rocket(20, "FUEL"), baseline(Scope.FUEL, vector(WATER, 5_000), Vector.EMPTY, WATER, 1), false);
        assertEquals(Status.MISSING_OBSERVATION, EscrowFluidAccount.settle(List.of(member), finals(device(10), vector(WATER, 4_999))).status());
        // A row cannot claim KNOWN without both sides, an adjustment under an unknown side, or a zero for it.
        var baseline = member.baseline();
        var known = new Frozen(Knowledge.KNOWN, id(1), List.of(id(1)),
                List.of(new FrozenEntry(WATER, OptionalLong.of(4_999), OptionalLong.empty(), OptionalLong.of(0), id(1))));
        assertEquals(Status.INVALID_KNOWLEDGE, new Row(id(1), baseline, known).status());
        var adjusted = new Frozen(Knowledge.UNKNOWN_ENDPOINT, id(1), List.of(id(1)),
                List.of(new FrozenEntry(WATER, OptionalLong.of(4_999), OptionalLong.empty(), OptionalLong.of(1), id(1))));
        assertEquals(Status.INVALID_KNOWLEDGE, new Row(id(1), baseline, adjusted).status());
        var observed = new Frozen(Knowledge.UNKNOWN_ENDPOINT, id(1), List.of(id(1)),
                List.of(new FrozenEntry(WATER, OptionalLong.of(4_999), OptionalLong.of(1), OptionalLong.empty(), id(1))));
        assertEquals(Status.INVALID_KNOWLEDGE, new Row(id(1), baseline, observed).status(), "Nothing is unknown in this row");
    }

    @Test void arithmeticIsCheckedAndOverflowIsARefusal() {
        long huge = Long.MAX_VALUE - 1_000;
        var member = new Member(id(1), 1, device(10), rocket(20, "FUEL"),
                baseline(Scope.FUEL, vector(WATER, Long.MAX_VALUE), vector(WATER, huge), WATER, 1_000), false);
        assertEquals(Status.OVERFLOW, EscrowFluidAccount.settle(List.of(member), finals(device(10), Vector.EMPTY, rocket(20, "FUEL"), Vector.EMPTY)).status());
        assertEquals(Status.OVERFLOW, EscrowFluidAccount.residual(new Baseline(Scope.FUEL, List.of(new Entry(WATER, 1_000, 0, -1_000, 1_000))),
                WATER, vector(WATER, Long.MAX_VALUE), vector(WATER, Long.MAX_VALUE)).status());
        assertEquals(Status.OVERFLOW, vector(WATER, Long.MAX_VALUE).plus(WATER, 1).status());
        assertEquals(Status.OVERFLOW, EscrowFluidAccount.project(vector(WATER, 1), List.of(new Delta(WATER, Long.MIN_VALUE))).status());
        assertEquals(Status.OVERFLOW, EscrowFluidAccount.withoutLater(vector(WATER, 1), List.of(new Delta(WATER, Long.MIN_VALUE))).status());
        // The row identity is checked too, not wrapped.
        var row = new Row(id(1), member.baseline(), new Frozen(Knowledge.KNOWN, id(1), List.of(id(1)),
                List.of(new FrozenEntry(WATER, OptionalLong.of(0), OptionalLong.of(0), OptionalLong.of(Long.MAX_VALUE), id(1)))));
        assertEquals(Status.OVERFLOW, row.status());
    }

    @Test void frozenCardinalitiesAndDuplicateKeysAreRefused() {
        var member = new Member(id(1), 1, device(10), rocket(20, "FUEL"),
                baseline(Scope.FUEL, vector(WATER, 5_000), vector(OXYGEN, 300), WATER, 1_000), false);
        // Six keys in a fuel union: two baseline keys and four observed ones.
        assertEquals(Status.CARDINALITY, EscrowFluidAccount.settle(List.of(member), finals(device(10), vector(LAVA, 1, MILK, 1),
                rocket(20, "FUEL"), vector(HYDROGEN, 1, TAGGED_WATER, 1))).status());
        var cargo = new Member(id(2), 2, device(11), rocket(21, "CARGO"), baseline(Scope.CARGO, vector(WATER, 5_000), Vector.EMPTY, WATER, 1), false);
        assertEquals(Status.CARDINALITY, EscrowFluidAccount.settle(List.of(cargo), finals(device(11), vector(LAVA, 1, MILK, 1),
                rocket(21, "CARGO"), vector(HYDROGEN, 1))).status());
        var baseline = cargo.baseline();
        var water = new FrozenEntry(WATER, OptionalLong.of(4_999), OptionalLong.of(1), OptionalLong.of(0), id(2));
        var lava = new FrozenEntry(LAVA, OptionalLong.of(1), OptionalLong.of(0), OptionalLong.of(-1), id(2));
        assertEquals(Status.OK, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(2), List.of(id(2)), List.of(lava, water))).status());
        assertEquals(Status.UNSORTED_KEYS, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(2), List.of(id(2)), List.of(water, lava))).status());
        assertEquals(Status.DUPLICATE_KEY, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(2), List.of(id(2)), List.of(water, water))).status());
        assertEquals(Status.CARDINALITY, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(2), List.of(id(2)), List.of())).status());
        assertEquals(Status.CARDINALITY, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(2), List.of(id(2)), List.of(lava))).status(),
                "The union contains every baseline key");
        var nothing = new FrozenEntry(LAVA, OptionalLong.of(0), OptionalLong.of(0), OptionalLong.of(0), id(2));
        assertEquals(Status.CARDINALITY, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(2), List.of(id(2)), List.of(nothing, water))).status());
        assertEquals(Status.NEGATIVE_AMOUNT, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(2), List.of(id(2)),
                List.of(new FrozenEntry(WATER, OptionalLong.of(-1), OptionalLong.of(1), OptionalLong.of(0), id(2))))).status());
        assertEquals(Status.INVALID_MASK, new Row(id(2), baseline, new Frozen(Knowledge.UNKNOWN_ENDPOINT, id(2), List.of(id(2)),
                List.of(new FrozenEntry(LAVA, OptionalLong.of(1), OptionalLong.empty(), OptionalLong.empty(), id(2)),
                        new FrozenEntry(WATER, OptionalLong.of(1), OptionalLong.of(1), OptionalLong.empty(), id(2))))).status(),
                "An endpoint is observed for every key or for none");
        for (Knowledge invalid : List.of(Knowledge.PLANNED, Knowledge.NOT_APPLICABLE)) {
            assertEquals(Status.INVALID_KNOWLEDGE, new Row(id(2), baseline, new Frozen(invalid, id(2), List.of(id(2)), List.of(water))).status());
        }
        assertEquals(Knowledge.PLANNED, new Row(id(2), baseline, null).knowledge()); assertEquals(Status.OK, new Row(id(2), baseline, null).status());
        // Member list: only in the group owner's row, the owner first, no duplicates, at most 64.
        assertEquals(Status.INVALID_MEMBERS, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(2), List.of(), List.of(water))).status());
        assertEquals(Status.INVALID_MEMBERS, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(2), List.of(id(3), id(2)), List.of(water))).status());
        assertEquals(Status.INVALID_MEMBERS, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(2), List.of(id(2), id(3), id(3)), List.of(water))).status());
        assertEquals(Status.INVALID_MEMBERS, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(9), List.of(id(9), id(2)), List.of(water))).status());
        var many = new ArrayList<UUID>(); for (int index = 0; index < 65; index++) { many.add(id(2 + index)); }
        assertEquals(Status.INVALID_MEMBERS, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(2), many, List.of(water))).status());
        assertEquals(Status.INVALID_OWNER, new Row(id(2), baseline, new Frozen(Knowledge.KNOWN, id(2), List.of(id(2)),
                List.of(new FrozenEntry(WATER, OptionalLong.of(4_999), OptionalLong.of(1), OptionalLong.empty(), id(8))))).status());
    }

    @Test void chainOnTheSameBankDoesNotChargeTheLaterFillToTheEarlierMember() {
        var bank = rocket(20, "FUEL");
        var first = new Member(id(1), 1, device(10), bank, baseline(Scope.FUEL, vector(WATER, 5_000), vector(WATER, 100), WATER, 1_000), false);
        // Admission projects the unresolved first fill: the second baseline is the projected amount, not the live one.
        var projected = EscrowFluidAccount.project(vector(WATER, 100), List.of(first.baseline().destinationDelta())).value();
        assertEquals(vector(WATER, 1_100), projected);
        var second = new Member(id(2), 2, device(11), bank, baseline(Scope.FUEL, vector(WATER, 3_000), projected, WATER, 700), false);
        // C6: the frontier holds both fills. Subtract the proved later member before judging the first.
        var frontier = vector(WATER, 1_800);
        assertEquals(first.baseline().destinationAfter(), EscrowFluidAccount.withoutLater(frontier, List.of(second.baseline().destinationDelta())).value());
        assertNotEquals(first.baseline().destinationAfter(), frontier, "Without the subtraction the first fill would be charged 700 mB");
        assertEquals(second.baseline().destinationAfter(), EscrowFluidAccount.withoutLater(frontier, List.of()).value());
        // A frontier below the recorded later delta is a broken chain, not a clamped zero.
        assertEquals(Status.BROKEN_CHAIN, EscrowFluidAccount.withoutLater(vector(WATER, 500), List.of(second.baseline().destinationDelta())).status());
        var settled = EscrowFluidAccount.settle(List.of(second, first), finals(device(10), vector(WATER, 4_000), device(11), vector(WATER, 2_300),
                bank, frontier)).value();
        assertTrue(settled.balanced()); assertEquals(id(1), settled.groupOwner()); assertEquals(List.of(id(1), id(2)), settled.members());
        // A second baseline taken from the live amount instead of the projection breaks numeric continuity.
        var live = new Member(id(2), 2, device(11), bank, baseline(Scope.FUEL, vector(WATER, 3_000), vector(WATER, 100), WATER, 700), false);
        assertEquals(Status.BROKEN_CHAIN, EscrowFluidAccount.settle(List.of(first, live), finals(device(10), vector(WATER, 4_000),
                device(11), vector(WATER, 2_300), bank, frontier)).status());
    }

    @Test void chainOnAlternatingBanksMergesTheFullBankVectorOnce() {
        var banks = rocket(20, "FUEL");
        var fuel = new Member(id(1), 1, device(10), banks, baseline(Scope.FUEL, vector(HYDROGEN, 5_000), vector(OXYGEN, 50), HYDROGEN, 1_000), false);
        var afterFuel = EscrowFluidAccount.project(vector(OXYGEN, 50), List.of(fuel.baseline().destinationDelta())).value();
        var oxidizer = new Member(id(2), 2, device(11), banks, baseline(Scope.FUEL, vector(OXYGEN, 5_000), afterFuel, OXYGEN, 500), false);
        var afterBoth = EscrowFluidAccount.project(afterFuel, List.of(oxidizer.baseline().destinationDelta())).value();
        var third = new Member(id(3), 3, device(12), banks, baseline(Scope.FUEL, vector(HYDROGEN, 900), afterBoth, HYDROGEN, 900), false);
        var frontier = vector(HYDROGEN, 1_900, OXYGEN, 550);
        assertEquals(fuel.baseline().destinationAfter(), EscrowFluidAccount.withoutLater(frontier,
                List.of(oxidizer.baseline().destinationDelta(), third.baseline().destinationDelta())).value());
        assertEquals(oxidizer.baseline().destinationAfter(), EscrowFluidAccount.withoutLater(frontier, List.of(third.baseline().destinationDelta())).value());
        var observed = finals(device(10), vector(HYDROGEN, 4_000), device(11), vector(OXYGEN, 4_500), device(12), Vector.EMPTY, banks, frontier);
        var settled = EscrowFluidAccount.settle(List.of(fuel, oxidizer, third), observed).value();
        assertTrue(settled.balanced()); assertEquals(Map.of(HYDROGEN, 0L, OXYGEN, 0L), settled.discrepancy());
        // The rocket is one unit: 50 oxygen at its earliest baseline, counted once, not once per member.
        var short50 = new HashMap<>(observed); short50.put(banks, Optional.of(vector(HYDROGEN, 1_900, OXYGEN, 500)));
        assertEquals(Map.of(HYDROGEN, 0L, OXYGEN, 50L), EscrowFluidAccount.settle(List.of(fuel, oxidizer, third), short50).value().discrepancy());
    }

    @Test void chainThroughACargoDeviceProjectsTheDeviceTankToo() {
        // One device loads two rockets in turn; its tank has a prefix chain like a bank has.
        var tankA = rocket(20, "CARGO:a"); var tankB = rocket(30, "CARGO:b");
        var first = new Member(id(1), 1, device(10), tankA, baseline(Scope.CARGO, vector(WATER, 8_000), Vector.EMPTY, WATER, 1_000), false);
        var projected = EscrowFluidAccount.project(vector(WATER, 8_000), List.of(first.baseline().sourceDelta())).value();
        var second = new Member(id(2), 2, device(10), tankB, baseline(Scope.CARGO, projected, vector(WATER, 10), WATER, 250), false);
        var frontier = vector(WATER, 6_750);
        assertEquals(first.baseline().sourceAfter(), EscrowFluidAccount.withoutLater(frontier, List.of(second.baseline().sourceDelta())).value());
        var observed = finals(device(10), frontier, tankA, vector(WATER, 1_000), tankB, vector(WATER, 260));
        var settled = EscrowFluidAccount.settle(List.of(first, second), observed).value();
        assertTrue(settled.balanced());
        // An unload into a device that an earlier unload already filled follows the same rule.
        var unloadA = new Member(id(3), 3, tankA, device(11), baseline(Scope.CARGO, vector(WATER, 1_000), Vector.EMPTY, WATER, 400), false);
        var unloadB = new Member(id(4), 4, tankB, device(11), baseline(Scope.CARGO, vector(WATER, 260), vector(WATER, 400), WATER, 60), false);
        var unloaded = EscrowFluidAccount.settle(List.of(unloadA, unloadB), finals(tankA, vector(WATER, 600), tankB, vector(WATER, 200),
                device(11), vector(WATER, 455))).value();
        assertEquals(Map.of(WATER, 5L), unloaded.discrepancy());
        assertEquals(Status.BROKEN_CHAIN, EscrowFluidAccount.settle(List.of(first,
                new Member(id(2), 2, device(10), tankB, baseline(Scope.CARGO, vector(WATER, 8_000), vector(WATER, 10), WATER, 250), false)), observed).status());
    }

    @Test void aComponentCountsEachPhysicalUnitOnceByItsFullTypedKey() {
        var banks = rocket(20, "FUEL");
        var first = new Member(id(1), 1, device(10), banks, baseline(Scope.FUEL, vector(WATER, 5_000), Vector.EMPTY, WATER, 1_000), false);
        var second = new Member(id(2), 2, device(11), banks, baseline(Scope.FUEL, vector(WATER, 5_000), vector(WATER, 1_000), WATER, 1_000), false);
        var observed = finals(device(10), vector(WATER, 4_000), device(11), vector(WATER, 4_000), banks, vector(WATER, 2_000));
        var settled = EscrowFluidAccount.settle(List.of(first, second), observed).value();
        // Summing the rocket total of both receipts would report a gain of 2,000; the deduplicated net is zero.
        assertTrue(settled.balanced());
        long naive = 0;
        for (var row : settled.rows().values()) {
            var entry = row.frozen().entries().get(0);
            naive += entry.source().getAsLong() - row.baseline().source(WATER) + entry.destination().getAsLong() - row.baseline().destination(WATER);
        }
        assertEquals(1_000, naive, "The per-row sum double counts the shared rocket and is not the component balance");
        // A cross-kind UUID alias is two endpoints: the same UUID as a block entity and as an entity (W114F).
        var aliasDevice = new UnitRef(new EscrowEndpoint.Key(EscrowEndpoint.Kind.BLOCK_ENTITY, "minecraft:overworld", banks.endpoint().identity(), null,
                "advancedrocketrycommunity:fluid_fuel_loader", 0, 64, 0, EscrowEndpoint.Unit.TANK), "TANK");
        assertEquals(aliasDevice.endpoint().identity(), banks.endpoint().identity()); assertNotEquals(aliasDevice, banks);
        var other = rocket(40, "FUEL");
        var aliased = new Member(id(3), 3, aliasDevice, other, baseline(Scope.FUEL, vector(WATER, 700), Vector.EMPTY, WATER, 700), false);
        var all = new HashMap<>(observed); all.put(aliasDevice, Optional.of(Vector.EMPTY)); all.put(other, Optional.of(vector(WATER, 700)));
        var joined = EscrowFluidAccount.settle(List.of(first, second, aliased), all).value();
        assertTrue(joined.balanced()); assertEquals(3, joined.rows().size());
        // The same endpoint under another resource scope is another unit, so a cargo tank is not merged with the banks.
        assertNotEquals(rocket(20, "FUEL"), rocket(20, "CARGO:1,2,3"));
    }

    @Test void aSharedAdjustmentIsOwnedByTheLowestSequenceMemberAndStoredOnce() {
        var banks = rocket(20, "FUEL");
        var first = new Member(id(5), 1, device(10), banks, baseline(Scope.FUEL, vector(WATER, 5_000), Vector.EMPTY, WATER, 1_000), false);
        var second = new Member(id(4), 2, device(11), banks, baseline(Scope.FUEL, vector(OXYGEN, 5_000), vector(WATER, 1_000), OXYGEN, 1_000), false);
        // The rocket lost 100 water; the second loader gave 1,000 oxygen and the rocket shows 990.
        var settled = EscrowFluidAccount.settle(List.of(second, first), finals(device(10), vector(WATER, 4_000), device(11), vector(OXYGEN, 4_000),
                banks, vector(WATER, 900, OXYGEN, 990))).value();
        assertEquals(Map.of(WATER, 100L, OXYGEN, 10L), settled.discrepancy());
        assertEquals(id(5), settled.groupOwner(), "The lowest sequence, not the smallest UUID");
        var ownerRow = settled.rows().get(id(5)); var otherRow = settled.rows().get(id(4));
        assertEquals(List.of(id(5), id(4)), ownerRow.frozen().members()); assertTrue(otherRow.frozen().members().isEmpty());
        // Both keys are in the first member's own union (the rocket shows both), so it owns both adjustments.
        for (var entry : otherRow.frozen().entries()) {
            assertEquals(id(5), entry.owner()); assertTrue(entry.shared(id(4))); assertEquals(OptionalLong.empty(), entry.adjustment());
        }
        long water = 0, oxygen = 0;
        for (var row : settled.rows().values()) {
            for (var entry : row.frozen().entries()) {
                if (entry.adjustment().isPresent()) { if (entry.key().equals(WATER)) { water += entry.adjustment().getAsLong(); } else { oxygen += entry.adjustment().getAsLong(); } }
            }
        }
        assertEquals(100, water); assertEquals(10, oxygen);
        assertEquals(Status.OK, EscrowFluidAccount.verifyGroup(List.of(otherRow, ownerRow)));
        // A key only a later member sees is owned by that member.
        var late = EscrowFluidAccount.settle(List.of(first, second), finals(device(10), vector(WATER, 4_000), device(11), vector(OXYGEN, 4_000, LAVA, 3),
                banks, vector(WATER, 1_000, OXYGEN, 1_000))).value();
        assertEquals(Map.of(WATER, 0L, OXYGEN, 0L, LAVA, -3L), late.discrepancy());
        var lava = late.rows().get(id(4)).frozen().entries().stream().filter(entry -> entry.key().equals(LAVA)).findFirst().orElseThrow();
        assertEquals(id(4), lava.owner()); assertEquals(OptionalLong.of(-3), lava.adjustment());
        assertEquals(Status.OK, EscrowFluidAccount.verifyGroup(List.copyOf(late.rows().values())));
        // No group releases with a missing owner row, a different member set, or a second copy of an adjustment.
        assertEquals(Status.INVALID_MEMBERS, EscrowFluidAccount.verifyGroup(List.of(otherRow)));
        assertEquals(Status.INVALID_MEMBERS, EscrowFluidAccount.verifyGroup(List.of(ownerRow)));
        assertEquals(Status.DUPLICATE_ROW, EscrowFluidAccount.verifyGroup(List.of(ownerRow, ownerRow)));
        var copied = new Row(id(4), otherRow.baseline(), new Frozen(Knowledge.KNOWN, id(5), List.of(), otherRow.frozen().entries().stream()
                .map(entry -> new FrozenEntry(entry.key(), entry.source(), entry.destination(), OptionalLong.of(100), entry.owner())).toList()));
        assertEquals(Status.INVALID_OWNER, copied.status()); assertEquals(Status.INVALID_OWNER, EscrowFluidAccount.verifyGroup(List.of(ownerRow, copied)));
        var stranger = new Row(id(4), otherRow.baseline(), new Frozen(Knowledge.KNOWN, id(5), List.of(), otherRow.frozen().entries().stream()
                .map(entry -> new FrozenEntry(entry.key(), entry.source(), entry.destination(), OptionalLong.empty(), id(99))).toList()));
        assertEquals(Status.INVALID_OWNER, EscrowFluidAccount.verifyGroup(List.of(ownerRow, stranger)));
        var lateOther = late.rows().get(id(4));
        var misattributed = new Row(id(4), lateOther.baseline(), new Frozen(Knowledge.KNOWN, id(5), List.of(), lateOther.frozen().entries().stream()
                .map(entry -> new FrozenEntry(entry.key(), entry.source(), entry.destination(), OptionalLong.empty(), id(5))).toList()));
        assertEquals(Status.OK, misattributed.status());
        assertEquals(Status.INVALID_OWNER, EscrowFluidAccount.verifyGroup(List.of(late.rows().get(id(5)), misattributed)),
                "The named owner's row lacks the key it is said to own");
        assertEquals(Status.INVALID_KNOWLEDGE, EscrowFluidAccount.verifyGroup(List.of(ownerRow, new Row(id(4), otherRow.baseline(), null))));
    }

    @Test void mixedComponentsKeepItemAndLegacyMembersOutOfTheFluidBalance() {
        var banks = rocket(20, "FUEL");
        var fluid = new Member(id(2), 2, device(10), banks, baseline(Scope.FUEL, vector(WATER, 5_000), Vector.EMPTY, WATER, 1_000), false);
        var item = Member.item(id(1), 1, device(50), rocket(20, "ITEMS"));
        var observed = finals(device(10), vector(WATER, 4_000), banks, vector(WATER, 1_000));
        var withItem = EscrowFluidAccount.settle(List.of(item, fluid), observed).value();
        assertEquals(Knowledge.KNOWN, withItem.knowledge()); assertTrue(withItem.balanced());
        assertEquals(java.util.Set.of(id(2)), withItem.rows().keySet(), "An item member has no row");
        assertEquals(id(2), withItem.groupOwner()); assertEquals(List.of(id(2)), withItem.members());
        // A legacy fluid member makes the whole component UNKNOWN_BASELINE; known amounts stay as diagnostics only.
        var legacy = Member.legacy(id(3), 3, device(60), banks);
        var withLegacy = EscrowFluidAccount.settle(List.of(fluid, legacy), observed).value();
        assertEquals(Knowledge.UNKNOWN_BASELINE, withLegacy.knowledge()); assertFalse(withLegacy.balanced()); assertTrue(withLegacy.discrepancy().isEmpty());
        var entry = withLegacy.rows().get(id(2)).frozen().entries().get(0);
        assertEquals(OptionalLong.of(4_000), entry.source()); assertEquals(OptionalLong.of(1_000), entry.destination());
        assertEquals(OptionalLong.empty(), entry.adjustment());
        assertEquals(java.util.Set.of(id(2)), withLegacy.rows().keySet());
        // Components without any row: item only is not applicable, legacy only has an unknown baseline.
        assertEquals(Knowledge.NOT_APPLICABLE, EscrowFluidAccount.settle(List.of(item), Map.of()).value().knowledge());
        assertEquals(Knowledge.UNKNOWN_BASELINE, EscrowFluidAccount.settle(List.of(item, legacy), Map.of()).value().knowledge());
        assertTrue(EscrowFluidAccount.settle(List.of(item), Map.of()).value().rows().isEmpty());
        assertEquals(Status.INVALID_MEMBERS, EscrowFluidAccount.settle(List.of(new Member(id(3), 3, device(60), banks, fluid.baseline(), true)), observed).status());
    }

    @Test void componentBoundsAreRefusedNotTruncated() {
        assertEquals(Status.COMPONENT_LIMIT, EscrowFluidAccount.settle(List.of(), Map.of()).status());
        var members = new ArrayList<Member>(); var observed = new HashMap<UnitRef, Optional<Vector>>();
        for (int index = 1; index <= 65; index++) {
            members.add(new Member(id(index), index, device(1_000 + index), rocket(5_000 + index * 2L, "CARGO"),
                    baseline(Scope.CARGO, vector(WATER, 2_000), Vector.EMPTY, WATER, 1_000), false));
            observed.put(device(1_000 + index), Optional.of(vector(WATER, 1_000)));
            observed.put(rocket(5_000 + index * 2L, "CARGO"), Optional.of(vector(WATER, 1_000)));
        }
        assertEquals(Status.COMPONENT_LIMIT, EscrowFluidAccount.settle(members, observed).status());
        var full = EscrowFluidAccount.settle(members.subList(0, 64), observed).value();
        assertTrue(full.balanced()); assertEquals(64, full.rows().size()); assertEquals(64, full.rows().get(id(1)).frozen().members().size());
        assertEquals(Status.OK, EscrowFluidAccount.verifyGroup(List.copyOf(full.rows().values())));
        var first = members.get(0);
        assertEquals(Status.INVALID_MEMBERS, EscrowFluidAccount.settle(List.of(first, first), observed).status());
        assertEquals(Status.INVALID_MEMBERS, EscrowFluidAccount.settle(List.of(first, new Member(id(2), 1, first.source(), first.destination(),
                first.baseline(), false)), observed).status(), "A sequence is used once");
        assertEquals(Status.INCORRECT_BALANCE, EscrowFluidAccount.settle(List.of(new Member(id(1), 1, first.source(), first.destination(),
                new Baseline(Scope.CARGO, List.of(new Entry(WATER, 5, 0, -1, 2))), false)), observed).status());
    }
}
