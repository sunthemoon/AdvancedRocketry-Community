package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Set;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Bounded external-only envelope. Legacy suit NBT never enters this codec. */
final class SuitOxygenPayloads {
    static final String KEY = "arce_suit_provider";
    static final int MAX_BYTES = 16384;
    static final int MAX_DEPTH = 16;
    static final int MAX_NODES = 256;
    private static final Set<String> KEYS = Set.of("schema_version", "provider", "payload_version", "data");

    private SuitOxygenPayloads() { }

    /** Null saved tag denotes a new empty tank. Invalid data is never copied. */
    static CompoundTag decode(Tag saved, SuitEquipmentCatalog.Provider provider) {
        if (saved == null) {
            return new CompoundTag();
        }
        if (!(saved instanceof CompoundTag envelope) || !bounded(envelope)
                || !envelope.getAllKeys().equals(KEYS)
                || !envelope.contains("schema_version", Tag.TAG_INT) || envelope.getInt("schema_version") != 1
                || !envelope.contains("provider", Tag.TAG_STRING)
                || !envelope.getString("provider").equals(provider.id().toString())
                || !envelope.contains("payload_version", Tag.TAG_INT)
                || envelope.getInt("payload_version") != provider.version()
                || !envelope.contains("data", Tag.TAG_COMPOUND)) {
            return null;
        }
        return envelope.getCompound("data").copy();
    }

    static CompoundTag encode(SuitEquipmentCatalog.Provider provider, CompoundTag data) {
        if (data == null || !bounded(data)) {
            return null;
        }
        CompoundTag envelope = new CompoundTag();
        envelope.putInt("schema_version", 1);
        envelope.putString("provider", provider.id().toString());
        envelope.putInt("payload_version", provider.version());
        envelope.put("data", data);
        return bounded(envelope) ? envelope.copy() : null;
    }

    /** Preflight before recursive copy/equality/write; counting output never allocates a payload buffer. */
    static boolean bounded(CompoundTag root) {
        var pending = new ArrayDeque<Node>();
        pending.push(new Node(root, 1));
        int nodes = 0;
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (++nodes > MAX_NODES || node.depth() > MAX_DEPTH) {
                return false;
            }
            Tag tag = node.tag();
            if (tag instanceof CompoundTag compound) {
                if (compound.size() + nodes + pending.size() > MAX_NODES) {
                    return false;
                }
                for (String key : compound.getAllKeys()) {
                    if (key.length() > MAX_BYTES) {
                        return false;
                    }
                    pending.push(new Node(compound.get(key), node.depth() + 1));
                }
            } else if (tag instanceof ListTag list) {
                if (list.size() + nodes + pending.size() > MAX_NODES) {
                    return false;
                }
                for (Tag child : list) {
                    pending.push(new Node(child, node.depth() + 1));
                }
            } else if (tag instanceof ByteArrayTag bytesTag && bytesTag.getAsByteArray().length > MAX_BYTES
                    || tag instanceof IntArrayTag intsTag && intsTag.getAsIntArray().length > MAX_BYTES / 4
                    || tag instanceof LongArrayTag longsTag && longsTag.getAsLongArray().length > MAX_BYTES / 8
                    || tag.getId() == Tag.TAG_STRING && tag.getAsString().length() > MAX_BYTES) {
                return false;
            }
        }
        try {
            NbtIo.write(root, new DataOutputStream(new LimitedOutput()));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private record Node(Tag tag, int depth) { }

    private static final class LimitedOutput extends OutputStream {
        private int bytes;

        @Override
        public void write(int ignored) throws IOException {
            count(1);
        }

        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            count(length);
        }

        private void count(int length) throws IOException {
            if (length > MAX_BYTES - bytes) {
                throw new IOException("Suit oxygen payload exceeds byte limit");
            }
            bytes += length;
        }
    }
}
