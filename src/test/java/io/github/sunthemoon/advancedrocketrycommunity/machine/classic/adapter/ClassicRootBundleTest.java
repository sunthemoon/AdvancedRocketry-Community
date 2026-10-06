package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

class ClassicRootBundleTest {
    private static CompoundTag minimalController() {
        CompoundTag parent = new CompoundTag();
        parent.put(ClassicRootBundle.MACHINE, new CompoundTag());
        parent.put(ClassicRootBundle.RESOURCES, new CompoundTag());
        parent.put(ClassicRootBundle.MARKER, new CompoundTag());
        return parent;
    }

    @Test void all4096PresenceVectorsPreserveExactTwelveKeySetAndUnrelatedMetadata() {
        for (int bits = 0; bits < 4096; bits++) {
            CompoundTag parent = new CompoundTag();
            for (int i = 0; i < 12; i++) {
                if ((bits & (1 << i)) != 0) { parent.putInt(ClassicRootBundle.KEYS.get(i), i); }
            }
            for (var type : ClassicRootBundle.OwnerType.values()) {
                var bundle = ClassicRootBundle.captureData(parent, type);
                assertTrue(bundle.bounded());
                for (int i = 0; i < 12; i++) { assertEquals((bits & (1 << i)) != 0, bundle.present(ClassicRootBundle.KEYS.get(i))); }
                boolean required = type == ClassicRootBundle.OwnerType.HATCH ? (bits & 1) != 0
                        : (bits & (2 | 4 | 16)) == (2 | 4 | 16);
                assertEquals(!required, bundle.requiresSaveRefusal(false));
                CompoundTag target = new CompoundTag(); target.putString("id", "not_family_authority");
                target.putLong("x", Long.MAX_VALUE); target.putString("other", "untouched");
                for (String key : ClassicRootBundle.KEYS) { target.putByte(key, (byte) -1); }
                if (required) {
                    bundle.emitData(target, false);
                    for (String key : ClassicRootBundle.KEYS) { assertEquals(parent.get(key), target.get(key)); }
                    assertEquals("not_family_authority", target.getString("id"));
                    assertEquals(Long.MAX_VALUE, target.getLong("x"));
                    assertEquals("untouched", target.getString("other"));
                } else {
                    CompoundTag before = target.copy();
                    assertThrows(IllegalStateException.class, () -> bundle.emitData(target, false));
                    assertEquals(before, target);
                }
            }
        }
    }

    @Test void exactRootCapsAndAggregateFormulaDoNotEnlargeSupportedLimits() {
        assertEquals(List.of("arce_classic_hatch", "arce_classic_resources", "arce_classic_machine",
                "arce_process_journal", "arce_recipe_signature", "arce_multiblock", "arce_part_binding",
                "arce_process", "arce_precision_resources", "arce_machine", "arce_rolling_port", "arce_precision_port"), ClassicRootBundle.KEYS);
        int bytes = 4; int nodes = 1;
        for (String key : ClassicRootBundle.KEYS) {
            ClassicNbtLimits limits = ClassicRootBundle.limits(key);
            bytes += key.length() + limits.bytes(); nodes += limits.nodes();
        }
        assertEquals(1_873_117, bytes); assertEquals(86_529, nodes);
        assertEquals(4_096, ClassicRootBundle.limits(ClassicRootBundle.HATCH).bytes());
        assertEquals(32_768, ClassicRootBundle.limits(ClassicRootBundle.RESOURCES).bytes());
        assertEquals(1_310_720, ClassicRootBundle.limits("arce_multiblock").bytes());
        assertThrows(IllegalArgumentException.class, () -> ClassicRootBundle.limits("arce_multiblock_controller"));
    }

