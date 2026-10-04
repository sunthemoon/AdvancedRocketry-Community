package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import net.minecraft.nbt.*;

/** Pure named-empty-root framing. The caller exclusively owns and keeps the input unchanged. */
final class ClassicNbtCanonicalBytes {
    private ClassicNbtCanonicalBytes() { }

    static byte[] encode(CompoundTag root, ClassicNbtLimits limits) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(limits, "limits");
        require(limits.bytes() <= ClassicNbtLimits.REJECTED.bytes()
                && limits.depth() <= ClassicNbtLimits.REJECTED.depth()
                && limits.nodes() <= ClassicNbtLimits.REJECTED.nodes(), "Limits exceed the private ceiling");
        // Shape first rejects foreign subclasses without calling their getId/write/copy methods.
        require(ClassicNbtShape.fits(root, limits), "Not bounded standard native data");
        require(BoundedNbt.fits(root, limits.bytes(), limits.depth(), limits.nodes()), "Not lossless native data");
        var bytes = new FixedOutput(limits.bytes());
        var writer = new Writer(bytes, limits);
        try {
            writer.output.writeByte(Tag.TAG_COMPOUND);
            writer.utf("");
            writer.payload(root, 1);
            return bytes.result();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Canonical framing failed", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalArgumentException(message); }
    }

    private static int type(Tag tag) {
        require(tag != null, "Missing child");
        Class<?> type = tag.getClass();
        if (type == ByteTag.class) { return Tag.TAG_BYTE; }
        if (type == ShortTag.class) { return Tag.TAG_SHORT; }
        if (type == IntTag.class) { return Tag.TAG_INT; }
        if (type == LongTag.class) { return Tag.TAG_LONG; }
        if (type == FloatTag.class) { return Tag.TAG_FLOAT; }
        if (type == DoubleTag.class) { return Tag.TAG_DOUBLE; }
        if (type == ByteArrayTag.class) { return Tag.TAG_BYTE_ARRAY; }
        if (type == StringTag.class) { return Tag.TAG_STRING; }
        if (type == ListTag.class) { return Tag.TAG_LIST; }
        if (type == CompoundTag.class) { return Tag.TAG_COMPOUND; }
        if (type == IntArrayTag.class) { return Tag.TAG_INT_ARRAY; }
        if (type == LongArrayTag.class) { return Tag.TAG_LONG_ARRAY; }
        throw new IllegalArgumentException("Non-native or illegal End value");
    }

    private static final class Writer {
        private final FixedOutput bytes;
        private final ClassicNbtLimits limits;
        private final DataOutputStream output;
        private int nodes;

        private Writer(FixedOutput bytes, ClassicNbtLimits limits) {
            this.bytes = bytes;
            this.limits = limits;
            this.output = new DataOutputStream(bytes);
        }

        private void payload(Tag tag, int depth) throws IOException {
            require(++nodes <= limits.nodes() && depth <= limits.depth(), "Node or depth overflow");
            switch (type(tag)) {
                case Tag.TAG_BYTE -> output.writeByte(((ByteTag) tag).getAsByte());
                case Tag.TAG_SHORT -> output.writeShort(((ShortTag) tag).getAsShort());
                case Tag.TAG_INT -> output.writeInt(((IntTag) tag).getAsInt());
                case Tag.TAG_LONG -> output.writeLong(((LongTag) tag).getAsLong());
                case Tag.TAG_FLOAT -> {
                    float value = ((FloatTag) tag).getAsFloat();
                    require(Float.floatToRawIntBits(value) == Float.floatToIntBits(value), "Lossy float");
                    output.writeFloat(value);
                }
                case Tag.TAG_DOUBLE -> {
                    double value = ((DoubleTag) tag).getAsDouble();
                    require(Double.doubleToRawLongBits(value) == Double.doubleToLongBits(value), "Lossy double");
                    output.writeDouble(value);
                }
                case Tag.TAG_BYTE_ARRAY -> {
                    byte[] value = ((ByteArrayTag) tag).getAsByteArray();
                    bytes.reserve(4L + value.length);
                    output.writeInt(value.length);
                    output.write(value);
                }
                case Tag.TAG_STRING -> utf(tag.getAsString());
                case Tag.TAG_LIST -> list((ListTag) tag, depth);
                case Tag.TAG_COMPOUND -> compound((CompoundTag) tag, depth);
                case Tag.TAG_INT_ARRAY -> {
                    int[] value = ((IntArrayTag) tag).getAsIntArray();
                    bytes.reserve(4L + 4L * value.length);
                    output.writeInt(value.length);
                    for (int element : value) { output.writeInt(element); }
                }
                case Tag.TAG_LONG_ARRAY -> {
                    long[] value = ((LongArrayTag) tag).getAsLongArray();
                    bytes.reserve(4L + 8L * value.length);
                    output.writeInt(value.length);
                    for (long element : value) { output.writeLong(element); }
                }
                default -> throw new IllegalArgumentException("Unsupported type");
            }
        }

