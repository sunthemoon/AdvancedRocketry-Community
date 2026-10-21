package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Probe-only device dispatch; the production endpoint adapters retain their separate authority. */
final class EscrowCargoProbeDevice {
    private final BlockEntity entity;
    private final boolean item;
    private EscrowCargoProbeDevice(BlockEntity entity, boolean item) {
        this.entity = entity; this.item = item;
        EscrowTags.require(item ? entity instanceof RocketItemCargoBlockEntity : entity instanceof RocketFluidCargoBlockEntity,
                "Cargo restart lost its device");
    }
    static EscrowCargoProbeDevice existing(ServerLevel level, BlockPos at, boolean item) {
        EscrowTestEndpoints.requireEnabled(level.getServer());
        return new EscrowCargoProbeDevice(level.getBlockEntity(at), item);
    }
    static EscrowCargoProbeDevice create(ServerLevel level, BlockPos at, RocketEntity rocket, boolean load, boolean item) {
        EscrowTestEndpoints.requireEnabled(level.getServer());
        level.setBlockAndUpdate(at, (item ? (load ? ModBlocks.ROCKET_ITEM_LOADER : ModBlocks.ROCKET_ITEM_UNLOADER)
                : (load ? ModBlocks.ROCKET_FLUID_LOADER : ModBlocks.ROCKET_FLUID_UNLOADER)).get().defaultBlockState());
        var entity = level.getBlockEntity(at); var tag = new CompoundTag();
        if (item) {
            var root = RocketItemCargoRoot.placed(rocket.ownerId().orElseThrow(), UUID.randomUUID(), load ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD)
                    .withBuffer(ItemCargoReconciliationFixtures.buffer(load)).withBinding(FluidFuelLoaderEscrowFixtures.binding(rocket));
            tag.put(RocketItemCargoRoot.KEY, root.encode());
        } else {
            var root = RocketFluidCargoRoot.placed(rocket.ownerId().orElseThrow(), UUID.randomUUID(), load ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD,
                    load ? LoaderTank.of(new FluidStack(Fluids.WATER, 5_000)) : LoaderTank.EMPTY)
                    .withBinding(FluidFuelLoaderEscrowFixtures.binding(rocket));
            tag.put(RocketFluidCargoRoot.KEY, root.encode());
        }
        EscrowTags.require(entity != null, "Missing probe device"); entity.load(tag);
        return new EscrowCargoProbeDevice(entity, item);
    }
    private RocketItemCargoRoot itemRoot() { return ((RocketItemCargoBlockEntity) entity).root().orElseThrow(); }
    private RocketFluidCargoRoot fluidRoot() { return ((RocketFluidCargoBlockEntity) entity).root().orElseThrow(); }
    UUID id() { return item ? itemRoot().device() : fluidRoot().device(); }
    RocketEscrowSection section() { return item ? itemRoot().escrow() : fluidRoot().escrow(); }
    int amount() { return item ? ItemCargoReconciliationFixtures.amount(itemRoot().buffer()) : fluidRoot().tank().amount(); }
}