    @Test void unexpectedRootPresenceIsClassifiedWithoutLegacyDecoding() {
        for (String key : ClassicRootBundle.KEYS) {
            CompoundTag parent = minimalController(); parent.putInt(key, 7);
            var controller = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.CONTROLLER);
            assertEquals(!Set.of(ClassicRootBundle.MACHINE, ClassicRootBundle.RESOURCES,
                    ClassicRootBundle.JOURNAL, ClassicRootBundle.MARKER).contains(key), controller.forbiddenPresent());
            CompoundTag hatchParent = new CompoundTag(); hatchParent.putInt(ClassicRootBundle.HATCH, 1); hatchParent.putInt(key, 7);
            var hatch = ClassicRootBundle.captureData(hatchParent, ClassicRootBundle.OwnerType.HATCH);
            assertEquals(!key.equals(ClassicRootBundle.HATCH), hatch.forbiddenPresent());
        }
    }

    @Test void boundedFutureWrongTypeAndMixedRootsArePreservedAsRawNotSupportedValues() {
        CompoundTag parent = minimalController();
        parent.putLong(ClassicRootBundle.MACHINE, Long.MIN_VALUE);
        CompoundTag future = new CompoundTag(); future.putInt("schema_version", Integer.MAX_VALUE); future.putString("future", "kept");
        parent.put(ClassicRootBundle.RESOURCES, future);
        ListTag list = new ListTag(); list.add(StringTag.valueOf("one")); list.add(StringTag.valueOf("two"));
        parent.put("arce_precision_resources", list);
        parent.put("arce_machine", new IntArrayTag(new int[]{1, 2, 3, 4}));
        var bundle = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.CONTROLLER);
        assertTrue(bundle.bounded()); assertTrue(bundle.forbiddenPresent());
        CompoundTag emitted = new CompoundTag(); bundle.emitData(emitted, false);
        assertEquals(parent, emitted);
        assertEquals(Tag.TAG_LONG, emitted.get(ClassicRootBundle.MACHINE).getId());
    }

    @Test void boundedCaptureAndEveryEmissionOwnAllNestedArraysListsAndMetadata() {
        CompoundTag parent = minimalController(); CompoundTag metadata = new CompoundTag();
        metadata.putLongArray("array", new long[]{1, 2}); ListTag list = new ListTag(); list.add(StringTag.valueOf("original")); metadata.put("list", list);
        parent.put("arce_machine", metadata);
        CompoundTag expected = parent.copy();
        var bundle = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.CONTROLLER);
        metadata.getLongArray("array")[0] = 9; list.set(0, StringTag.valueOf("caller")); parent.remove(ClassicRootBundle.MARKER);
        CompoundTag emitted = new CompoundTag(); bundle.emitData(emitted, false);
        assertEquals(expected, emitted);
        emitted.getCompound("arce_machine").getLongArray("array")[0] = 99;
        CompoundTag again = new CompoundTag(); bundle.emitData(again, false);
        assertEquals(expected, again);
    }

    @Test void everyIndividualRootCeilingRefusesWholeBundleWithOriginalCompanions() {
        for (String key : ClassicRootBundle.KEYS) {
            CompoundTag parent = minimalController();
            Tag large = new ByteArrayTag(new byte[ClassicRootBundle.limits(key).bytes()]); parent.put(key, large);
            var bundle = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.CONTROLLER);
            assertFalse(bundle.bounded()); assertTrue(bundle.requiresSaveRefusal(false));
            for (String captured : ClassicRootBundle.KEYS) {
                if (parent.contains(captured)) { assertTrue(bundle.retainsBorrowedIdentity(captured, parent.get(captured))); }
            }
            CompoundTag target = new CompoundTag(); target.putString("unchanged", "yes"); CompoundTag before = target.copy();
            assertThrows(IllegalStateException.class, () -> bundle.emitData(target, false));
            assertEquals(before, target);
        }
    }

    @Test void unsafeCompanionPreventsEveryCopyIncludingOtherwiseBoundedRoots() {
        CompoundTag parent = minimalController();
        CompoundTag trap = new CompoundTag() { @Override public CompoundTag copy() { fail("Unbounded companion copied"); return null; } };
        parent.put("arce_precision_port", trap);
        var bundle = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.CONTROLLER);
        assertTrue(bundle.retainsBorrowedIdentity(ClassicRootBundle.MACHINE, parent.get(ClassicRootBundle.MACHINE)));
        assertTrue(bundle.retainsBorrowedIdentity("arce_precision_port", trap));
        assertTrue(bundle.requiresSaveRefusal(false));
    }

    @Test void missingRequiredAndConditionalJournalRefuseWithoutSynthesizingRoots() {
        var empty = ClassicRootBundle.captureData(new CompoundTag(), ClassicRootBundle.OwnerType.HATCH);
        assertTrue(empty.missingRequired(false)); assertTrue(empty.requiresSaveRefusal(false));
        var controller = ClassicRootBundle.captureData(minimalController(), ClassicRootBundle.OwnerType.CONTROLLER);
        assertFalse(controller.missingRequired(false)); assertTrue(controller.missingRequired(true));
        CompoundTag withJournal = minimalController(); withJournal.put(ClassicRootBundle.JOURNAL, new CompoundTag());
        assertFalse(ClassicRootBundle.captureData(withJournal, ClassicRootBundle.OwnerType.CONTROLLER).missingRequired(true));
    }

    @Test void deepOrCyclicRootRetainsCompleteOriginalBundleWithoutEqualityOrCopy() {
        CompoundTag parent = minimalController(); parent.put("arce_multiblock", ClassicNbtShapeTest.nested(21));
        var deep = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.CONTROLLER);
        assertFalse(deep.bounded()); assertTrue(deep.retainsBorrowedIdentity(ClassicRootBundle.MARKER, parent.get(ClassicRootBundle.MARKER)));
        CompoundTag cycle = new CompoundTag(); cycle.put("cycle", cycle); parent.put(ClassicRootBundle.MACHINE, cycle);
        var cyclic = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.CONTROLLER);
        assertFalse(cyclic.bounded()); assertTrue(cyclic.retainsBorrowedIdentity(ClassicRootBundle.MACHINE, cycle));
    }

    @Test void bounded4096PartPositionFixtureIsRawPreservedNotLegacyMigration() {
        ListTag parts = new ListTag();
        for (int i = 0; i < 4096; i++) { CompoundTag part = new CompoundTag(); part.putInt("x", i); part.putInt("y", -64); part.putInt("z", -i); parts.add(part); }
        CompoundTag legacy = new CompoundTag(); legacy.put("parts", parts);
        CompoundTag parent = minimalController(); parent.put("arce_multiblock", legacy);
        var bundle = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.CONTROLLER);
        assertTrue(bundle.bounded()); assertTrue(bundle.forbiddenPresent());
        CompoundTag emitted = new CompoundTag(); bundle.emitData(emitted, false);
        assertEquals(parent, emitted);
        // Position-shaped raw fixture only; no old controller codec/native/accounted equivalence claimed.
    }

    @Test void publicNullBackingsAtEveryManagedRootRetainTheEntireBundle() {
        List<Tag> values = List.of(new ByteArrayTag((byte[]) null), new IntArrayTag((int[]) null),
                new LongArrayTag((long[]) null));
        for (String key : ClassicRootBundle.KEYS) {
            for (Tag value : values) {
                CompoundTag parent = minimalController(); parent.put(key, value);
                assertUnsafeBundle(parent);
                assertSame(value, parent.get(key));
            }
        }
        assertNull(((ByteArrayTag) values.get(0)).getAsByteArray());
        assertNull(((IntArrayTag) values.get(1)).getAsIntArray());
        assertNull(((LongArrayTag) values.get(2)).getAsLongArray());
    }

    @Test void nativeLoadedTypedEmptyListsRetainEverySubtypeAndAllCompanionIdentities() throws IOException {
        for (int type = Tag.TAG_BYTE; type <= Tag.TAG_LONG_ARRAY; type++) {
            for (String key : ClassicRootBundle.KEYS) {
                ListTag list = loadedEmptyList(type);
                assertEquals(type, list.getElementType());
                assertTrue(ClassicNbtShape.fits(list, ClassicRootBundle.limits(key)));
                CompoundTag parent = minimalController(); parent.put(key, list);
                assertUnsafeBundle(parent);
                assertEquals(type, list.getElementType()); assertEquals(0, list.size());
                // Do not write after refusal: native emission would normalize this retained input.
            }
        }
        CompoundTag parent = minimalController(); parent.put("arce_machine", loadedEmptyList(Tag.TAG_END));
        var safe = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.CONTROLLER);
        assertTrue(safe.bounded());
        CompoundTag emitted = new CompoundTag(); safe.emitData(emitted, false);
        assertEquals(Tag.TAG_END, ((ListTag) emitted.get("arce_machine")).getElementType());
        assertNotSame(parent.get("arce_machine"), emitted.get("arce_machine"));
    }

    @Test void noncanonicalNanBitsAtEveryRootRefuseWithoutChangingTheInput() {
        List<Tag> values = List.of(FloatTag.valueOf(Float.intBitsToFloat(0x7fc00001)),
                FloatTag.valueOf(Float.intBitsToFloat(0xffc00001)),
                DoubleTag.valueOf(Double.longBitsToDouble(0x7ff8000000000001L)),
                DoubleTag.valueOf(Double.longBitsToDouble(0xfff8000000000001L)));
        for (String key : ClassicRootBundle.KEYS) {
            for (Tag value : values) {
                long before = rawBits(value);
                assertTrue(ClassicNbtShape.fits(value, ClassicRootBundle.limits(key)));
                CompoundTag parent = minimalController(); parent.put(key, value);
                assertUnsafeBundle(parent);
                assertSame(value, parent.get(key)); assertEquals(before, rawBits(value));
            }
        }
    }

    @Test void canonicalNanSignedZerosAndInfinitiesKeepActualBitsAndDetachedCompanions()
            throws ReflectiveOperationException {
        // Native factories/load normalize -0; exact scalar constructors isolate emission/copy fidelity.
        var floatConstructor = FloatTag.class.getDeclaredConstructor(float.class); floatConstructor.setAccessible(true);
        var doubleConstructor = DoubleTag.class.getDeclaredConstructor(double.class); doubleConstructor.setAccessible(true);
        List<Tag> values = List.of(FloatTag.valueOf(Float.NaN), DoubleTag.valueOf(Double.NaN),
                FloatTag.valueOf(0.0f), DoubleTag.valueOf(0.0d),
                floatConstructor.newInstance(-0.0f), doubleConstructor.newInstance(-0.0d),
                FloatTag.valueOf(Float.POSITIVE_INFINITY), DoubleTag.valueOf(Double.NEGATIVE_INFINITY));
        assertEquals(0x80000000L, Integer.toUnsignedLong(Float.floatToRawIntBits(((FloatTag) values.get(4)).getAsFloat())));
        assertEquals(0x8000000000000000L, rawBits(values.get(5)));
        for (Tag value : values) {
            CompoundTag parent = minimalController(); parent.put(ClassicRootBundle.MACHINE, value);
            parent.putByteArray("arce_machine", new byte[]{1, 2});
            long before = rawBits(value);
            var bundle = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.CONTROLLER);
            assertTrue(bundle.bounded()); assertFalse(bundle.requiresSaveRefusal(false));
            parent.getByteArray("arce_machine")[0] = 9;
            CompoundTag first = new CompoundTag(); bundle.emitData(first, false);
            assertEquals(value.getId(), first.get(ClassicRootBundle.MACHINE).getId());
            assertEquals(before, rawBits(first.get(ClassicRootBundle.MACHINE)));
            assertArrayEquals(new byte[]{1, 2}, first.getByteArray("arce_machine"));
            first.getByteArray("arce_machine")[0] = 8;
            CompoundTag second = new CompoundTag(); bundle.emitData(second, false);
            assertArrayEquals(new byte[]{1, 2}, second.getByteArray("arce_machine"));
            assertEquals(before, rawBits(value));
        }
    }

    @Test void foreignListChildrenCannotReachNativeGetIdCopyWriteOrEqualityDuringPreflight() {
        AtomicInteger calls = new AtomicInteger();
        CompoundTag foreign = new CompoundTag() {
            @Override public byte getId() { calls.incrementAndGet(); return Tag.TAG_COMPOUND; }
            @Override public CompoundTag copy() { calls.incrementAndGet(); fail("Foreign copy"); return null; }
            @Override public void write(DataOutput output) { calls.incrementAndGet(); fail("Foreign write"); }
            @Override public boolean equals(Object other) { calls.incrementAndGet(); fail("Foreign equality"); return false; }
        };
        ListTag list = new ListTag(); list.add(foreign); calls.set(0);
        CompoundTag parent = minimalController(); parent.put("arce_machine", list);
        assertUnsafeBundle(parent);
        assertEquals(0, calls.get()); assertSame(foreign, list.get(0));
    }

    @Test void mismatchedNativeListSubtypeAndNamedEndRefuseBeforeAnyCopy() throws ReflectiveOperationException {
        ListTag mismatched = new ListTag(); mismatched.add(IntTag.valueOf(7));
        var field = ListTag.class.getDeclaredField("type"); field.setAccessible(true);
        field.setByte(mismatched, (byte) Tag.TAG_LONG);
        CompoundTag parent = minimalController(); parent.put("arce_machine", mismatched);
        assertUnsafeBundle(parent);
        assertEquals(Tag.TAG_LONG, mismatched.getElementType()); assertEquals(7, ((IntTag) mismatched.get(0)).getAsInt());
        parent.put("arce_machine", EndTag.INSTANCE); assertUnsafeBundle(parent);
        assertSame(EndTag.INSTANCE, parent.get("arce_machine"));
    }

    @Test void exactPerRootAndCompleteProjectionByteCeilingsRemainInclusive() throws IOException {
        CompoundTag projection = new CompoundTag();
        for (String key : ClassicRootBundle.KEYS) {
            ClassicNbtLimits limits = ClassicRootBundle.limits(key);
            Tag exact = new ByteArrayTag(new byte[limits.bytes() - 7]);
            assertEquals(limits.bytes(), ClassicNbtShapeTest.nativeBytes(exact));
            CompoundTag parent = minimalController(); parent.put(key, exact);
            assertTrue(ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.CONTROLLER).bounded());
            projection.put(key, exact);
            parent.put(key, new ByteArrayTag(new byte[limits.bytes() - 6]));
            assertUnsafeBundle(parent);
        }
        assertEquals(ClassicNbtLimits.REJECTED.bytes(), ClassicNbtShapeTest.nativeBytes(projection));
        var bundle = ClassicRootBundle.captureData(projection, ClassicRootBundle.OwnerType.CONTROLLER);
        assertTrue(bundle.bounded());
        CompoundTag emitted = new CompoundTag(); bundle.emitData(emitted, true);
        assertEquals(ClassicNbtLimits.REJECTED.bytes(), ClassicNbtShapeTest.nativeBytes(emitted));
        assertNotSame(projection.getByteArray("arce_multiblock"), emitted.getByteArray("arce_multiblock"));
        projection.putByteArray(ClassicRootBundle.HATCH, new byte[ClassicNbtLimits.HATCH.bytes() - 6]);
        assertEquals(ClassicNbtLimits.REJECTED.bytes() + 1, ClassicNbtShapeTest.nativeBytes(projection));
        assertUnsafeBundle(projection);
    }

    @Test void publicNullCompoundKeyAtEveryRootRetainsAllRootsAndRefusesEmission() {
        for (String key : ClassicRootBundle.KEYS) {
            CompoundTag malformed = new CompoundTag(); Tag child = IntTag.valueOf(7);
            malformed.put(null, child); // Actual public native put route, not a forged map/subclass.
            CompoundTag parent = minimalController(); parent.put(key, malformed);
            assertUnsafeBundle(parent);
            assertTrue(malformed.getAllKeys().contains(null)); assertSame(child, malformed.get(null));
        }
    }

    @Test void constructedNullListChildAtEveryRootRetainsAllRootsAndRefusesEmission()
            throws ReflectiveOperationException {
        // Exact native class via its package-private constructor; not claimed to be naturally loaded data.
        var constructor = ListTag.class.getDeclaredConstructor(List.class, byte.class); constructor.setAccessible(true);
        for (String key : ClassicRootBundle.KEYS) {
            List<Tag> children = new ArrayList<>(); children.add(null);
            ListTag malformed = constructor.newInstance(children, (byte) Tag.TAG_INT);
            CompoundTag parent = minimalController(); parent.put(key, malformed);
            assertUnsafeBundle(parent);
            assertEquals(1, malformed.size()); assertNull(malformed.get(0)); assertNull(children.get(0));
            assertEquals(Tag.TAG_INT, malformed.getElementType());
        }
    }

    private static void assertUnsafeBundle(CompoundTag parent) {
        for (var type : ClassicRootBundle.OwnerType.values()) {
            var bundle = ClassicRootBundle.captureData(parent, type);
            assertFalse(bundle.bounded()); assertTrue(bundle.requiresSaveRefusal(false));
            for (String key : ClassicRootBundle.KEYS) {
                assertEquals(parent.contains(key), bundle.present(key));
                if (parent.contains(key)) { assertTrue(bundle.retainsBorrowedIdentity(key, parent.get(key))); }
            }
            CompoundTag destination = new CompoundTag(); destination.putString("other", "untouched");
            for (String key : ClassicRootBundle.KEYS) { destination.putInt(key, -1); }
            CompoundTag before = destination.copy();
            assertThrows(IllegalStateException.class, () -> bundle.emitData(destination, false));
            assertEquals(before, destination);
        }
    }

    private static ListTag loadedEmptyList(int type) throws IOException {
        var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes);
        output.writeByte(Tag.TAG_COMPOUND); output.writeUTF(""); output.writeByte(Tag.TAG_LIST); output.writeUTF("value");
        output.writeByte(type); output.writeInt(0); output.writeByte(Tag.TAG_END);
        return (ListTag) NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))).get("value");
    }

    private static long rawBits(Tag value) {
        return value instanceof FloatTag scalar ? Float.floatToRawIntBits(scalar.getAsFloat())
                : Double.doubleToRawLongBits(((DoubleTag) value).getAsDouble());
    }
}
