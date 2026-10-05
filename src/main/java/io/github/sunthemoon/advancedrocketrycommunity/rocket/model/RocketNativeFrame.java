package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.Iterator;
import net.minecraft.nbt.*;

/** Pure NC1 framing of stable, exclusively owned standard native data; no caller is admitted here. */
final class RocketNativeFrame {
    private static final int CAP = RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES;

    enum Status { SHAPE_VALID, REJECTED }
    enum Reason { NONE, NULL_INPUT, TYPE, KEY, LIST, UTF, NUMBER_BITS, CYCLE, BYTES, NODES, DEPTH, CHANGED }
    record Inspection(Status status, long namedBytes, long nodes,
                      int deepestTag, int deepestContainer, Reason reason) { }

    private RocketNativeFrame() { }

    static Inspection inspectOwned(CompoundTag owned) {
        try {
            return new Walk(null, null).run(owned);
        } catch (Refusal refusal) {
            return rejected(refusal.reason);
        } catch (ConcurrentModificationException exception) {
            return rejected(Reason.CHANGED);
        }
    }

    static byte[] encodeOwned(CompoundTag owned) {
        Inspection expected = inspectOwned(owned);
        if (expected.status() != Status.SHAPE_VALID) { throw error(expected.reason()); }
        byte[] output = new byte[(int) expected.namedBytes()];
        try {
            Inspection actual = new Walk(output, expected).run(owned);
            if (!actual.equals(expected)) { throw error(Reason.CHANGED); }
            return output;
        } catch (Refusal | ConcurrentModificationException exception) {
            // Inspection admitted the first pass; a second-pass inconsistency exposes no partial bytes.
            throw error(Reason.CHANGED);
        }
    }

    private static Inspection rejected(Reason reason) {
        return new Inspection(Status.REJECTED, 0, 0, 0, 0, reason);
    }

    private static IllegalArgumentException error(Reason reason) {
        return new IllegalArgumentException("Rocket NC1 refusal: " + reason.name());
    }

    private static void require(boolean condition, Reason reason) {
        if (!condition) { throw new Refusal(reason); }
    }

    private static int type(Tag tag) {
        require(tag != null, Reason.TYPE);
        Class<?> kind = tag.getClass();
        if (kind == ByteTag.class) { return 1; }
        if (kind == ShortTag.class) { return 2; }
        if (kind == IntTag.class) { return 3; }
        if (kind == LongTag.class) { return 4; }
        if (kind == FloatTag.class) { return 5; }
        if (kind == DoubleTag.class) { return 6; }
        if (kind == ByteArrayTag.class) { return 7; }
        if (kind == StringTag.class) { return 8; }
        if (kind == ListTag.class) { return 9; }
        if (kind == CompoundTag.class) { return 10; }
        if (kind == IntArrayTag.class) { return 11; }
        if (kind == LongArrayTag.class) { return 12; }
        throw new Refusal(Reason.TYPE);
    }

    private static int utfLength(String value) {
        require(value != null, Reason.UTF);
        require(value.length() <= 65_535, Reason.UTF);
        int bytes = 0;
        for (int index = 0; index < value.length(); index++) {
            int unit = value.charAt(index);
            bytes += unit >= 1 && unit <= 127 ? 1 : unit <= 2047 ? 2 : 3;
            require(bytes <= 65_535, Reason.UTF);
        }
        return bytes;
    }

    private record Key(String text, byte[] order) { }

    /** A cursor exists only for an active container; children are visited one at a time. */
    private static final class Frame {
        private final Tag tag;
        private final int depth;
        private final int containerDepth;
        private final int count;
        private final int subtype;
        private final Iterator<String> keys;
        private final ArrayList<Key> sorted;
        private final long orderBytes;
        private int index;

        private Frame(Tag tag, int depth, int containerDepth, int count, int subtype,
                      Iterator<String> keys, ArrayList<Key> sorted, long orderBytes) {
            this.tag = tag;
            this.depth = depth;
            this.containerDepth = containerDepth;
            this.count = count;
            this.subtype = subtype;
            this.keys = keys;
            this.sorted = sorted;
            this.orderBytes = orderBytes;
        }
    }

    private static final class Walk {
        private final byte[] output;
        private final Inspection expected;
        private final ArrayDeque<Frame> stack = new ArrayDeque<>();
        private final IdentityHashMap<Tag, Boolean> ancestors = new IdentityHashMap<>();
        private long bytes;
        private long nodes;
        private int deepestTag;
        private int deepestContainer;
        private int position;
        private long retainedKeys;
        private long retainedOrderBytes;

