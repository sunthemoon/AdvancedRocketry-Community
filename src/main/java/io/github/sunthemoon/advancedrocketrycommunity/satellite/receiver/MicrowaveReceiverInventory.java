package io.github.sunthemoon.advancedrocketrycommunity.satellite.receiver;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.HashSet;
import java.util.Optional;
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

/** Four chip slots (ADR-049 section 9): only bound chips of solar satellites, one per slot. */
final class MicrowaveReceiverInventory extends ItemStackHandler {
    private final BooleanSupplier blocked;
    private final Runnable changed;

    MicrowaveReceiverInventory(BooleanSupplier blocked, Runnable changed) {
        super(MicrowaveReceiverBlockEntity.SLOT_COUNT);
        this.blocked = blocked;
        this.changed = changed;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !blocked.getAsBoolean() && validChip(stack);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return blocked.getAsBoolean() ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    protected void onContentsChanged(int slot) {
        changed.run();
    }

    static boolean validChip(ItemStack stack) {
        return stack.is(ModItems.SATELLITE_CONTROL_CHIP.get())
                && BoundedNbt.fits(stack.serializeNBT(), 4096, 16, 256)
                && identity(stack).isPresent();
    }

    /** The solar identity a slot's chip carries, if any. */
    static Optional<SatelliteIdentity> identity(ItemStack stack) {
        return stack.is(ModItems.SATELLITE_CONTROL_CHIP.get())
                ? SatelliteItemData.read(stack).identity().filter(identity -> identity.kind() == SatelliteKind.SOLAR)
                : Optional.empty();
    }

    /** Preflight before native decoding; anything unexpected quarantines the whole root. */
    void loadValidated(CompoundTag data) {
        int size = MicrowaveReceiverBlockEntity.SLOT_COUNT;
        if (!data.getAllKeys().equals(Set.of("Size", "Items"))
                || !data.contains("Size", Tag.TAG_INT) || data.getInt("Size") != size
                || !(data.get("Items") instanceof ListTag items) || items.size() > size
                || !items.isEmpty() && items.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Invalid microwave receiver inventory shape");
        }
        ItemStack[] decoded = new ItemStack[size];
        java.util.Arrays.fill(decoded, ItemStack.EMPTY);
        Set<Integer> seen = new HashSet<>();
        for (Tag raw : items) {
            CompoundTag saved = (CompoundTag) raw;
            if (!saved.contains("Slot", Tag.TAG_INT)) {
                throw new IllegalArgumentException("Microwave receiver item has no slot");
            }
            int slot = saved.getInt("Slot");
            if (slot < 0 || slot >= size || !seen.add(slot)) {
                throw new IllegalArgumentException("Invalid microwave receiver slot");
            }
            CompoundTag nativeItem = saved.copy();
            nativeItem.remove("Slot");
            if (!BoundedNbt.fits(nativeItem, 4096, 16, 256)
                    || !nativeItem.contains("id", Tag.TAG_STRING) || nativeItem.getString("id").length() > 128
                    || !nativeItem.contains("Count", Tag.TAG_BYTE) || nativeItem.getByte("Count") != 1) {
                throw new IllegalArgumentException("Invalid microwave receiver item");
            }
            ResourceLocation id = ResourceLocation.tryParse(nativeItem.getString("id"));
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id) || ForgeRegistries.ITEMS.getValue(id) == Items.AIR) {
                throw new IllegalArgumentException("Unknown microwave receiver item");
            }
            ItemStack stack = ItemStack.of(nativeItem);
            if (stack.isEmpty() || !stack.is(ModItems.SATELLITE_CONTROL_CHIP.get())
                    || !nativeItem.equals(stack.serializeNBT())) {
                throw new IllegalArgumentException("Microwave receiver item would be normalized");
            }
            decoded[slot] = stack;
        }
        for (int slot = 0; slot < size; slot++) {
            setStackInSlot(slot, decoded[slot]);
        }
    }
}
