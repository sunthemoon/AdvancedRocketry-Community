package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKind;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Owned pure-NBT data. Guard-bound capture/detach and native registry validation belong to 03b-02. */
final class OwnedNativeTag {
    private final CompoundTag value;
    private final boolean item;
    private final long amount;
    private final String resourceId;

    private OwnedNativeTag(CompoundTag value, boolean item, long amount, String resourceId) {
        this.value = value.copy();
        this.item = item;
        this.amount = amount;
        this.resourceId = resourceId;
    }

    static OwnedNativeTag captureData(CompoundTag raw, ClassicBankKind kind) {
        ClassicValueChecks.require(ClassicNbtShape.fits(raw, ClassicNbtLimits.RESOURCES), "Unbounded native data");
        boolean item = kind.isItem();
        if (raw.isEmpty()) { return new OwnedNativeTag(raw, item, 0, ""); }
        String idKey = item ? "id" : "FluidName";
        String countKey = item ? "Count" : "Amount";
        String metadataKey = item ? "tag" : "Tag";
        Set<String> allowed = Set.of(idKey, countKey, metadataKey);
        ClassicValueChecks.require(allowed.containsAll(raw.getAllKeys())
                && raw.contains(idKey, Tag.TAG_STRING)
                && raw.contains(countKey, item ? Tag.TAG_BYTE : Tag.TAG_INT)
                && (!raw.contains(metadataKey) || raw.contains(metadataKey, Tag.TAG_COMPOUND)), "Native envelope types");
        String resourceId = ClassicValueChecks.id(raw.getString(idKey)).toString();
        long amount = item ? raw.getByte(countKey) : raw.getInt(countKey);
        ClassicValueChecks.require(amount >= 1 && amount <= (item ? 64 : 16_000), "Native envelope amount");
        return new OwnedNativeTag(raw, item, amount, resourceId);
    }

    boolean item() { return item; }
    long amount() { return amount; }
    String resourceId() { return resourceId; }
    void writeData(CompoundTag destination, String key) { destination.put(key, value.copy()); }

    boolean sameMetadata(OwnedNativeTag other) {
        if (item != other.item) { return false; }
        String key = item ? "tag" : "Tag";
        return value.contains(key) == other.value.contains(key)
                && (!value.contains(key) || value.get(key).equals(other.value.get(key)));
    }
}
