package io.github.sunthemoon.advancedrocketrycommunity.persistence;

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

/** Non-mutating native preflight before recursive copy, equality or decode; never buffers bytes. */
public final class BoundedNbt {
    private BoundedNbt() { }

    public static boolean fits(Tag root, int maxBytes, int maxDepth, int maxNodes) {
        if (root == null || maxBytes < 3 || maxDepth < 1 || maxNodes < 1) { return false; }
        var pending = new ArrayDeque<Node>();
        pending.push(new Node(root, 1));
        long bytes = 3; // Named root type and empty modified-UTF name.
        int nodes = 0;
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (++nodes > maxNodes || node.depth() > maxDepth) { return false; }
            Tag tag = node.tag();
            Class<?> type = tag.getClass();
            if (type == CompoundTag.class) {
                CompoundTag compound = (CompoundTag) tag;
                bytes++; // TAG_End terminator.
                if (compound.size() > maxNodes - nodes - pending.size()) { return false; }
                for (String key : compound.getAllKeys()) {
                    int length = utfBytes(key, maxBytes - bytes - 3);
                    if (length < 0) { return false; }
                    bytes += 3L + length; // Child type and modified-UTF name.
                    Tag child = compound.get(key);
                    if (child == null) { return false; }
                    pending.push(new Node(child, node.depth() + 1));
                }
            } else if (type == ListTag.class) {
                ListTag list = (ListTag) tag;
                // Native write changes a typed empty list to TAG_End, even when only counting bytes.
                if (list.isEmpty() && list.getElementType() != Tag.TAG_END) { return false; }
                bytes += 5; // Element type and signed length.
                if (list.size() > maxNodes - nodes - pending.size()) { return false; }
                for (Tag child : list) {
                    if (child == null || child.getId() != list.getElementType()) { return false; }
                    pending.push(new Node(child, node.depth() + 1));
                }
            } else if (type == StringTag.class) {
                // StringTag.write swallows an overlong writeUTF failure and writes an empty fallback.
                int length = utfBytes(tag.getAsString(), maxBytes - bytes - 2);
                if (length < 0) { return false; }
                bytes += 2L + length;
            } else if (type == ByteArrayTag.class) { bytes += 4L + ((ByteArrayTag) tag).getAsByteArray().length; }
            else if (type == IntArrayTag.class) { bytes += 4L + 4L * ((IntArrayTag) tag).getAsIntArray().length; }
            else if (type == LongArrayTag.class) { bytes += 4L + 8L * ((LongArrayTag) tag).getAsLongArray().length; }
            else if (type == ByteTag.class) { bytes++; }
            else if (type == ShortTag.class) { bytes += 2; }
            else if (type == IntTag.class) { bytes += 4; }
            else if (type == LongTag.class) { bytes += 8; }
            else if (type == FloatTag.class) {
                float value = ((FloatTag) tag).getAsFloat();
                if (Float.floatToRawIntBits(value) != Float.floatToIntBits(value)) { return false; }
                bytes += 4;
            } else if (type == DoubleTag.class) {
                double value = ((DoubleTag) tag).getAsDouble();
                if (Double.doubleToRawLongBits(value) != Double.doubleToLongBits(value)) { return false; }
                bytes += 8;
            } else {
                // TAG_End cannot be a named root/child or a list value; foreign writers are unproven.
                return false;
            }
            if (bytes > maxBytes) { return false; }
        }
        return bytes <= maxBytes;
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
