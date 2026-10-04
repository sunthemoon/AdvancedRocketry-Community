package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Immutable native payload owner. Every public native getter returns a detached value. */
public final class ClassicResourceBank {
    public static final int ITEM_SLOTS = 4;
    public static final int FLUID_CAPACITY = 16_000;
    private final ClassicBankKey key;
    private final List<CompoundTag> items;
    private final CompoundTag fluid;

    private ClassicResourceBank(ClassicBankKey key, List<CompoundTag> items, CompoundTag fluid) {
        this.key = Objects.requireNonNull(key, "key");
        this.items = List.copyOf(items);
        this.fluid = fluid;
    }

    public static ClassicResourceBank empty(ClassicBankKey key) {
        return key.kind().isItem() ? items(key, List.of(ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY)) : fluid(key, FluidStack.EMPTY);
    }

    public static ClassicResourceBank items(ClassicBankKey key, List<ItemStack> items) {
        Objects.requireNonNull(key, "key");
        if (!key.kind().isItem() || items.size() != ITEM_SLOTS) {
            throw new IllegalArgumentException("Item bank requires its exact kind and four slots");
        }
        List<CompoundTag> owned = new ArrayList<>(ITEM_SLOTS);
        for (ItemStack stack : items) { owned.add(ClassicNativePayload.item(stack)); }
        return new ClassicResourceBank(key, owned, null);
    }

    public static ClassicResourceBank fluid(ClassicBankKey key, FluidStack fluid) {
        Objects.requireNonNull(key, "key");
        if (key.kind().isItem()) { throw new IllegalArgumentException("Fluid bank requires a Fluid kind"); }
        return new ClassicResourceBank(key, List.of(), ClassicNativePayload.fluid(fluid));
    }

    public ClassicBankKey key() { return key; }

    public ItemStack item(int slot) {
        requireItemSlot(slot);
        return ClassicNativePayload.decodeItem(items.get(slot));
    }

    public List<ItemStack> items() {
        if (!key.kind().isItem()) { throw new IllegalStateException("Not an Item bank"); }
        List<ItemStack> result = new ArrayList<>(ITEM_SLOTS);
        for (CompoundTag encoded : items) { result.add(ClassicNativePayload.decodeItem(encoded)); }
        return List.copyOf(result);
    }

    public FluidStack fluid() {
        if (key.kind().isItem()) { throw new IllegalStateException("Not a Fluid bank"); }
        return ClassicNativePayload.decodeFluid(fluid);
    }

    public ClassicResourceBank withItem(int slot, ItemStack stack) {
        requireItemSlot(slot);
        List<CompoundTag> replacement = new ArrayList<>(items);
        replacement.set(slot, ClassicNativePayload.item(stack));
        return new ClassicResourceBank(key, replacement, null);
    }

    public ClassicResourceBank withFluid(FluidStack stack) {
        if (key.kind().isItem()) { throw new IllegalStateException("Not a Fluid bank"); }
        return fluid(key, stack);
    }

    /** Package-only framing; callers must bound the aggregate before recursive copy/equality. */
    CompoundTag frame() {
        CompoundTag bank = new CompoundTag();
        bank.putString("channel", key.channel());
        bank.putString("kind", key.kind().id());
        bank.putInt("x", key.x()); bank.putInt("y", key.y()); bank.putInt("z", key.z());
        if (key.kind().isItem()) {
            ListTag list = new ListTag();
            items.forEach(list::add);
            bank.put("items", list);
        } else { bank.put("fluid", fluid); }
        return bank;
    }

    boolean samePayload(ClassicResourceBank other) {
        return key.equals(other.key) && items.equals(other.items) && Objects.equals(fluid, other.fluid);
    }

    private void requireItemSlot(int slot) {
        if (!key.kind().isItem() || slot < 0 || slot >= ITEM_SLOTS) {
            throw new IllegalArgumentException("Invalid Item bank slot");
        }
    }
}
