package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

class ClassicNbtCanonicalHashTest {
    @Test void recordsRuntimeNativeClassResourceIdentities() throws Exception {
        for (Class<?> type : List.of(CompoundTag.class, ListTag.class, NbtIo.class)) {
            String entry = "/" + type.getName().replace('.', '/') + ".class";
            try (InputStream input = type.getResourceAsStream(entry)) {
                assertNotNull(input);
                System.out.println("K3_PRIMARY " + type.getName() + " " + digest(input.readAllBytes())
                        + " " + type.getProtectionDomain().getCodeSource().getLocation());
            }
        }
    }

    @Test void emptyRootHashesItsExactNamedNativeFrameWithoutPrefixOrTextConversion() throws Exception {
        String actual = hash(new CompoundTag());
        assertEquals(digest(new byte[]{10, 0, 0, 0}), actual);
        assertEquals(64, actual.length());
        assertTrue(actual.matches("[0-9a-f]{64}"));
        assertNotEquals(digest(new byte[0]), actual);
    }

    @Test void singleChildNativePayloadFramesHaveTheSameIndependentDigest() throws Exception {
        ListTag list = new ListTag(); list.add(IntTag.valueOf(7)); list.add(IntTag.valueOf(-9));
        CompoundTag nested = new CompoundTag(); nested.putLong("x", Long.MIN_VALUE);
        List<Tag> payloads = List.of(ByteTag.valueOf((byte) -128), ShortTag.valueOf((short) -32768),
                IntTag.valueOf(Integer.MIN_VALUE), LongTag.valueOf(Long.MAX_VALUE),
                FloatTag.valueOf(1.25f), DoubleTag.valueOf(Double.NEGATIVE_INFINITY),
                new ByteArrayTag(new byte[]{-1, 0, 1, -128}), StringTag.valueOf("\0\ud800\u00e9"),
                list, nested, new IntArrayTag(new int[]{-1, 0, 1}),
                new LongArrayTag(new long[]{Long.MIN_VALUE, 0, Long.MAX_VALUE}),
                new ListTag(), new CompoundTag(), FloatTag.valueOf(Float.NaN), DoubleTag.valueOf(Double.NaN));
        for (Tag payload : payloads) {
            CompoundTag root = new CompoundTag(); root.put("v", payload);
            byte[] before = nativeBytes(root);
            assertEquals(digest(before), hash(root), payload.getClass().getSimpleName());
            assertArrayEquals(before, nativeBytes(root));
            assertSame(payload, root.get("v"));
        }
    }

