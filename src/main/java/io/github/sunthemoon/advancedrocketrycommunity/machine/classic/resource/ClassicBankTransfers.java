package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Bounded immutable automation arithmetic only; loaded-owner authority belongs to later adapters. */
public final class ClassicBankTransfers {
    public record ItemInsert(ClassicResources resources, ItemStack remainder, int accepted) {
        public ItemInsert { remainder = detached(remainder); }
        @Override public ItemStack remainder() { return detached(remainder); }
    }
    public record ItemExtract(ClassicResources resources, ItemStack extracted) {
        public ItemExtract { extracted = detached(extracted); }
        @Override public ItemStack extracted() { return detached(extracted); }
    }
    public record FluidFill(ClassicResources resources, int accepted) { }
    public record FluidDrain(ClassicResources resources, FluidStack extracted) {
        public FluidDrain { extracted = detached(extracted); }
        @Override public FluidStack extracted() { return detached(extracted); }
    }

    public static ItemInsert insertItem(ClassicResources resources, ClassicBankKey key, int slot,
            ItemStack offered, boolean simulate) {
        ClassicResourceBank bank = require(resources, key, ClassicBankKind.ITEM_INPUT);
        ItemStack incoming = detached(offered);
        ItemStack stored = bank.item(slot);
        if (incoming.isEmpty() || (!stored.isEmpty() && !ItemStack.isSameItemSameTags(stored, incoming))) {
            return new ItemInsert(resources, incoming, 0);
        }
        int accepted = Math.min(incoming.getCount(), Math.min(64, incoming.getMaxStackSize()) - stored.getCount());
        if (accepted <= 0) { return new ItemInsert(resources, incoming, 0); }
        ItemStack replacement = incoming.copy();
        replacement.setCount(stored.getCount() + accepted);
        ClassicResources changed = resources.replace(List.of(bank.withItem(slot, replacement)), simulate);
        incoming.shrink(accepted);
        return new ItemInsert(changed, incoming, accepted);
    }

    public static ItemExtract extractItem(ClassicResources resources, ClassicBankKey key, int slot,
            int requested, boolean simulate) {
        if (requested < 0) { throw new IllegalArgumentException("Negative Item request"); }
        ClassicResourceBank bank = require(resources, key, ClassicBankKind.ITEM_OUTPUT);
        ItemStack stored = bank.item(slot);
        int amount = Math.min(requested, stored.getCount());
        if (amount == 0) { return new ItemExtract(resources, ItemStack.EMPTY); }
        ItemStack extracted = stored.copy(); extracted.setCount(amount);
        stored.shrink(amount);
        ClassicResources changed = resources.replace(List.of(bank.withItem(slot, stored)), simulate);
        return new ItemExtract(changed, extracted);
    }

    /** Offers must themselves be representable bank payloads (at most 16,000 mB). */
    public static FluidFill fillFluid(ClassicResources resources, ClassicBankKey key,
            FluidStack offered, boolean simulate) {
        ClassicResourceBank bank = require(resources, key, ClassicBankKind.FLUID_INPUT);
        FluidStack incoming = detached(offered);
        FluidStack stored = bank.fluid();
        if (incoming.isEmpty() || (!stored.isEmpty() && !stored.isFluidEqual(incoming))) {
            return new FluidFill(resources, 0);
        }
        int accepted = Math.min(incoming.getAmount(), ClassicResourceBank.FLUID_CAPACITY - stored.getAmount());
        if (accepted <= 0) { return new FluidFill(resources, 0); }
        incoming.setAmount(stored.getAmount() + accepted);
        return new FluidFill(resources.replace(List.of(bank.withFluid(incoming)), simulate), accepted);
    }

    public static FluidDrain drainFluid(ClassicResources resources, ClassicBankKey key,
            FluidStack requested, boolean simulate) {
        ClassicResourceBank bank = require(resources, key, ClassicBankKind.FLUID_OUTPUT);
        FluidStack request = detached(requested);
        FluidStack stored = bank.fluid();
        if (request.isEmpty() || !stored.isFluidEqual(request)) {
            return new FluidDrain(resources, FluidStack.EMPTY);
        }
        int amount = Math.min(request.getAmount(), stored.getAmount());
        FluidStack extracted = stored.copy(); extracted.setAmount(amount);
        stored.shrink(amount);
        return new FluidDrain(resources.replace(List.of(bank.withFluid(stored)), simulate), extracted);
    }

    private static ClassicResourceBank require(ClassicResources resources, ClassicBankKey key, ClassicBankKind kind) {
        if (key.kind() != kind) { throw new IllegalArgumentException("Automation operation violates bank role"); }
        return resources.bank(key).orElseThrow(() -> new IllegalArgumentException("Unknown retained bank"));
    }

    private static ItemStack detached(ItemStack stack) { return ClassicNativePayload.decodeItem(ClassicNativePayload.item(stack)); }
    private static FluidStack detached(FluidStack stack) { return ClassicNativePayload.decodeFluid(ClassicNativePayload.fluid(stack)); }

    private ClassicBankTransfers() { }
}
