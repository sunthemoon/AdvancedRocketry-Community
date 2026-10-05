package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

class RocketNativeFrameTest {
    private static final int CAP = RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES;

    @Test void emptyNamedRootHasExactGoldenAndMeasurements() {
        CompoundTag root = new CompoundTag();
        assertArrayEquals(hex("0a000000"), RocketNativeFrame.encodeOwned(root));
        assertEquals(new RocketNativeFrame.Inspection(RocketNativeFrame.Status.SHAPE_VALID,
                4, 1, 1, 1, RocketNativeFrame.Reason.NONE), RocketNativeFrame.inspectOwned(root));
    }

    @Test void allTwelveExactTypesHaveBigEndianGoldenFrames() {
        ListTag list = new ListTag(); list.add(IntTag.valueOf(0x01020304));
        Tag[] tags = {ByteTag.valueOf((byte) -2), ShortTag.valueOf((short) 0x1234),
                IntTag.valueOf(0x01020304), LongTag.valueOf(0x0102030405060708L),
                FloatTag.valueOf(1.0f), DoubleTag.valueOf(-2.0), new ByteArrayTag(new byte[]{-1, 2}),
                StringTag.valueOf("\0A"), list, new CompoundTag(),
                new IntArrayTag(new int[]{0x01020304, -1}), new LongArrayTag(new long[]{-1L})};
        String[] payloads = {"fe", "1234", "01020304", "0102030405060708", "3f800000",
                "c000000000000000", "00000002ff02", "0003c08041", "030000000101020304", "00",
                "0000000201020304ffffffff", "00000001ffffffffffffffff"};
        for (int index = 0; index < tags.length; index++) {
            CompoundTag root = root("v", tags[index]);
            String type = String.format("%02x", index + 1);
            byte[] expected = hex("0a0000" + type + "000176" + payloads[index] + "00");
            assertArrayEquals(expected, RocketNativeFrame.encodeOwned(root), "type " + (index + 1));
            assertEquals(expected.length, RocketNativeFrame.inspectOwned(root).namedBytes());
        }
    }

    @Test void smallSafeNativePayloadDifferentialCoversEveryTypeAndNestedOrder() throws Exception {
        CompoundTag root = new CompoundTag();
        root.putByte("byte", (byte) -7); root.putShort("short", (short) -17);
        root.putInt("int", Integer.MIN_VALUE); root.putLong("long", Long.MAX_VALUE);
        root.putFloat("float", Float.POSITIVE_INFINITY); root.putDouble("double", Double.NaN);
        root.putByteArray("bytes", new byte[]{0, -1, 5}); root.putIntArray("ints", new int[]{-1, 0});
        root.putLongArray("longs", new long[]{Long.MIN_VALUE, 4}); root.putString("str", "\0\ud800\ud83d\ude80");
        ListTag list = new ListTag(); list.add(StringTag.valueOf("one")); list.add(StringTag.valueOf("two"));
        root.put("list", list); root.put("compound", root("inner", ByteTag.valueOf((byte) 3)));
        assertArrayEquals(oracle(root), RocketNativeFrame.encodeOwned(root));
        ByteArrayOutputStream nativeBytes = new ByteArrayOutputStream();
        NbtIo.write(root, new DataOutputStream(nativeBytes));
        assertEquals(nativeBytes.size(), RocketNativeFrame.inspectOwned(root).namedBytes());
    }

    @Test void canonicalOrderingRetainsUtf8CollisionKeysAcrossPermutations() throws Exception {
        List<String> keys = List.of("?", "\ud800", "\ud801", "\udc00", "\ue000", "\ud83d\ude80", "a", "\0");
        CompoundTag expected = new CompoundTag();
        for (String key : keys) { expected.putString(key, key); }
        byte[] golden = oracle(expected);
        for (int seed = 0; seed < 64; seed++) {
            List<String> order = new ArrayList<>(keys); Collections.shuffle(order, new Random(seed));
            CompoundTag root = new CompoundTag(); for (String key : order) { root.putString(key, key); }
            assertArrayEquals(golden, RocketNativeFrame.encodeOwned(root));
            assertEquals(8, root.size());
        }
        assertArrayEquals("?".getBytes(StandardCharsets.UTF_8), "\ud800".getBytes(StandardCharsets.UTF_8));
        assertFalse(Arrays.equals(oracle(root("?", ByteTag.ONE)), oracle(root("\ud800", ByteTag.ONE))));
    }

