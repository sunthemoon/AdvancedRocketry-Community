package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Exercises only the public provider contract; it neither stores nor mutates a source world. */
final class FixtureInventoryAdapter implements RocketBlockEntityAdapter {
    @Override
    public boolean canMove(BlockEntity blockEntity) {
        if (blockEntity instanceof FixtureContainerBlockEntity container && container.fault != null) {
            container.fault.enter("CAN_MOVE");
        }
        return blockEntity instanceof FixtureContainerBlockEntity;
    }

    @Override
    public CompoundTag capture(BlockEntity blockEntity) {
        FixtureContainerBlockEntity container = (FixtureContainerBlockEntity) blockEntity;
        if (container.fault != null) { container.fault.enter("CAPTURE"); }
        FixtureAdapterFaults.Probe restoreProbe = FixtureAdapterFaults.restoring();
        if (restoreProbe != null) { restoreProbe.readbacks++; }
        CompoundTag body = container.captureInventory();
        if (container.fault != null && container.fault.mode == FixtureAdapterFaults.Mode.CAPTURE_OVERSIZED) {
            // The complete envelope must fit 262,144 bytes; this bounded body already exceeds it.
            body.putByteArray("oversized_fixture", new byte[262_144]);
        }
        return body;
    }

    @Override
    public boolean restore(BlockEntity blockEntity, CompoundTag payload) {
        if (!(blockEntity instanceof FixtureContainerBlockEntity container) || !container.restoreInventory(payload)) {
            return false;
        }
        FixtureAdapterFaults.Probe probe = FixtureAdapterFaults.restoring();
        if (probe != null) {
            // Fail after writing only the new target's owned data, exercising real cleanup.
            probe.enter("RESTORE");
            if (probe.mode == FixtureAdapterFaults.Mode.RESTORE_FALSE) { return false; }
            if (probe.mode == FixtureAdapterFaults.Mode.RESTORE_MISMATCH) { container.clearContent(); }
        }
        return true;
    }
}
