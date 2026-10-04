package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import io.github.sunthemoon.advancedrocketrycommunity.fluid.CanisterStackSwap;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.GasCanisterHandler;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;

/** Prepare on one detached bucket/canister and affected inventory slots before committing any tank resource. */
public final class TankContainerExchange {
    public record Plan(FluidStack fluid, CanisterStackSwap.Plan slots) { }

    public static Optional<Plan> plan(ItemStack held, List<ItemStack> storage, FluidStack fluid, int capacity) {
        return plan(held, storage, fluid, capacity, unit ->
                unit.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElse(null));
    }

    static Optional<Plan> plan(ItemStack held, List<ItemStack> storage, FluidStack fluid, int capacity,
            Function<ItemStack, IFluidHandlerItem> factory) {
        if (!safeItem(held) || !TankSave.fits(fluid) || held.getCount() > 16 || storage.size() > 64) {
            return Optional.empty();
        }
        ItemStack unit = held.copyWithCount(1);
        IFluidHandlerItem handler = factory.apply(unit);
        // Only inspected whole-unit implementations; arbitrary item callbacks cannot mutate a world tank.
        if (handler == null || !(handler instanceof GasCanisterHandler)
                && handler.getClass() != FluidBucketWrapper.class) {
            return Optional.empty();
        }
        FluidStack contained = handler.getFluidInTank(0);
        FluidStack after;
        if (!contained.isEmpty()) {
            if (!TankSave.safe(contained) || contained.getAmount() != 1_000
                    || TankCapacity.accepted(fluid.getAmount(), capacity, 1_000,
                            fluid.isEmpty() || fluid.isFluidEqual(contained)) != 1_000) {
                return Optional.empty();
            }
            FluidStack simulated = handler.drain(1_000, FluidAction.SIMULATE);
            if (!contained.isFluidStackIdentical(simulated)) { return Optional.empty(); }
            FluidStack taken = handler.drain(1_000, FluidAction.EXECUTE);
            if (!contained.isFluidStackIdentical(taken) || !handler.getFluidInTank(0).isEmpty()) {
                return Optional.empty();
            }
            after = fluid.isEmpty() ? contained.copy() : fluid.copy();
            if (!fluid.isEmpty()) { after.grow(1_000); }
        } else {
            if (fluid.getAmount() < 1_000 || fluid.hasTag()) {
                // Buckets/canisters cannot losslessly represent arbitrary native fluid metadata.
                return Optional.empty();
            }
            FluidStack offer = fluid.copy(); offer.setAmount(1_000);
            if (handler.fill(offer, FluidAction.SIMULATE) != 1_000
                    || handler.fill(offer, FluidAction.EXECUTE) != 1_000
                    || !handler.getFluidInTank(0).isFluidStackIdentical(offer)) {
                return Optional.empty();
            }
            after = fluid.copy(); after.shrink(1_000);
        }
        ItemStack swapped = handler.getContainer();
        if (!safeItem(swapped) || swapped.getCount() != 1 || !preserveMetadata(unit, swapped)
                || !TankSave.fits(after)) {
            return Optional.empty();
        }
        return CanisterStackSwap.plan(held, swapped, storage).map(slots -> new Plan(after, slots));
    }

    private static boolean preserveMetadata(ItemStack original, ItemStack swapped) {
        CompoundTag tag = original.getTag();
        if (tag != null) {
            CompoundTag target = swapped.getTag() == null ? new CompoundTag() : swapped.getTag().copy();
            for (String key : tag.getAllKeys()) {
                if (target.contains(key) && !target.get(key).equals(tag.get(key))) { return false; }
                target.put(key, tag.get(key).copy());
            }
            swapped.setTag(target);
        }
        return safeItem(swapped);
    }

    static boolean safeItem(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() <= 0 || stack.getCount() > stack.getMaxStackSize()
                || stack.getTag() != null && !BoundedNbt.fits(stack.getTag(), 8_192, 16, 1_024)) {
            return false;
        }
        CompoundTag encoded = stack.save(new CompoundTag());
        return !encoded.contains("ForgeCaps") && BoundedNbt.fits(encoded, 8_192, 16, 1_024);
    }

    private TankContainerExchange() { }
}
