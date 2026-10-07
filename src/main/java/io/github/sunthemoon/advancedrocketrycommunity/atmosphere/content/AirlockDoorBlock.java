package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Ordinary iron-door behavior with bounded, installed-atmosphere invalidation. */
public final class AirlockDoorBlock extends DoorBlock {
    private final BooleanSupplier enabled;

    public AirlockDoorBlock(Properties properties, BooleanSupplier enabled) {
        super(properties, BlockSetType.IRON);
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return enabled.getAsBoolean() ? super.getStateForPlacement(context) : null;
    }

    /** The supplied local state is already observed; only the guarded counterpart is read. */
    public CellObservation observeBoundary(LevelReader level, BlockPos position, BlockState state) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        if (level.isOutsideBuildHeight(position)) { return CellObservation.OPEN; }
        if (!level.hasChunkAt(position)) { return CellObservation.UNLOADED; }
        if (!state.is(this)) { return CellObservation.OPEN; }
        BlockPos otherPosition = counterpart(position, state);
        if (level.isOutsideBuildHeight(otherPosition)) { return CellObservation.OPEN; }
        if (!level.hasChunkAt(otherPosition)) { return CellObservation.UNLOADED; }
        BlockState other = level.getBlockState(otherPosition);
        if (!other.is(this) || state.getValue(HALF) == other.getValue(HALF)
                || state.getValue(FACING) != other.getValue(FACING)
                || state.getValue(HINGE) != other.getValue(HINGE)
                || state.getValue(POWERED) != other.getValue(POWERED)
                || state.getValue(OPEN) != other.getValue(OPEN)) {
            return CellObservation.OPEN;
        }
        return state.getValue(OPEN) ? CellObservation.TRAVERSABLE : CellObservation.SEALED;
    }

    @Override
    public void setOpen(Entity entity, Level level, BlockState state, BlockPos position, boolean open) {
        if (state.is(this) && state.getValue(OPEN) != open) { invalidate(level, position, state); }
        super.setOpen(entity, level, state, position, open);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos position,
            Block neighbour, BlockPos neighbourPosition, boolean moving) {
        invalidate(level, position, state);
        super.neighborChanged(state, level, position, neighbour, neighbourPosition, moving);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos position, BlockState previous, boolean moving) {
        invalidate(level, position, state);
        super.onPlace(state, level, position, previous, moving);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState next, boolean moving) {
        invalidate(level, position, state);
        super.onRemove(state, level, position, next, moving);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        invalidate(level, position, state);
        super.playerWillDestroy(level, position, state, player);
    }

    private void invalidate(Level level, BlockPos position, BlockState state) {
        if (level instanceof ServerLevel server && state.is(this)) {
            AtmosphereRuntime.invalidateDoorBoundary(server, position, counterpart(position, state));
        }
    }

    private static BlockPos counterpart(BlockPos position, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? position.above() : position.below();
    }
}
