package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Explicit chest/barrel adapter; no arbitrary Forge capability or metadata NBT is copied. */
public final class VanillaContainerRocketAdapter implements RocketBlockEntityAdapter {
    public static final ResourceLocation ID = ModIdentity.id("vanilla_container_v1");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean supports(BlockEntity blockEntity) {
        return blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity;
    }

    @Override
    public RocketBlockEntityPayload capture(BlockEntity blockEntity) {
        if (!supports(blockEntity) || !(blockEntity instanceof Container container)) {
            throw new IllegalArgumentException("Adapter supports only vanilla chest/barrel BlockEntities");
        }
        CompoundTag source = blockEntity.saveWithoutMetadata();
        CompoundTag approved = whitelist(source, container.getContainerSize());
        return new RocketBlockEntityPayload(ID, approved);
    }

    @Override
    public boolean restore(BlockEntity blockEntity, RocketBlockEntityPayload payload) {
        if (!supports(blockEntity)
                || !(blockEntity instanceof Container container)
                || !ID.equals(payload.adapterId())) {
            return false;
        }
        try {
            CompoundTag approved = whitelist(payload.data(), container.getContainerSize());
            blockEntity.load(approved);
            blockEntity.setChanged();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static CompoundTag whitelist(CompoundTag source, int slots) {
        if (slots <= 0 || slots > 54) {
            throw new IllegalArgumentException("Container slot count is outside the approved range");
        }
        CompoundTag approved = new CompoundTag();
        boolean hasItems = source.contains("Items", Tag.TAG_LIST);
        boolean hasLootTable = source.contains("LootTable", Tag.TAG_STRING);
        if (hasItems && hasLootTable) {
            throw new IllegalArgumentException("Container cannot contain both Items and LootTable");
        }
        if (hasItems) {
            ListTag items = (ListTag) source.get("Items");
            if (!items.isEmpty() && items.getElementType() != Tag.TAG_COMPOUND) {
                throw new IllegalArgumentException("Container Items list has the wrong element type");
            }
            if (items.size() > slots) {
                throw new IllegalArgumentException("Container has more entries than slots");
            }
            Set<Integer> occupiedSlots = new HashSet<>();
            java.util.ArrayList<CompoundTag> sorted = new java.util.ArrayList<>();
            for (Tag raw : items) {
                if (!(raw instanceof CompoundTag itemTag) || !itemTag.contains("Slot", Tag.TAG_BYTE)) {
                    throw new IllegalArgumentException("Container item entry is malformed");
                }
                int slot = Byte.toUnsignedInt(itemTag.getByte("Slot"));
                if (slot >= slots || !occupiedSlots.add(slot)) {
                    throw new IllegalArgumentException("Container item slot is invalid or duplicated");
                }
                ItemStack stack = ItemStack.of(itemTag);
                if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()) {
                    throw new IllegalArgumentException("Container item stack is invalid or oversized");
                }
                sorted.add(itemTag.copy());
            }
            sorted.sort(Comparator.comparingInt(tag -> Byte.toUnsignedInt(tag.getByte("Slot"))));
            ListTag canonicalItems = new ListTag();
            canonicalItems.addAll(sorted);
            approved.put("Items", canonicalItems);
        }
        if (hasLootTable) {
            String lootTable = source.getString("LootTable");
            if (lootTable.length() > RocketLimits.MAX_IDENTIFIER_LENGTH
                    || ResourceLocation.tryParse(lootTable) == null) {
                throw new IllegalArgumentException("Container loot table identifier is invalid");
            }
            approved.putString("LootTable", lootTable);
            if (source.contains("LootTableSeed", Tag.TAG_LONG)) {
                approved.putLong("LootTableSeed", source.getLong("LootTableSeed"));
            }
        }
        copyBoundedString(source, approved, "CustomName", 512);
        copyBoundedString(source, approved, "Lock", 256);
        return approved;
    }

    private static void copyBoundedString(
            CompoundTag source,
            CompoundTag target,
            String key,
            int maxLength
    ) {
        if (!source.contains(key)) {
            return;
        }
        if (!source.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Container field " + key + " has the wrong type");
        }
        String value = source.getString(key);
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("Container field " + key + " exceeds the length limit");
        }
        target.putString(key, value);
    }
}
