package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Prepared affected slots for caller-owned inventory/fluid transactions. */
public final class CanisterStackSwap {
    public static final int MAX_SLOTS = 64;

    private CanisterStackSwap() { }

    public static Optional<Plan> plan(ItemStack held, ItemStack swapped, List<ItemStack> storage) {
        if (storage.size() > MAX_SLOTS || swapped.getCount() != 1
                || !CanisterItemSafety.safe(held) || !CanisterItemSafety.safe(swapped)) {
            return Optional.empty();
        }
        CanisterSwapPlanner.Decision single = CanisterSwapPlanner.plan(held.getCount(), 0);
        if (single.accepted()) { return Optional.of(new Plan(swapped.copy(), -1, ItemStack.EMPTY)); }
        int destination = -1;
        for (int slot = 0; slot < storage.size(); slot++) {
            ItemStack existing = storage.get(slot);
            if (existing.isEmpty()) {
                if (destination < 0) { destination = slot; }
            } else {
                if (existing.getItem() == swapped.getItem()
                        && CanisterItemSafety.safe(existing)
                        && ItemStack.isSameItemSameTags(existing, swapped)
                        && existing.getCount() < Math.min(CanisterSwapPlanner.STACK_LIMIT, existing.getMaxStackSize())) {
                    destination = slot;
                    break;
                }
            }
        }
        CanisterSwapPlanner.Decision decision = CanisterSwapPlanner.plan(held.getCount(), destination < 0 ? 0 : 1);
        if (!decision.accepted()) { return Optional.empty(); }
        ItemStack heldAfter = held.copyWithCount(decision.heldCountAfter());
        ItemStack existing = storage.get(destination);
        ItemStack destinationAfter = existing.isEmpty() ? swapped.copy() : existing.copy();
        if (!existing.isEmpty()) { destinationAfter.grow(1); }
        return Optional.of(new Plan(heldAfter, destination, destinationAfter));
    }

    public record Plan(ItemStack held, int destinationSlot, ItemStack destination) { }
}
