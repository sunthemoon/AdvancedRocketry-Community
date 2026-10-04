package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
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
}
