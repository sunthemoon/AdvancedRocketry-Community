package io.github.sunthemoon.advancedrocketrycommunity.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

class BoundedNbtTest {
    @Test void standardPayloadsMatchExactNativeByteBoundaries() throws IOException {
        CompoundTag compound = new CompoundTag();
        compound.putString("key\u0000\ud800", "value\u00e9\udc00");
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(1)); list.add(IntTag.valueOf(2));
        for (Tag tag : List.of(ByteTag.valueOf((byte) 1), ShortTag.valueOf((short) 2),
                IntTag.valueOf(3), LongTag.valueOf(4), FloatTag.valueOf(1.5F), DoubleTag.valueOf(2.5),
                new ByteArrayTag(new byte[] {1, 2}), new IntArrayTag(new int[] {3, 4}),
                new LongArrayTag(new long[] {5, 6}), StringTag.valueOf("a\u0000\u0800\ud800"),
                new ListTag(), list, compound)) {
            byte[] before = nativeBytes(tag.copy());
            assertTrue(BoundedNbt.fits(tag, before.length, 16, 256), tag.getClass().getSimpleName());
            assertFalse(BoundedNbt.fits(tag, before.length - 1, 16, 256));
            assertArrayEquals(before, nativeBytes(tag.copy()));
        }
    }

    @Test void nativeReadTypedEmptyListsAreRefusedWithoutMutation() throws IOException {
        for (byte elementType = 1; elementType <= 12; elementType++) {
            CompoundTag parent = typedEmptyList(elementType);
            ListTag list = (ListTag) parent.get("future");
            assertEquals(elementType, list.getElementType());
            assertFalse(BoundedNbt.fits(parent, 65_536, 20, 4_096));
            assertFalse(BoundedNbt.fits(list, 65_536, 20, 4_096));
            assertEquals(elementType, list.getElementType());
            assertEquals(elementType, ((ListTag) parent.copy().get("future")).getElementType());
        }
        assertTrue(BoundedNbt.fits(typedEmptyList((byte) 0), 65_536, 20, 4_096));
    }

    @Test void nativeUtfFallbackCannotMakeAnOverlongStringFit() {
        CompoundTag parent = new CompoundTag();
        String value = "\u0800".repeat(21_846);
        parent.putString("future", value);
        assertFalse(BoundedNbt.fits(parent, 65_536, 20, 4_096));
        assertEquals(value, parent.getString("future"));
        assertFalse(BoundedNbt.fits(StringTag.valueOf(value), 131_072, 20, 4_096));
        parent = new CompoundTag();
        parent.putString(value, "x");
        assertFalse(BoundedNbt.fits(parent, 131_072, 20, 4_096));
        assertTrue(parent.contains(value));
    }

    @Test void nativeUtfCeilingAndRemainingByteBudgetAreExact() throws IOException {
        StringTag atLimit = StringTag.valueOf("\u0800".repeat(21_845));
        assertEquals(65_540, nativeBytes(atLimit).length);
        assertTrue(BoundedNbt.fits(atLimit, 65_540, 1, 1));
        assertFalse(BoundedNbt.fits(atLimit, 65_539, 1, 1));
        assertFalse(BoundedNbt.fits(StringTag.valueOf("\u0000".repeat(32_768)), 131_072, 1, 1));
    }

    @Test void nativeNanNormalizationIsRefusedButCanonicalValuesAreAllowed() {
        FloatTag rawFloat = FloatTag.valueOf(Float.intBitsToFloat(0x7fc00001));
        DoubleTag rawDouble = DoubleTag.valueOf(Double.longBitsToDouble(0x7ff8000000000001L));
        assertEquals(0x7fc00001, Float.floatToRawIntBits(rawFloat.getAsFloat()));
        assertEquals(0x7ff8000000000001L, Double.doubleToRawLongBits(rawDouble.getAsDouble()));
        assertFalse(BoundedNbt.fits(rawFloat, 7, 1, 1));
        assertFalse(BoundedNbt.fits(rawDouble, 11, 1, 1));
        assertTrue(BoundedNbt.fits(FloatTag.valueOf(Float.NaN), 7, 1, 1));
        assertTrue(BoundedNbt.fits(DoubleTag.valueOf(Double.NaN), 11, 1, 1));
        assertTrue(BoundedNbt.fits(FloatTag.valueOf(Float.POSITIVE_INFINITY), 7, 1, 1));
        assertTrue(BoundedNbt.fits(DoubleTag.valueOf(Double.NEGATIVE_INFINITY), 11, 1, 1));
    }

    @Test void illegalEndAndForeignCompoundCannotReachNativeEmission() {
        assertFalse(BoundedNbt.fits(EndTag.INSTANCE, 1_024, 16, 256));
        CompoundTag parent = new CompoundTag();
        parent.put("future", EndTag.INSTANCE);
        assertFalse(BoundedNbt.fits(parent, 1_024, 16, 256));
        assertSame(EndTag.INSTANCE, parent.get("future"));
        assertFalse(BoundedNbt.fits(new CompoundTag() { }, 1_024, 16, 256));
    }

    @Test void unchangedDepthAndNodeCeilingsRefuseWithoutMutatingCompanions() {
        CompoundTag parent = new CompoundTag();
        parent.putInt("one", 1); parent.putInt("two", 2);
        assertTrue(BoundedNbt.fits(parent, 1_024, 2, 3));
        assertFalse(BoundedNbt.fits(parent, 1_024, 1, 3));
        assertFalse(BoundedNbt.fits(parent, 1_024, 2, 2));
        assertEquals(2, parent.size());
        assertEquals(1, parent.getInt("one"));
        assertEquals(2, parent.getInt("two"));
        assertFalse(BoundedNbt.fits(null, 1_024, 2, 3));
        assertFalse(BoundedNbt.fits(parent, -1, 2, 3));
    }

    private static CompoundTag typedEmptyList(byte type) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(Tag.TAG_COMPOUND); output.writeUTF("");
            output.writeByte(Tag.TAG_LIST); output.writeUTF("future");
            output.writeByte(type); output.writeInt(0); output.writeByte(Tag.TAG_END);
        }
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    private static byte[] nativeBytes(Tag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(tag.getId()); output.writeUTF(""); tag.write(output);
        }
        return bytes.toByteArray();
    }
}
