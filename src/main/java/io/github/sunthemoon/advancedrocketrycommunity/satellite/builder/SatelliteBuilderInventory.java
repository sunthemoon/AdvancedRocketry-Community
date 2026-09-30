package io.github.sunthemoon.advancedrocketrycommunity.satellite.builder;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentRole;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
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

/** Eleven-slot builder inventory (ADR-049 section 5, revision 4) with preflight before native decoding. */
final class SatelliteBuilderInventory extends ItemStackHandler {
    private final BooleanSupplier blocked;
    private final Runnable changed;

    SatelliteBuilderInventory(BooleanSupplier blocked, Runnable changed) {
        super(SatelliteBuilderBlockEntity.SLOT_COUNT);
        this.blocked = blocked;
        this.changed = changed;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !blocked.getAsBoolean() && validItemForSlot(slot, stack);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return blocked.getAsBoolean() ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot == SatelliteBuilderBlockEntity.SLOT_CHARGE ? 64 : 1;
    }

    @Override
    protected void onContentsChanged(int slot) {
        changed.run();
    }

    /** Server-side slot rules; component roles come from the active catalog. */
    static boolean validItemForSlot(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CompoundTag nativeItem = stack.serializeNBT();
        if (!BoundedNbt.fits(nativeItem, 4096, 16, 256)) {
            return false;
        }
        if (slot == SatelliteBuilderBlockEntity.SLOT_CHIP) {
            return stack.is(ModItems.SATELLITE_CONTROL_CHIP.get())
                    && SatelliteItemData.read(stack).status() == SatelliteItemData.DecodeStatus.EMPTY;
        }
        if (slot == SatelliteBuilderBlockEntity.SLOT_OUTPUT) {
            return false;
        }
        if (!plain(nativeItem)) {
            return false;
        }
        if (slot == SatelliteBuilderBlockEntity.SLOT_CHARGE) {
            return stack.is(Items.REDSTONE);
        }
        Optional<SatelliteComponentRole> role = role(stack);
        if (role.isEmpty()) {
            return false;
        }
        return switch (slot) {
            case SatelliteBuilderBlockEntity.SLOT_CHASSIS -> role.get() == SatelliteComponentRole.CHASSIS;
            case SatelliteBuilderBlockEntity.SLOT_PRIMARY -> role.get() == SatelliteComponentRole.PRIMARY;
            default -> slot >= SatelliteBuilderBlockEntity.SLOT_MODULE_FIRST
                    && slot <= SatelliteBuilderBlockEntity.SLOT_MODULE_LAST && role.get().module();
        };
    }

    static Optional<SatelliteComponentRole> role(ItemStack stack) {
        ResourceLocation item = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return item == null ? Optional.empty() : components().get(item).map(SatelliteComponentDefinition::role);
    }

    static SatelliteComponentCatalog components() {
        return SatelliteRuntime.catalog().map(catalog -> catalog.components()).orElse(SatelliteComponentCatalog.EMPTY);
    }

    private static boolean plain(CompoundTag data) {
        return !data.contains("tag") && !data.contains("ForgeCaps");
    }

    /** Missing registrations do not erase an otherwise valid native item: the root is quarantined instead. */
    void loadValidated(CompoundTag data) {
        int size = SatelliteBuilderBlockEntity.SLOT_COUNT;
        if (!data.getAllKeys().equals(Set.of("Size", "Items"))
                || !data.contains("Size", Tag.TAG_INT) || data.getInt("Size") != size
                || !(data.get("Items") instanceof ListTag items) || items.size() > size
                || !items.isEmpty() && items.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Invalid satellite builder inventory shape");
        }
        ItemStack[] decoded = new ItemStack[size];
        java.util.Arrays.fill(decoded, ItemStack.EMPTY);
        Set<Integer> seen = new HashSet<>();
        for (Tag raw : items) {
            CompoundTag saved = (CompoundTag) raw;
            if (!saved.contains("Slot", Tag.TAG_INT)) {
                throw new IllegalArgumentException("Satellite builder item has no slot");
            }
            int slot = saved.getInt("Slot");
            if (slot < 0 || slot >= size || !seen.add(slot)) {
                throw new IllegalArgumentException("Invalid satellite builder slot");
            }
            CompoundTag nativeItem = saved.copy();
            nativeItem.remove("Slot");
            if (!BoundedNbt.fits(nativeItem, 4096, 16, 256)
                    || !nativeItem.contains("id", Tag.TAG_STRING) || nativeItem.getString("id").length() > 128
                    || !nativeItem.contains("Count", Tag.TAG_BYTE) || nativeItem.getByte("Count") <= 0) {
                throw new IllegalArgumentException("Invalid satellite builder item");
            }
            ResourceLocation id = ResourceLocation.tryParse(nativeItem.getString("id"));
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id) || ForgeRegistries.ITEMS.getValue(id) == Items.AIR) {
                throw new IllegalArgumentException("Unknown satellite builder item");
            }
            ItemStack stack = ItemStack.of(nativeItem);
            if (stack.isEmpty() || stack.getCount() > getSlotLimit(slot) || !nativeItem.equals(stack.serializeNBT())) {
                throw new IllegalArgumentException("Satellite builder item would be normalized");
            }
            decoded[slot] = stack;
        }
        for (int slot = 0; slot < size; slot++) {
            setStackInSlot(slot, decoded[slot]);
        }
    }
}
