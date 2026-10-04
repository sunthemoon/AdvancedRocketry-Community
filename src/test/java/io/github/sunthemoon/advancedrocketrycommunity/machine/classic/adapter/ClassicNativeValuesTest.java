package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

class ClassicNativeValuesTest {
    static final ClassicBankKey INPUT = new ClassicBankKey(ClassicBankKind.ITEM_INPUT, 0, 0, 0);
    static final String STONE = "minecraft:stone";

    static CompoundTag item(int count, String metadata) {
        CompoundTag tag = new CompoundTag();
        if (count > 0) {
            tag.putString("id", STONE); tag.putByte("Count", (byte) count);
            if (metadata != null) { CompoundTag data = new CompoundTag(); data.putString("value", metadata); tag.put("tag", data); }
        }
        return tag;
    }
    static OwnedNativeTag payload(int count) { return OwnedNativeTag.captureData(item(count, null), ClassicBankKind.ITEM_INPUT); }
    static ClassicNativeEntry entry(ClassicBankKey key, int slot, int before, int after) {
        return new ClassicNativeEntry(key, OptionalInt.of(slot), STONE, 64, payload(before), payload(after));
    }
    static ClassicAllocation row(int index, int amount, List<ClassicAllocationPart> parts) {
        return new ClassicAllocation(true, ProcessResourceKind.ITEM, index, STONE, amount, parts);
    }
    static ClassicNativePlan plan(List<ClassicNativeEntry> entries, List<ClassicAllocation> allocations) {
        return new ClassicNativePlan(new UUID(3, 4), 1, 1, ClassicValuesTest.RECIPE,
                ClassicValuesTest.HASH, 0, 1, ClassicValuesTest.HASH, "1".repeat(64), entries, allocations);
    }

    @Test void pureCaptureDetachesIncomingAndEveryEmittedPayload() {
        CompoundTag raw = item(2, "original");
        OwnedNativeTag owned = OwnedNativeTag.captureData(raw, ClassicBankKind.ITEM_INPUT);
        raw.getCompound("tag").putString("value", "caller");
        CompoundTag first = new CompoundTag(); owned.writeData(first, "payload");
        assertEquals("original", first.getCompound("payload").getCompound("tag").getString("value"));
        first.getCompound("payload").getCompound("tag").putString("value", "emitter");
        CompoundTag second = new CompoundTag(); owned.writeData(second, "payload");
        assertEquals(item(2, "original"), second.getCompound("payload"));
    }

    @Test void itemEnvelopeRejectsWrongNativeTypesHiddenAmountsAndCaps() {
        for (int count : new int[]{-128, -1, 0, 65, 127}) {
            CompoundTag raw = item(1, null); raw.putByte("Count", (byte) count);
            assertThrows(IllegalArgumentException.class, () -> OwnedNativeTag.captureData(raw, ClassicBankKind.ITEM_INPUT));
        }
        CompoundTag wrong = item(1, null); wrong.putInt("Count", 1);
        assertThrows(IllegalArgumentException.class, () -> OwnedNativeTag.captureData(wrong, ClassicBankKind.ITEM_INPUT));
        CompoundTag caps = item(1, null); caps.put("ForgeCaps", new CompoundTag());
        assertThrows(IllegalArgumentException.class, () -> OwnedNativeTag.captureData(caps, ClassicBankKind.ITEM_INPUT));
        CompoundTag shorthand = item(1, null); shorthand.putString("id", "stone");
        assertThrows(IllegalArgumentException.class, () -> OwnedNativeTag.captureData(shorthand, ClassicBankKind.ITEM_INPUT));
    }

    @Test void nativeIdentityRegistrationAndPerItemMaxAreExplicitlyDeferred() {
        CompoundTag raw = item(64, null); raw.putString("id", "unregistered:structural_data");
        var value = OwnedNativeTag.captureData(raw, ClassicBankKind.ITEM_INPUT);
        assertEquals(64, value.amount());
        assertEquals("unregistered:structural_data", value.resourceId());
        // This is a private data envelope, not native decoding or supported frame admission.
    }

