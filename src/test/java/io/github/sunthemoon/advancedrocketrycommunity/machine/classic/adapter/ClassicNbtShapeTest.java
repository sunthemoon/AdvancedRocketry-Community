package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

class ClassicNbtShapeTest {
    static int nativeBytes(Tag tag) throws IOException {
        var buffer = new ByteArrayOutputStream();
        var output = new DataOutputStream(buffer);
        output.writeByte(tag.getId()); output.writeUTF(""); tag.write(output);
        return buffer.size();
    }

    @Test void nativeByteBoundsMatchEveryStandardPayloadType() throws IOException {
        List<Tag> values = List.of(ByteTag.valueOf((byte) -1), ShortTag.valueOf((short) -1), IntTag.valueOf(Integer.MIN_VALUE),
                LongTag.valueOf(Long.MAX_VALUE), FloatTag.valueOf(Float.NaN), DoubleTag.valueOf(-0.0),
                new ByteArrayTag(new byte[]{1, -1}), new IntArrayTag(new int[]{1, Integer.MIN_VALUE}),
                new LongArrayTag(new long[]{1, Long.MAX_VALUE}), StringTag.valueOf("a\0\u00ff\u0800\ud83d\ude80\ud800"),
                new ListTag(), new CompoundTag());
        for (Tag tag : values) {
            int size = nativeBytes(tag);
            assertTrue(ClassicNbtShape.fits(tag, new ClassicNbtLimits(size, 24, 16_384)), tag.toString());
            if (size > 3) { assertFalse(ClassicNbtShape.fits(tag, new ClassicNbtLimits(size - 1, 24, 16_384))); }
        }
    }

    @Test void compoundAndListsUseNativeNameAndPayloadFraming() throws IOException {
        CompoundTag root = new CompoundTag();
        root.putString("\0\ud800\ud83d\ude80", "\udc00");
        ListTag list = new ListTag(); list.add(IntTag.valueOf(1)); list.add(IntTag.valueOf(2)); root.put("ordered", list);
        root.put("nested", new CompoundTag()); root.putByteArray("array", new byte[]{1, 2, 3});
        int size = nativeBytes(root);
        assertTrue(ClassicNbtShape.fits(root, new ClassicNbtLimits(size, 3, 8)));
        assertFalse(ClassicNbtShape.fits(root, new ClassicNbtLimits(size - 1, 3, 8)));
        assertFalse(ClassicNbtShape.fits(root, new ClassicNbtLimits(size, 2, 8)));
        assertFalse(ClassicNbtShape.fits(root, new ClassicNbtLimits(size, 3, 6)));
    }

    @Test void depthLimitsAreInclusiveAndCyclesRefuseWithoutRecursion() {
        CompoundTag root = nested(12);
        assertTrue(ClassicNbtShape.fits(root, ClassicNbtLimits.HATCH));
        assertFalse(ClassicNbtShape.fits(nested(13), ClassicNbtLimits.HATCH));
        CompoundTag cycle = new CompoundTag(); cycle.put("self", cycle);
        assertFalse(ClassicNbtShape.fits(cycle, ClassicNbtLimits.REJECTED));
    }

    static CompoundTag nested(int depth) {
        CompoundTag root = new CompoundTag(); CompoundTag tail = root;
        for (int i = 1; i < depth; i++) { CompoundTag next = new CompoundTag(); tail.put("n", next); tail = next; }
        return root;
    }

    @Test void nodeLimitsAreInclusiveWithoutHugePendingAllocation() {
        CompoundTag root = new CompoundTag();
        for (int i = 0; i < 255; i++) { root.putByte("n" + i, (byte) 1); }
        assertTrue(ClassicNbtShape.fits(root, ClassicNbtLimits.HATCH));
        root.putByte("extra", (byte) 1);
        assertFalse(ClassicNbtShape.fits(root, ClassicNbtLimits.HATCH));
    }

    @Test void arraysHaveNativeByteWidthsAndNoElementNodeExpansion() throws IOException {
        for (Tag tag : List.of(new ByteArrayTag(new byte[100]), new IntArrayTag(new int[100]), new LongArrayTag(new long[100]))) {
            int bytes = nativeBytes(tag);
            assertTrue(ClassicNbtShape.fits(tag, new ClassicNbtLimits(bytes, 1, 1)));
            assertFalse(ClassicNbtShape.fits(tag, new ClassicNbtLimits(bytes - 1, 1, 1)));
        }
    }

    @Test void modifiedUtfBoundaryRefusesBeforeAnEncoderWouldThrow() throws IOException {
        Tag fitting = StringTag.valueOf("\0".repeat(32_767));
        assertEquals(65_539, nativeBytes(fitting));
        assertTrue(ClassicNbtShape.fits(fitting, new ClassicNbtLimits(65_539, 1, 1)));
        Tag overflowing = StringTag.valueOf("\0".repeat(32_768));
        assertFalse(ClassicNbtShape.fits(overflowing, ClassicNbtLimits.REJECTED));
        // StringTag's native writer swallows this failure and emits an empty fallback.
        // Check the actual DataOutput boundary, and separately document that lossy native fallback.
        assertThrows(UTFDataFormatException.class, () -> new DataOutputStream(new ByteArrayOutputStream())
                .writeUTF(overflowing.getAsString()));
        assertEquals(5, nativeBytes(overflowing));
        CompoundTag badKey = new CompoundTag(); badKey.putByte("\0".repeat(32_768), (byte) 0);
        assertFalse(ClassicNbtShape.fits(badKey, ClassicNbtLimits.REJECTED));
    }

