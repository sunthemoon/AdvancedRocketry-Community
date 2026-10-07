package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKind;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

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

    static OwnedNativeTag captureBounded(CompoundTag raw, ClassicBankKind kind, GuardTicket ticket) {
        ticket.requireValid();
        OwnedNativeTag result = captureData(raw, kind);
        if (result.amount == 0) { ticket.requireValid(); return result; }
        var id = ClassicValueChecks.id(result.resourceId);
        if (result.item) {
            boolean known = ForgeRegistries.ITEMS.containsKey(id); ticket.requireValid();
            ClassicValueChecks.require(known, "Unknown native Item");
            ItemStack stack = ItemStack.of(result.value.copy()); ticket.requireValid();
            boolean empty = stack.isEmpty(); ticket.requireValid();
            int maximum = stack.getMaxStackSize(); ticket.requireValid();
            int count = stack.getCount(); ticket.requireValid();
            CompoundTag encoded = stack.serializeNBT(); ticket.requireValid();
            ClassicValueChecks.require(!empty && maximum > 0 && count == result.amount
                    && count <= Math.min(64, maximum) && ClassicNbtShape.fits(encoded, ClassicNbtLimits.RESOURCES)
                    && result.value.equals(encoded), "Lossy native Item");
        } else {
            boolean known = ForgeRegistries.FLUIDS.containsKey(id); ticket.requireValid();
            var fluid = ForgeRegistries.FLUIDS.getValue(id); ticket.requireValid();
            ClassicValueChecks.require(known && fluid != null && fluid != Fluids.EMPTY, "Unknown native Fluid");
            FluidStack stack = FluidStack.loadFluidStackFromNBT(result.value.copy()); ticket.requireValid();
            boolean empty = stack.isEmpty(); ticket.requireValid();
            CompoundTag encoded = stack.writeToNBT(new CompoundTag()); ticket.requireValid();
            ClassicValueChecks.require(!empty && ClassicNbtShape.fits(encoded, ClassicNbtLimits.RESOURCES)
                    && result.value.equals(encoded), "Lossy native Fluid");
        }
        ticket.requireValid(); return result;
    }

    CompoundTag detached(GuardTicket ticket) {
        ticket.requireValid(); CompoundTag result = value.copy(); ticket.requireValid(); return result;
    }

    int capacity(GuardTicket ticket) {
        ticket.requireValid();
        if (!item) { return 16_000; }
        if (amount == 0) { throw new IllegalStateException("Empty Item cannot select native capacity"); }
        ItemStack stack = ItemStack.of(value.copy()); ticket.requireValid();
        int result = stack.getMaxStackSize(); ticket.requireValid();
        ClassicValueChecks.require(result >= 1, "Invalid native Item capacity");
        return Math.min(64, result);
    }
}