        private void compound(CompoundTag tag, int depth) throws IOException {
            int remainingNodes = limits.nodes() - nodes;
            require(tag.size() <= remainingNodes, "Compound child overflow");
            var keys = new ArrayList<String>(tag.size());
            for (String key : tag.getAllKeys()) {
                require(keys.size() < remainingNodes, "Compound grew beyond the node budget");
                utfLength(key);
                keys.add(key);
            }
            keys.sort(ClassicNbtKeyOrder::compare);
            for (String key : keys) {
                Tag child = tag.get(key);
                output.writeByte(type(child));
                utf(key);
                payload(child, depth + 1);
            }
            output.writeByte(Tag.TAG_END);
        }

        private void list(ListTag tag, int depth) throws IOException {
            int size = tag.size();
            byte elementType = tag.getElementType();
            require(size <= limits.nodes() - nodes, "List child overflow");
            require(size != 0 || elementType == Tag.TAG_END, "Lossy empty-list subtype");
            require(size == 0 || elementType >= Tag.TAG_BYTE && elementType <= Tag.TAG_LONG_ARRAY,
                    "Illegal list subtype");
            output.writeByte(elementType);
            output.writeInt(size);
            for (int index = 0; index < size; index++) {
                Tag child = tag.get(index);
                require(type(child) == elementType, "List child type mismatch");
                payload(child, depth + 1);
            }
            require(tag.size() == size && tag.getElementType() == elementType, "List changed during framing");
        }

        private int utfLength(String value) {
            int available = Math.min(65_535, bytes.remaining() - 2);
            require(value != null && value.length() <= available, "UTF field exceeds remaining bytes");
            int length = 0;
            for (int index = 0; index < value.length(); index++) {
                int unit = value.charAt(index);
                length += unit >= 1 && unit <= 127 ? 1 : unit <= 2047 ? 2 : 3;
                require(length <= available, "Modified UTF overflow");
            }
            return length;
        }

        private void utf(String value) throws IOException {
            bytes.reserve(2L + utfLength(value));
            output.writeUTF(value);
        }
    }

    /** One fixed bounded scratch buffer; only a fresh trimmed copy is returned on success. */
    private static final class FixedOutput extends OutputStream {
        private final byte[] value;
        private int size;

        private FixedOutput(int capacity) { value = new byte[capacity]; }
        private int remaining() { return value.length - size; }
        private void reserve(long count) {
            require(count >= 0 && count <= remaining(), "Canonical byte overflow");
        }
        @Override public void write(int element) {
            reserve(1);
            value[size++] = (byte) element;
        }
        @Override public void write(byte[] source, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, source.length);
            reserve(length);
            System.arraycopy(source, offset, value, size, length);
            size += length;
        }
        private byte[] result() { return Arrays.copyOf(value, size); }
    }
}