    @Test void fluidEnvelopeRetainsAllMetadataAndEnforcesExactAmountType() {
        CompoundTag raw = new CompoundTag(); raw.putString("FluidName", "minecraft:water"); raw.putInt("Amount", 16_000);
        CompoundTag meta = new CompoundTag(); meta.putLongArray("values", new long[]{1, -1, Long.MAX_VALUE}); raw.put("Tag", meta);
        var value = OwnedNativeTag.captureData(raw, ClassicBankKind.FLUID_OUTPUT);
        raw.getCompound("Tag").getLongArray("values")[0] = 9;
        CompoundTag emitted = new CompoundTag(); value.writeData(emitted, "value");
        assertArrayEquals(new long[]{1, -1, Long.MAX_VALUE}, emitted.getCompound("value").getCompound("Tag").getLongArray("values"));
        for (int amount : new int[]{-1, 0, 16_001}) {
            CompoundTag bad = emitted.getCompound("value").copy(); bad.putInt("Amount", amount);
            assertThrows(IllegalArgumentException.class, () -> OwnedNativeTag.captureData(bad, ClassicBankKind.FLUID_OUTPUT));
        }
        raw.putLong("Amount", 1);
        assertThrows(IllegalArgumentException.class, () -> OwnedNativeTag.captureData(raw, ClassicBankKind.FLUID_OUTPUT));
    }

    @Test void oversizedMetadataRefusesBeforeCopy() {
        CompoundTag raw = item(1, null); CompoundTag metadata = new CompoundTag();
        metadata.putByteArray("large", new byte[32_768]); raw.put("tag", metadata);
        assertThrows(IllegalArgumentException.class, () -> OwnedNativeTag.captureData(raw, ClassicBankKind.ITEM_INPUT));
    }

