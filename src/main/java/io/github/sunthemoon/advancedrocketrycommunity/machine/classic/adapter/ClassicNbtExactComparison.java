package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.ArrayDeque;
import java.util.Arrays;
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

/** Private bounded native data equality, never resource validation or saving permission. */
final class ClassicNbtExactComparison {
    static boolean matches(Tag expected, Tag actual, ClassicNbtLimits limits) {
        if (!preflight(expected, limits) || !preflight(actual, limits)) { return false; }
        var pairs = new ArrayDeque<Pair>();
        pairs.push(new Pair(expected, actual, 1));
        int visited = 0;
        while (!pairs.isEmpty()) {
            Pair pair = pairs.pop();
            if (++visited > limits.nodes() || pair.depth() > limits.depth()) { return false; }
            Tag left = pair.expected();
            Tag right = pair.actual();
            // Preflight already rejected subclasses; repeat exact class checks before every getter.
            if (left == null || right == null || left.getClass() != right.getClass()) { return false; }
            Class<?> type = left.getClass();
            if (type == CompoundTag.class) {
                CompoundTag a = (CompoundTag) left;
                CompoundTag b = (CompoundTag) right;
                if (a.size() != b.size() || a.size() > limits.nodes() - visited - pairs.size()) { return false; }
                for (String key : a.getAllKeys()) {
                    if (key == null || !b.contains(key)) { return false; }
                    pairs.push(new Pair(a.get(key), b.get(key), pair.depth() + 1));
                }
            } else if (type == ListTag.class) {
                ListTag a = (ListTag) left;
                ListTag b = (ListTag) right;
                if (a.getElementType() != b.getElementType() || a.size() != b.size()
                        || a.size() > limits.nodes() - visited - pairs.size()) { return false; }
                for (int index = a.size() - 1; index >= 0; index--) {
                    pairs.push(new Pair(a.get(index), b.get(index), pair.depth() + 1));
                }
            } else if (type == ByteArrayTag.class) {
                if (!Arrays.equals(((ByteArrayTag) left).getAsByteArray(), ((ByteArrayTag) right).getAsByteArray())) { return false; }
            } else if (type == IntArrayTag.class) {
                if (!Arrays.equals(((IntArrayTag) left).getAsIntArray(), ((IntArrayTag) right).getAsIntArray())) { return false; }
            } else if (type == LongArrayTag.class) {
                if (!Arrays.equals(((LongArrayTag) left).getAsLongArray(), ((LongArrayTag) right).getAsLongArray())) { return false; }
            } else if (type == ByteTag.class) {
                if (((ByteTag) left).getAsByte() != ((ByteTag) right).getAsByte()) { return false; }
            } else if (type == ShortTag.class) {
                if (((ShortTag) left).getAsShort() != ((ShortTag) right).getAsShort()) { return false; }
            } else if (type == IntTag.class) {
                if (((IntTag) left).getAsInt() != ((IntTag) right).getAsInt()) { return false; }
            } else if (type == LongTag.class) {
                if (((LongTag) left).getAsLong() != ((LongTag) right).getAsLong()) { return false; }
            } else if (type == FloatTag.class) {
                if (Float.floatToRawIntBits(((FloatTag) left).getAsFloat())
                        != Float.floatToRawIntBits(((FloatTag) right).getAsFloat())) { return false; }
            } else if (type == DoubleTag.class) {
                if (Double.doubleToRawLongBits(((DoubleTag) left).getAsDouble())
                        != Double.doubleToRawLongBits(((DoubleTag) right).getAsDouble())) { return false; }
            } else if (type == StringTag.class) {
                // String.equals compares UTF-16 units, including lone surrogates, without normalization.
                if (!((StringTag) left).getAsString().equals(((StringTag) right).getAsString())) { return false; }
            } else {
                return false;
            }
        }
        return true;
    }

    private static boolean preflight(Tag root, ClassicNbtLimits limits) {
        return limits != null && ClassicNbtShape.fits(root, limits)
                && BoundedNbt.fits(root, limits.bytes(), limits.depth(), limits.nodes());
    }

    private record Pair(Tag expected, Tag actual, int depth) { }
    private ClassicNbtExactComparison() { }
}
