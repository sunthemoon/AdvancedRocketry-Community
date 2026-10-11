package io.github.sunthemoon.arceadaptertest;

import java.util.ArrayDeque;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Test-local preflight; the independent API consumer cannot import host persistence classes. */
final class NativeFixtureNbt {
    private static final int BYTES = 1_048_576;
    private static final int NODES = 65_536;
    private static final int DEPTH = 32;

    private NativeFixtureNbt() { }

    static void requireBounded(CompoundTag root) {
        if (!fits(root)) { throw new IllegalStateException("Native fixture NBT exceeds its frozen envelope"); }
    }

    static boolean fits(Tag root) {
        if (root == null) { return false; }
        var pending = new ArrayDeque<Node>();
        pending.push(new Node(root, 1));
        long bytes = 3;
        int nodes = 0;
        while (!pending.isEmpty()) {
            Node row = pending.pop();
            if (++nodes > NODES || row.depth() > DEPTH) { return false; }
            Tag tag = row.tag();
            switch (tag.getId()) {
                case Tag.TAG_COMPOUND -> {
                    if (tag.getClass() != CompoundTag.class) { return false; }
                    CompoundTag compound = (CompoundTag) tag;
                    bytes++;
                    if (compound.size() > NODES - nodes - pending.size()) { return false; }
                    for (String key : compound.getAllKeys()) {
                        int length = utfBytes(key, BYTES - bytes - 3);
                        if (length < 0) { return false; }
                        bytes += 3L + length;
                        Tag child = compound.get(key);
                        if (child == null) { return false; }
                        pending.push(new Node(child, row.depth() + 1));
                    }
                }
                case Tag.TAG_LIST -> {
                    if (tag.getClass() != ListTag.class) { return false; }
                    ListTag list = (ListTag) tag;
                    if (list.isEmpty() && list.getElementType() != Tag.TAG_END) { return false; }
                    bytes += 5;
                    if (list.size() > NODES - nodes - pending.size()) { return false; }
                    for (Tag child : list) {
                        if (child == null || child.getId() != list.getElementType()) { return false; }
                        pending.push(new Node(child, row.depth() + 1));
                    }
                }
                case Tag.TAG_STRING -> {
                    if (tag.getClass() != StringTag.class) { return false; }
                    int length = utfBytes(tag.getAsString(), BYTES - bytes - 2);
                    if (length < 0) { return false; }
                    bytes += 2L + length;
                }
                case Tag.TAG_BYTE_ARRAY -> {
                    if (tag.getClass() != ByteArrayTag.class) { return false; }
                    bytes += 4L + ((ByteArrayTag) tag).getAsByteArray().length;
                }
                case Tag.TAG_INT_ARRAY -> {
                    if (tag.getClass() != IntArrayTag.class) { return false; }
                    bytes += 4L + 4L * ((IntArrayTag) tag).getAsIntArray().length;
                }
                case Tag.TAG_LONG_ARRAY -> {
                    if (tag.getClass() != LongArrayTag.class) { return false; }
                    bytes += 4L + 8L * ((LongArrayTag) tag).getAsLongArray().length;
                }
                case Tag.TAG_BYTE -> { if (tag.getClass() != ByteTag.class) { return false; } bytes++; }
                case Tag.TAG_SHORT -> { if (tag.getClass() != ShortTag.class) { return false; } bytes += 2; }
                case Tag.TAG_INT -> { if (tag.getClass() != IntTag.class) { return false; } bytes += 4; }
                case Tag.TAG_LONG -> { if (tag.getClass() != LongTag.class) { return false; } bytes += 8; }
                case Tag.TAG_FLOAT -> {
                    if (tag.getClass() != FloatTag.class) { return false; }
                    float value = ((FloatTag) tag).getAsFloat();
                    if (Float.floatToRawIntBits(value) != Float.floatToIntBits(value)) { return false; }
                    bytes += 4;
                }
                case Tag.TAG_DOUBLE -> {
                    if (tag.getClass() != DoubleTag.class) { return false; }
                    double value = ((DoubleTag) tag).getAsDouble();
                    if (Double.doubleToRawLongBits(value) != Double.doubleToLongBits(value)) { return false; }
                    bytes += 8;
                }
                default -> { return false; }
            }
            if (bytes > BYTES) { return false; }
        }
        return true;
    }

    private static int utfBytes(String value, long remaining) {
        long limit = Math.min(65_535, remaining);
        if (value.length() > limit) { return -1; }
        int bytes = 0;
        for (int index = 0; index < value.length(); index++) {
            int unit = value.charAt(index);
            bytes += unit >= 1 && unit <= 127 ? 1 : unit <= 2047 ? 2 : 3;
            if (bytes > limit) { return -1; }
        }
        return bytes;
    }

    private record Node(Tag tag, int depth) { }
}
