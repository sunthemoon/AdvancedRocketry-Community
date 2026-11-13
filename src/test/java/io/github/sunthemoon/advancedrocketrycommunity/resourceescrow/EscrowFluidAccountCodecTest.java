package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccountTest.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Baseline;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Frozen;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.FrozenEntry;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Knowledge;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Member;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Row;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Scope;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Status;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.UnitRef;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Vector;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccountCodec.Limits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W361: account row schema 1 (W357 section 4.1) and every bound of section 5. */
class EscrowFluidAccountCodecTest {
    static final Limits MAX = EscrowFluidAccountCodec.MAXIMUM;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    /** A fuel fill with a collateral oxygen bank; frozen KNOWN with a foreign lava unit and a 100 mB water loss. */
    static Row frozenRow() {
        var member = new Member(id(1), 1, device(10), rocket(20, "FUEL"),
                baseline(Scope.FUEL, vector(WATER, 5_000), vector(OXYGEN, 300), WATER, 1_000), false);
        return EscrowFluidAccount.settle(List.of(member), finals(device(10), vector(WATER, 4_000), rocket(20, "FUEL"),
                vector(WATER, 900, OXYGEN, 300, LAVA, 7))).value().rows().get(id(1));
    }
    static Row plannedRow() { return new Row(id(1), frozenRow().baseline(), null); }
    static CompoundTag encoded(Row row) {
        var result = EscrowFluidAccountCodec.encode(row, MAX);
        assertEquals(Status.OK, result.status()); return result.value();
    }
    private static Status decodedWith(Row row, Consumer<CompoundTag> change) {
        var tag = encoded(row); change.accept(tag); return EscrowFluidAccountCodec.decode(tag, MAX).status();
    }
    private static CompoundTag entry(CompoundTag row, String part, int index) { return row.getCompound(part).getList("e", 10).getCompound(index); }

    @Test void plannedAndFrozenRowsRoundTripInTheClosedShortKeyForm() {
        for (Row row : List.of(plannedRow(), frozenRow())) {
            var tag = encoded(row);
            assertEquals(row, EscrowFluidAccountCodec.decode(tag, MAX).value());
            assertEquals(tag, encoded(EscrowFluidAccountCodec.decode(tag, MAX).value()));
            assertEquals(1, tag.getInt("v")); assertEquals(id(1), tag.getUUID("op"));
            assertEquals(row.frozen() == null ? java.util.Set.of("v", "op", "b") : java.util.Set.of("v", "op", "b", "f"), tag.getAllKeys());
        }
        var tag = encoded(frozenRow());
        assertEquals(java.util.Set.of("s", "e"), tag.getCompound("b").getAllKeys()); assertEquals(0, tag.getCompound("b").getByte("s"));
        assertEquals(java.util.Set.of("i", "d", "s", "t", "x", "y"), entry(tag, "b", 0).getAllKeys());
        assertEquals("minecraft:water", entry(tag, "b", 1).getString("i")); assertEquals(32, entry(tag, "b", 1).getByteArray("d").length);
        assertEquals(-1_000, entry(tag, "b", 1).getLong("x")); assertEquals(1_000, entry(tag, "b", 1).getLong("y"));
        var frozen = tag.getCompound("f");
        assertEquals(java.util.Set.of("k", "g", "m", "e"), frozen.getAllKeys()); assertEquals(1, frozen.getByte("k"));
        assertEquals(4, frozen.getIntArray("m").length); assertEquals(3, frozen.getList("e", 10).size());
        assertEquals(java.util.Set.of("i", "d", "m", "s", "t", "a", "o"), entry(tag, "f", 0).getAllKeys());
        assertEquals(7, entry(tag, "f", 0).getByte("m"));
        // The loss is signed: +100 water lost, -7 lava gained.
        assertEquals(-7, entry(tag, "f", 1).getLong("a")); assertEquals(100, entry(tag, "f", 2).getLong("a"));
        // Digests are stable, cover the frozen part, and the baseline digest does not move when the row is frozen.
        var planned = EscrowFluidAccountCodec.digest(plannedRow(), MAX).value(); var final_ = EscrowFluidAccountCodec.digest(frozenRow(), MAX).value();
        assertTrue(planned.matches("[0-9a-f]{64}")); assertNotEquals(planned, final_);
        assertEquals(final_, EscrowFluidAccountCodec.digest(EscrowFluidAccountCodec.decode(tag, MAX).value(), MAX).value());
        assertEquals(EscrowFluidAccountCodec.baselineDigest(plannedRow().baseline()).value(), EscrowFluidAccountCodec.baselineDigest(frozenRow().baseline()).value());
        assertNotEquals(EscrowFluidAccountCodec.baselineDigest(frozenRow().baseline()).value(), EscrowFluidAccountCodec.baselineDigest(
                baseline(Scope.FUEL, vector(WATER, 5_000), vector(OXYGEN, 300), WATER, 999)).value());
    }

