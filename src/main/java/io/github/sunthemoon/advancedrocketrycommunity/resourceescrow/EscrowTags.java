package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Exact native field types and bounded preflight before copying or recursive decoding. */
final class EscrowTags {
    private EscrowTags() { }
    static void require(boolean valid, String reason) {
        if (!valid) { throw new IllegalArgumentException(reason); }
    }
    static void bound(Tag tag, int bytes, int depth, int nodes) {
        if (!BoundedNbt.fits(tag, bytes, depth, nodes)) { throw new TooLarge(); }
    }
    static final class TooLarge extends IllegalArgumentException {
        TooLarge() { super("Escrow NBT envelope exceeded"); }
    }
    static void fields(CompoundTag tag, String... fields) {
        require(Set.of(fields).containsAll(tag.getAllKeys()), "Unknown escrow field");
    }
    static void type(CompoundTag tag, String field, int type) {
        require(tag.contains(field, type), "Missing or invalid escrow field: " + field);
    }
    static String string(CompoundTag tag, String key) { type(tag, key, Tag.TAG_STRING); return tag.getString(key); }
    static int integer(CompoundTag tag, String key) { type(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    static long number(CompoundTag tag, String key) { type(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    static UUID uuid(CompoundTag tag, String key) {
        type(tag, key, Tag.TAG_INT_ARRAY); require(tag.hasUUID(key), "Invalid escrow UUID"); return tag.getUUID(key);
    }
    static CompoundTag compound(CompoundTag tag, String key) { type(tag, key, Tag.TAG_COMPOUND); return tag.getCompound(key); }
    static ListTag list(CompoundTag tag, String key, int max) {
        type(tag, key, Tag.TAG_LIST); ListTag list = (ListTag) tag.get(key);
        require(list.size() <= max && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND), "Invalid escrow list");
        return list;
    }
    static void id(String value) {
        require(value != null && value.length() <= 128 && value.chars().allMatch(c -> c > 32 && c < 127), "Invalid canonical ID");
        ResourceLocation id = ResourceLocation.tryParse(value);
        require(id != null && id.toString().equals(value), "Noncanonical escrow ID");
    }
    static void digest(String value) { require(value != null && value.matches("[0-9a-f]{64}"), "Invalid escrow digest"); }
    static <E extends Enum<E>> E enumeration(Class<E> type, CompoundTag tag, String key) {
        return Enum.valueOf(type, string(tag, key));
    }
}
