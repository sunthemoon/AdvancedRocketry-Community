package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicCheckpointComparisonTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyNativePayloadTypeMatchesDetachedEqualData() {
        ListTag list = ints(1, 2);
        List<Tag> tags = List.of(ByteTag.valueOf((byte) -1), ShortTag.valueOf((short) -2), IntTag.valueOf(3),
                LongTag.valueOf(Long.MIN_VALUE), FloatTag.valueOf(1.5f), DoubleTag.valueOf(-2.5),
                StringTag.valueOf("x\0\ud800"), new ByteArrayTag(new byte[]{-1, 2}),
                new IntArrayTag(new int[]{Integer.MIN_VALUE, 2}), new LongArrayTag(new long[]{Long.MAX_VALUE}),
                list, new ListTag(), new CompoundTag());
        for (Tag tag : tags) { assertTrue(same(tag, tag.copy())); }
    }

    @Test void integralClassesAndValuesAreNotCoerced() {
        assertFalse(same(ByteTag.valueOf((byte) 1), IntTag.valueOf(1)));
        assertFalse(same(ShortTag.valueOf((short) 1), LongTag.valueOf(1)));
        assertFalse(same(IntTag.valueOf(1), IntTag.valueOf(2)));
        assertFalse(same(LongTag.valueOf(Long.MIN_VALUE), LongTag.valueOf(Long.MAX_VALUE)));
    }

    @Test void compoundsCompareExactUtf16KeySetsRegardlessOfInsertionOrder() {
        CompoundTag first = new CompoundTag(); first.putInt("a", 1); first.putString("\ud800", "\udc00");
        CompoundTag second = new CompoundTag(); second.putString("\ud800", "\udc00"); second.putInt("a", 1);
        assertTrue(same(first, second));
        second.remove("\ud800"); second.putString("\ud801", "\udc00");
        assertFalse(same(first, second));
        assertEquals(2, first.size()); assertTrue(first.contains("\ud800"));
    }

    @Test void listLengthOrderAndStoredSubtypeAreNotIgnored() throws Exception {
        assertTrue(same(ints(1, 2), ints(1, 2)));
        assertFalse(same(ints(1, 2), ints(2, 1)));
        assertFalse(same(ints(1, 2), ints(1)));
        ListTag bytes = new ListTag(); bytes.add(ByteTag.valueOf((byte) 1));
        assertFalse(same(ints(1), bytes));
        // Package-private constructor fixture: not claimed reachable through public add/set.
        Constructor<ListTag> ctor = ListTag.class.getDeclaredConstructor(List.class, byte.class);
        ctor.setAccessible(true);
        ListTag typedEmpty = ctor.newInstance(new ArrayList<Tag>(), (byte) Tag.TAG_INT);
        assertFalse(same(typedEmpty, typedEmpty)); assertFalse(same(new ListTag(), typedEmpty));
        assertEquals(Tag.TAG_INT, typedEmpty.getElementType());
    }

    @Test void arraysCompareLengthAndEveryElementWithoutElementNodeExpansion() {
        for (Tag tag : List.of(new ByteArrayTag(new byte[]{1, 2}), new IntArrayTag(new int[]{1, 2}),
                new LongArrayTag(new long[]{1, 2}))) {
            int width = tag instanceof ByteArrayTag ? 1 : tag instanceof IntArrayTag ? 4 : 8;
            assertTrue(ClassicNbtExactComparison.matches(tag, tag.copy(), new ClassicNbtLimits(7 + 2 * width, 1, 1)));
            assertFalse(ClassicNbtExactComparison.matches(tag, tag, new ClassicNbtLimits(6 + 2 * width, 1, 1)));
        }
        assertFalse(same(new ByteArrayTag(new byte[]{1, 2}), new ByteArrayTag(new byte[]{1, 3})));
        assertFalse(same(new IntArrayTag(new int[]{1, 2}), new IntArrayTag(new int[]{1})));
        assertFalse(same(new LongArrayTag(new long[]{1}), new LongArrayTag(new long[]{2})));
    }

    @Test void publicNullArrayBackingsRefuseWithoutChangingTheInputs() {
        List<Tag> tags = List.of(new ByteArrayTag((byte[]) null), new IntArrayTag((int[]) null),
                new LongArrayTag((long[]) null));
        for (Tag tag : tags) {
            assertFalse(same(tag, tag));
            CompoundTag parent = new CompoundTag(); parent.put("a", tag);
            assertFalse(same(parent, parent)); assertSame(tag, parent.get("a"));
        }
        assertNull(((ByteArrayTag) tags.get(0)).getAsByteArray());
        assertNull(((IntArrayTag) tags.get(1)).getAsIntArray());
        assertNull(((LongArrayTag) tags.get(2)).getAsLongArray());
    }

    @Test void utf16StringsAreNeitherNormalizedNorUtf8ReplacementCompared() {
        assertTrue(same(StringTag.valueOf("\0\ud800\udc00"), StringTag.valueOf("\0\ud800\udc00")));
        assertFalse(same(StringTag.valueOf("\ud800"), StringTag.valueOf("\ud801")));
        assertFalse(same(StringTag.valueOf("?"), StringTag.valueOf("\ud800")));
        assertFalse(same(StringTag.valueOf("\u00e9"), StringTag.valueOf("e\u0301")));
    }

    @Test void signedZerosUseRawBitsAndAreNotNativeReloadParityClaims() throws Exception {
        FloatTag negativeFloat = exactFloat(-0.0f); DoubleTag negativeDouble = exactDouble(-0.0);
        assertEquals(0x80000000, Float.floatToRawIntBits(negativeFloat.getAsFloat()));
        assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(negativeDouble.getAsDouble()));
        assertTrue(same(negativeFloat, exactFloat(-0.0f)));
        assertTrue(same(negativeDouble, exactDouble(-0.0)));
        assertFalse(same(negativeFloat, FloatTag.valueOf(0.0f)));
        assertFalse(same(negativeDouble, DoubleTag.valueOf(0.0)));
        assertEquals(0x80000000, Float.floatToRawIntBits(negativeFloat.getAsFloat()));
    }

    @Test void canonicalNanAndInfinitiesStayAdmittedButLossyNanBitsRefuse() throws Exception {
        assertTrue(same(FloatTag.valueOf(Float.NaN), FloatTag.valueOf(Float.NaN)));
        assertTrue(same(DoubleTag.valueOf(Double.POSITIVE_INFINITY), DoubleTag.valueOf(Double.POSITIVE_INFINITY)));
        assertFalse(same(DoubleTag.valueOf(Double.POSITIVE_INFINITY), DoubleTag.valueOf(Double.NEGATIVE_INFINITY)));
        FloatTag rawFloat = exactFloat(Float.intBitsToFloat(0x7fc00001));
        DoubleTag rawDouble = exactDouble(Double.longBitsToDouble(0x7ff8000000000001L));
        assertFalse(same(rawFloat, rawFloat)); assertFalse(same(rawDouble, rawDouble));
        assertEquals(0x7fc00001, Float.floatToRawIntBits(rawFloat.getAsFloat()));
        assertEquals(0x7ff8000000000001L, Double.doubleToRawLongBits(rawDouble.getAsDouble()));
    }

    @Test void foreignTagsAndEndChildrenNeverReceiveCustomEqualityOrCopyDispatch() {
        CompoundTag foreign = new CompoundTag() {
            @Override public boolean equals(Object other) { throw new AssertionError("Custom equality"); }
            @Override public CompoundTag copy() { throw new AssertionError("Custom copy"); }
        };
        assertFalse(same(foreign, foreign));
        CompoundTag nested = new CompoundTag(); nested.put("foreign", foreign);
        assertFalse(same(nested, nested)); assertSame(foreign, nested.get("foreign"));
        assertFalse(same(EndTag.INSTANCE, EndTag.INSTANCE));
        CompoundTag end = new CompoundTag(); end.put("end", EndTag.INSTANCE);
        assertFalse(same(end, end)); assertSame(EndTag.INSTANCE, end.get("end"));
    }

    @Test void inclusiveDepthAndNodeLimitsRefuseCyclesWithoutRecursion() {
        CompoundTag deep = nested(12);
        assertTrue(ClassicNbtExactComparison.matches(deep, deep, ClassicNbtLimits.HATCH));
        assertFalse(ClassicNbtExactComparison.matches(nested(13), nested(13), ClassicNbtLimits.HATCH));
        CompoundTag nodes = new CompoundTag(); for (int i = 0; i < 255; i++) { nodes.putByte("n" + i, (byte) 1); }
        assertTrue(ClassicNbtExactComparison.matches(nodes, nodes, ClassicNbtLimits.HATCH));
        nodes.putByte("extra", (byte) 1);
        assertFalse(ClassicNbtExactComparison.matches(nodes, nodes, ClassicNbtLimits.HATCH));
        CompoundTag cycle = new CompoundTag(); cycle.put("self", cycle);
        assertFalse(same(cycle, cycle)); assertSame(cycle, cycle.get("self"));
    }

    @Test void modifiedUtfLimitsApplyBeforeComparingStringsOrKeys() {
        Tag fitting = StringTag.valueOf("a".repeat(65_535));
        assertTrue(ClassicNbtExactComparison.matches(fitting, fitting, new ClassicNbtLimits(65_540, 1, 1)));
        assertFalse(ClassicNbtExactComparison.matches(fitting, fitting, new ClassicNbtLimits(65_539, 1, 1)));
        assertFalse(same(StringTag.valueOf("a".repeat(65_536)), StringTag.valueOf("a".repeat(65_536))));
        CompoundTag badKey = new CompoundTag(); badKey.putInt("\0".repeat(32_768), 1);
        assertFalse(same(badKey, badKey));
        CompoundTag nullKey = new CompoundTag(); nullKey.put(null, IntTag.valueOf(1));
        assertFalse(same(nullKey, nullKey)); assertTrue(nullKey.getAllKeys().contains(null));
    }

    @Test void everyManagedPresenceAndLegacyValueParticipatesInRetainedComparison() {
        CompoundTag expected = new CompoundTag();
        for (String key : ClassicRootBundle.KEYS) { expected.putString(key, "legacy"); }
        var roots = ClassicRootBundle.captureData(expected, ClassicRootBundle.OwnerType.HATCH);
        assertTrue(roots.forbiddenPresent()); assertFalse(roots.requiresSaveRefusal(false));
        try (var emit = ClassicRawPermit.emission(state())) {
            assertTrue(roots.matchesRetained(expected, false, emit));
            for (String key : ClassicRootBundle.KEYS) {
                CompoundTag absent = expected.copy(); absent.remove(key);
                CompoundTag changed = expected.copy(); changed.putString(key, "different");
                assertFalse(roots.matchesRetained(absent, false, emit), key);
                assertFalse(roots.matchesRetained(changed, false, emit), key);
            }
        }
    }

    @Test void absentManagedRootsDifferFromPresentEmptyButUnmanagedSubtreesAreNotTraversed() {
        CompoundTag expected = hatchParent();
        var roots = ClassicRootBundle.captureData(expected, ClassicRootBundle.OwnerType.HATCH);
        CompoundTag actual = expected.copy(); CompoundTag cycle = new CompoundTag(); cycle.put("self", cycle);
        actual.put("unmanaged", cycle);
        try (var emit = ClassicRawPermit.emission(state())) {
            assertTrue(roots.matchesRetained(actual, false, emit));
            actual.put("arce_machine", new CompoundTag()); assertFalse(roots.matchesRetained(actual, false, emit));
            actual.remove("arce_machine"); assertTrue(roots.matchesRetained(actual, false, emit));
            assertSame(cycle, actual.get("unmanaged")); assertSame(cycle, cycle.get("self"));
        }
    }

    @Test void comparisonDoesNotMutateOrExposeInputRoots() {
        CompoundTag expected = hatchParent(); expected.getCompound(ClassicRootBundle.HATCH).putInt("v", 4);
        var roots = ClassicRootBundle.captureData(expected, ClassicRootBundle.OwnerType.HATCH);
        CompoundTag actual = expected.copy(); Tag selected = actual.get(ClassicRootBundle.HATCH);
        try (var emit = ClassicRawPermit.emission(state())) {
            assertTrue(roots.matchesRetained(actual, false, emit));
            assertSame(selected, actual.get(ClassicRootBundle.HATCH)); assertEquals(1, actual.size());
            actual.getCompound(ClassicRootBundle.HATCH).putInt("v", 5);
            assertFalse(roots.matchesRetained(actual, false, emit));
            assertTrue(roots.matchesRetained(expected, false, emit));
            assertEquals(4, expected.getCompound(ClassicRootBundle.HATCH).getInt("v"));
        }
    }

    @Test void missingRequiredRootsAndConditionalJournalStaySaveRefusals() {
        var missing = ClassicRootBundle.captureData(new CompoundTag(), ClassicRootBundle.OwnerType.HATCH);
        CompoundTag controller = controllerParent();
        var roots = ClassicRootBundle.captureData(controller, ClassicRootBundle.OwnerType.CONTROLLER);
        try (var emit = ClassicRawPermit.emission(state())) {
            assertFalse(missing.matchesRetained(new CompoundTag(), false, emit));
            assertTrue(roots.matchesRetained(controller, false, emit));
            assertFalse(roots.matchesRetained(controller, true, emit));
        }
        controller.put(ClassicRootBundle.JOURNAL, new CompoundTag());
        var journal = ClassicRootBundle.captureData(controller, ClassicRootBundle.OwnerType.CONTROLLER);
        try (var emit = ClassicRawPermit.emission(state())) { assertTrue(journal.matchesRetained(controller, true, emit)); }
    }

    @Test void individualNamedRootByteLimitIsInclusiveAndOversizeDoesNotBecomeEqual() {
        CompoundTag exact = new CompoundTag(); exact.putByteArray(ClassicRootBundle.HATCH, new byte[4_089]);
        var fitting = ClassicRootBundle.captureData(exact, ClassicRootBundle.OwnerType.HATCH);
        CompoundTag oversized = new CompoundTag(); oversized.putByteArray(ClassicRootBundle.HATCH, new byte[4_090]);
        var refused = ClassicRootBundle.captureData(oversized, ClassicRootBundle.OwnerType.HATCH);
        assertTrue(fitting.bounded()); assertFalse(refused.bounded());
        try (var emit = ClassicRawPermit.emission(state())) {
            assertTrue(fitting.matchesRetained(exact, false, emit));
            assertFalse(fitting.matchesRetained(oversized, false, emit));
            assertFalse(refused.matchesRetained(oversized, false, emit));
        }
    }

    @Test void aggregateNamedProjectionRetainsExactExistingRejectedBudget() {
        CompoundTag all = new CompoundTag(); int bytes = 4;
        for (String key : ClassicRootBundle.KEYS) {
            int individual = ClassicRootBundle.limits(key).bytes();
            all.putByteArray(key, new byte[individual - 7]); bytes += individual + key.length();
        }
        assertEquals(1_873_117, bytes);
        assertTrue(ClassicNbtExactComparison.matches(all, all, ClassicNbtLimits.REJECTED));
        assertFalse(ClassicNbtExactComparison.matches(all, all, new ClassicNbtLimits(bytes - 1, 25, 86_529)));
        assertFalse(ClassicNbtExactComparison.matches(all, all, new ClassicNbtLimits(bytes, 25, 12)));
        var roots = ClassicRootBundle.captureData(all, ClassicRootBundle.OwnerType.HATCH);
        try (var emit = ClassicRawPermit.emission(state())) { assertTrue(roots.matchesRetained(all, false, emit)); }
    }

    @Test void supportedHatchEncodingIsSingleRootAndForbidsAdditionalManagedPresence() {
        CompoundTag encoded = new CompoundTag(); encoded.putInt("schema_version", 1);
        CompoundTag outgoing = new CompoundTag(); outgoing.put(ClassicRootBundle.HATCH, encoded.copy());
        try (var emit = ClassicRawPermit.emission(state())) {
            assertTrue(ClassicRootBundle.matchesEncoded(encoded, ClassicRootBundle.OwnerType.HATCH, outgoing, emit));
            outgoing.putString("arce_machine", "legacy");
            assertFalse(ClassicRootBundle.matchesEncoded(encoded, ClassicRootBundle.OwnerType.HATCH, outgoing, emit));
            outgoing.remove("arce_machine"); outgoing.getCompound(ClassicRootBundle.HATCH).putInt("schema_version", 2);
            assertFalse(ClassicRootBundle.matchesEncoded(encoded, ClassicRootBundle.OwnerType.HATCH, outgoing, emit));
        }
    }

    @Test void supportedControllerEncodingUsesExistingStrictCompoundRootInventory() {
        CompoundTag encoded = controllerParent(); CompoundTag outgoing = encoded.copy();
        try (var emit = ClassicRawPermit.emission(state())) {
            assertTrue(ClassicRootBundle.matchesEncoded(encoded, ClassicRootBundle.OwnerType.CONTROLLER, outgoing, emit));
            encoded.putString(ClassicRootBundle.MARKER, "wrong type");
            assertFalse(ClassicRootBundle.matchesEncoded(encoded, ClassicRootBundle.OwnerType.CONTROLLER, encoded, emit));
            assertFalse(ClassicRootBundle.matchesEncoded(null, ClassicRootBundle.OwnerType.CONTROLLER, outgoing, emit));
            assertFalse(ClassicRootBundle.matchesEncoded(controllerParent(), null, outgoing, emit));
        }
    }

    @Test void comparisonRequiresCurrentEmissionPurposeAndDoesNotAcquireOrReleaseCallerGuard() {
        ClassicOwnerState owner = state(); CompoundTag parent = hatchParent();
        var roots = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.HATCH);
        try (var capture = ClassicRawPermit.acquire(owner, ClassicRawPurpose.CAPTURE).orElseThrow()) {
            assertThrows(IllegalStateException.class, () -> roots.matchesRetained(parent, false, capture));
            assertThrows(IllegalStateException.class, () -> ClassicRootBundle.matchesEncoded(new CompoundTag(),
                    ClassicRootBundle.OwnerType.HATCH, parent, capture));
            assertTrue(capture.storageStillCurrent()); assertTrue(owner.busy());
        }
        var emit = ClassicRawPermit.emission(owner);
        assertTrue(roots.matchesRetained(parent, false, emit)); assertTrue(owner.busy());
        assertTrue(emit.storageStillCurrent()); emit.close(); assertFalse(owner.busy());
        assertThrows(IllegalStateException.class, () -> roots.matchesRetained(parent, false, emit));
        assertThrows(IllegalStateException.class, () -> ClassicRootBundle.matchesEncoded(new CompoundTag(),
                ClassicRootBundle.OwnerType.HATCH, parent, emit));
    }

    @Test void retiredUnavailableRawOwnerCanCompareButForeignPendingOwnerIsNotAuthority() {
        ClassicOwnerState first = state(); ClassicOwnerState second = state(); CompoundTag parent = hatchParent();
        ClassicPendingLoad pending;
        try (var capture = ClassicRawPermit.acquire(first, ClassicRawPurpose.CAPTURE).orElseThrow()) {
            pending = ClassicPendingLoad.capture(parent, ClassicRootBundle.OwnerType.HATCH, capture);
            first.captured(pending, capture);
        }
        first.retire(); assertFalse(first.available());
        try (var emit = ClassicRawPermit.emission(first)) {
            assertTrue(pending.ownedBy(emit.state()));
            assertTrue(pending.roots().matchesRetained(parent, pending.planRequiresJournal(), emit));
        }
        try (var foreign = ClassicRawPermit.emission(second)) {
            // A data Boolean never authenticates the caller. Existing pending ownership must gate it.
            assertFalse(pending.ownedBy(foreign.state()));
            assertThrows(IllegalStateException.class, () -> pending.emit(new CompoundTag(), foreign));
        }
    }

    private static boolean same(Tag a, Tag b) { return ClassicNbtExactComparison.matches(a, b, ClassicNbtLimits.REJECTED); }
    private static ClassicOwnerState state() {
        return new ClassicOwnerState(new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()));
    }
    private static CompoundTag hatchParent() {
        CompoundTag parent = new CompoundTag(); parent.put(ClassicRootBundle.HATCH, new CompoundTag()); return parent;
    }
    private static CompoundTag controllerParent() {
        CompoundTag parent = new CompoundTag();
        for (String key : List.of(ClassicRootBundle.MACHINE, ClassicRootBundle.RESOURCES, ClassicRootBundle.MARKER)) {
            parent.put(key, new CompoundTag());
        }
        return parent;
    }
    private static ListTag ints(int... values) {
        ListTag list = new ListTag(); for (int value : values) { list.add(IntTag.valueOf(value)); } return list;
    }
    private static CompoundTag nested(int depth) {
        CompoundTag root = new CompoundTag(); CompoundTag tail = root;
        for (int i = 1; i < depth; i++) { CompoundTag child = new CompoundTag(); tail.put("n", child); tail = child; }
        return root;
    }
    private static FloatTag exactFloat(float value) throws Exception {
        Constructor<FloatTag> ctor = FloatTag.class.getDeclaredConstructor(float.class);
        ctor.setAccessible(true); return ctor.newInstance(value);
    }
    private static DoubleTag exactDouble(double value) throws Exception {
        Constructor<DoubleTag> ctor = DoubleTag.class.getDeclaredConstructor(double.class);
        ctor.setAccessible(true); return ctor.newInstance(value);
    }
}