    @Test void modifiedUtfPreservesNulSurrogatesAndExact65535Boundaries() throws Exception {
        String exact = "a".repeat(65_535);
        for (CompoundTag root : List.of(root(exact, ByteTag.ONE), root("", StringTag.valueOf(exact)),
                root("\ud800\0", StringTag.valueOf("\udc00\ud83d\ude80")))) {
            assertArrayEquals(oracle(root), RocketNativeFrame.encodeOwned(root));
        }
        refusal(root("a".repeat(65_536), ByteTag.ONE), RocketNativeFrame.Reason.UTF);
        refusal(root("", StringTag.valueOf("a".repeat(65_536))), RocketNativeFrame.Reason.UTF);
        assertArrayEquals(oracle(root("", StringTag.valueOf("\u0800".repeat(21_845)))),
                RocketNativeFrame.encodeOwned(root("", StringTag.valueOf("\u0800".repeat(21_845)))));
        refusal(root("", StringTag.valueOf("\u0800".repeat(21_846))), RocketNativeFrame.Reason.UTF);
    }

    @Test void canonicalEmptyListAndSafeNativeListsRetainSubtypeAndOrder() throws Exception {
        ListTag empty = new ListTag(); assertArrayEquals(hex("0a0000090000000000000000"),
                RocketNativeFrame.encodeOwned(root("", empty)));
        for (int type : new int[]{1, 8, 10, 11, 12}) {
            Tag child = switch (type) {
                case 1 -> ByteTag.ONE; case 8 -> StringTag.valueOf("text");
                case 10 -> root("q", IntTag.valueOf(6)); case 11 -> new IntArrayTag(new int[]{3});
                default -> new LongArrayTag(new long[]{8});
            };
            ListTag list = rawList(new ArrayList<>(List.of(child)), (byte) type);
            assertArrayEquals(oracle(root("l", list)), RocketNativeFrame.encodeOwned(root("l", list)));
            assertEquals(type, list.getElementType());
        }
    }

    @Test void externallyEncodedTypedEmptyListsRefuseWithoutWriterNormalization() throws Exception {
        for (int type = 1; type <= 12; type++) {
            byte[] encoded = hex("0a00000900016c" + String.format("%02x", type) + "0000000000");
            CompoundTag root = NbtIo.read(new DataInputStream(new ByteArrayInputStream(encoded)));
            ListTag list = (ListTag) root.get("l");
            assertEquals(type, list.getElementType());
            refusal(root, RocketNativeFrame.Reason.LIST);
            assertEquals(type, list.getElementType()); assertEquals(0, list.size());
        }
    }

    @Test void malformedExactListsRejectMismatchNullAndIllegalSubtype() throws Exception {
        refusal(root("l", rawList(new ArrayList<>(List.of(IntTag.valueOf(1))), (byte) 1)), RocketNativeFrame.Reason.LIST);
        List<Tag> nullChild = new ArrayList<>(); nullChild.add(null);
        refusal(root("l", rawList(nullChild, (byte) 1)), RocketNativeFrame.Reason.TYPE);
        refusal(root("l", rawList(new ArrayList<>(List.of(ByteTag.ONE)), (byte) 0)), RocketNativeFrame.Reason.LIST);
        refusal(root("l", rawList(new ArrayList<>(), (byte) -1)), RocketNativeFrame.Reason.LIST);
    }

    @Test void nullRootForeignRootNamedEndNullKeyAndNullChildHaveFixedErrors() throws Exception {
        refusal(null, RocketNativeFrame.Reason.NULL_INPUT);
        ForeignCompound foreign = new ForeignCompound(); refusal(foreign, RocketNativeFrame.Reason.TYPE);
        assertEquals(0, foreign.calls);
        refusal(root("secret", EndTag.INSTANCE), RocketNativeFrame.Reason.TYPE);
        CompoundTag missing = new CompoundTag(); Map<String, Tag> fields = new HashMap<>();
        fields.put("secret", null); setField(missing, "tags", fields);
        refusal(missing, RocketNativeFrame.Reason.TYPE);
        CompoundTag nullKey = new CompoundTag(); fields = new HashMap<>(); fields.put(null, ByteTag.ONE);
        setField(nullKey, "tags", fields); refusal(nullKey, RocketNativeFrame.Reason.KEY);
    }

    @Test void foreignChildrenNeverReceiveTypeWriteCopyOrEqualityCallbacks() throws Exception {
        ForeignCompound foreign = new ForeignCompound();
        refusal(root("secret", foreign), RocketNativeFrame.Reason.TYPE);
        refusal(root("l", rawList(new ArrayList<>(List.of(foreign)), (byte) 10)), RocketNativeFrame.Reason.TYPE);
        assertEquals(0, foreign.calls);
    }

