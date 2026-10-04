package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKind;
import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ClassicNativeEntryCapacityTest {
    @Test void fluidInputAndOutputRequireTheExactFrozenCapacity() {
        for (ClassicBankKind kind : new ClassicBankKind[]{ClassicBankKind.FLUID_INPUT, ClassicBankKind.FLUID_OUTPUT}) {
            for (int amount : new int[]{1, 15_999, 16_000}) {
                assertEquals(16_000, fluidEntry(kind, 16_000, amount).capacity());
            }
            for (long capacity : new long[]{Long.MIN_VALUE, -1, 0, 1, 2, 15_999, 16_001, Long.MAX_VALUE}) {
                assertThrows(IllegalArgumentException.class, () -> fluidEntry(kind, capacity, 1));
            }
        }
    }

    @Test void itemInputAndOutputKeepEveryCapacityInTheStructuralRange() {
        for (ClassicBankKind kind : new ClassicBankKind[]{ClassicBankKind.ITEM_INPUT, ClassicBankKind.ITEM_OUTPUT}) {
            for (int capacity = 1; capacity <= 64; capacity++) {
                assertEquals(capacity, itemEntry(kind, capacity, capacity).capacity());
            }
            for (long capacity : new long[]{Long.MIN_VALUE, -1, 0, 65, 16_000, Long.MAX_VALUE}) {
                assertThrows(IllegalArgumentException.class, () -> itemEntry(kind, capacity, 1));
            }
        }
    }

    @Test void itemAmountsCannotExceedTheirUnchangedDeclaredCapacity() {
        for (ClassicBankKind kind : new ClassicBankKind[]{ClassicBankKind.ITEM_INPUT, ClassicBankKind.ITEM_OUTPUT}) {
            assertThrows(IllegalArgumentException.class, () -> itemEntry(kind, 1, 2));
            assertThrows(IllegalArgumentException.class, () -> itemEntry(kind, 63, 64));
        }
    }

    @Test void refusingAFluidCapacityDoesNotAlterOwnedNativeMetadata() {
        CompoundTag raw = new CompoundTag();
        raw.putString("FluidName", "minecraft:water");
        raw.putInt("Amount", 1);
        CompoundTag metadata = new CompoundTag();
        metadata.putIntArray("values", new int[]{7, -1});
        raw.put("Tag", metadata);
        OwnedNativeTag before = OwnedNativeTag.captureData(raw, ClassicBankKind.FLUID_INPUT);
        OwnedNativeTag after = payload(ClassicBankKind.FLUID_INPUT, 0);
        assertThrows(IllegalArgumentException.class, () -> new ClassicNativeEntry(
                new ClassicBankKey(ClassicBankKind.FLUID_INPUT, 0, 0, 0), OptionalInt.empty(),
                "minecraft:water", 1, before, after));
        metadata.getIntArray("values")[0] = 99;
        CompoundTag emitted = new CompoundTag();
        before.writeData(emitted, "payload");
        assertArrayEquals(new int[]{7, -1}, emitted.getCompound("payload").getCompound("Tag").getIntArray("values"));
        assertEquals(1, emitted.getCompound("payload").getInt("Amount"));
    }

    private static ClassicNativeEntry fluidEntry(ClassicBankKind kind, long capacity, int amount) {
        return entry(kind, capacity, amount, "minecraft:water");
    }

    private static ClassicNativeEntry itemEntry(ClassicBankKind kind, long capacity, int amount) {
        return entry(kind, capacity, amount, "minecraft:stone");
    }

    private static ClassicNativeEntry entry(ClassicBankKind kind, long capacity, int amount, String id) {
        OwnedNativeTag before = payload(kind, kind.isInput() ? amount : 0);
        OwnedNativeTag after = payload(kind, kind.isInput() ? 0 : amount);
        return new ClassicNativeEntry(new ClassicBankKey(kind, 0, 0, 0),
                kind.isItem() ? OptionalInt.of(0) : OptionalInt.empty(), id, capacity, before, after);
    }

    private static OwnedNativeTag payload(ClassicBankKind kind, int amount) {
        CompoundTag raw = new CompoundTag();
        if (amount != 0) {
            raw.putString(kind.isItem() ? "id" : "FluidName", kind.isItem() ? "minecraft:stone" : "minecraft:water");
            if (kind.isItem()) { raw.putByte("Count", (byte) amount); }
            else { raw.putInt("Amount", amount); }
        }
        return OwnedNativeTag.captureData(raw, kind);
    }
}
