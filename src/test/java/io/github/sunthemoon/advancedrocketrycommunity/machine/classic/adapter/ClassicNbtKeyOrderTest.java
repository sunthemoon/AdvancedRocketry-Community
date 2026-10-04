package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Pure JDK comparator/framed-key fixtures, not native NBT/hash/resource admission. */
class ClassicNbtKeyOrderTest {
    @Test
    void completeUnsignedPrimaryOrderWinsOverNaturalUtf16Order() {
        assertTrue(ClassicNbtKeyOrder.compare("z", units(0x0080)) < 0);
        assertTrue(ClassicNbtKeyOrder.compare(units(0x007f), units(0x0080)) < 0);
        assertTrue(ClassicNbtKeyOrder.compare(units(0xe000), units(0xd800, 0xdc00)) < 0);
        assertTrue(units(0xe000).compareTo(units(0xd800, 0xdc00)) > 0);
        assertTrue(ClassicNbtKeyOrder.compare(units(0x07ff), units(0x0800)) < 0);
        assertTrue(ClassicNbtKeyOrder.compare("", units(0)) < 0);
    }

    @Test
    void fallbackOccursOnlyAfterAnEntirePrimaryTieNotAPrefix() {
        String high = units(0xd800);
        assertTrue(ClassicNbtKeyOrder.compare(high, "?a") < 0);
        assertTrue(ClassicNbtKeyOrder.compare(high + "a", "?z") < 0);
        assertTrue(ClassicNbtKeyOrder.compare("a", "aa") < 0);
        assertTrue(ClassicNbtKeyOrder.compare("?" + high, high + "?") < 0);
        assertEquals(0, ClassicNbtKeyOrder.compare(high, new String(high)));
    }

    @Test
    void replacementCollisionsRemainDistinctOriginalUnitsAndNativeUtfKeys() throws IOException {
        List<String> keys = List.of("?", units(0xd800), units(0xd801), units(0xdc00));
        List<String> frames = new ArrayList<>();
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            assertArrayEquals(new byte[] {0x3f}, key.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = framedKeys(List.of(key));
            frames.add(java.util.HexFormat.of().formatHex(bytes));
            assertEquals(key, new DataInputStream(new ByteArrayInputStream(bytes)).readUTF());
            for (int other = 0; other < keys.size(); other++) {
                assertEquals(Integer.compare(index, other),
                        Integer.signum(ClassicNbtKeyOrder.compare(key, keys.get(other))));
            }
        }
        assertEquals(List.of("00013f", "0003eda080", "0003eda081", "0003edb080"), frames);
        assertTrue(ClassicNbtKeyOrder.compare(units(0xfffd), "?") > 0);
    }

    @Test
    void finiteComparatorLawsEqualityAndNonzeroPrimarySignsHold() {
        List<String> keys = List.of("", "?", "??", "A", "a", "a?", "aZ", units(0),
                units(0x0080), units(0x00e9), units(0x0065, 0x0301), units(0xd800),
                units(0xd801), units(0xdc00), units(0xdfff), units(0xd800, 0xdc00),
                units(0xd800, 0x0061), units(0x003f, 0xd800), units(0xe000));
        for (String left : keys) {
            for (String right : keys) {
                int actual = Integer.signum(ClassicNbtKeyOrder.compare(left, right));
                assertEquals(left.equals(right), actual == 0);
                assertEquals(-actual, Integer.signum(ClassicNbtKeyOrder.compare(right, left)));
                int primary = unsignedPrimary(left, right);
                if (primary != 0) { assertEquals(primary, actual); }
                for (String third : keys) {
                    if (actual <= 0 && ClassicNbtKeyOrder.compare(right, third) <= 0) {
                        assertTrue(ClassicNbtKeyOrder.compare(left, third) <= 0);
                    }
                }
            }
        }
    }

    @Test
    void everySingleUtf16UnitPreservesPrimarySignAndOriginalEquality() {
        List<String> anchors = List.of("", "?", "A", units(0x0080), units(0xe000),
                units(0xd800), units(0xdc00), units(0xd800, 0xdc00));
        for (int unit = 0; unit <= 0xffff; unit++) {
            String key = units(unit);
            assertEquals(0, ClassicNbtKeyOrder.compare(key, new String(key)));
            for (String anchor : anchors) {
                int primary = unsignedPrimary(key, anchor);
                int actual = Integer.signum(ClassicNbtKeyOrder.compare(key, anchor));
                if (primary != 0) { assertEquals(primary, actual); }
                assertEquals(key.equals(anchor), actual == 0);
                if (primary == 0 && !key.equals(anchor)) {
                    assertEquals(originalUnits(key, anchor), actual);
                }
            }
        }
    }

