package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRevision;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessCapabilityCache;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessEnergyPortStorage;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessFluidPortHandler;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessItemPortHandler;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** Lifecycle-scoped shared-port views that preserve the accepted Electrolyzer side matrix. */
final class ElectrolyzerCapabilityAdapter {
    private final ElectrolyzerInventory inventory;
    private final ElectrolyzerFluidTank waterTank;
    private final ElectrolyzerEnergyStorage energyStorage;
    private final BooleanSupplier inputLocked;
    private final BooleanSupplier accessAllowed;
    private final Runnable externalMutation;

    private ProcessCapabilityCache cache;
    private IItemHandler menuItems;
    private long capabilityEpoch;

    ElectrolyzerCapabilityAdapter(
            ElectrolyzerInventory inventory,
            ElectrolyzerFluidTank waterTank,
            ElectrolyzerEnergyStorage energyStorage,
            BooleanSupplier inputLocked,
            BooleanSupplier accessAllowed,
            Runnable externalMutation
    ) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.waterTank = Objects.requireNonNull(waterTank, "waterTank");
        this.energyStorage = Objects.requireNonNull(energyStorage, "energyStorage");
        this.inputLocked = Objects.requireNonNull(inputLocked, "inputLocked");
        this.accessAllowed = Objects.requireNonNull(accessAllowed, "accessAllowed");
        this.externalMutation = Objects.requireNonNull(externalMutation, "externalMutation");
        rebuild();
    }

    <T> LazyOptional<T> get(Capability<T> capability, @Nullable Direction side) {
        return cache.get(capability, side);
    }

    IItemHandler menuItems() {
        return menuItems;
    }

    IFluidHandler unsidedFluid() {
        return get(ForgeCapabilities.FLUID_HANDLER, null).orElseThrow(IllegalStateException::new);
    }

    void invalidate() {
        capabilityEpoch = Math.incrementExact(capabilityEpoch);
        cache.invalidate();
    }

    void revive() {
        if (!cache.isValid()) {
            rebuild();
        }
    }

    private void rebuild() {
        ProcessPortRevision revision = new ProcessPortRevision(0, externalMutation);
        ProcessCapabilityCache replacement = new ProcessCapabilityCache();
        long viewEpoch = capabilityEpoch;
        BooleanSupplier viewAccess = () -> viewEpoch == capabilityEpoch && accessAllowed.getAsBoolean();
        menuItems = item(ElectrolyzerPortPolicy.UNSIDED_ITEMS, () -> false, viewAccess, revision);
        replacement.register(
                ForgeCapabilities.ITEM_HANDLER,
                null,
                () -> menuItems
        );
        replacement.register(
                ForgeCapabilities.ITEM_HANDLER,
                Direction.UP,
                () -> item(ElectrolyzerPortPolicy.TOP_INPUT, inputLocked, viewAccess, revision)
        );
        replacement.register(
                ForgeCapabilities.ITEM_HANDLER,
                Direction.DOWN,
                () -> item(ElectrolyzerPortPolicy.BOTTOM_OUTPUT, () -> false, viewAccess, revision)
        );
        for (Direction side : Direction.Plane.HORIZONTAL) {
            replacement.register(
                    ForgeCapabilities.ITEM_HANDLER,
                    side,
                    () -> item(ElectrolyzerPortPolicy.SIDE_CHARGE, () -> false, viewAccess, revision)
            );
        }

        replacement.register(
                ForgeCapabilities.FLUID_HANDLER,
                null,
                () -> fluid(ElectrolyzerPortPolicy.UNSIDED_FLUID, viewAccess, revision)
        );
        replacement.register(
                ForgeCapabilities.ENERGY,
                null,
                () -> energy(ElectrolyzerPortPolicy.UNSIDED_ENERGY, viewAccess, revision)
        );
        for (Direction side : Direction.Plane.HORIZONTAL) {
            replacement.register(
                    ForgeCapabilities.FLUID_HANDLER,
                    side,
                    () -> fluid(ElectrolyzerPortPolicy.SIDE_FLUID, viewAccess, revision)
            );
            replacement.register(
                    ForgeCapabilities.ENERGY,
                    side,
                    () -> energy(ElectrolyzerPortPolicy.SIDE_ENERGY, viewAccess, revision)
            );
        }
        cache = replacement;
    }

    private IItemHandler item(
            io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortDefinition port,
            BooleanSupplier locked,
            BooleanSupplier viewAccess,
            ProcessPortRevision revision
    ) {
        return new ProcessItemPortHandler(
                inventory,
                port,
                locked,
                viewAccess,
                revision
        );
    }

    private IFluidHandler fluid(
            io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortDefinition port,
            BooleanSupplier viewAccess,
            ProcessPortRevision revision
    ) {
        return new ProcessFluidPortHandler(
                List.of(waterTank),
                port,
                inputLocked,
                viewAccess,
                revision
        );
    }

    private IEnergyStorage energy(
            io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortDefinition port,
            BooleanSupplier viewAccess,
            ProcessPortRevision revision
    ) {
        return new ProcessEnergyPortStorage(
                energyStorage,
                port,
                inputLocked,
                viewAccess,
                revision
        );
    }
}
