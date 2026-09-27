package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatellitePayloadRuntime;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** Six-slot input policy and preflight before native item decoding. */
final class SatelliteTerminalInventory extends ItemStackHandler {
    private final BooleanSupplier blocked;
    private final Runnable changed;

    SatelliteTerminalInventory(BooleanSupplier blocked, Runnable changed) {
        super(6);
        this.blocked = blocked;
        this.changed = changed;
    }

    @Override public boolean isItemValid(int slot, ItemStack stack) {
        return !blocked.getAsBoolean() && validItemForSlot(slot, stack);
    }

    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return blocked.getAsBoolean() ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
    }

    @Override protected void onContentsChanged(int slot) { changed.run(); }

    static boolean validItemForSlot(int slot, ItemStack stack) {
        if (stack.isEmpty()) { return false; }
        CompoundTag nativeItem = stack.serializeNBT();
        if (!BoundedNbt.fits(nativeItem, 4096, 16, 256)) { return false; }
        return switch (slot) {
            case 0 -> stack.is(ModItems.SATELLITE_CHASSIS.get()) && plain(nativeItem);
            case 1 -> stack.is(ModItems.SATELLITE_SOLAR_MODULE.get()) && plain(nativeItem);
            case 2 -> SatellitePayloadRuntime.definitionFor(stack) != null;
            case 3 -> stack.is(ModItems.SATELLITE_CONTROL_CHIP.get())
                    && SatelliteItemData.read(stack).status() != SatelliteItemData.DecodeStatus.INVALID;
            case 4 -> stack.is(ModItems.DATA_SATELLITE_PACKAGE.get())
                    && SatelliteItemData.read(stack).status() == SatelliteItemData.DecodeStatus.VALID;
            case 5 -> stack.is(Items.REDSTONE) && plain(nativeItem);
            default -> false;
        };
    }

    private static boolean plain(CompoundTag data) { return !data.contains("tag") && !data.contains("ForgeCaps"); }

    /** Missing registrations do not erase an otherwise valid native payload item. */
    void loadValidated(CompoundTag data) {
        if (!data.getAllKeys().equals(Set.of("Size", "Items"))
                || !data.contains("Size", Tag.TAG_INT) || data.getInt("Size") != 6
                || !(data.get("Items") instanceof ListTag items) || items.size() > 6
                || !items.isEmpty() && items.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Invalid satellite inventory shape");
        }
        ItemStack[] decoded = new ItemStack[6];
        java.util.Arrays.fill(decoded, ItemStack.EMPTY);
        Set<Integer> seen = new HashSet<>();
        for (Tag entry : items) {
            CompoundTag saved = (CompoundTag) entry;
            // The handler adds one integer Slot field (11 bytes, one node) around the native item.
            if (!BoundedNbt.fits(saved, 4096 + 11, 16, 257) || !saved.contains("Slot", Tag.TAG_INT)) {
                throw new IllegalArgumentException("Invalid satellite inventory entry");
            }
            int slot = saved.getInt("Slot");
            if (slot < 0 || slot >= 6 || !seen.add(slot)) { throw new IllegalArgumentException("Invalid satellite slot"); }
            CompoundTag nativeItem = saved.copy();
            nativeItem.remove("Slot");
            if (!BoundedNbt.fits(nativeItem, 4096, 16, 256)
                    || !nativeItem.contains("id", Tag.TAG_STRING) || nativeItem.getString("id").length() > 128
                    || !nativeItem.contains("Count", Tag.TAG_BYTE) || nativeItem.getByte("Count") <= 0) {
                throw new IllegalArgumentException("Invalid satellite inventory item");
            }
            ResourceLocation id = ResourceLocation.tryParse(nativeItem.getString("id"));
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id) || ForgeRegistries.ITEMS.getValue(id) == Items.AIR) {
                throw new IllegalArgumentException("Unavailable satellite inventory item");
            }
            ItemStack stack = ItemStack.of(nativeItem);
            if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()
                    || !nativeItem.equals(stack.serializeNBT()) || slot != 2 && !validItemForSlot(slot, stack)) {
                throw new IllegalArgumentException("Satellite item would be normalized or is invalid for its slot");
            }
            decoded[slot] = stack;
        }
        for (int slot = 0; slot < 6; slot++) { setStackInSlot(slot, decoded[slot]); }
    }
}