    @Test void anUnknownEndpointIsSavedWithoutAnyNumber() {
        var member = new Member(id(1), 1, device(10), rocket(20, "FUEL"), baseline(Scope.FUEL, vector(WATER, 5_000), Vector.EMPTY, WATER, 1_000), false);
        var row = EscrowFluidAccount.settle(List.of(member), finals(device(10), vector(WATER, 4_000), rocket(20, "FUEL"), null)).value().rows().get(id(1));
        var tag = encoded(row); var saved = entry(tag, "f", 0);
        assertEquals(3, tag.getCompound("f").getByte("k")); assertEquals(1, saved.getByte("m"));
        assertTrue(saved.contains("s")); assertFalse(saved.contains("t")); assertFalse(saved.contains("a"));
        assertEquals(row, EscrowFluidAccountCodec.decode(tag, MAX).value());
        // A zero written for the unknown side, or a mask bit without its field, is refused.
        assertEquals(Status.INVALID_MASK, decodedWith(row, t -> entry(t, "f", 0).putLong("t", 0)));
        assertEquals(Status.INVALID_MASK, decodedWith(row, t -> entry(t, "f", 0).putByte("m", (byte) 3)));
        assertEquals(Status.INVALID_MASK, decodedWith(row, t -> entry(t, "f", 0).putByte("m", (byte) 8)));
        assertEquals(Status.INVALID_MASK, decodedWith(row, t -> entry(t, "f", 0).putByte("m", (byte) -1)));
        assertEquals(Status.INVALID_KNOWLEDGE, decodedWith(row, t -> t.getCompound("f").putByte("k", (byte) 1)));
        assertEquals(Status.INVALID_KNOWLEDGE, decodedWith(row, t -> { entry(t, "f", 0).putByte("m", (byte) 5); entry(t, "f", 0).putLong("a", 0); }));
    }

