package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

/** Preflight before native recursive copy, equality or item decoding; never buffers encoded bytes. */
public final class FuelPayloadBounds {
    private FuelPayloadBounds() { }

    public static boolean item(Tag tag) { return bounded(tag, 4096, 16, 256); }
    public static boolean root(Tag tag) { return bounded(tag, 16384, 20, 1024); }

    private static boolean bounded(Tag root, int maxBytes, int maxDepth, int maxNodes) {
        if (root == null) { return false; }
        var pending = new ArrayDeque<Node>();
        pending.push(new Node(root, 1));
        int nodes = 0;
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (++nodes > maxNodes || node.depth() > maxDepth) { return false; }
            Tag tag = node.tag();
            if (tag instanceof CompoundTag compound) {
                if (compound.size() > maxNodes - nodes - pending.size()) { return false; }
                for (String key : compound.getAllKeys()) {
                    if (key.length() > maxBytes) { return false; }
                    pending.push(new Node(compound.get(key), node.depth() + 1));
                }
            } else if (tag instanceof ListTag list) {
                if (list.size() > maxNodes - nodes - pending.size()) { return false; }
                for (Tag child : list) { pending.push(new Node(child, node.depth() + 1)); }
            } else if (tag instanceof ByteArrayTag bytes && bytes.getAsByteArray().length > maxBytes
                    || tag instanceof IntArrayTag ints && ints.getAsIntArray().length > maxBytes / 4
                    || tag instanceof LongArrayTag longs && longs.getAsLongArray().length > maxBytes / 8
                    || tag.getId() == Tag.TAG_STRING && tag.getAsString().length() > maxBytes) {
                return false;
            }
        }
        try {
            root.write(new DataOutputStream(new LimitedOutput(maxBytes)));
            return true;
        } catch (IOException exception) { return false; }
    }

    private record Node(Tag tag, int depth) { }

    private static final class LimitedOutput extends OutputStream {
        private final int limit;
        // Named root type byte and empty UTF name, as used by native NBT writers.
        private int bytes = 3;

        private LimitedOutput(int limit) { this.limit = limit; }
        @Override public void write(int value) throws IOException { count(1); }
        @Override public void write(byte[] value, int offset, int length) throws IOException { count(length); }
        private void count(int length) throws IOException {
            if (length > limit - bytes) { throw new IOException("Fuel payload exceeds byte limit"); }
            bytes += length;
        }
    }
}