    @Test
    void allEightKeyInsertionOrdersProduceTheSameOriginalKeyFrames() throws IOException {
        List<String> keys = List.of("?", units(0xd800), units(0xd801), units(0xdc00),
                "Z", units(0x0065, 0x0301), units(0x00e9), units(0xd800, 0xdc00));
        List<String> expected = List.copyOf(keys);
        byte[] frames = framedKeys(expected);
        assertEquals("00013f0003eda0800003eda0810003edb08000015a000365cc810002c3a90006eda080edb080",
                java.util.HexFormat.of().formatHex(frames));
        AtomicInteger visited = new AtomicInteger();
        permutations(new ArrayList<>(keys), 0, current -> {
            List<String> copy = new ArrayList<>(current);
            copy.sort(ClassicNbtKeyOrder::compare);
            assertEquals(expected, copy);
            assertArrayEquals(frames, framedKeys(copy));
            visited.incrementAndGet();
        });
        assertEquals(40_320, visited.get());
        assertEquals(keys, List.of("?", units(0xd800), units(0xd801), units(0xdc00),
                "Z", units(0x0065, 0x0301), units(0x00e9), units(0xd800, 0xdc00)));
    }

    @Test
    void callersCanIndependentlyOrderNestedKeySetsWithoutMutationOrAliasReplacement() {
        String shared = new String(units(0xd800));
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put(shared, "inner"); inner.put("?", "question");
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("z", inner); outer.put(shared, inner); outer.put("?", shared);
        List<String> originalOuter = new ArrayList<>(outer.keySet());
        List<String> originalInner = new ArrayList<>(inner.keySet());
        TreeMap<String, Object> sortedOuter = new TreeMap<>(ClassicNbtKeyOrder::compare);
        TreeMap<String, Object> sortedInner = new TreeMap<>(ClassicNbtKeyOrder::compare);
        sortedOuter.putAll(outer); sortedInner.putAll(inner);
        assertEquals(3, sortedOuter.size()); assertEquals(2, sortedInner.size());
        assertEquals(originalOuter, new ArrayList<>(outer.keySet()));
        assertEquals(originalInner, new ArrayList<>(inner.keySet()));
        assertSame(inner, outer.get(shared)); assertSame(inner, sortedOuter.get(shared));
        assertSame(shared, outer.get("?")); assertSame(shared, sortedOuter.get("?"));
        assertTrue(sortedOuter.keySet().stream().anyMatch(key -> key == shared));
        assertTrue(sortedInner.keySet().stream().anyMatch(key -> key == shared));
    }

    @Test
    void orderingLeavesNormalizationAndNativeEmissionEligibilityToCaller() throws IOException {
        String composed = units(0x00e9), decomposed = units(0x0065, 0x0301);
        assertNotEquals(0, ClassicNbtKeyOrder.compare(composed, decomposed));
        assertFalse(Arrays.equals(framedKeys(List.of(composed)), framedKeys(List.of(decomposed))));
        String nul = units(0);
        assertArrayEquals(new byte[] {0}, nul.getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(new byte[] {0, 2, (byte) 0xc0, (byte) 0x80}, framedKeys(List.of(nul)));
        String nativeLimit = "a".repeat(65_535);
        assertEquals(65_537, framedKeys(List.of(nativeLimit)).length);
        String overNativeLimit = nativeLimit + "a";
        assertTrue(ClassicNbtKeyOrder.compare(nativeLimit, overNativeLimit) < 0);
        assertThrows(UTFDataFormatException.class, () -> framedKeys(List.of(overNativeLimit)));
        assertEquals(65_536, overNativeLimit.length());
        // These JDK-only boundary strings exceed the resource-root budget; they are not admitted NBT.
    }

    private static int unsignedPrimary(String left, String right) {
        byte[] a = left.getBytes(StandardCharsets.UTF_8), b = right.getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < Math.min(a.length, b.length); index++) {
            int av = Byte.toUnsignedInt(a[index]), bv = Byte.toUnsignedInt(b[index]);
            if (av != bv) { return Integer.compare(av, bv); }
        }
        return Integer.compare(a.length, b.length);
    }

    private static int originalUnits(String left, String right) {
        for (int index = 0; index < Math.min(left.length(), right.length()); index++) {
            int a = left.charAt(index), b = right.charAt(index);
            if (a != b) { return Integer.compare(a, b); }
        }
        return Integer.compare(left.length(), right.length());
    }

    private static String units(int... values) {
        char[] result = new char[values.length];
        for (int index = 0; index < values.length; index++) { result[index] = (char) values[index]; }
        return new String(result);
    }

    private static byte[] framedKeys(List<String> keys) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        for (String key : keys) { out.writeUTF(key); }
        out.flush(); return bytes.toByteArray();
    }

    private static void permutations(List<String> keys, int start, FramedPermutation consumer) throws IOException {
        if (start == keys.size()) { consumer.accept(keys); return; }
        for (int index = start; index < keys.size(); index++) {
            java.util.Collections.swap(keys, start, index);
            permutations(keys, start + 1, consumer);
            java.util.Collections.swap(keys, start, index);
        }
    }

    @FunctionalInterface
    private interface FramedPermutation {
        void accept(List<String> keys) throws IOException;
    }
}
