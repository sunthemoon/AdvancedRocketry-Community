package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Exercises only the public provider contract; it neither stores nor mutates a source world. */
final class FixtureInventoryAdapter implements RocketBlockEntityAdapter {
    @Override
    public boolean canMove(BlockEntity blockEntity) {
        return blockEntity instanceof FixtureContainerBlockEntity;
    }

    @Override
    public CompoundTag capture(BlockEntity blockEntity) {
        return ((FixtureContainerBlockEntity) blockEntity).captureInventory();
    }

    @Override
    public boolean restore(BlockEntity blockEntity, CompoundTag payload) {
        return blockEntity instanceof FixtureContainerBlockEntity container
                && container.restoreInventory(payload);
    }
}
