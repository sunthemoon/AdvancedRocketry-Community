package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import java.util.Objects;
import javax.annotation.Nonnull;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.ItemStackHandler;

/** Small concrete stores; capability policy and process revisions remain external. */
final class PrecisionAssemblerItemStorage extends ItemStackHandler {
    private final Runnable changed;

    PrecisionAssemblerItemStorage(Runnable changed) {
        super(1);
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        validateSlotIndex(slot);
        return PrecisionAssemblerPortPersistence.acceptsItem(stack);
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (!PrecisionAssemblerPortPersistence.acceptsItem(stack)) {
            throw new IllegalArgumentException("Precision Assembler port rejected invalid item data");
        }
        super.setStackInSlot(slot, stack.copy());
    }

    ItemStack storedCopy() {
        return getStackInSlot(0).copy();
    }

    void loadStored(ItemStack stack) {
        if (!PrecisionAssemblerPortPersistence.acceptsItem(stack)) {
            throw new IllegalArgumentException("Precision Assembler port rejected loaded item data");
        }
        stacks.set(0, stack.copy());
    }

    void replaceStored(ItemStack stack) {
        loadStored(stack);
        changed.run();
    }

    @Override
    protected void onContentsChanged(int slot) {
        changed.run();
    }
}

final class PrecisionAssemblerEnergyStorage extends EnergyStorage {
    static final int RECEIVE_LIMIT = 1_000;

    private final Runnable changed;

    PrecisionAssemblerEnergyStorage(Runnable changed) {
        super(PrecisionAssemblerPortPersistence.ENERGY_CAPACITY, RECEIVE_LIMIT, 0, 0);
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    @Override
    public int receiveEnergy(int requested, boolean simulate) {
        if (requested <= 0) {
            return 0;
        }
        int received = super.receiveEnergy(requested, simulate);
        if (!simulate && received > 0) {
            changed.run();
        }
        return received;
    }

    void loadStored(int stored) {
        if (stored < 0 || stored > capacity) {
            throw new IllegalArgumentException("Precision Assembler port rejected loaded energy data");
        }
        energy = stored;
    }

    boolean consumeInternal(int requested) {
        if (requested < 0) {
            throw new IllegalArgumentException("Precision Assembler cannot consume negative energy");
        }
        if (requested > energy) {
            return false;
        }
        if (requested > 0) {
            energy -= requested;
            changed.run();
        }
        return true;
    }
}