        private Walk(byte[] output, Inspection expected) {
            this.output = output;
            this.expected = expected;
        }

        private Inspection run(CompoundTag root) {
            require(root != null, Reason.NULL_INPUT);
            require(type(root) == 10, Reason.TYPE);
            charge(3);
            number(10, 1);
            number(0, 2);
            visit(root, 10, 1, 0);
            while (!stack.isEmpty()) {
                Frame frame = stack.peek();
                if (frame.tag.getClass() == CompoundTag.class) {
                    CompoundTag compound = (CompoundTag) frame.tag;
                    boolean more = frame.sorted != null ? frame.index < frame.sorted.size() : frame.keys.hasNext();
                    if (more) {
                        require(frame.index < frame.count, Reason.CHANGED);
                        String key = frame.sorted != null ? frame.sorted.get(frame.index).text() : frame.keys.next();
                        frame.index++;
                        require(key != null, Reason.KEY);
                        Tag child = compound.get(key);
                        int childType = type(child);
                        int length = utfLength(key);
                        charge(3L + length);
                        number(childType, 1);
                        utf(key, length);
                        visit(child, childType, frame.depth + 1, frame.containerDepth);
                    } else {
                        require(frame.index == frame.count && compound.size() == frame.count, Reason.CHANGED);
                        number(0, 1);
                        finish(frame);
                    }
                } else {
                    ListTag list = (ListTag) frame.tag;
                    require(list.size() == frame.count && list.getElementType() == frame.subtype, Reason.CHANGED);
                    if (frame.index < frame.count) {
                        Tag child = list.get(frame.index++);
                        int childType = type(child);
                        require(childType == frame.subtype, Reason.LIST);
                        visit(child, childType, frame.depth + 1, frame.containerDepth);
                    } else { finish(frame); }
                }
            }
            if (output != null) { require(position == output.length, Reason.CHANGED); }
            return new Inspection(Status.SHAPE_VALID, bytes, nodes, deepestTag, deepestContainer, Reason.NONE);
        }

        private void finish(Frame frame) {
            stack.pop();
            ancestors.remove(frame.tag);
            if (frame.sorted != null) {
                retainedKeys -= frame.count;
                retainedOrderBytes -= frame.orderBytes;
            }
        }

        private void charge(long amount) {
            require(amount >= 0 && amount <= CAP - bytes, Reason.BYTES);
            if (expected != null) { require(amount <= expected.namedBytes() - bytes, Reason.CHANGED); }
            bytes += amount;
        }

        private void visit(Tag tag, int kind, int depth, int parentContainers) {
            // No accessor (including type inference) has run before exact-class validation.
            require(type(tag) == kind, Reason.TYPE);
            require(nodes < CAP, Reason.NODES);
            nodes++;
            require(depth <= CAP, Reason.DEPTH);
            deepestTag = Math.max(deepestTag, depth);
            if (expected != null) {
                require(nodes <= expected.nodes() && depth <= expected.deepestTag(), Reason.CHANGED);
            }
            switch (kind) {
                case 1 -> scalar(((ByteTag) tag).getAsByte(), 1);
                case 2 -> scalar(((ShortTag) tag).getAsShort(), 2);
                case 3 -> scalar(((IntTag) tag).getAsInt(), 4);
                case 4 -> scalar(((LongTag) tag).getAsLong(), 8);
                case 5 -> {
                    float value = ((FloatTag) tag).getAsFloat();
                    int bits = Float.floatToRawIntBits(value);
                    require(bits == Float.floatToIntBits(value), Reason.NUMBER_BITS);
                    scalar(bits, 4);
                }
                case 6 -> {
                    double value = ((DoubleTag) tag).getAsDouble();
                    long bits = Double.doubleToRawLongBits(value);
                    require(bits == Double.doubleToLongBits(value), Reason.NUMBER_BITS);
                    scalar(bits, 8);
                }
                case 7 -> {
                    byte[] value = ((ByteArrayTag) tag).getAsByteArray();
                    require(value != null, Reason.TYPE);
                    charge(4L + value.length);
                    number(value.length, 4);
                    if (output != null) {
                        reserve(value.length);
                        System.arraycopy(value, 0, output, position, value.length);
                        position += value.length;
                    }
                }
                case 8 -> {
                    String value = ((StringTag) tag).getAsString();
                    int length = utfLength(value);
                    charge(2L + length);
                    utf(value, length);
                }
                case 9, 10 -> container(tag, kind, depth, parentContainers + 1);
                case 11 -> {
                    int[] value = ((IntArrayTag) tag).getAsIntArray();
                    require(value != null, Reason.TYPE);
                    charge(4L + 4L * value.length);
                    number(value.length, 4);
                    if (output != null) { for (int element : value) { number(element, 4); } }
                }
                case 12 -> {
                    long[] value = ((LongArrayTag) tag).getAsLongArray();
                    require(value != null, Reason.TYPE);
                    charge(4L + 8L * value.length);
                    number(value.length, 4);
                    if (output != null) { for (long element : value) { number(element, 8); } }
                }
                default -> throw new Refusal(Reason.TYPE);
            }
        }

