package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class SolarSaveTest {
    @Test void everySupportedEnergyRoundTripsWithoutEphemeralFields() {
        for (int energy = 0; energy <= 10_000; energy++) {
            CompoundTag root = SolarSave.encode(energy), before = root.copy();
            assertEquals(energy, SolarSave.decode(root));
            assertEquals(before, root);
            assertEquals(root, SolarSave.encode(SolarSave.decode(root)));
            assertEquals(2, root.size());
            assertTrue(root.contains("schema", Tag.TAG_INT) && root.contains("energy", Tag.TAG_INT));
        }
    }
    @Test void schemaKeysAndExactNativeIntegerWidthsDoNotCoerce() {
        for (String key : new String[]{"schema", "energy"}) {
            CompoundTag missing = SolarSave.encode(50); missing.remove(key);
            assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(missing));
            CompoundTag shortTag = SolarSave.encode(50); shortTag.putShort(key, (short) 1);
            assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(shortTag));
            CompoundTag longTag = SolarSave.encode(50); longTag.putLong(key, 1L);
            assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(longTag));
        }
        CompoundTag extra = SolarSave.encode(50); extra.putString("future", "retain me");
        assertTrue(SolarSave.bounded(extra));
        assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(extra));
        for (int schema : new int[]{-1, 0, 2, Integer.MAX_VALUE}) {
            CompoundTag future = SolarSave.encode(50); future.putInt("schema", schema);
            assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(future));
        }
        assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(IntTag.valueOf(1)));
        assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(StringTag.valueOf("retain")));
    }
    @Test void impossibleEnergyIsNotResetOrSaturated() {
        for (int energy : new int[]{-1, 10_001, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> SolarSave.encode(energy));
            CompoundTag raw = SolarSave.encode(0); raw.putInt("energy", energy); CompoundTag before = raw.copy();
            assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(raw));
            assertEquals(before, raw);
        }
    }
    @Test void byteDepthAndNodeBudgetsApplyBeforeRecursiveCopy() {
        CompoundTag edge = new CompoundTag(); edge.putByteArray("payload", new byte[4_078]);
        assertTrue(SolarSave.bounded(edge));
        edge.putByteArray("payload", new byte[4_079]); assertFalse(SolarSave.bounded(edge));
        CompoundTag deep = new CompoundTag(), cursor = deep;
        for (int index = 0; index < 16; index++) { CompoundTag child = new CompoundTag(); cursor.put("x", child); cursor = child; }
        assertFalse(SolarSave.bounded(deep));
        CompoundTag many = new CompoundTag();
        for (int index = 0; index < 1_024; index++) { many.putInt("k" + index, 0); }
        assertFalse(SolarSave.bounded(many));
        assertFalse(SolarSave.bounded(null));
        assertFalse(SolarSave.bounded(new ByteArrayTag((byte[]) null)));
        assertFalse(SolarSave.bounded(new LongArrayTag((long[]) null)));
        assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(deep));
    }
}
