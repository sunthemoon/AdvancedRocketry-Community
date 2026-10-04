package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.ArrayDeque;
import net.minecraft.nbt.*;

/** Iterative preflight of standard native tags. Does not copy, compare or decode a payload. */
final class ClassicNbtShape {
    static boolean fits(Tag root, ClassicNbtLimits limit) {
        if (root == null) { return false; }
        var pending = new ArrayDeque<Node>();
        pending.push(new Node(root, 1));
        long bytes = 3; // named root: type and empty UTF name
        int nodes = 0;
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (++nodes > limit.nodes() || node.depth() > limit.depth()) { return false; }
            Tag tag = node.tag();
            Class<?> type = tag.getClass();
            if (type == CompoundTag.class) {
                CompoundTag compound = (CompoundTag) tag;
                bytes++; // native TAG_End terminator
                if (compound.size() > limit.nodes() - nodes - pending.size()) { return false; }
                for (String key : compound.getAllKeys()) {
                    int keyBytes = utfBytes(key);
                    if (keyBytes < 0) { return false; }
                    bytes += 3L + keyBytes; // child type + writeUTF key
                    Tag child = compound.get(key);
                    if (child == null || child.getClass() == EndTag.class) { return false; }
                    pending.push(new Node(child, node.depth() + 1));
                }
            } else if (type == ListTag.class) {
                ListTag list = (ListTag) tag;
                bytes += 5;
                if (list.size() > limit.nodes() - nodes - pending.size()) { return false; }
                for (Tag child : list) { pending.push(new Node(child, node.depth() + 1)); }
            } else if (type == StringTag.class) {
                int length = utfBytes(tag.getAsString());
                if (length < 0) { return false; }
                bytes += 2L + length;
            } else if (type == ByteArrayTag.class) { bytes += 4L + ((ByteArrayTag) tag).getAsByteArray().length; }
            else if (type == IntArrayTag.class) { bytes += 4L + 4L * ((IntArrayTag) tag).getAsIntArray().length; }
            else if (type == LongArrayTag.class) { bytes += 4L + 8L * ((LongArrayTag) tag).getAsLongArray().length; }
            else if (type == ByteTag.class) { bytes++; }
            else if (type == ShortTag.class) { bytes += 2; }
            else if (type == IntTag.class || type == FloatTag.class) { bytes += 4; }
            else if (type == LongTag.class || type == DoubleTag.class) { bytes += 8; }
            else if (type != EndTag.class) { return false; }
            if (bytes > limit.bytes()) { return false; }
        }
        return bytes <= limit.bytes();
    }

    /** DataOutput.writeUTF's modified-UTF length, including lone UTF-16 surrogate units. */
    private static int utfBytes(String value) {
        if (value.length() > 65_535) { return -1; }
        int bytes = 0;
        for (int i = 0; i < value.length(); i++) {
            int unit = value.charAt(i);
            bytes += unit >= 1 && unit <= 127 ? 1 : unit <= 2047 ? 2 : 3;
            if (bytes > 65_535) { return -1; }
        }
        return bytes;
    }

    private record Node(Tag tag, int depth) { }
    private ClassicNbtShape() { }
}
