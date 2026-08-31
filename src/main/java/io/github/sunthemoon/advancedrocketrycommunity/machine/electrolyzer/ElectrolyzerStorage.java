package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.function.BooleanSupplier;
import javax.annotation.Nonnull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

final class ElectrolyzerInventory extends ItemStackHandler {
    private final BooleanSupplier inputLocked;
    private final BooleanSupplier internalMutation;
    private final Runnable contentsChanged;
    private final Runnable inputChanged;

    ElectrolyzerInventory(
            BooleanSupplier inputLocked,
            BooleanSupplier internalMutation,
            Runnable contentsChanged,
            Runnable inputChanged
    ) {
        super(ElectrolyzerBlockEntity.SLOT_COUNT);
        this.inputLocked = inputLocked;
        this.internalMutation = internalMutation;
        this.contentsChanged = contentsChanged;
        this.inputChanged = inputChanged;
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (!stack.isEmpty()) {
            boolean allowedType = switch (slot) {
                case ElectrolyzerBlockEntity.SLOT_INPUT -> stack.is(ModItems.EMPTY_CANISTER.get());
                case ElectrolyzerBlockEntity.SLOT_CHARGE -> stack.is(Items.REDSTONE);
                case ElectrolyzerBlockEntity.SLOT_HYDROGEN -> stack.is(ModItems.HYDROGEN_CANISTER.get());
                case ElectrolyzerBlockEntity.SLOT_OXYGEN -> stack.is(ModItems.OXYGEN_CANISTER.get());
                default -> false;
            };
            boolean outputSlot = slot == ElectrolyzerBlockEntity.SLOT_HYDROGEN
                    || slot == ElectrolyzerBlockEntity.SLOT_OXYGEN;
            if (!allowedType
                    || !hasNoTag(stack)
                    || stack.getCount() > Math.min(stack.getMaxStackSize(), getSlotLimit(slot))
                    || (outputSlot && !internalMutation.getAsBoolean())
                    || (slot == ElectrolyzerBlockEntity.SLOT_INPUT
                    && inputLocked.getAsBoolean()
                    && !internalMutation.getAsBoolean())) {
                return;
            }
        }
        super.setStackInSlot(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (slot == ElectrolyzerBlockEntity.SLOT_INPUT) {
            return (internalMutation.getAsBoolean() || !inputLocked.getAsBoolean())
                    && stack.is(ModItems.EMPTY_CANISTER.get())
                    && hasNoTag(stack);
        }
        if (slot == ElectrolyzerBlockEntity.SLOT_CHARGE) {
            return stack.is(Items.REDSTONE) && hasNoTag(stack);
        }
        return internalMutation.getAsBoolean()
                && ((slot == ElectrolyzerBlockEntity.SLOT_HYDROGEN
                && stack.is(ModItems.HYDROGEN_CANISTER.get()))
                || (slot == ElectrolyzerBlockEntity.SLOT_OXYGEN
                && stack.is(ModItems.OXYGEN_CANISTER.get())));
    }

    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (slot == ElectrolyzerBlockEntity.SLOT_INPUT
                && inputLocked.getAsBoolean()
                && !internalMutation.getAsBoolean()) {
            return stack;
        }
        return super.insertItem(slot, stack, simulate);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        if (slot == ElectrolyzerBlockEntity.SLOT_INPUT
                && inputLocked.getAsBoolean()
                && !internalMutation.getAsBoolean()) {
            return ItemStack.EMPTY;
        }
        if (slot == ElectrolyzerBlockEntity.SLOT_CHARGE && !internalMutation.getAsBoolean()) {
            return ItemStack.EMPTY;
        }
        return super.extractItem(slot, amount, simulate);
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (slot == ElectrolyzerBlockEntity.SLOT_INPUT) {
            inputChanged.run();
        }
        contentsChanged.run();
    }

    private static boolean hasNoTag(ItemStack stack) {
        return !stack.hasTag();
    }
}

final class ElectrolyzerFluidTank extends FluidTank {
    private final BooleanSupplier inputLocked;
    private final BooleanSupplier internalMutation;
    private final Runnable contentsChanged;

    ElectrolyzerFluidTank(
            BooleanSupplier inputLocked,
            BooleanSupplier internalMutation,
            Runnable contentsChanged
    ) {
        super(
                ElectrolyzerBlockEntity.WATER_CAPACITY,
                stack -> stack.getFluid() == Fluids.WATER && !stack.hasTag()
        );
        this.inputLocked = inputLocked;
        this.internalMutation = internalMutation;
        this.contentsChanged = contentsChanged;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (inputLocked.getAsBoolean() && !internalMutation.getAsBoolean()) {
            return 0;
        }
        return super.fill(resource, action);
    }

    FluidStack drainInternal(int amount) {
        return super.drain(amount, FluidAction.EXECUTE);
    }

    void setFluidInternal(FluidStack stack) {
        super.setFluid(stack);
    }

    @Override
    protected void onContentsChanged() {
        contentsChanged.run();
    }
}

final class ElectrolyzerEnergyStorage extends EnergyStorage {
    private final Runnable contentsChanged;

    ElectrolyzerEnergyStorage(Runnable contentsChanged) {
        super(ElectrolyzerBlockEntity.ENERGY_CAPACITY, 1_000, 0, 0);
        this.contentsChanged = contentsChanged;
    }

    @Override
    public int receiveEnergy(int requested, boolean simulate) {
        if (requested <= 0) {
            return 0;
        }
        int received = super.receiveEnergy(requested, simulate);
        if (received > 0 && !simulate) {
            contentsChanged.run();
        }
        return received;
    }

    void addInternal(int amount) {
        energy = Math.min(capacity, Math.addExact(energy, amount));
        contentsChanged.run();
    }

    void consume(int amount) {
        if (amount < 0 || energy < amount) {
            throw new IllegalStateException("Electrolyzer attempted to consume unavailable energy");
        }
        energy -= amount;
        contentsChanged.run();
    }

    void setStored(int amount) {
        energy = Math.max(0, Math.min(capacity, amount));
    }
}

final class FillOnlyFluidHandler implements IFluidHandler {
    private final ElectrolyzerFluidTank tank;

    FillOnlyFluidHandler(ElectrolyzerFluidTank tank) {
        this.tank = tank;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tankIndex) {
        return tank.getFluid().copy();
    }

    @Override
    public int getTankCapacity(int tankIndex) {
        return ElectrolyzerBlockEntity.WATER_CAPACITY;
    }

    @Override
    public boolean isFluidValid(int tankIndex, @Nonnull FluidStack stack) {
        return tank.isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return tank.fill(resource, action);
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Nonnull
    @Override
    public FluidStack drain(int amount, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
