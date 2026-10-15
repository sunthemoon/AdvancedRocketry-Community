package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay;

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

/** Bounded native preflight, separate from the operational schema and terrain save admission. */
final class SatelliteBayNativeCheck {
    // Work/copy budgets, not terrain format limits: RegionFileStorage reads with NbtAccounter.UNLIMITED.
    static final int MAX_BYTES = 2_097_152;
    static final int MAX_NODES = 2_097_152;

    enum Result { LOSSLESS, WORK_LIMIT, INCOMPATIBLE }

    private SatelliteBayNativeCheck() { }

    /** Only an observed native incompatibility denies a save; unexamined data stays raw. */
    static Result inspect(Tag root) {
        if (root == null) { return Result.INCOMPATIBLE; }
        var pending = new ArrayDeque<Node>();
        pending.push(new Node(root, 1));
        long bytes = 3; // Named root type and empty modified-UTF name.
        int nodes = 0;
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (node.depth() > SatelliteBayRoot.NATIVE_MAX_DEPTH) { return Result.INCOMPATIBLE; }
            if (++nodes > MAX_NODES) { return Result.WORK_LIMIT; }
            Tag tag = node.tag();
            Class<?> type = tag.getClass();
            if (type == CompoundTag.class) {
                CompoundTag compound = (CompoundTag) tag;
                bytes++; // TAG_End terminator.
                if (compound.size() > MAX_NODES - nodes - pending.size()) { return Result.WORK_LIMIT; }
                for (String key : compound.getAllKeys()) {
                    int length = utfBytes(key);
                    if (length < 0) { return Result.INCOMPATIBLE; }
                    bytes += 3L + length;
                    if (bytes > MAX_BYTES) { return Result.WORK_LIMIT; }
                    Tag child = compound.get(key);
                    if (child == null) { return Result.INCOMPATIBLE; }
                    pending.push(new Node(child, node.depth() + 1));
                }
            } else if (type == ListTag.class) {
                ListTag list = (ListTag) tag;
                if (list.isEmpty() && list.getElementType() != Tag.TAG_END) { return Result.INCOMPATIBLE; }
                bytes += 5;
                if (list.size() > MAX_NODES - nodes - pending.size()) { return Result.WORK_LIMIT; }
                for (Tag child : list) {
                    if (child == null || child.getId() != list.getElementType()) { return Result.INCOMPATIBLE; }
                    pending.push(new Node(child, node.depth() + 1));
                }
            } else if (type == StringTag.class) {
                int length = utfBytes(tag.getAsString());
                if (length < 0) { return Result.INCOMPATIBLE; }
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
                if (Float.floatToRawIntBits(value) != Float.floatToIntBits(value)) { return Result.INCOMPATIBLE; }
                bytes += 4;
            } else if (type == DoubleTag.class) {
                double value = ((DoubleTag) tag).getAsDouble();
                if (Double.doubleToRawLongBits(value) != Double.doubleToLongBits(value)) { return Result.INCOMPATIBLE; }
                bytes += 8;
            } else {
                // TAG_End cannot be a named root/child or a list value; foreign writers are unproven.
                return Result.INCOMPATIBLE;
            }
            if (bytes > MAX_BYTES) { return Result.WORK_LIMIT; }
        }
        return Result.LOSSLESS;
    }

    private static int utfBytes(String value) {
        if (value.length() > 65_535) { return -1; }
        int bytes = 0;
        for (int index = 0; index < value.length(); index++) {
            int unit = value.charAt(index);
            bytes += unit >= 1 && unit <= 127 ? 1 : unit <= 2047 ? 2 : 3;
            if (bytes > 65_535) { return -1; }
        }
        return bytes;
    }

    private record Node(Tag tag, int depth) { }
}
