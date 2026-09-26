package io.github.sunthemoon.arceadaptertest;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Independent two-slot container, deliberately not a vanilla chest/barrel subclass. */
final class FixtureContainerBlockEntity extends BlockEntity implements Container {
    private static final int SLOT_COUNT = 2;
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    FixtureContainerBlockEntity(BlockPos position, BlockState state) {
        super(AdapterTestMod.CONTAINER_TYPE.get(), position, state);
    }

    CompoundTag captureInventory() {
        CompoundTag data = new CompoundTag();
        ContainerHelper.saveAllItems(data, inventory, true);
        return data;
    }

    boolean restoreInventory(CompoundTag data) {
        if (!data.getAllKeys().equals(Set.of("Items")) || !data.contains("Items", Tag.TAG_LIST)) {
            return false;
        }
        ListTag items = data.getList("Items", Tag.TAG_COMPOUND);
        if (items.size() != ((ListTag) data.get("Items")).size() || items.size() > SLOT_COUNT) {
            return false;
        }
        NonNullList<ItemStack> restored = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        for (int index = 0; index < items.size(); index++) {
            CompoundTag item = items.getCompound(index);
            if (!item.contains("Slot", Tag.TAG_BYTE)) {
                return false;
            }
            int slot = Byte.toUnsignedInt(item.getByte("Slot"));
            ItemStack stack = ItemStack.of(item);
            if (slot >= SLOT_COUNT || !restored.get(slot).isEmpty() || stack.isEmpty()
                    || stack.getCount() > Math.min(getMaxStackSize(), stack.getMaxStackSize())) {
                return false;
            }
            restored.set(slot, stack);
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.set(slot, restored.get(slot));
        }
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag parent) {
        super.saveAdditional(parent);
        CompoundTag data = captureInventory();
        data.putInt("schema_version", 1);
        parent.put("FixtureInventory", data);
    }

    @Override
    public void load(CompoundTag parent) {
        super.load(parent);
        CompoundTag data = parent.getCompound("FixtureInventory").copy();
        if (data.getInt("schema_version") != 1) {
            throw new IllegalArgumentException("Unsupported fixture inventory schema");
        }
        data.remove("schema_version");
        if (!restoreInventory(data)) {
            throw new IllegalArgumentException("Invalid fixture inventory");
        }
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return inventory.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return inventory.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(inventory, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(inventory, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack.getCount() > Math.min(getMaxStackSize(), stack.getMaxStackSize())) {
            throw new IllegalArgumentException("Oversized fixture inventory stack");
        }
        inventory.set(slot, stack.copy());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        inventory.clear();
        setChanged();
    }
}
