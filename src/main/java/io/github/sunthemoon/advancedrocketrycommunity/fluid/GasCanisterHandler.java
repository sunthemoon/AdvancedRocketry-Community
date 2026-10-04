package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Bucket-style whole-unit swaps; callers must store getContainer() after execution. */
public final class GasCanisterHandler implements IFluidHandlerItem, ICapabilityProvider {
    public static final int CAPACITY = 1_000;
    private final Supplier<GasCanisterCatalog> catalog;
    private final LazyOptional<IFluidHandlerItem> capability = LazyOptional.of(() -> this);
    private ItemStack container;
    private boolean active = true;
    private boolean inspecting;

    public GasCanisterHandler(ItemStack container, Supplier<GasCanisterCatalog> catalog) {
        this.container = Objects.requireNonNull(container, "container");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @NotNull @Override public ItemStack getContainer() { return container; }
    @Override public int getTanks() { return 1; }

    @NotNull @Override
    public FluidStack getFluidInTank(int tank) {
        checkTank(tank);
        if (!usable()) { return FluidStack.EMPTY; }
        GasCanisterCatalog.Entry gas = catalog.get().forItem(container.getItem());
        return gas == null ? FluidStack.EMPTY : new FluidStack(gas.fluid(), CAPACITY);
    }

    @Override public int getTankCapacity(int tank) { checkTank(tank); return CAPACITY; }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack resource) {
        checkTank(tank);
        return usable() && !resource.isEmpty() && !resource.hasTag()
                && catalog.get().forFluid(resource.getFluid()) != null;
    }

    @Override
    public int fill(@NotNull FluidStack resource, FluidAction action) {
        Objects.requireNonNull(action, "action");
        if (!usable() || resource.isEmpty() || resource.getAmount() < CAPACITY || resource.hasTag()) { return 0; }
        GasCanisterCatalog bindings = catalog.get();
        GasCanisterCatalog.Entry gas = bindings.forFluid(resource.getFluid());
        if (container.getItem() != bindings.empty() || gas == null) { return 0; }
        if (action.execute()) { container = CanisterItemSafety.swap(container, gas.item()); }
        return CAPACITY;
    }

    @NotNull @Override
    public FluidStack drain(@NotNull FluidStack resource, FluidAction action) {
        if (!usable() || resource.isEmpty() || resource.getAmount() < CAPACITY || resource.hasTag()) {
            return FluidStack.EMPTY;
        }
        GasCanisterCatalog.Entry gas = catalog.get().forItem(container.getItem());
        return gas != null && gas.fluid() == resource.getFluid()
                ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
    }

    @NotNull @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        Objects.requireNonNull(action, "action");
        if (!usable() || maxDrain < CAPACITY) { return FluidStack.EMPTY; }
        GasCanisterCatalog bindings = catalog.get();
        GasCanisterCatalog.Entry gas = bindings.forItem(container.getItem());
        if (gas == null) { return FluidStack.EMPTY; }
        FluidStack result = new FluidStack(gas.fluid(), CAPACITY);
        if (action.execute()) { container = CanisterItemSafety.swap(container, bindings.empty()); }
        return result;
    }

    @NotNull @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> requested, @Nullable Direction side) {
        return active ? ForgeCapabilities.FLUID_HANDLER_ITEM.orEmpty(requested, capability) : LazyOptional.empty();
    }

    public void invalidate() {
        active = false;
        capability.invalidate();
    }

    private boolean usable() {
        if (!active || inspecting || container.getCount() != 1) { return false; }
        inspecting = true;
        ItemStack inspected = container;
        try {
            return CanisterItemSafety.safe(inspected) && active
                    && container == inspected && inspected.getCount() == 1;
        }
        finally { inspecting = false; }
    }

    private static void checkTank(int tank) {
        if (tank != 0) { throw new IndexOutOfBoundsException("Canister tank index must be zero"); }
    }
}
