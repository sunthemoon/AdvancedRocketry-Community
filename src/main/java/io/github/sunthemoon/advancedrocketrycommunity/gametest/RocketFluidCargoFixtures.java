package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderCarrier;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

final class RocketFluidCargoFixtures {
    static final String BATCH = "rocket_fluid_cargo";
    static final String TEMPLATE = "rocket_test";
    static FluidStack water(int amount) { return new FluidStack(Fluids.WATER, amount); }
    static LoaderTank tank(int amount) { return LoaderTank.of(water(amount)); }
    static CompoundTag owned(UUID owner, RocketCargoRole role, int amount) {
        return RocketFluidCargoRoot.placed(owner, UUID.randomUUID(), role, tank(amount)).encode();
    }
    static CompoundTag outer(Tag raw) { var outer = new CompoundTag(); outer.put(RocketFluidCargoRoot.KEY, raw); return outer; }
    static RocketFluidCargoBlockEntity place(GameTestHelper helper, BlockPos relative, RocketCargoRole role, Tag raw) {
        helper.setBlock(relative, role == RocketCargoRole.LOAD ? ModBlocks.ROCKET_FLUID_LOADER.get() : ModBlocks.ROCKET_FLUID_UNLOADER.get());
        var device = (RocketFluidCargoBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        if (raw != null) { device.load(outer(raw)); }
        return device;
    }
    static ItemStack item(RocketCargoRole role) {
        return new ItemStack(role == RocketCargoRole.LOAD ? ModItems.ROCKET_FLUID_LOADER.get() : ModItems.ROCKET_FLUID_UNLOADER.get());
    }
    static ItemStack carrier(RocketCargoRole role, UUID owner, int amount) {
        ItemStack stack = item(role); stack.getOrCreateTag().put(LoaderCarrier.KEY, new LoaderCarrier(owner, tank(amount)).encode());
        return stack;
    }
    static IFluidHandler port(RocketFluidCargoBlockEntity device) {
        return device.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow();
    }
    private RocketFluidCargoFixtures() { }
}