    @Test void entriesRequireProperSlotCapacityDirectionAndFullNativeIdentity() {
        assertDoesNotThrow(() -> entry(INPUT, 0, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> entry(INPUT, 4, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> entry(INPUT, 0, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> entry(INPUT, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ClassicNativeEntry(INPUT, OptionalInt.empty(),
                STONE, 64, payload(2), payload(1)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicNativeEntry(INPUT, OptionalInt.of(0),
                "minecraft:dirt", 64, payload(2), payload(1)));
        assertThrows(IllegalArgumentException.class, () -> new ClassicNativeEntry(INPUT, OptionalInt.of(0),
                STONE, 1, payload(2), payload(1)));
    }

    @Test void remainderAndMergeCannotChangeOrInventNativeMetadata() {
        var a = OwnedNativeTag.captureData(item(2, "a"), ClassicBankKind.ITEM_INPUT);
        var b = OwnedNativeTag.captureData(item(1, "b"), ClassicBankKind.ITEM_INPUT);
        assertThrows(IllegalArgumentException.class, () -> new ClassicNativeEntry(INPUT, OptionalInt.of(0), STONE, 64, a, b));
        assertThrows(IllegalArgumentException.class, () -> new ClassicNativeEntry(INPUT, OptionalInt.of(0), STONE, 64, a, payload(1)));
        assertDoesNotThrow(() -> new ClassicNativeEntry(INPUT, OptionalInt.of(0), STONE, 64, a, payload(0)));
    }

    @Test void allocationBoundsSortedUniquePartsAndCheckedTotals() {
        var source = new ArrayList<>(List.of(new ClassicAllocationPart(0, 1), new ClassicAllocationPart(1, 2)));
        var row = row(0, 3, source); source.clear();
        assertEquals(2, row.parts().size());
        assertThrows(UnsupportedOperationException.class, () -> row.parts().clear());
        assertThrows(IllegalArgumentException.class, () -> row(0, 4, List.of(new ClassicAllocationPart(0, 3))));
        assertThrows(IllegalArgumentException.class, () -> row(0, 2, List.of(new ClassicAllocationPart(0, 1), new ClassicAllocationPart(0, 1))));
        assertThrows(IllegalArgumentException.class, () -> row(4, 1, List.of(new ClassicAllocationPart(0, 1))));
        assertThrows(IllegalArgumentException.class, () -> new ClassicAllocationPart(64, 1));
        assertThrows(IllegalArgumentException.class, () -> new ClassicAllocationPart(0, 16_001));
    }

    @Test void planOwnsCollectionsAndProvesExactEntryDebits() {
        var entries = new ArrayList<>(List.of(entry(INPUT, 0, 2, 1)));
        var rows = new ArrayList<>(List.of(row(0, 1, List.of(new ClassicAllocationPart(0, 1)))));
        var value = plan(entries, rows); entries.clear(); rows.clear();
        assertEquals(1, value.entryCount());
        assertEquals(ClassicValuesTest.RECIPE, value.recipeId());
        assertThrows(UnsupportedOperationException.class, () -> value.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> value.allocations().clear());
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(entry(INPUT, 0, 2, 0)),
                List.of(row(0, 1, List.of(new ClassicAllocationPart(0, 1))))));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(entry(INPUT, 0, 2, 1)),
                List.of(row(0, 1, List.of(new ClassicAllocationPart(1, 1))))));
    }

    @Test void planRejectsDuplicatePhysicalSlotUnsortedEntriesAndRows() {
        var a = entry(INPUT, 0, 2, 1); var b = entry(INPUT, 1, 2, 1);
        var r0 = row(0, 1, List.of(new ClassicAllocationPart(0, 1)));
        var r1 = row(1, 1, List.of(new ClassicAllocationPart(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(a, a), List.of(r0)));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(b, a), List.of(r0, r1)));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(a, b), List.of(r1, r0)));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(a), List.of(r0, r0)));
    }

    @Test void planCountersRefuseOverflowAndKeepLargeOrdinals() {
        var entries = List.of(entry(INPUT, 0, 2, 1)); var rows = List.of(row(0, 1, List.of(new ClassicAllocationPart(0, 1))));
        assertDoesNotThrow(() -> new ClassicNativePlan(new UUID(1, 1), Long.MAX_VALUE, Long.MAX_VALUE,
                ClassicValuesTest.RECIPE, ClassicValuesTest.HASH, Long.MAX_VALUE - 1, Long.MAX_VALUE,
                ClassicValuesTest.HASH, ClassicValuesTest.HASH, entries, rows));
        assertThrows(IllegalArgumentException.class, () -> new ClassicNativePlan(new UUID(1, 1), 1, 1,
                ClassicValuesTest.RECIPE, ClassicValuesTest.HASH, Long.MAX_VALUE, Long.MIN_VALUE,
                ClassicValuesTest.HASH, ClassicValuesTest.HASH, entries, rows));
    }

    @Test void sixtyFourEntriesAndTwoHundredFiftySixAllocationPartsFit() {
        var entries = new ArrayList<ClassicNativeEntry>();
        for (int i = 0; i < 64; i++) {
            // Lexicographic kernel channel ordering differs from numeric coordinate ordering.
            entries.add(entry(new ClassicBankKey(ClassicBankKind.ITEM_INPUT, i, 0, 0), 0, 4, 0));
        }
        entries.sort(Comparator.comparing(ClassicNativeEntry::quantitativeKey));
        var parts = new ArrayList<ClassicAllocationPart>();
        for (int i = 0; i < 64; i++) { parts.add(new ClassicAllocationPart(i, 1)); }
        var rows = new ArrayList<ClassicAllocation>();
        for (int i = 0; i < 4; i++) { rows.add(row(i, 64, parts)); }
        assertEquals(64, plan(entries, rows).entryCount());
        entries.add(entry(new ClassicBankKey(ClassicBankKind.ITEM_INPUT, 64, 0, 0), 0, 4, 0));
        assertThrows(IllegalArgumentException.class, () -> plan(entries, rows));
    }

    @Test void machineRequiresCompletedMatchingWorkAndActivePlanBanks() {
        var plan = plan(List.of(entry(INPUT, 0, 2, 1)), List.of(row(0, 1, List.of(new ClassicAllocationPart(0, 1)))));
        var work = new ClassicProcessFrame.Work(ClassicValuesTest.RECIPE, ClassicValuesTest.HASH, 300, 20,
                300, 6_000, 1, ProcessMachineState.RECOVERY_REQUIRED);
        assertDoesNotThrow(() -> machine(plan, work, List.of(ClassicValuesTest.item(0, 0, 0))));
        assertThrows(IllegalArgumentException.class, () -> machine(plan, work, List.of()));
        var unfinished = new ClassicProcessFrame.Work(ClassicValuesTest.RECIPE, ClassicValuesTest.HASH, 300, 20,
                299, 5_980, 1, ProcessMachineState.RUNNING);
        assertThrows(IllegalArgumentException.class, () -> machine(plan, unfinished, List.of(ClassicValuesTest.item(0, 0, 0))));
    }

    private static ClassicMachineState machine(ClassicNativePlan plan, ClassicProcessFrame work, List<ClassicAssignment> assignments) {
        return new ClassicMachineState(ClassicValuesTest.LATHE, ClassicValuesTest.OWNER, ClassicValuesTest.LEVEL,
                BlockPos.ZERO, 1, PatternRotation.ZERO, MultiblockFormationState.FORMED, assignments, 1,
                ClassicValuesTest.NONE, work, Optional.of(plan), Optional.empty(), Optional.empty());
    }
}