    @Test void malformedSurrogateKeysAreNotSilentlyRefusedOrNormalized() throws IOException {
        CompoundTag root = new CompoundTag(); root.putByte("?", (byte) 1);
        root.putByte("\ud800", (byte) 2); root.putByte("\ud801", (byte) 3); root.putByte("\udc00", (byte) 4);
        assertTrue(ClassicNbtShape.fits(root, ClassicNbtLimits.HATCH));
        assertEquals(4, root.size());
        assertEquals(nativeBytes(root), 3 + 1 + (3 + 1 + 1) + 3 * (3 + 3 + 1));
    }

    @Test void actualJavaOrderingTiesHaveDistinctLosslessModifiedUtfBytes() throws IOException {
        String[] keys = {"?", "\ud800", "\ud801", "\udc00"};
        for (String key : keys) { assertArrayEquals(new byte[]{0x3f}, key.getBytes(StandardCharsets.UTF_8)); }
        var encodings = new java.util.HashSet<String>();
        for (String key : keys) {
            var buffer = new ByteArrayOutputStream(); new DataOutputStream(buffer).writeUTF(key);
            byte[] bytes = buffer.toByteArray(); encodings.add(java.util.HexFormat.of().formatHex(bytes));
            assertEquals(key, new DataInputStream(new ByteArrayInputStream(bytes)).readUTF());
        }
        assertEquals(4, encodings.size());
        // Observation only: no primary/tie comparator or hash amendment is implemented here.
    }

    @Test void nonnativeTagSubclassAndIllegalCompoundEndChildCannotBeCopiedAsSupportedData() {
        CompoundTag probe = new CompoundTag() { @Override public CompoundTag copy() { fail("Unsafe copy called"); return null; } };
        assertFalse(ClassicNbtShape.fits(probe, ClassicNbtLimits.HATCH));
        CompoundTag illegal = new CompoundTag(); illegal.put("end", EndTag.INSTANCE);
        assertFalse(ClassicNbtShape.fits(illegal, ClassicNbtLimits.HATCH));
        assertFalse(ClassicNbtShape.fits(null, ClassicNbtLimits.HATCH));
    }

    @Test void typedEmptyListStoredSubtypeCopiesButNativeWriterNormalizesIt() throws ReflectiveOperationException, IOException {
        ListTag value = new ListTag();
        var field = ListTag.class.getDeclaredField("type"); field.setAccessible(true); field.setByte(value, (byte) Tag.TAG_BYTE);
        assertEquals(Tag.TAG_BYTE, value.getElementType());
        ListTag copied = value.copy();
        assertEquals(Tag.TAG_BYTE, copied.getElementType());
        assertEquals(value, new ListTag()); // equality alone omits the native list subtype
        byte[] sourceNative = namedBytes(value);
        assertEquals(Tag.TAG_END, value.getElementType());
        assertEquals(Tag.TAG_END, sourceNative[3]);
        assertArrayEquals(namedBytes(new ListTag()), sourceNative);
        // Raw capture/copy is not proof that a later vanilla writer can preserve the stored subtype.
    }

    @Test void publicNullArrayBackingsRefuseWithoutNormalizationAtRootOrNestedPositions() {
        List<Tag> values = List.of(new ByteArrayTag((byte[]) null), new IntArrayTag((int[]) null),
                new LongArrayTag((long[]) null));
        for (Tag value : values) {
            assertFalse(ClassicNbtShape.fits(value, ClassicNbtLimits.REJECTED));
            CompoundTag compound = new CompoundTag(); compound.put("value", value);
            assertFalse(ClassicNbtShape.fits(compound, ClassicNbtLimits.REJECTED));
            ListTag list = new ListTag(); list.add(value);
            assertFalse(ClassicNbtShape.fits(list, ClassicNbtLimits.REJECTED));
            assertSame(value, compound.get("value")); assertSame(value, list.get(0));
        }
        assertNull(((ByteArrayTag) values.get(0)).getAsByteArray());
        assertNull(((IntArrayTag) values.get(1)).getAsIntArray());
        assertNull(((LongArrayTag) values.get(2)).getAsLongArray());
        for (Tag empty : List.of(new ByteArrayTag(new byte[0]), new IntArrayTag(new int[0]),
                new LongArrayTag(new long[0]))) {
            assertTrue(ClassicNbtShape.fits(empty, new ClassicNbtLimits(7, 1, 1)));
        }
    }

    @Test void publicCompoundPutNullKeyRefusesShapeWithoutRemovingIt() {
        CompoundTag value = new CompoundTag(); Tag child = IntTag.valueOf(7);
        value.put(null, child);
        assertTrue(value.getAllKeys().contains(null)); assertSame(child, value.get(null));
        assertFalse(ClassicNbtShape.fits(value, ClassicNbtLimits.REJECTED));
        assertTrue(value.getAllKeys().contains(null)); assertSame(child, value.get(null));
    }

    @Test void constructedMalformedNativeListNullChildRefusesShapeWithoutNormalization()
            throws ReflectiveOperationException {
        // Package-private constructor fixture only: public add/set and native loading are not asserted to produce null.
        var constructor = ListTag.class.getDeclaredConstructor(List.class, byte.class); constructor.setAccessible(true);
        List<Tag> children = new java.util.ArrayList<>(); children.add(null);
        ListTag value = constructor.newInstance(children, (byte) Tag.TAG_INT);
        assertFalse(ClassicNbtShape.fits(value, ClassicNbtLimits.REJECTED));
        assertEquals(1, value.size()); assertNull(value.get(0)); assertNull(children.get(0));
        assertEquals(Tag.TAG_INT, value.getElementType());
    }

    static byte[] namedBytes(Tag tag) throws IOException {
        var buffer = new ByteArrayOutputStream(); var output = new DataOutputStream(buffer);
        output.writeByte(tag.getId()); output.writeUTF(""); tag.write(output);
        return buffer.toByteArray();
    }
}