    @Test void compoundAndListCyclesRefuseButRepeatedAliasesExpand() throws Exception {
        CompoundTag cycle = new CompoundTag(); cycle.put("self", cycle);
        refusal(cycle, RocketNativeFrame.Reason.CYCLE);
        ListTag listCycle = rawList(new ArrayList<>(), (byte) 9);
        @SuppressWarnings("unchecked") List<Tag> backing = (List<Tag>) field(listCycle, "list");
        backing.add(listCycle); refusal(root("l", listCycle), RocketNativeFrame.Reason.CYCLE);
        CompoundTag shared = root("n", IntTag.valueOf(4));
        CompoundTag alias = new CompoundTag(); alias.put("a", shared); alias.put("b", shared);
        ListTag nested = new ListTag(); nested.add(shared); alias.put("c", nested);
        assertArrayEquals(oracle(alias), RocketNativeFrame.encodeOwned(alias));
        assertEquals(8, RocketNativeFrame.inspectOwned(alias).nodes());
        assertSame(shared, alias.get("a")); assertSame(shared, alias.get("b"));
    }

    @Test void arraysCountAsOneNodeAndContainerDepthIsNotTagDepth() {
        ListTag outer = new ListTag(); outer.add(new IntArrayTag(new int[]{1, 2, 3}));
        var result = RocketNativeFrame.inspectOwned(root("a", outer));
        assertEquals(3, result.nodes()); assertEquals(3, result.deepestTag()); assertEquals(2, result.deepestContainer());
        CompoundTag metadata = new CompoundTag(); metadata.putString("id", "opaque:owner");
        metadata.putInt("x", -1); metadata.putInt("y", 5000); metadata.putInt("z", 99);
        assertEquals(RocketNativeFrame.Status.SHAPE_VALID, RocketNativeFrame.inspectOwned(metadata).status());
    }

