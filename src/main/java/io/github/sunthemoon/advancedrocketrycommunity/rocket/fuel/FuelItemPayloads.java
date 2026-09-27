package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Native item bridge. Reject registry-default air and lossy capability/NBT normalization. */
final class FuelItemPayloads {
    private FuelItemPayloads() { }

    static CompoundTag captureOne(ItemStack stack) {
        if (stack.isEmpty() || stack.getTag() != null && !FuelPayloadBounds.item(stack.getTag())) {
            throw new IllegalArgumentException("Empty or oversized fuel input");
        }
        CompoundTag saved = stack.save(new CompoundTag());
        saved.putByte("Count", (byte) 1);
        decode(saved);
        return saved;
    }

    static ItemStack decode(CompoundTag saved) {
        FuelLoaderStorage.validateItem(saved);
        if (saved.isEmpty()) { return ItemStack.EMPTY; }
        ResourceLocation id = ResourceLocation.tryParse(saved.getString("id"));
        if (!ForgeRegistries.ITEMS.containsKey(id)) { throw new IllegalArgumentException("Unresolved fuel item"); }
        ItemStack stack = ItemStack.of(saved.copy());
        if (stack.isEmpty() || stack.getCount() != 1
                || stack.getTag() != null && !FuelPayloadBounds.item(stack.getTag())) {
            throw new IllegalArgumentException("Invalid decoded fuel item");
        }
        CompoundTag roundTrip = stack.save(new CompoundTag());
        if (!FuelPayloadBounds.item(roundTrip) || !saved.equals(roundTrip)) {
            throw new IllegalArgumentException("Fuel item cannot round-trip without normalization");
        }
        return stack;
    }
}
