package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameNbt;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/**
 * ADR-055 section 1 buffers of the drill controller: one lens slot (laser lens only, not consumed), an 18-slot
 * output that automation may only extract from, and a 200,000 FE input-only buffer taking at most 20,000 FE per
 * tick (ADR-054 section 8). Reads are strict: a defect throws, so the device root is quarantined.
 */
public final class LaserDrillStorage {
    public static final int OUTPUT_SLOTS = 18;
    public static final int ENERGY_CAPACITY = 200_000;
    public static final int MAX_INPUT_PER_TICK = 20_000;

    private final Runnable changed;
    private final Supplier<Level> level;
    private final ItemStackHandler lens;
    private final ItemStackHandler output;
    private final IItemHandler automation = new OutputOnly();
    private final IEnergyStorage energyInput = new EnergyInput();
    private int energy;
    private long receivedTick = Long.MIN_VALUE;
    private int receivedThisTick;

    public LaserDrillStorage(Runnable changed, Supplier<Level> level) {
        this.changed = Objects.requireNonNull(changed, "changed");
        this.level = Objects.requireNonNull(level, "level");
        lens = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return stack.is(ModItems.LASER_LENS.get());
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                changed.run();
            }
        };
        output = new ItemStackHandler(OUTPUT_SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                changed.run();
            }
        };
    }

    public void reset() {
        lens.setStackInSlot(0, ItemStack.EMPTY);
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            output.setStackInSlot(slot, ItemStack.EMPTY);
        }
        energy = 0;
    }

    public void read(CompoundTag root) {
        energy = EndgameNbt.requireInt(root, "energy");
        if (energy < 0 || energy > ENERGY_CAPACITY) {
            throw new IllegalArgumentException("Laser drill energy is outside its buffer");
        }
        readItems(EndgameNbt.requireCompound(root, "lens"), lens, true);
        readItems(EndgameNbt.requireCompound(root, "output"), output, false);
    }

    public void write(CompoundTag root) {
        root.putInt("energy", energy);
        root.put("lens", lens.serializeNBT());
        root.put("output", output.serializeNBT());
    }

    /** Strict inventory read: the slot count is fixed, slots are unique and in range, and every item exists. */
    static void readItems(CompoundTag tag, ItemStackHandler handler, boolean lensOnly) {
        EndgameNbt.requireKeys(tag, Set.of("Items", "Size"), "Laser drill inventory");
        if (EndgameNbt.requireInt(tag, "Size") != handler.getSlots()) {
            throw new IllegalArgumentException("A laser drill inventory has the wrong size");
        }
        ListTag items = EndgameNbt.requireList(tag, "Items", Tag.TAG_COMPOUND, handler.getSlots());
        boolean[] seen = new boolean[handler.getSlots()];
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            int slot = EndgameNbt.requireInt(item, "Slot");
            if (slot < 0 || slot >= seen.length || seen[slot]) {
                throw new IllegalArgumentException("A laser drill inventory slot is invalid");
            }
            seen[slot] = true;
            ItemStack stack = ItemStack.of(item);
            if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()
                    || lensOnly && (!stack.is(ModItems.LASER_LENS.get()) || stack.getCount() != 1)) {
                throw new IllegalArgumentException("A laser drill inventory holds an unknown or invalid item");
            }
            handler.setStackInSlot(slot, stack);
        }
    }

    public boolean lensPresent() {
        return !lens.getStackInSlot(0).isEmpty();
    }

    public int energy() {
        return energy;
    }

    /** Takes {@code cost} FE; the caller has checked the buffer holds it. */
    public void spend(int cost) {
        if (cost < 0 || cost > energy) {
            throw new IllegalStateException("The checked energy does not cover the cost");
        }
        energy -= cost;
        changed.run();
    }

    /** Whether the output can take the whole stack. */
    public boolean fits(ItemStack stack) {
        return !stack.isEmpty() && ItemHandlerHelper.insertItemStacked(output, stack.copy(), true).isEmpty();
    }

    /** Adds the whole stack; the caller has checked it fits. */
    public void insert(ItemStack stack) {
        if (!ItemHandlerHelper.insertItemStacked(output, stack, false).isEmpty()) {
            throw new IllegalStateException("The checked output did not take the whole stack");
        }
    }

    public int usedOutputSlots() {
        int used = 0;
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            if (!output.getStackInSlot(slot).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /** Breaking drops the lens and the output; energy is lost (ADR-054 section 2). */
    public void drop(Level world, BlockPos position) {
        Containers.dropItemStack(world, position.getX(), position.getY(), position.getZ(), lens.getStackInSlot(0));
        lens.setStackInSlot(0, ItemStack.EMPTY);
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            Containers.dropItemStack(world, position.getX(), position.getY(), position.getZ(),
                    output.getStackInSlot(slot));
            output.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    public ItemStackHandler lens() {
        return lens;
    }

    public ItemStackHandler output() {
        return output;
    }

    public IItemHandler automation() {
        return automation;
    }

    public IEnergyStorage energyInput() {
        return energyInput;
    }

    /** Test seam: a fixture's installed energy, within the buffer. */
    public void setEnergy(int value) {
        if (value < 0 || value > ENERGY_CAPACITY) {
            throw new IllegalArgumentException("Energy is outside the buffer");
        }
        energy = value;
        changed.run();
    }

    @Nullable
    private ServerLevel serverLevel() {
        return level.get() instanceof ServerLevel server && server.getServer().isSameThread() ? server : null;
    }

    /** Automation sees only the output buffer, extract only. */
    private final class OutputOnly implements IItemHandler {
        @Override
        public int getSlots() {
            return OUTPUT_SLOTS;
        }

        @Override
        public @Nonnull ItemStack getStackInSlot(int slot) {
            return output.getStackInSlot(slot);
        }

        @Override
        public @Nonnull ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @Nonnull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return serverLevel() == null ? ItemStack.EMPTY : output.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return output.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return false;
        }
    }

    /** Input only, effective on the server thread, at most 20,000 FE per tick; a simulation changes nothing. */
    private final class EnergyInput implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maximum, boolean simulate) {
            ServerLevel server = serverLevel();
            if (maximum <= 0 || server == null) {
                return 0;
            }
            long now = server.getGameTime();
            int usedThisTick = receivedTick == now ? receivedThisTick : 0;
            int accepted = Math.min(maximum, Math.min(ENERGY_CAPACITY - energy, MAX_INPUT_PER_TICK - usedThisTick));
            if (accepted <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += accepted;
                receivedTick = now;
                receivedThisTick = usedThisTick + accepted;
                changed.run();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maximum, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return ENERGY_CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