    @Test void duplicateKeysUnknownFieldsAndMalformedDigestsAreRefused() {
        Row row = frozenRow();
        assertEquals(Status.DUPLICATE_KEY, decodedWith(row, t -> t.getCompound("b").getList("e", 10).set(0, entry(t, "b", 1).copy())));
        assertEquals(Status.DUPLICATE_KEY, decodedWith(row, t -> t.getCompound("f").getList("e", 10).set(1, entry(t, "f", 2).copy())));
        assertEquals(Status.UNSORTED_KEYS, decodedWith(row, t -> {
            var list = t.getCompound("b").getList("e", 10); var first = list.getCompound(0).copy(); list.set(0, list.getCompound(1).copy()); list.set(1, first);
        }));
        assertEquals(Status.UNKNOWN_FIELD, decodedWith(row, t -> t.putString("reason", "free text")));
        assertEquals(Status.UNKNOWN_FIELD, decodedWith(row, t -> t.getCompound("b").putInt("q", 1)));
        assertEquals(Status.UNKNOWN_FIELD, decodedWith(row, t -> entry(t, "b", 0).putLong("a", 0)));
        assertEquals(Status.UNKNOWN_FIELD, decodedWith(row, t -> t.getCompound("f").put("Tag", new CompoundTag())));
        assertEquals(Status.UNKNOWN_FIELD, decodedWith(row, t -> entry(t, "f", 0).putLong("x", 0)));
        assertEquals(Status.MALFORMED_DIGEST, decodedWith(row, t -> entry(t, "b", 0).putByteArray("d", new byte[31])));
        assertEquals(Status.MALFORMED_DIGEST, decodedWith(row, t -> entry(t, "f", 0).putByteArray("d", new byte[33])));
        assertEquals(Status.INVALID_FIELD, decodedWith(row, t -> entry(t, "b", 0).putString("d", "0".repeat(64))));
        assertEquals(Status.INVALID_KEY, decodedWith(row, t -> entry(t, "b", 0).putString("i", "Minecraft:Water")));
        assertEquals(Status.INVALID_KEY, decodedWith(row, t -> entry(t, "f", 0).putString("i", "minecraft:" + "w".repeat(119))));
        assertEquals(Status.INVALID_FIELD, decodedWith(row, t -> entry(t, "b", 0).putInt("s", 0)));
        assertEquals(Status.INVALID_FIELD, decodedWith(row, t -> entry(t, "f", 0).putInt("a", 0)));
        assertEquals(Status.INVALID_FIELD, decodedWith(row, t -> t.putLong("v", 1)));
        assertEquals(Status.INVALID_FIELD, decodedWith(row, t -> t.putIntArray("op", new int[3])));
        assertEquals(Status.INVALID_FIELD, decodedWith(row, t -> t.remove("b")));
        assertEquals(Status.INVALID_FIELD, decodedWith(row, t -> t.getCompound("b").putByte("s", (byte) 2)));
        assertEquals(Status.INVALID_FIELD, decodedWith(row, t -> entry(t, "f", 0).remove("o")));
        assertEquals(Status.UNSUPPORTED_SCHEMA, decodedWith(row, t -> t.putInt("v", 2)));
        assertEquals(Status.UNSUPPORTED_SCHEMA, decodedWith(row, t -> t.putInt("v", 0)));
        assertEquals(Status.INVALID_KNOWLEDGE, decodedWith(row, t -> t.getCompound("f").putByte("k", (byte) 0)));
        assertEquals(Status.INVALID_KNOWLEDGE, decodedWith(row, t -> t.getCompound("f").putByte("k", (byte) 4)));
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.decode(null, MAX).status());
    }

    @Test void negativeAmountsExcessCardinalityAndAnIncorrectBalanceAreRefused() {
        Row row = frozenRow();
        assertEquals(Status.NEGATIVE_AMOUNT, decodedWith(row, t -> entry(t, "b", 0).putLong("t", -300)));
        assertEquals(Status.NEGATIVE_AMOUNT, decodedWith(row, t -> entry(t, "f", 0).putLong("s", -1)));
        // The planned identity and the frozen identity dS + dD + loss = 0.
        assertEquals(Status.INCORRECT_BALANCE, decodedWith(row, t -> entry(t, "b", 1).putLong("y", 999)));
        assertEquals(Status.INCORRECT_BALANCE, decodedWith(row, t -> entry(t, "f", 2).putLong("a", 0)));
        assertEquals(Status.INCORRECT_BALANCE, decodedWith(row, t -> entry(t, "f", 2).putLong("t", 1_000)));
        assertEquals(Status.INCORRECT_BALANCE, decodedWith(row, t -> entry(t, "f", 1).putLong("a", 7)), "A gain is not a loss");
        assertEquals(Status.INVALID_PLAN, decodedWith(row, t -> { entry(t, "b", 1).putLong("x", -1_001); entry(t, "b", 1).putLong("y", 1_001); }));
        assertEquals(Status.INVALID_PLAN, decodedWith(row, t -> { entry(t, "b", 1).putLong("x", 0); entry(t, "b", 1).putLong("y", 0); }));
        // Cardinality: fuel has two baseline keys and five union keys; cargo one and three.
        assertEquals(Status.CARDINALITY, decodedWith(row, t -> t.getCompound("b").getList("e", 10).add(entry(t, "b", 0).copy())));
        assertEquals(Status.CARDINALITY, decodedWith(row, t -> t.getCompound("b").put("e", new ListTag())));
        assertEquals(Status.CARDINALITY, decodedWith(row, t -> t.getCompound("f").put("e", new ListTag())));
        // Six frozen entries: the node bound of a row refuses them first, and the cardinality rule refuses them without it.
        var six = encoded(row); for (int i = 0; i < 3; i++) { six.getCompound("f").getList("e", 10).add(entry(six, "f", 0).copy()); }
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.decode(six, MAX).status());
        assertEquals(Status.CARDINALITY, EscrowFluidAccountCodec.decode(six, new Limits(4_096, 8, 200, 64)).status());
        assertEquals(Status.CARDINALITY, decodedWith(row, t -> t.getCompound("b").putByte("s", (byte) 1)), "A cargo baseline has one key");
        var cargo = new Member(id(2), 2, device(11), rocket(21, "CARGO"), baseline(Scope.CARGO, vector(WATER, 5_000), Vector.EMPTY, WATER, 1), false);
        var cargoRow = EscrowFluidAccount.settle(List.of(cargo), finals(device(11), vector(WATER, 4_999, LAVA, 1), rocket(21, "CARGO"), vector(WATER, 1, OXYGEN, 2)))
                .value().rows().get(id(2));
        assertEquals(3, encoded(cargoRow).getCompound("f").getList("e", 10).size());
        assertEquals(cargoRow, EscrowFluidAccountCodec.decode(encoded(cargoRow), MAX).value());
        assertEquals(Status.CARDINALITY, decodedWith(cargoRow, t -> t.getCompound("f").getList("e", 10).add(entry(t, "f", 0).copy())));
        // The encoder refuses the same rows; it never writes an invalid account.
        var bad = new Row(id(1), row.baseline(), new Frozen(Knowledge.KNOWN, id(1), List.of(id(1)), row.frozen().entries().stream()
                .map(e -> new FrozenEntry(e.key(), e.source(), e.destination(), OptionalLong.of(0), e.owner())).toList()));
        assertEquals(Status.INCORRECT_BALANCE, EscrowFluidAccountCodec.encode(bad, MAX).status());
        assertEquals(Status.INCORRECT_BALANCE, EscrowFluidAccountCodec.digest(bad, MAX).status());
        assertEquals(Status.NEGATIVE_AMOUNT, EscrowFluidAccountCodec.baselineDigest(new Baseline(Scope.FUEL, List.of(new Entry(WATER, -1, 0, -1, 1)))).status());
    }

    @Test void memberListAndOwnershipFormsAreRefused() {
        Row row = frozenRow();
        assertEquals(Status.INVALID_MEMBERS, decodedWith(row, t -> t.getCompound("f").remove("m")));
        assertEquals(Status.INVALID_MEMBERS, decodedWith(row, t -> t.getCompound("f").putIntArray("m", new int[0])));
        assertEquals(Status.INVALID_MEMBERS, decodedWith(row, t -> t.getCompound("f").putIntArray("m", new int[5])));
        assertEquals(Status.INVALID_MEMBERS, decodedWith(row, t -> t.getCompound("f").putUUID("g", id(9))));
        assertEquals(Status.INVALID_FIELD, decodedWith(row, t -> t.getCompound("f").putLongArray("m", new long[2])));
        assertEquals(Status.INVALID_OWNER, decodedWith(row, t -> entry(t, "f", 0).putUUID("o", id(9))));
        // A second member in the list makes this a group: the single-row balance is no longer claimed, the owner is.
        assertEquals(Status.INVALID_OWNER, decodedWith(row, t -> {
            t.getCompound("f").putIntArray("m", java.util.stream.IntStream.concat(java.util.Arrays.stream(t.getCompound("f").getIntArray("m")),
                    java.util.Arrays.stream(net.minecraft.core.UUIDUtil.uuidToIntArray(id(2)))).toArray());
            entry(t, "f", 0).putUUID("o", id(3));
        }));
    }

    @Test void aSixtyFourMemberOwnerRowStaysWithinItsBoundAndBoundsAreRefusals() {
        var members = new ArrayList<Member>(); var observed = new HashMap<UnitRef, Optional<Vector>>();
        for (int index = 1; index <= 64; index++) {
            members.add(new Member(id(index), index, device(1_000 + index), rocket(5_000 + index * 2L, "FUEL"),
                    baseline(Scope.FUEL, vector(WATER, 2_000), vector(OXYGEN, 5), WATER, 1_000), false));
            observed.put(device(1_000 + index), Optional.of(vector(WATER, 1_000)));
            observed.put(rocket(5_000 + index * 2L, "FUEL"), Optional.of(vector(WATER, 1_000, OXYGEN, 5)));
        }
        var settled = EscrowFluidAccount.settle(members, observed).value();
        var owner = settled.rows().get(id(1)); assertEquals(64, owner.frozen().members().size());
        var tag = encoded(owner); var size = BoundedNbt.measure(tag, MAX.bytes(), MAX.depth(), MAX.nodes());
        assertNotNull(size); assertEquals(256, tag.getCompound("f").getIntArray("m").length);
        assertEquals(owner, EscrowFluidAccountCodec.decode(tag, MAX).value());
        System.out.println("ACCOUNT_ROW case=owner-64-members bytes=" + size.bytes() + " nodes=" + size.nodes());
        // The member list is one array of 1,032 bytes with its name. Another row lacks it and the two adjustments its owner holds.
        var other = encoded(settled.rows().get(id(2)));
        assertEquals(size.nodes() - 1 - 2, BoundedNbt.measure(other, MAX.bytes(), MAX.depth(), MAX.nodes()).nodes());
        assertEquals(size.bytes() - (3 + 1 + 4 + 64 * 16) - 2 * (3 + 1 + 8), BoundedNbt.measure(other, MAX.bytes(), MAX.depth(), MAX.nodes()).bytes());
        // A whole group of 64 rows is a valid account list and each row is checked alone and together.
        var rows = List.copyOf(settled.rows().values());
        var list = EscrowFluidAccountCodec.encodeRows(rows, MAX).value();
        assertEquals(rows, EscrowFluidAccountCodec.decodeRows(list, MAX).value());
        assertEquals(Status.OK, EscrowFluidAccount.verifyGroup(EscrowFluidAccountCodec.decodeRows(list, MAX).value()));
        // Every bound is a refusal value: member count, bytes, depth and nodes.
        assertEquals(Status.CARDINALITY, EscrowFluidAccountCodec.encode(owner, new Limits(4_096, 8, 65, 63)).status());
        assertEquals(Status.CARDINALITY, EscrowFluidAccountCodec.decode(tag, new Limits(4_096, 8, 65, 63)).status());
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.encode(owner, new Limits((int) size.bytes() - 1, 8, 65, 64)).status());
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.decode(tag, new Limits((int) size.bytes() - 1, 8, 65, 64)).status());
        assertEquals(Status.OK, EscrowFluidAccountCodec.decode(tag, new Limits((int) size.bytes(), 5, size.nodes(), 64)).status());
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.decode(tag, new Limits(4_096, 4, 65, 64)).status());
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.decode(tag, new Limits(4_096, 8, size.nodes() - 1, 64)).status());
        var deep = tag.copy(); var nested = new CompoundTag(); nested.put("z", new CompoundTag()); entry(deep, "f", 0).put("z", nested);
        assertEquals(Status.UNKNOWN_FIELD, EscrowFluidAccountCodec.decode(deep, MAX).status());
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.decode(deep, new Limits(4_096, 5, 65, 64)).status(),
                "A real row has depth 5; deeper content is refused by the bound before its fields are read");
    }

    @Test void theAccountListRefusesExcessRowsAndDuplicateOperations() {
        var rows = new ArrayList<Row>();
        for (int index = 1; index <= 129; index++) { rows.add(new Row(id(index), plannedRow().baseline(), null)); }
        assertEquals(Status.CARDINALITY, EscrowFluidAccountCodec.encodeRows(rows, MAX).status());
        var full = EscrowFluidAccountCodec.encodeRows(rows.subList(0, 128), MAX).value();
        assertEquals(128, EscrowFluidAccountCodec.decodeRows(full, MAX).value().size());
        var excess = full.copy(); excess.add(encoded(rows.get(128)));
        assertEquals(Status.CARDINALITY, EscrowFluidAccountCodec.decodeRows(excess, MAX).status());
        assertEquals(Status.DUPLICATE_ROW, EscrowFluidAccountCodec.encodeRows(List.of(plannedRow(), frozenRow()), MAX).status());
        var duplicate = new ListTag(); duplicate.add(encoded(plannedRow())); duplicate.add(encoded(frozenRow()));
        assertEquals(Status.DUPLICATE_ROW, EscrowFluidAccountCodec.decodeRows(duplicate, MAX).status());
        var invalid = new ListTag(); var broken = encoded(plannedRow()); broken.putInt("v", 9); invalid.add(broken);
        assertEquals(Status.UNSUPPORTED_SCHEMA, EscrowFluidAccountCodec.decodeRows(invalid, MAX).status());
        var strings = new ListTag(); strings.add(net.minecraft.nbt.StringTag.valueOf("row"));
        assertEquals(Status.INVALID_FIELD, EscrowFluidAccountCodec.decodeRows(strings, MAX).status());
        assertTrue(EscrowFluidAccountCodec.decodeRows(new ListTag(), MAX).value().isEmpty());
    }

    @Test void nativeFluidKeysIgnoreTheAmountAndSeparateAnAbsentTagFromAnEmptyOne() {
        var water = new CompoundTag(); water.putString("FluidName", "minecraft:water"); water.putInt("Amount", 1_000);
        var more = water.copy(); more.putInt("Amount", 16_000);
        var emptyTag = water.copy(); emptyTag.put("Tag", new CompoundTag());
        var tagged = water.copy(); var tag = new CompoundTag(); tag.putInt("grade", 3); tagged.put("Tag", tag);
        var retagged = water.copy(); var other = new CompoundTag(); other.putInt("grade", 4); retagged.put("Tag", other);
        var lava = water.copy(); lava.putString("FluidName", "minecraft:lava");
        var absent = EscrowFluidAccountCodec.key(water).value();
        assertEquals(absent, EscrowFluidAccountCodec.key(more).value());
        assertEquals("minecraft:water", absent.fluid()); assertEquals(WATER, absent);
        var keys = new java.util.HashSet<>(List.of(absent, EscrowFluidAccountCodec.key(emptyTag).value(), EscrowFluidAccountCodec.key(tagged).value(),
                EscrowFluidAccountCodec.key(retagged).value(), EscrowFluidAccountCodec.key(lava).value()));
        assertEquals(5, keys.size());
        var unknown = water.copy(); unknown.putString("Owner", "x");
        assertEquals(Status.UNKNOWN_FIELD, EscrowFluidAccountCodec.key(unknown).status());
        var invalid = water.copy(); invalid.putString("FluidName", "Water");
        assertEquals(Status.INVALID_KEY, EscrowFluidAccountCodec.key(invalid).status());
        var wrongTag = water.copy(); wrongTag.putString("Tag", "x");
        assertEquals(Status.INVALID_FIELD, EscrowFluidAccountCodec.key(wrongTag).status());
        assertEquals(Status.INVALID_FIELD, EscrowFluidAccountCodec.key(new CompoundTag()).status());
        var huge = water.copy(); var blob = new CompoundTag(); blob.putByteArray("blob", new byte[8_192]); huge.put("Tag", blob);
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.key(huge).status());
        assertEquals(Status.TOO_LARGE, EscrowFluidAccountCodec.key(null).status());
    }
}
