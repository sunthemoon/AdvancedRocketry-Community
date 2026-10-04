package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

class ClassicNbtCanonicalBytesTest {
    @Test void recordsActualNativeClassResourceIdentityWithoutRedistributingBodies() throws Exception {
        for (Class<?> type : List.of(Tag.class, CompoundTag.class, ListTag.class, NbtIo.class)) {
            String entry = "/" + type.getName().replace('.', '/') + ".class";
            try (InputStream input = type.getResourceAsStream(entry)) {
                assertNotNull(input);
                String sha = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                System.out.println("K2_PRIMARY " + type.getName() + " " + sha + " "
                        + type.getProtectionDomain().getCodeSource().getLocation());
            }
        }
    }

    @Test void emptyNamedRootHasExactTypeEmptyNameAndTerminator() {
        assertArrayEquals(new byte[]{10, 0, 0, 0}, encode(new CompoundTag()));
    }

    @Test void allNativePayloadTypesMatchActualNativeSingleChildFrames() throws IOException {
        ListTag ints = new ListTag(); ints.add(IntTag.valueOf(7)); ints.add(IntTag.valueOf(-9));
        CompoundTag child = new CompoundTag(); child.putLong("only", Long.MIN_VALUE);
        List<Tag> values = List.of(ByteTag.valueOf((byte) -128), ShortTag.valueOf((short) -32768),
                IntTag.valueOf(Integer.MIN_VALUE), LongTag.valueOf(Long.MAX_VALUE),
                FloatTag.valueOf(-0.0f), DoubleTag.valueOf(Double.POSITIVE_INFINITY),
                new ByteArrayTag(new byte[]{-128, 0, 127}), StringTag.valueOf("\0\ud800\udc00\ud83d\ude80"),
                ints, child, new IntArrayTag(new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE}),
                new LongArrayTag(new long[]{Long.MIN_VALUE, 0, Long.MAX_VALUE}), new ListTag(),
                new CompoundTag(), new ByteArrayTag(new byte[0]), new IntArrayTag(new int[0]),
                new LongArrayTag(new long[0]), FloatTag.valueOf(Float.NaN), DoubleTag.valueOf(Double.NaN));
        for (Tag value : values) {
            CompoundTag root = new CompoundTag(); root.put("value", value);
            byte[] before = nativeBytes(root);
            assertArrayEquals(before, encode(root), value.getClass().getSimpleName());
            assertArrayEquals(before, nativeBytes(root));
            assertEquals(value.getId(), NbtIo.read(new DataInputStream(new ByteArrayInputStream(encode(root))))
                    .get("value").getId());
        }
    }

    @Test void nestedCompoundsSortIndependentlyWhileListAndArrayOrderStayOriginal() throws IOException {
        CompoundTag first = new CompoundTag(); first.putInt("z", 9); first.putInt("a", 1);
        CompoundTag second = new CompoundTag(); second.putInt("b", 2);
        ListTag list = new ListTag(); list.add(first); list.add(second);
        CompoundTag root = new CompoundTag(); root.put("z", list); root.putIntArray("a", new int[]{3, 2, 1});
        assertArrayEquals(reference(root), encode(root));
        CompoundTag decoded = NbtIo.read(new DataInputStream(new ByteArrayInputStream(encode(root))));
        assertArrayEquals(new int[]{3, 2, 1}, decoded.getIntArray("a"));
        assertEquals(9, decoded.getList("z", Tag.TAG_COMPOUND).getCompound(0).getInt("z"));
        assertEquals(2, decoded.getList("z", Tag.TAG_COMPOUND).getCompound(1).getInt("b"));
        assertSame(first, list.get(0)); assertSame(second, list.get(1));
    }

    @Test void utf8CollisionPermutationsEmitOriginalDistinctKeysInTheFrozenTieOrder() throws IOException {
        String[] keys = {"?", "\ud800", "\ud801", "\udc00"};
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(expected); output.writeByte(10); output.writeUTF("");
        for (int i = 0; i < keys.length; i++) { output.writeByte(1); output.writeUTF(keys[i]); output.writeByte(i); }
        output.writeByte(0);
        for (List<String> order : permutations(Arrays.asList(keys))) {
            CompoundTag root = new CompoundTag();
            for (String key : order) { root.putByte(key, (byte) Arrays.asList(keys).indexOf(key)); }
            assertEquals(4, root.size());
            assertArrayEquals(expected.toByteArray(), encode(root));
            CompoundTag decoded = NbtIo.read(new DataInputStream(new ByteArrayInputStream(encode(root))));
            for (int i = 0; i < keys.length; i++) { assertEquals(i, decoded.getByte(keys[i])); }
        }
    }

    @Test void ordinaryOrderingIsNotModifiedUtfOrderingOrNormalization() throws IOException {
        String[] keys = {"\0", "!", "?", "\ud800", "\u00e9", "\ud83d\ude80"};
        CompoundTag root = new CompoundTag();
        for (int i = keys.length - 1; i >= 0; i--) { root.putString(keys[i], "\0\ud800\u00e9e\u0301"); }
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(expected); out.writeByte(10); out.writeUTF("");
        for (String key : keys) { out.writeByte(8); out.writeUTF(key); out.writeUTF(root.getString(key)); }
        out.writeByte(0);
        assertArrayEquals(expected.toByteArray(), encode(root));
        CompoundTag decoded = NbtIo.read(new DataInputStream(new ByteArrayInputStream(encode(root))));
        for (String key : keys) { assertEquals(root.getString(key), decoded.getString(key)); }
    }

    @Test void equalCopiedAndAliasedTreesHaveEqualFramesWithoutReplacingAliases() {
        CompoundTag child = new CompoundTag(); child.putInt("a", 7);
        CompoundTag shared = new CompoundTag(); shared.put("a", child); shared.put("b", child);
        CompoundTag separate = new CompoundTag(); separate.put("a", child.copy()); separate.put("b", child.copy());
        assertArrayEquals(encode(separate), encode(shared));
        assertSame(child, shared.get("a")); assertSame(child, shared.get("b"));
    }

    @Test void outputOwnershipAndMutationBetweenCallsDoNotAliasInputOrEarlierBytes() {
        byte[] source = {1, 2, 3};
        CompoundTag root = new CompoundTag(); root.putByteArray("a", source);
        byte[] first = encode(root); byte[] preserved = first.clone(); byte[] second = encode(root);
        assertNotSame(first, second); first[0] = 0;
        assertArrayEquals(preserved, second); assertArrayEquals(new byte[]{1, 2, 3}, source);
        root.getByteArray("a")[0] = 9;
        assertFalse(Arrays.equals(second, encode(root)));
        assertArrayEquals(preserved, second);
        // Inputs must remain exclusively owned/stable during one call: this is not a concurrency snapshot.
    }

    @Test void foreignRootAndNestedListCannotCallTheirWritersGetIdOrCopy() {
        AtomicInteger calls = new AtomicInteger();
        CompoundTag foreign = new CompoundTag() {
            @Override public byte getId() { calls.incrementAndGet(); return Tag.TAG_COMPOUND; }
            @Override public void write(DataOutput output) { calls.incrementAndGet(); fail("Foreign write"); }
            @Override public CompoundTag copy() { calls.incrementAndGet(); fail("Foreign copy"); return null; }
        };
        assertThrows(IllegalArgumentException.class, () -> encode(foreign)); assertEquals(0, calls.get());
        ListTag list = new ListTag(); list.add(foreign); calls.set(0);
        CompoundTag root = new CompoundTag(); root.put("a", list);
        assertThrows(IllegalArgumentException.class, () -> encode(root)); assertEquals(0, calls.get());
        assertSame(foreign, list.get(0));
    }

    @Test void externallyEncodedTypedEmptyListsRefuseWithoutChangingTheRetainedSubtype() throws IOException {
        for (byte type : new byte[]{1, 8, 10, 11, 12}) {
            CompoundTag root = typedEmpty(type);
            ListTag child = (ListTag) root.get("a");
            assertEquals(type, child.getElementType());
            assertThrows(IllegalArgumentException.class, () -> encode(root));
            assertSame(child, root.get("a")); assertEquals(type, child.getElementType()); assertEquals(0, child.size());
            // No forced native write after refusal: that would normalize rather than prove admission.
        }
        assertArrayEquals(nativeBytes(typedEmpty((byte) 0)), encode(typedEmpty((byte) 0)));
    }

    @Test void noncanonicalNanRefusalPreservesRawBitsButCanonicalNanUsesNativeFrames() throws IOException {
        CompoundTag floatRoot = new CompoundTag();
        floatRoot.putFloat("a", Float.intBitsToFloat(0x7fc00001));
        assertThrows(IllegalArgumentException.class, () -> encode(floatRoot));
        assertEquals(0x7fc00001, Float.floatToRawIntBits(floatRoot.getFloat("a")));
        CompoundTag doubleRoot = new CompoundTag(); doubleRoot.putDouble("a", Double.longBitsToDouble(0x7ff8000000000001L));
        assertThrows(IllegalArgumentException.class, () -> encode(doubleRoot));
        assertEquals(0x7ff8000000000001L, Double.doubleToRawLongBits(doubleRoot.getDouble("a")));
        doubleRoot.putDouble("a", Double.NaN);
        assertArrayEquals(nativeBytes(doubleRoot), encode(doubleRoot));
    }

    @Test void signedZerosRemainDistinct() throws ReflectiveOperationException, IOException {
        CompoundTag positive = new CompoundTag(); positive.putFloat("a", 0.0f); positive.putDouble("b", 0.0d);
        // The pinned native valueOf/load factories return cached +0 for -0. Build the exact native values,
        // not foreign subclasses, through their private scalar constructors for this byte-fidelity fixture.
        var floatConstructor = FloatTag.class.getDeclaredConstructor(float.class); floatConstructor.setAccessible(true);
        var doubleConstructor = DoubleTag.class.getDeclaredConstructor(double.class); doubleConstructor.setAccessible(true);
        CompoundTag negative = new CompoundTag();
        negative.put("a", floatConstructor.newInstance(-0.0f)); negative.put("b", doubleConstructor.newInstance(-0.0d));
        assertEquals(0x80000000, Float.floatToRawIntBits(negative.getFloat("a")));
        assertEquals(0x8000000000000000L, Double.doubleToRawLongBits(negative.getDouble("b")));
        assertArrayEquals(reference(negative), encode(negative));
        assertFalse(Arrays.equals(encode(positive), encode(negative)));
        assertEquals(0x80000000, Float.floatToRawIntBits(negative.getFloat("a")));
        assertEquals(0x8000000000000000L, Double.doubleToRawLongBits(negative.getDouble("b")));
    }

    @Test void factoryAndNativeLoadZeroInputsAreObservedBeforeTheEncoder() throws IOException {
        CompoundTag factory = new CompoundTag(); factory.putFloat("a", -0.0f); factory.putDouble("b", -0.0d);
        System.out.println("K2_FACTORY_ZERO float=" + Integer.toHexString(Float.floatToRawIntBits(factory.getFloat("a")))
                + " double=" + Long.toHexString(Double.doubleToRawLongBits(factory.getDouble("b"))));
        assertSame(FloatTag.valueOf(0.0f), FloatTag.valueOf(-0.0f));
        assertSame(DoubleTag.valueOf(0.0d), DoubleTag.valueOf(-0.0d));
        assertArrayEquals(reference(factory), encode(factory));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10); out.writeUTF(""); out.writeByte(5); out.writeUTF("a"); out.writeFloat(-0.0f);
        out.writeByte(6); out.writeUTF("b"); out.writeDouble(-0.0d); out.writeByte(0);
        CompoundTag loaded = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        System.out.println("K2_LOADED_ZERO float=" + Integer.toHexString(Float.floatToRawIntBits(loaded.getFloat("a")))
                + " double=" + Long.toHexString(Double.doubleToRawLongBits(loaded.getDouble("b"))));
        assertEquals(Float.floatToRawIntBits(factory.getFloat("a")), Float.floatToRawIntBits(loaded.getFloat("a")));
        assertEquals(Double.doubleToRawLongBits(factory.getDouble("b")), Double.doubleToRawLongBits(loaded.getDouble("b")));
        assertArrayEquals(reference(loaded), encode(loaded));
        // This input observation is not a claim that native decoding preserves an arbitrary original wire payload.
    }

    @Test void modifiedUtfDataLength65535IsExactButDoesNotOverrideResourceRootBudget() throws IOException {
        CompoundTag root = new CompoundTag(); root.putString("v", "\u0800".repeat(21_845));
        byte[] expected = nativeBytes(root);
        assertEquals(65_545, expected.length);
        assertArrayEquals(expected, ClassicNbtCanonicalBytes.encode(root, new ClassicNbtLimits(expected.length, 2, 2)));
        assertThrows(IllegalArgumentException.class, () -> encode(root));
        root.putString("v", "\u0800".repeat(21_846));
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalBytes.encode(root, ClassicNbtLimits.REJECTED));
        assertEquals(21_846, root.getString("v").length());
    }

    @Test void overlongKeyRefusesWithoutRenamingOrWritingIt() throws IOException {
        String atLimit = "\u0800".repeat(21_845);
        CompoundTag root = new CompoundTag(); root.putByte(atLimit, (byte) 7);
        byte[] expected = nativeBytes(root);
        assertArrayEquals(expected, ClassicNbtCanonicalBytes.encode(root, new ClassicNbtLimits(expected.length, 2, 2)));
        String over = atLimit + "\u0800"; root.putByte(over, (byte) 8);
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalBytes.encode(root, ClassicNbtLimits.REJECTED));
        assertEquals(2, root.size()); assertEquals(7, root.getByte(atLimit)); assertEquals(8, root.getByte(over));
    }

    @Test void exactByteDepthAndNodeBudgetsAreIndependentAndInclusive() {
        CompoundTag root = new CompoundTag(); root.putInt("a", 7);
        assertEquals(12, ClassicNbtCanonicalBytes.encode(root, new ClassicNbtLimits(12, 2, 2)).length);
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalBytes.encode(root, new ClassicNbtLimits(11, 2, 2)));
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalBytes.encode(root, new ClassicNbtLimits(12, 1, 2)));
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalBytes.encode(root, new ClassicNbtLimits(12, 2, 1)));
        assertEquals(7, root.getInt("a"));
    }

    @Test void arraysUseNativeWidthsNotAnElementNodeCount() throws IOException {
        for (Tag value : List.of(new ByteArrayTag(new byte[100]), new IntArrayTag(new int[100]), new LongArrayTag(new long[100]))) {
            CompoundTag root = new CompoundTag(); root.put("a", value);
            byte[] expected = nativeBytes(root);
            assertArrayEquals(expected, ClassicNbtCanonicalBytes.encode(root, new ClassicNbtLimits(expected.length, 2, 2)));
            assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalBytes.encode(root,
                    new ClassicNbtLimits(expected.length - 1, 2, 2)));
        }
    }

    @Test void maximumDepthAndCyclesRefuseBeforeRecursiveEmission() {
        CompoundTag root = nested(25);
        assertDoesNotThrow(() -> ClassicNbtCanonicalBytes.encode(root, new ClassicNbtLimits(512, 25, 25)));
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalBytes.encode(nested(26), ClassicNbtLimits.REJECTED));
        CompoundTag cycle = new CompoundTag(); cycle.put("self", cycle);
        assertThrows(IllegalArgumentException.class, () -> encode(cycle)); assertSame(cycle, cycle.get("self"));
    }

    @Test void arbitraryLimitsCannotExceedAnyExistingPrivateCeilingAndAreNeverClamped() {
        CompoundTag root = new CompoundTag();
        assertEquals(4, ClassicNbtCanonicalBytes.encode(root, new ClassicNbtLimits(4, 1, 1)).length);
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalBytes.encode(root, new ClassicNbtLimits(3, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalBytes.encode(root,
                new ClassicNbtLimits(ClassicNbtLimits.REJECTED.bytes() + 1, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalBytes.encode(root, new ClassicNbtLimits(4, 26, 1)));
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalBytes.encode(root, new ClassicNbtLimits(4, 1, 86_530)));
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalBytes.encode(root,
                new ClassicNbtLimits(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)));
        assertEquals(4, ClassicNbtCanonicalBytes.encode(root, ClassicNbtLimits.REJECTED).length);
    }

    @Test void illegalEndAndNullInputsRefuseWithoutRemovingSourceFields() {
        CompoundTag root = new CompoundTag(); root.put("a", EndTag.INSTANCE);
        assertThrows(IllegalArgumentException.class, () -> encode(root)); assertSame(EndTag.INSTANCE, root.get("a"));
        assertThrows(NullPointerException.class, () -> ClassicNbtCanonicalBytes.encode(null, ClassicNbtLimits.RESOURCES));
        assertThrows(NullPointerException.class, () -> ClassicNbtCanonicalBytes.encode(new CompoundTag(), null));
    }

    private static byte[] encode(CompoundTag root) { return ClassicNbtCanonicalBytes.encode(root, ClassicNbtLimits.RESOURCES); }
    private static byte[] nativeBytes(CompoundTag root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); NbtIo.write(root, new DataOutputStream(bytes));
        return bytes.toByteArray();
    }
    private static CompoundTag typedEmpty(byte type) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10); out.writeUTF(""); out.writeByte(9); out.writeUTF("a");
        out.writeByte(type); out.writeInt(0); out.writeByte(0);
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }
    private static CompoundTag nested(int depth) {
        CompoundTag root = new CompoundTag(), tail = root;
        for (int i = 1; i < depth; i++) { CompoundTag next = new CompoundTag(); tail.put("n", next); tail = next; }
        return root;
    }
    private static List<List<String>> permutations(List<String> keys) {
        if (keys.isEmpty()) { return List.of(List.of()); }
        List<List<String>> result = new ArrayList<>();
        for (String key : keys) {
            List<String> rest = new ArrayList<>(keys); rest.remove(key);
            for (List<String> suffix : permutations(rest)) {
                List<String> row = new ArrayList<>(); row.add(key); row.addAll(suffix); result.add(row);
            }
        }
        return result;
    }
    /** Independent framing oracle: native payload writers, with only compounds/lists expanded for canonical order. */
    private static byte[] reference(CompoundTag root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10); out.writeUTF(""); referencePayload(root, out); return bytes.toByteArray();
    }
    private static void referencePayload(Tag value, DataOutputStream out) throws IOException {
        if (value instanceof CompoundTag compound) {
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            keys.sort((a, b) -> {
                int primary = Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
                return primary != 0 ? primary : a.compareTo(b);
            });
            for (String key : keys) { Tag child = compound.get(key); out.writeByte(child.getId()); out.writeUTF(key); referencePayload(child, out); }
            out.writeByte(0);
        } else if (value instanceof ListTag list) {
            out.writeByte(list.getElementType()); out.writeInt(list.size());
            for (Tag child : list) { referencePayload(child, out); }
        } else { value.write(out); }
    }
}
