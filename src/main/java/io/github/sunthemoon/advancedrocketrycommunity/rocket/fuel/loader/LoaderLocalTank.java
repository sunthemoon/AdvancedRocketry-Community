package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankContainerExchange;
import java.util.ArrayList;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Local tank and container operations share the device's non-reentrant authority lock. */
final class LoaderLocalTank {
    /**
     * One mB of the offered Fluid when it may enter {@code tank} at all, whatever the amount and the room: a registered
     * Fluid within the tank envelope, the same Fluid and native tag as any contents, and (Root's W82 decision) a
     * native envelope that an escrow operation could carry. The envelope's size does not depend on the amount, so an
     * offer larger than the tank is judged by its identity alone. Null when intake refuses it.
     */
    @Nullable
    static LoaderTank intake(LoaderTank tank, FluidStack offered) {
        if (offered == null || offered.isEmpty()) {
            return null;
        }
        LoaderTank unit;
        try {
            FluidStack one = offered.copy();
            one.setAmount(1);
            unit = LoaderTank.of(one);
        } catch (IllegalArgumentException refused) {
            return null;
        }
        if (!unit.escrowCarriable()
                || !tank.isEmpty() && (!tank.fluid().equals(unit.fluid()) || !Objects.equals(tank.tag(), unit.tag()))) {
            return null;
        }
        return unit;
    }

    /** The tank after accepting as much of {@code offered} as fits, or null when nothing is accepted. */
    @Nullable
    static LoaderTank accepting(LoaderTank tank, FluidStack offered) {
        LoaderTank unit = intake(tank, offered);
        int accepted = unit == null ? 0 : Math.min(offered.getAmount(), tank.room());
        return accepted <= 0 ? null : unit.withAmount(tank.amount() + accepted);
    }

    static int fill(FluidFuelLoaderBlockEntity loader, FluidStack offered, IFluidHandler.FluidAction action) {
        Objects.requireNonNull(action, "action");
        if (offered == null || !loader.beginLocal()) {
            return 0;
        }
        try {
            LoaderTank before = loader.tank();
            LoaderTank after = accepting(before, offered);
            if (after == null) {
                return 0;
            }
            if (action.execute()) {
                loader.writeTank(after);
            }
            return after.amount() - before.amount();
        } finally {
            loader.endLocal();
        }
    }

    static FluidStack drain(FluidFuelLoaderBlockEntity loader, int maximum, @Nullable FluidStack matching, IFluidHandler.FluidAction action) {
        Objects.requireNonNull(action, "action");
        if (!loader.beginLocal()) {
            return FluidStack.EMPTY;
        }
        try {
            LoaderTank tank = loader.tank();
            if (tank.isEmpty()) { return FluidStack.EMPTY; }
            FluidStack contents = tank.fluidStack();
            if (matching != null) {
                if (matching.isEmpty() || !contents.isFluidEqual(matching)) {
                    return FluidStack.EMPTY;
                }
                maximum = matching.getAmount();
            }
            int amount = Math.min(maximum, tank.amount());
            if (amount <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack result = tank.withAmount(amount).fluidStack();
            if (action.execute()) {
                loader.writeTank(tank.withAmount(tank.amount() - amount));
            }
            return result;
        } finally {
            loader.endLocal();
        }
    }

    static boolean validFluid(FluidFuelLoaderBlockEntity loader, FluidStack offered) {
        if (offered == null || !loader.beginLocal()) {
            return false;
        }
        try {
            return intake(loader.tank(), offered) != null;
        } finally {
            loader.endLocal();
        }
    }

    /** Local container use by the owner: one whole bucket or canister, prepared on detached copies before any write. */
    static boolean exchange(FluidFuelLoaderBlockEntity loader, Player player, InteractionHand hand) {
        if (!loader.beginLocal()) {
            return false;
        }
        try {
            if (!loader.admitted(player)) { return false; }
            var inventory = player.getInventory();
            ItemStack heldStack = player.getItemInHand(hand);
            ItemStack heldBefore = heldStack.copy();
            var storage = new ArrayList<ItemStack>();
            var slots = new ArrayList<Integer>();
            for (int slot = 0; slot < inventory.items.size(); slot++) {
                if (hand != InteractionHand.MAIN_HAND || slot != inventory.selected) {
                    storage.add(inventory.getItem(slot));
                    slots.add(slot);
                }
            }
            LoaderTank before = loader.tank();
            var plan = TankContainerExchange.plan(heldBefore, storage, before.fluidStack(), LoaderTank.CAPACITY);
            if (plan.isEmpty()) {
                return false;
            }
            LoaderTank after;
            try {
                after = LoaderTank.of(plan.get().fluid());
            } catch (IllegalArgumentException refused) {
                return false;
            }
            if (after.amount() > before.amount() && !after.escrowCarriable()) {
                return false;
            }
            var prepared = plan.get().slots();
            int destination = prepared.destinationSlot() < 0 ? -1 : slots.get(prepared.destinationSlot());
            if (!ItemStack.matches(player.getItemInHand(hand), heldBefore)) {
                return false;
            }
            loader.writeTank(after);
            player.setItemInHand(hand, prepared.held());
            if (destination >= 0) {
                inventory.setItem(destination, prepared.destination());
            }
            inventory.setChanged();
            return true;
        } finally {
            loader.endLocal();
        }
    }

    private LoaderLocalTank() { }
}
