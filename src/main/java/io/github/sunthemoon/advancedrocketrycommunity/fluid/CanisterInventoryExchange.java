package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** One-shot slot preparation. The caller revalidates owner/hand/config before callback-free publication. */
public final class CanisterInventoryExchange {
    private final int source;
    private final int destination;
    private final Witness held;
    private final Witness returned;
    private final ItemStack heldAfter;
    private final ItemStack returnedAfter;
    private boolean used;

    private CanisterInventoryExchange(int source, int destination, Witness held, Witness returned,
                                      ItemStack heldAfter, ItemStack returnedAfter) {
        this.source = source; this.destination = destination; this.held = held; this.returned = returned;
        this.heldAfter = heldAfter; this.returnedAfter = returnedAfter;
    }
    public static CanisterInventoryExchange prepare(List<ItemStack> inventory, int source, ItemStack empty) {
        if (inventory.size() != 36 && inventory.size() != 41 || source < 0 || source >= inventory.size()
                || source >= 36 && source != 40) { return null; }
        ItemStack held = inventory.get(source);
        if (!CanisterItemSafety.safe(held) || held.getCount() < 1 || held.getCount() > held.getMaxStackSize()) { return null; }
        Witness heldWitness = Witness.capture(held);
        Witness[] destinations = new Witness[36];
        for (int i = 0; i < 36; i++) {
            ItemStack candidate = inventory.get(i);
            if (candidate.isEmpty() || candidate.getItem() == empty.getItem()) { destinations[i] = Witness.capture(candidate); }
        }
        // Only main storage can receive the return; armor/offhand are never an arbitrary destination.
        var plan = CanisterStackSwap.plan(held, empty, inventory.subList(0, 36));
        if (plan.isEmpty()) { return null; }
        int destination = plan.get().destinationSlot();
        Witness returnedWitness = destination < 0 ? null : destinations[destination];
        return heldWitness == null || !heldWitness.matches(inventory.get(source))
                || destination >= 0 && (returnedWitness == null || !returnedWitness.matches(inventory.get(destination))) ? null
                : new CanisterInventoryExchange(source, destination, heldWitness, returnedWitness, plan.get().held(), plan.get().destination());
    }
    public boolean matches(List<ItemStack> inventory) {
        return !used && source < inventory.size() && held.matches(inventory.get(source))
                && (destination < 0 || destination < inventory.size() && returned.matches(inventory.get(destination)));
    }
    /** Only native list writes, no inventory add/drop/capability/foreign callback. */
    public void publish(List<ItemStack> inventory) {
        if (!matches(inventory)) { throw new IllegalStateException("Stale canister exchange"); }
        used = true; inventory.set(source, heldAfter);
        if (destination >= 0) { inventory.set(destination, returnedAfter); }
    }
    public static final class Witness {
        private final ItemStack stack;
        private final int count;
        private final CompoundTag tag;
        private Witness(ItemStack stack, int count, CompoundTag tag) { this.stack = stack; this.count = count; this.tag = tag; }
        public static Witness capture(ItemStack stack) {
            if (stack.hasTag() && !io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt.fits(stack.getTag(), 8192, 16, 1024)) { return null; }
            return new Witness(stack, stack.getCount(), stack.hasTag() ? stack.getTag().copy() : null);
        }
        public boolean matches(ItemStack actual) {
            return actual == stack && actual.getCount() == count
                    && (actual.getTag() == null ? tag == null
                    : io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt.fits(actual.getTag(), 8192, 16, 1024)
                    && actual.getTag().equals(tag));
        }
    }
}