        private void scalar(long value, int width) { charge(width); number(value, width); }

        private void container(Tag tag, int kind, int depth, int containerDepth) {
            require(containerDepth <= CAP, Reason.DEPTH);
            if (expected != null) { require(containerDepth <= expected.deepestContainer(), Reason.CHANGED); }
            deepestContainer = Math.max(deepestContainer, containerDepth);
            require(!ancestors.containsKey(tag), Reason.CYCLE);
            ancestors.put(tag, Boolean.TRUE);
            if (kind == 9) {
                ListTag list = (ListTag) tag;
                int count = list.size();
                int subtype = list.getElementType();
                require(count >= 0 && count <= CAP - nodes, Reason.NODES);
                require(count == 0 ? subtype == 0 : subtype >= 1 && subtype <= 12, Reason.LIST);
                charge(5);
                number(subtype, 1);
                number(count, 4);
                stack.push(new Frame(tag, depth, containerDepth, count, subtype, null, null, 0));
            } else {
                CompoundTag compound = (CompoundTag) tag;
                int count = compound.size();
                require(count >= 0 && count <= CAP - nodes, Reason.NODES);
                charge(1);
                Iterator<String> keys = compound.getAllKeys().iterator();
                ArrayList<Key> sorted = null;
                long orderBytes = 0;
                if (output != null) {
                    require(count <= expected.nodes() - nodes, Reason.CHANGED);
                    sorted = new ArrayList<>();
                    long keyCharge = 0;
                    while (keys.hasNext()) {
                        require(sorted.size() < count, Reason.CHANGED);
                        String key = keys.next();
                        require(key != null, Reason.KEY);
                        int length = utfLength(key);
                        keyCharge += 4L + length; // Named header plus at least one child payload byte.
                        require(keyCharge <= expected.namedBytes() - bytes, Reason.CHANGED);
                        require(retainedKeys + sorted.size() < expected.nodes(), Reason.CHANGED);
                        require(length <= expected.namedBytes() - retainedOrderBytes - orderBytes, Reason.CHANGED);
                        byte[] order = key.getBytes(StandardCharsets.UTF_8);
                        orderBytes += order.length;
                        sorted.add(new Key(key, order));
                    }
                    require(sorted.size() == count && compound.size() == count, Reason.CHANGED);
                    sorted.sort((left, right) -> {
                        int compared = Arrays.compareUnsigned(left.order(), right.order());
                        return compared != 0 ? compared : left.text().compareTo(right.text());
                    });
                    retainedKeys += count;
                    retainedOrderBytes += orderBytes;
                }
                stack.push(new Frame(tag, depth, containerDepth, count, 0, keys, sorted, orderBytes));
            }
        }

        private void reserve(int amount) {
            require(amount >= 0 && amount <= output.length - position, Reason.CHANGED);
        }

        private void number(long value, int width) {
            if (output == null) { return; }
            reserve(width);
            for (int shift = (width - 1) * 8; shift >= 0; shift -= 8) {
                output[position++] = (byte) (value >>> shift);
            }
        }

        private void utf(String value, int length) {
            if (output == null) { return; }
            reserve(2 + length);
            number(length, 2);
            for (int index = 0; index < value.length(); index++) {
                int unit = value.charAt(index);
                if (unit >= 1 && unit <= 127) { output[position++] = (byte) unit; }
                else if (unit <= 2047) {
                    output[position++] = (byte) (0xC0 | unit >> 6);
                    output[position++] = (byte) (0x80 | unit & 63);
                } else {
                    output[position++] = (byte) (0xE0 | unit >> 12);
                    output[position++] = (byte) (0x80 | unit >> 6 & 63);
                    output[position++] = (byte) (0x80 | unit & 63);
                }
            }
        }
    }

    private static final class Refusal extends RuntimeException {
        private final Reason reason;
        private Refusal(Reason reason) { super(null, null, false, false); this.reason = reason; }
    }
}