    @Test void binaryWidthsAndModifiedUtfUseManualCanonicalBytes() throws Exception {
        CompoundTag root = new CompoundTag();
        root.putString("z", "\0\ud800\u0800"); root.putInt("a", 0xff00fe81);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10); out.writeUTF("");
        out.writeByte(3); out.writeUTF("a"); out.writeInt(0xff00fe81);
        out.writeByte(8); out.writeUTF("z"); out.writeUTF("\0\ud800\u0800"); out.writeByte(0);
        assertEquals(digest(bytes.toByteArray()), hash(root));
        assertEquals(digest(ClassicNbtCanonicalBytes.encode(root, ClassicNbtLimits.RESOURCES)), hash(root));
    }

    @Test void compoundPermutationsAndOrdinaryUtf8TiesKeepOneManualNativeDigest() throws Exception {
        String[] keys = {"\0", "!", "?", "\ud800", "\ud801", "\udc00", "\u00e9", "\ud83d\ude80"};
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes); out.writeByte(10); out.writeUTF("");
        for (int i = 0; i < keys.length; i++) { out.writeByte(1); out.writeUTF(keys[i]); out.writeByte(i); }
        out.writeByte(0);
        String expected = digest(bytes.toByteArray());
        for (int offset = 0; offset < keys.length; offset++) {
            for (int direction : new int[]{1, -1}) {
                CompoundTag root = new CompoundTag();
                for (int i = 0; i < keys.length; i++) {
                    int index = Math.floorMod(offset + direction * i, keys.length);
                    root.putByte(keys[index], (byte) index);
                }
                assertEquals(expected, hash(root));
                assertEquals(keys.length, root.size());
                for (int i = 0; i < keys.length; i++) { assertEquals(i, root.getByte(keys[i])); }
            }
        }
    }

    @Test void nestedCompoundOrderIsCanonicalButListAndArrayOrderAreNotReordered() throws Exception {
        CompoundTag child = new CompoundTag(); child.putInt("z", 9); child.putInt("a", 1);
        CompoundTag reversed = new CompoundTag(); reversed.putInt("a", 1); reversed.putInt("z", 9);
        CompoundTag first = new CompoundTag(); first.put("child", child);
        CompoundTag second = new CompoundTag(); second.put("child", reversed);
        assertEquals(hash(first), hash(second));
        ListTag forward = new ListTag(); forward.add(IntTag.valueOf(1)); forward.add(IntTag.valueOf(2));
        ListTag backward = new ListTag(); backward.add(IntTag.valueOf(2)); backward.add(IntTag.valueOf(1));
        first.put("list", forward); second.put("list", backward);
        assertNotEquals(hash(first), hash(second));
        second.put("list", forward.copy());
        assertEquals(hash(first), hash(second));
        first.putIntArray("array", new int[]{1, 2}); second.putIntArray("array", new int[]{2, 1});
        assertNotEquals(hash(first), hash(second));
        assertSame(child, first.get("child")); assertSame(forward, first.get("list"));
        assertArrayEquals(new int[]{1, 2}, first.getIntArray("array"));
    }

    @Test void typePresenceAndUnnormalizedStringsRemainDifferentFiniteFixtures() {
        CompoundTag byteRoot = new CompoundTag(); byteRoot.putByte("v", (byte) 1);
        CompoundTag intRoot = new CompoundTag(); intRoot.putInt("v", 1);
        assertNotEquals(hash(byteRoot), hash(intRoot));
        assertNotEquals(hash(new CompoundTag()), hash(byteRoot));
        CompoundTag composed = new CompoundTag(); composed.putString("v", "\u00e9");
        CompoundTag decomposed = new CompoundTag(); decomposed.putString("v", "e\u0301");
        assertNotEquals(hash(composed), hash(decomposed));
        // Distinct finite observations are not a proof of general collision freedom.
    }

    @Test void aliasesArePreservedAndLaterInputMutationCannotChangeEarlierString() throws Exception {
        CompoundTag child = new CompoundTag(); child.putInt("v", 7);
        CompoundTag aliases = new CompoundTag(); aliases.put("a", child); aliases.put("b", child);
        CompoundTag separate = new CompoundTag(); separate.put("a", child.copy()); separate.put("b", child.copy());
        byte[] before = nativeBytes(aliases);
        String original = hash(aliases);
        assertEquals(hash(separate), original);
        assertArrayEquals(before, nativeBytes(aliases));
        assertSame(child, aliases.get("a")); assertSame(child, aliases.get("b"));
        child.putInt("v", 8);
        assertNotEquals(original, hash(aliases));
        assertEquals(hash(separate), original);
    }

    @Test void independentCallsUseNoMutableDigestState() throws Exception {
        assertEquals(0, ClassicNbtCanonicalHash.class.getDeclaredFields().length);
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int id = 0; id < 4; id++) {
                int ownedId = id;
                jobs.add(() -> {
                    CompoundTag root = new CompoundTag(); root.putInt("id", ownedId);
                    String expected = digest(nativeBytes(root));
                    for (int i = 0; i < 32; i++) { assertEquals(expected, hash(root)); }
                    return null;
                });
            }
            for (var future : executor.invokeAll(jobs)) { future.get(); }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void stricterByteDepthAndNodeBudgetsAreInclusiveAndNotRelaxedByDigesting() throws Exception {
        CompoundTag root = new CompoundTag(); root.putInt("a", 7);
        String expected = digest(nativeBytes(root));
        assertEquals(expected, ClassicNbtCanonicalHash.sha256(root, new ClassicNbtLimits(12, 2, 2)));
        for (ClassicNbtLimits limits : List.of(new ClassicNbtLimits(11, 2, 2),
                new ClassicNbtLimits(12, 1, 2), new ClassicNbtLimits(12, 2, 1))) {
            assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalHash.sha256(root, limits));
        }
        assertEquals(7, root.getInt("a"));
        assertEquals(digest(new byte[]{10, 0, 0, 0}), ClassicNbtCanonicalHash.sha256(new CompoundTag(),
                new ClassicNbtLimits(4, 1, 1)));
    }

    @Test void existingMaximumByteCeilingAndEveryGreaterLimitComponentRemainBounded() throws Exception {
        CompoundTag root = new CompoundTag();
        byte[] payload = new byte[ClassicNbtLimits.REJECTED.bytes() - 12];
        payload[0] = -1; payload[payload.length - 1] = -128;
        root.putByteArray("a", payload);
        byte[] reference = nativeBytes(root);
        assertEquals(ClassicNbtLimits.REJECTED.bytes(), reference.length);
        assertEquals(digest(reference), ClassicNbtCanonicalHash.sha256(root, ClassicNbtLimits.REJECTED));
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalHash.sha256(root,
                new ClassicNbtLimits(reference.length - 1, 25, 86_529)));
        assertSame(payload, root.getByteArray("a")); assertEquals(-1, payload[0]);
        CompoundTag empty = new CompoundTag();
        for (ClassicNbtLimits limits : List.of(new ClassicNbtLimits(1_873_118, 1, 1),
                new ClassicNbtLimits(4, 26, 1), new ClassicNbtLimits(4, 1, 86_530))) {
            assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalHash.sha256(empty, limits));
        }
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalHash.sha256(empty,
                new ClassicNbtLimits(3, 1, 1)));
    }

    @Test void nullIllegalEndAndForeignInputsRefuseWithoutNativeCallbacksOrReplacement() {
        assertThrows(NullPointerException.class, () -> hash(null));
        assertThrows(NullPointerException.class, () -> ClassicNbtCanonicalHash.sha256(new CompoundTag(), null));
        CompoundTag illegal = new CompoundTag(); illegal.put("end", EndTag.INSTANCE);
        assertThrows(IllegalArgumentException.class, () -> hash(illegal));
        assertSame(EndTag.INSTANCE, illegal.get("end"));
        CompoundTag cycle = new CompoundTag(); cycle.put("self", cycle);
        assertThrows(IllegalArgumentException.class, () -> hash(cycle));
        assertSame(cycle, cycle.get("self"));
        AtomicInteger callbacks = new AtomicInteger();
        CompoundTag foreign = new CompoundTag() {
            @Override public byte getId() { callbacks.incrementAndGet(); return Tag.TAG_COMPOUND; }
            @Override public void write(DataOutput output) { callbacks.incrementAndGet(); fail("Foreign write"); }
            @Override public CompoundTag copy() { callbacks.incrementAndGet(); fail("Foreign copy"); return null; }
        };
        assertThrows(IllegalArgumentException.class, () -> hash(foreign));
        CompoundTag root = new CompoundTag(); root.put("foreign", foreign);
        assertThrows(IllegalArgumentException.class, () -> hash(root));
        assertEquals(0, callbacks.get()); assertSame(foreign, root.get("foreign"));
    }

    @Test void externallyEncodedTypedEmptyListsRefuseWithoutLosingSubtype() throws Exception {
        for (byte type = 1; type <= 12; type++) {
            CompoundTag root = typedEmpty(type);
            ListTag retained = (ListTag) root.get("v");
            assertEquals(type, retained.getElementType());
            assertThrows(IllegalArgumentException.class, () -> hash(root));
            assertSame(retained, root.get("v")); assertEquals(type, retained.getElementType());
            assertEquals(0, retained.size());
        }
        CompoundTag canonical = typedEmpty((byte) 0);
        assertEquals(digest(nativeBytes(canonical)), hash(canonical));
    }

    @Test void lossyNanAndOverlongModifiedUtfRefuseWithoutNormalization() {
        CompoundTag nan = new CompoundTag(); nan.putFloat("v", Float.intBitsToFloat(0x7fc00001));
        assertThrows(IllegalArgumentException.class, () -> hash(nan));
        assertEquals(0x7fc00001, Float.floatToRawIntBits(nan.getFloat("v")));
        nan.putDouble("v", Double.longBitsToDouble(0x7ff8000000000001L));
        assertThrows(IllegalArgumentException.class, () -> hash(nan));
        assertEquals(0x7ff8000000000001L, Double.doubleToRawLongBits(nan.getDouble("v")));
        CompoundTag text = new CompoundTag(); String overlong = "\u0800".repeat(21_846);
        text.putString("v", overlong);
        assertThrows(IllegalArgumentException.class, () -> ClassicNbtCanonicalHash.sha256(text, ClassicNbtLimits.REJECTED));
        assertEquals(overlong, text.getString("v"));
    }

    @Test void exactConstructorSignedZerosFollowWrittenBytesNotFactoryOrLoadClaims() throws Exception {
        var floats = FloatTag.class.getDeclaredConstructor(float.class); floats.setAccessible(true);
        var doubles = DoubleTag.class.getDeclaredConstructor(double.class); doubles.setAccessible(true);
        CompoundTag positive = new CompoundTag(); positive.putFloat("f", 0.0f); positive.putDouble("d", 0.0d);
        CompoundTag negative = new CompoundTag();
        negative.put("f", floats.newInstance(-0.0f)); negative.put("d", doubles.newInstance(-0.0d));
        assertEquals(0x80000000, Float.floatToRawIntBits(negative.getFloat("f")));
        assertEquals(0x8000000000000000L, Double.doubleToRawLongBits(negative.getDouble("d")));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10); out.writeUTF(""); out.writeByte(6); out.writeUTF("d"); out.writeDouble(-0.0d);
        out.writeByte(5); out.writeUTF("f"); out.writeFloat(-0.0f); out.writeByte(0);
        assertEquals(digest(bytes.toByteArray()), hash(negative));
        assertNotEquals(hash(positive), hash(negative));
        assertEquals(0x80000000, Float.floatToRawIntBits(negative.getFloat("f")));
        assertEquals(0x8000000000000000L, Double.doubleToRawLongBits(negative.getDouble("d")));
        // This exact-class emission fixture is not naturally loaded gameplay/persistence support.
    }

    private static String hash(CompoundTag root) {
        return ClassicNbtCanonicalHash.sha256(root, ClassicNbtLimits.RESOURCES);
    }

    private static String digest(byte[] input) throws NoSuchAlgorithmException {
        byte[] value = MessageDigest.getInstance("SHA-256").digest(input);
        StringBuilder hex = new StringBuilder(64);
        for (byte element : value) {
            hex.append(Character.forDigit((element & 255) >>> 4, 16));
            hex.append(Character.forDigit(element & 15, 16));
        }
        return hex.toString();
    }

    private static byte[] nativeBytes(CompoundTag root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.write(root, new DataOutputStream(bytes));
        return bytes.toByteArray();
    }

    private static CompoundTag typedEmpty(byte type) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10); out.writeUTF(""); out.writeByte(9); out.writeUTF("v");
        out.writeByte(type); out.writeInt(0); out.writeByte(0);
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }
}
