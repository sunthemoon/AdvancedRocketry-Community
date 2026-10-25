package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlock;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

/** Level-less owned restore/recapture. Publication permanently transfers cleanup responsibility. */
public final class SealedTargetPreparation implements AutoCloseable {
    private final Thread owner;
    private final BlockEntity target;
    private boolean published;
    private boolean closed;

    private SealedTargetPreparation(Thread owner, BlockEntity target) {
        this.owner = owner;
        this.target = target;
    }

    public static Optional<SealedTargetPreparation> prepare(Thread owner, BlockPos pos, BlockState state,
            RocketBlockEntityPayload payload, RocketBlockEntityAdapters adapters) {
        requireOwner(owner);
        try { return prepareOwned(owner, pos, state, payload, adapters); }
        catch (RuntimeException failure) { return Optional.empty(); }
    }

    private static Optional<SealedTargetPreparation> prepareOwned(Thread owner, BlockPos pos, BlockState state,
            RocketBlockEntityPayload payload, RocketBlockEntityAdapters adapters) {
        if (state.isAir() || state.getBlock() instanceof SealedRocketBlock
                || state.hasBlockEntity() != (payload != null)) { return Optional.empty(); }
        if (payload == null) { return Optional.of(new SealedTargetPreparation(owner, null)); }
        if (!(state.getBlock() instanceof EntityBlock block) || !adapters.supportsPayload(payload)) {
            return Optional.empty();
        }
        BlockEntity target = block.newBlockEntity(pos, state);
        if (target == null) { return Optional.empty(); }
        var prepared = new SealedTargetPreparation(owner, target);
        boolean accepted = false;
        try {
            accepted = adapters.restoreDetached(owner, target, payload);
            return accepted ? Optional.of(prepared) : Optional.empty();
        } catch (RuntimeException failure) {
            return Optional.empty();
        } finally {
            if (!accepted) { prepared.close(); }
        }
    }

    public BlockEntity target() { requireOwner(owner); return target; }

    /** Called before the native publication callback; never reclaim resources after this point. */
    public void published() {
        requireOwner(owner);
        if (closed) { throw new IllegalStateException("Preparation already closed"); }
        published = true;
    }

    @Override public void close() {
        requireOwner(owner);
        if (closed) { return; }
        closed = true;
        if (published || target == null) { return; }
        try {
            if (target instanceof ChestBlockEntity || target instanceof BarrelBlockEntity) {
                var container = (RandomizableContainerBlockEntity) target;
                // Do not unpack a loot table or retain its future resource authority.
                container.lootTable = null;
                container.lootTableSeed = 0;
                container.getItems().clear();
            } else if (target instanceof PressurizedTankBlockEntity) {
                var empty = new CompoundTag(); empty.put(TankSave.ROOT, TankSave.encode(FluidStack.EMPTY));
                target.load(empty);
            } else if (target instanceof SatelliteBayBlockEntity) {
                target.load(new CompoundTag());
            }
        } finally {
            target.setRemoved();
        }
    }

    private static void requireOwner(Thread owner) {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Wrong sealed preparation thread"); }
    }
}
