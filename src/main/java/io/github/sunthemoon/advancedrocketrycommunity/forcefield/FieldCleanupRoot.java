package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Reserved records make removal admission independent of unloaded field chunks. */
public final class FieldCleanupRoot {
    public static final int MAX_ENTRIES = 4096;
    public static final int MAX_BYTES = 1_048_576;
    private static final Set<String> ENTRY_KEYS = Set.of("schema", "id", "owner", "generation", "level", "pos",
            "direction", "claims", "orphan");
    private final Map<FieldIdentity.Key, Entry> entries = new LinkedHashMap<>();
    private int bytes = emptyBytes();
    public FieldCleanupRoot() { }
    private static int emptyBytes() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.put("entries", new ListTag());
        return encodedSize(tag);
    }
    public record Entry(FieldIdentity identity, int claims, boolean orphan) {
        public Entry { java.util.Objects.requireNonNull(identity); }
        public Entry withClaims(int mask) { return new Entry(identity, mask, orphan); }
        CompoundTag encode() {
            CompoundTag tag = ForceFieldCodecs.field(identity);
            tag.putInt("claims", claims);
            tag.putBoolean("orphan", orphan);
            return tag;
        }
    }

    public int size() { return entries.size(); }
    public int bytes() { return bytes; }
    public boolean full() { return size() >= MAX_ENTRIES; }
    public Entry get(FieldIdentity.Key key) { return entries.get(key); }
    public Collection<Entry> entries() { return java.util.List.copyOf(entries.values()); }

    public boolean canAdmit(Entry entry) {
        Entry old = entries.get(entry.identity().key());
        if (old != null && !old.identity().equals(entry.identity())) { return false; }
        int delta = payloadBytes(entry) - (old == null ? 0 : payloadBytes(old));
        return (old != null || !full()) && bytes + delta <= MAX_BYTES;
    }
    public boolean put(Entry entry) {
        if (!canAdmit(entry)) { return false; }
        Entry old = entries.put(entry.identity().key(), entry);
        bytes += payloadBytes(entry) - (old == null ? 0 : payloadBytes(old));
        return true;
    }
    public void remove(FieldIdentity.Key key) {
        Entry old = entries.remove(key);
        if (old != null) { bytes -= payloadBytes(old); }
    }
    public boolean canOrphan(Entry entry) {
        return !full() && canAdmit(new Entry(entry.identity(), entry.claims(), true));
    }
    public boolean orphan(Entry entry) {
        return canOrphan(entry) && put(new Entry(entry.identity(), entry.claims(), true));
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        ListTag list = new ListTag();
        entries.values().forEach(entry -> list.add(entry.encode()));
        tag.put("entries", list);
        return tag;
    }
    public static boolean bounded(Tag raw) { return BoundedNbt.fits(raw, MAX_BYTES, 8, 65_536); }
    public static FieldCleanupRoot decode(Tag raw) {
        if (!bounded(raw) || !(raw instanceof CompoundTag tag) || !tag.getAllKeys().equals(Set.of("schema", "entries"))
                || ForceFieldCodecs.integer(tag, "schema") != 1 || !tag.contains("entries", Tag.TAG_LIST)) {
            throw ForceFieldCodecs.invalid();
        }
        ListTag list = (ListTag) tag.get("entries");
        if (list.size() > MAX_ENTRIES || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw ForceFieldCodecs.invalid();
        }
        FieldCleanupRoot root = new FieldCleanupRoot();
        for (Tag value : list) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.getAllKeys().equals(ENTRY_KEYS) || !entry.contains("orphan", Tag.TAG_BYTE)
                    || entry.getByte("orphan") < 0 || entry.getByte("orphan") > 1) { throw ForceFieldCodecs.invalid(); }
            Entry decoded = new Entry(ForceFieldCodecs.identity(entry), ForceFieldCodecs.integer(entry, "claims"),
                    entry.getBoolean("orphan"));
            if (root.get(decoded.identity().key()) != null || !root.put(decoded)) { throw ForceFieldCodecs.invalid(); }
        }
        return root;
    }
    private static int payloadBytes(Entry entry) { return encodedSize(entry.encode()) - 3; }
    static int encodedSize(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(bytes));
            return bytes.size();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
