package io.github.sunthemoon.advancedrocketrycommunity.endgame.cargo;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameEnergyBuffer;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameNbt;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitDestinationState;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/**
 * The buffers of a cargo endpoint (ADR-056 section 2, ADR-059 section 2): a 4-slot input (the payload source,
 * insert-only for automation), a 9-slot receive buffer (the claim target, extract-only for automation) and an
 * input-only energy buffer with the system's capacity and per-tick input. Incoming payloads live in the ledger's
 * destination state, never here, until the incoming gate moves them. Reads are strict: a defect throws, so the device
 * root is quarantined.
 */
public final class CargoStorage {
    public static final int INPUT_SLOTS = 4;
    public static final int RECEIVE_SLOTS = 9;

    private final ItemStackHandler input;
    private final ItemStackHandler receive;
    private final EndgameEnergyBuffer energy;
    private final IItemHandler automation = new Automation();

    public CargoStorage(Runnable changed, Supplier<Level> level, int energyCapacity, int maxInputPerTick) {
        Objects.requireNonNull(changed, "changed");
        input = handler(INPUT_SLOTS, changed);
        receive = handler(RECEIVE_SLOTS, changed);
        energy = new EndgameEnergyBuffer(energyCapacity, maxInputPerTick, changed, level);
    }

    private static ItemStackHandler handler(int slots, Runnable changed) {
        return new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                changed.run();
            }
        };
    }

    public void reset() {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            input.setStackInSlot(slot, ItemStack.EMPTY);
        }
        for (int slot = 0; slot < RECEIVE_SLOTS; slot++) {
            receive.setStackInSlot(slot, ItemStack.EMPTY);
        }
        energy.set(0);
    }

    public void read(CompoundTag root) {
        energy.read(root, "energy");
        readItems(EndgameNbt.requireCompound(root, "input"), input);
        readItems(EndgameNbt.requireCompound(root, "receive"), receive);
    }

    public void write(CompoundTag root) {
        energy.write(root, "energy");
        root.put("input", input.serializeNBT());
        root.put("receive", receive.serializeNBT());
    }

    /** Strict inventory read: the slot count is fixed, slots are unique and in range, and every item exists. */
    private static void readItems(CompoundTag tag, ItemStackHandler handler) {
        EndgameNbt.requireKeys(tag, Set.of("Items", "Size"), "Cargo inventory");
        if (EndgameNbt.requireInt(tag, "Size") != handler.getSlots()) {
            throw new IllegalArgumentException("A cargo inventory has the wrong size");
        }
        ListTag items = EndgameNbt.requireList(tag, "Items", Tag.TAG_COMPOUND, handler.getSlots());
        boolean[] seen = new boolean[handler.getSlots()];
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            int slot = EndgameNbt.requireInt(item, "Slot");
            if (slot < 0 || slot >= seen.length || seen[slot]) {
                throw new IllegalArgumentException("A cargo inventory slot is invalid");
            }
            seen[slot] = true;
            ItemStack stack = ItemStack.of(item);
            if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()) {
                throw new IllegalArgumentException("A cargo inventory holds an unknown or invalid item");
            }
            handler.setStackInSlot(slot, stack);
        }
    }

    public ItemStackHandler input() {
        return input;
    }

    public ItemStackHandler receive() {
        return receive;
    }

    public EndgameEnergyBuffer energy() {
        return energy;
    }

    public IItemHandler automation() {
        return automation;
    }

    /** The input stacks in slot order, for the payload selection. */
    public List<ItemStack> inputStacks() {
        return List.of(input.getStackInSlot(0), input.getStackInSlot(1), input.getStackInSlot(2),
                input.getStackInSlot(3));
    }

    /** Whether all these stacks fit the handler at once, beyond its current contents. */
    private static boolean fitAll(ItemStackHandler handler, List<ItemStack> stacks) {
        ItemStackHandler copy = new ItemStackHandler(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            copy.setStackInSlot(slot, handler.getStackInSlot(slot).copy());
        }
        for (ItemStack stack : stacks) {
            if (!ItemHandlerHelper.insertItemStacked(copy, stack.copy(), false).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void insertAll(ItemStackHandler handler, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false).isEmpty()) {
                throw new IllegalStateException("A checked buffer did not take the whole payload");
            }
        }
    }

    /** A resolve hands a never-registered payload back to the input; false (and nothing changed) if it does not fit. */
    public boolean returnToInput(List<ItemStack> stacks) {
        if (!fitAll(input, stacks)) {
            return false;
        }
        insertAll(input, stacks);
        return true;
    }

    /** The ledger's view of the receive buffer: room for whole payloads, and the insertion of a moved one. */
    public TransitDestinationState.ReceiveBuffer receiveBuffer() {
        return new TransitDestinationState.ReceiveBuffer() {
            @Override
            public boolean fits(List<List<ItemStack>> payloads) {
                return fitAll(receive, payloads.stream().flatMap(List::stream).toList());
            }

            @Override
            public void insert(List<ItemStack> stacks) {
                insertAll(receive, stacks);
            }
        };
    }

    /** Breaking drops the input and the receive buffer; energy is lost (ADR-054 section 2). */
    public void drop(Level world, BlockPos position) {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            Containers.dropItemStack(world, position.getX(), position.getY(), position.getZ(),
                    input.getStackInSlot(slot));
            input.setStackInSlot(slot, ItemStack.EMPTY);
        }
        for (int slot = 0; slot < RECEIVE_SLOTS; slot++) {
            Containers.dropItemStack(world, position.getX(), position.getY(), position.getZ(),
                    receive.getStackInSlot(slot));
            receive.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    /** Automation inserts into the input (slots 0..3) and extracts from the receive buffer (slots 4..12). */
    private final class Automation implements IItemHandler {
        @Override
        public int getSlots() {
            return INPUT_SLOTS + RECEIVE_SLOTS;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < INPUT_SLOTS ? input.getStackInSlot(slot) : receive.getStackInSlot(slot - INPUT_SLOTS);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return slot >= 0 && slot < INPUT_SLOTS ? input.insertItem(slot, stack, simulate) : stack;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= INPUT_SLOTS && slot < getSlots() ? receive.extractItem(slot - INPUT_SLOTS, amount, simulate)
                    : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot >= 0 && slot < INPUT_SLOTS;
        }
    }
}