    @Test void canonicalNaNsAndInfinitiesEmitWhileLossyRawNaNsRemainUntouched() throws Exception {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            CompoundTag root = root("f", FloatTag.valueOf(value)); assertArrayEquals(oracle(root), RocketNativeFrame.encodeOwned(root));
        }
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            CompoundTag root = root("d", DoubleTag.valueOf(value)); assertArrayEquals(oracle(root), RocketNativeFrame.encodeOwned(root));
        }
        FloatTag f = exactFloat(Float.intBitsToFloat(0x7fc00001));
        DoubleTag d = exactDouble(Double.longBitsToDouble(0xfff8000000000001L));
        refusal(root("f", f), RocketNativeFrame.Reason.NUMBER_BITS);
        refusal(root("d", d), RocketNativeFrame.Reason.NUMBER_BITS);
        assertEquals(0x7fc00001, Float.floatToRawIntBits(f.getAsFloat()));
        assertEquals(0xfff8000000000001L, Double.doubleToRawLongBits(d.getAsDouble()));
    }

    @Test void exactConstructorSignedZeroIsAnEmissionFixtureNotReloadAdmission() throws Exception {
        CompoundTag root = new CompoundTag(); root.put("f", exactFloat(-0.0f)); root.put("d", exactDouble(-0.0));
        byte[] result = RocketNativeFrame.encodeOwned(root);
        assertArrayEquals(hex("0a0000060001648000000000000000050001668000000000"), result);
        assertArrayEquals(oracle(root), result);
        assertEquals(0x80000000, Float.floatToRawIntBits(((FloatTag) root.get("f")).getAsFloat()));
        assertEquals(0x8000000000000000L, Double.doubleToRawLongBits(((DoubleTag) root.get("d")).getAsDouble()));
    }

    @Test void nativeFactoriesAndLoadNormalizeZeroBeforeEncoderInvocation() throws Exception {
        assertSame(FloatTag.valueOf(0.0f), FloatTag.valueOf(-0.0f));
        assertSame(DoubleTag.valueOf(0.0), DoubleTag.valueOf(-0.0));
        byte[] signed = hex("0a0000050001668000000006000164800000000000000000");
        CompoundTag loaded = NbtIo.read(new DataInputStream(new ByteArrayInputStream(signed)));
        assertEquals(0, Float.floatToRawIntBits(((FloatTag) loaded.get("f")).getAsFloat()));
        assertEquals(0L, Double.doubleToRawLongBits(((DoubleTag) loaded.get("d")).getAsDouble()));
        assertArrayEquals(hex("0a0000060001640000000000000000050001660000000000"), RocketNativeFrame.encodeOwned(loaded));
    }

    @Test void exactByteArrayCeilingAndOneByteOverUseNamedNotCompressedBytes() {
        CompoundTag exact = root("", new ByteArrayTag(new byte[CAP - 11]));
        assertEquals(CAP, RocketNativeFrame.inspectOwned(exact).namedBytes());
        assertEquals(CAP, RocketNativeFrame.encodeOwned(exact).length);
        refusal(root("", new ByteArrayTag(new byte[CAP - 10])), RocketNativeFrame.Reason.BYTES);
    }

    @Test void intAndLongArrayWidthsChargeBeforeEmission() {
        for (CompoundTag exact : List.of(root("a", new IntArrayTag(new int[(CAP - 12) / 4])),
                root("abcde", new LongArrayTag(new long[(CAP - 16) / 8])))) {
            assertEquals(CAP, RocketNativeFrame.inspectOwned(exact).namedBytes());
            assertEquals(CAP, RocketNativeFrame.encodeOwned(exact).length);
        }
        refusal(root("a", new IntArrayTag(new int[(CAP - 12) / 4 + 1])), RocketNativeFrame.Reason.BYTES);
        refusal(root("abcde", new LongArrayTag(new long[(CAP - 16) / 8 + 1])), RocketNativeFrame.Reason.BYTES);
    }

    @Test void denseListExactCeilingIsLazyAndExpandsOccurrences() throws Exception {
        // No native recursive writer or copy participates in the worst-case fixtures.
        List<Tag> backing = new ArrayList<>(); for (int i = 0; i < CAP - 12; i++) { backing.add(ByteTag.ONE); }
        ListTag list = rawList(backing, (byte) 1); CompoundTag root = root("", list);
        var measured = RocketNativeFrame.inspectOwned(root);
        assertEquals(CAP, measured.namedBytes()); assertEquals(CAP - 10, measured.nodes());
        byte[] encoded = RocketNativeFrame.encodeOwned(root); assertEquals(CAP, encoded.length);
        assertEquals(1, encoded[encoded.length - 2]);
        backing.add(ByteTag.ONE); refusal(root, RocketNativeFrame.Reason.BYTES);
    }

    @Test void deepCompoundExactCeilingIsIterativeAndDoesNotImportReader512() {
        CompoundTag root = new CompoundTag(); CompoundTag last = root;
        for (int i = 0; i < CAP / 4 - 1; i++) { CompoundTag child = new CompoundTag(); last.put("", child); last = child; }
        var measured = RocketNativeFrame.inspectOwned(root);
        assertEquals(CAP, measured.namedBytes()); assertEquals(CAP / 4, measured.nodes());
        assertEquals(CAP / 4, measured.deepestTag()); assertEquals(CAP / 4, measured.deepestContainer());
        byte[] encoded = RocketNativeFrame.encodeOwned(root); assertEquals(CAP, encoded.length);
        assertEquals(0, encoded[encoded.length - 1]);
        last.put("", new CompoundTag()); refusal(root, RocketNativeFrame.Reason.BYTES);
    }

    @Test void outputsAreFreshAndDoNotExposeOrRetainInputArrays() throws Exception {
        byte[] source = {1, 2, 3}; ByteArrayTag array = new ByteArrayTag(source); CompoundTag root = root("b", array);
        byte[] before = oracle(root); byte[] first = RocketNativeFrame.encodeOwned(root); byte[] second = RocketNativeFrame.encodeOwned(root);
        assertNotSame(first, second); assertArrayEquals(before, first);
        first[0] = 99; assertArrayEquals(before, second); assertArrayEquals(before, RocketNativeFrame.encodeOwned(root));
        source[0] = 7; assertEquals(1, second[11]); assertEquals(7, RocketNativeFrame.encodeOwned(root)[11]);
    }

    @Test void observedBackingMutationRefusesWithoutClaimingRaceDetection() throws Exception {
        // Reflective backing replacement is a mechanical observation seam, outside stable-owner admission.
        List<Tag> changing = new ArrayList<>(List.of(ByteTag.ONE)) {
            @Override public Tag get(int index) { Tag value = super.get(index); add(ByteTag.ONE); return value; }
        };
        CompoundTag root = root("l", rawList(changing, (byte) 1));
        refusal(root, RocketNativeFrame.Reason.CHANGED);
        Map<String, Tag> betweenPasses = new HashMap<>() {
            private int reads;
            @Override public Tag get(Object key) {
                Tag value = super.get(key); if (++reads == 2) { put("grew", ByteTag.ONE); } return value;
            }
        };
        betweenPasses.put("a", ByteTag.ONE); CompoundTag second = new CompoundTag(); setField(second, "tags", betweenPasses);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> RocketNativeFrame.encodeOwned(second));
        assertEquals("Rocket NC1 refusal: CHANGED", error.getMessage()); assertNull(error.getCause());
    }

    @Test void inspectionHasNoPartialAuthorityAndRefusalDoesNotEchoInput() {
        String secret = "sensitive-key-" + "x".repeat(65_536);
        var result = RocketNativeFrame.inspectOwned(root(secret, ByteTag.ONE));
        assertEquals(new RocketNativeFrame.Inspection(RocketNativeFrame.Status.REJECTED,
                0, 0, 0, 0, RocketNativeFrame.Reason.UTF), result);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RocketNativeFrame.encodeOwned(root(secret, ByteTag.ONE)));
        assertEquals("Rocket NC1 refusal: UTF", error.getMessage()); assertNull(error.getCause());
    }

    @Test void publicNullBackedArraysRefuseWithoutNormalization() {
        ByteArrayTag bytes = new ByteArrayTag((byte[]) null);
        IntArrayTag ints = new IntArrayTag((int[]) null);
        LongArrayTag longs = new LongArrayTag((long[]) null);
        assertNull(bytes.getAsByteArray()); assertNull(ints.getAsIntArray()); assertNull(longs.getAsLongArray());
        for (Tag input : new Tag[]{bytes, ints, longs}) {
            CompoundTag root = root("array", input);
            assertSame(input, root.get("array"));
            refusal(root, RocketNativeFrame.Reason.TYPE);
            assertSame(input, root.get("array")); assertEquals(1, root.size());
        }
        assertNull(bytes.getAsByteArray()); assertNull(ints.getAsIntArray()); assertNull(longs.getAsLongArray());
    }

    private static CompoundTag root(String key, Tag value) { CompoundTag root = new CompoundTag(); root.put(key, value); return root; }
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static void refusal(CompoundTag root, RocketNativeFrame.Reason reason) {
        assertEquals(new RocketNativeFrame.Inspection(RocketNativeFrame.Status.REJECTED, 0, 0, 0, 0, reason),
                RocketNativeFrame.inspectOwned(root));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> RocketNativeFrame.encodeOwned(root));
        assertEquals("Rocket NC1 refusal: " + reason, error.getMessage()); assertNull(error.getCause());
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void setField(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static ListTag rawList(List<Tag> children, byte type) throws Exception {
        Constructor<ListTag> constructor = ListTag.class.getDeclaredConstructor(List.class, byte.class);
        constructor.setAccessible(true); return constructor.newInstance(children, type);
    }
    private static FloatTag exactFloat(float value) throws Exception {
        Constructor<FloatTag> constructor = FloatTag.class.getDeclaredConstructor(float.class);
        constructor.setAccessible(true); return constructor.newInstance(value);
    }
    private static DoubleTag exactDouble(double value) throws Exception {
        Constructor<DoubleTag> constructor = DoubleTag.class.getDeclaredConstructor(double.class);
        constructor.setAccessible(true); return constructor.newInstance(value);
    }

    /** Recursive only for the small safe differential cases, never dense/deep fixtures. */
    private static byte[] oracle(CompoundTag root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10); out.writeUTF(""); oraclePayload(root, out); return bytes.toByteArray();
    }
    private static void oraclePayload(Tag tag, DataOutputStream out) throws IOException {
        if (tag instanceof CompoundTag compound) {
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            keys.sort((a, b) -> { int order = Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
                return order != 0 ? order : a.compareTo(b); });
            for (String key : keys) { Tag child = compound.get(key); out.writeByte(child.getId()); out.writeUTF(key); oraclePayload(child, out); }
            out.writeByte(0);
        } else if (tag instanceof ListTag list) {
            out.writeByte(list.getElementType()); out.writeInt(list.size());
            for (Tag child : list) { oraclePayload(child, out); }
        } else { tag.write(out); }
    }
    private static final class ForeignCompound extends CompoundTag {
        private int calls;
        @Override public byte getId() { calls++; throw new AssertionError("foreign getId"); }
        @Override public void write(DataOutput out) { calls++; throw new AssertionError("foreign write"); }
        @Override public CompoundTag copy() { calls++; throw new AssertionError("foreign copy"); }
        @Override public boolean equals(Object value) { calls++; throw new AssertionError("foreign equals"); }
        @Override public int hashCode() { calls++; throw new AssertionError("foreign hash"); }
        @Override public String toString() { calls++; throw new AssertionError("foreign toString"); }
    }
}
