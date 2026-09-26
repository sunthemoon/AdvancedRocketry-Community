package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Typed physical port; breaking it drops only its own Item resource once. */
public final class PrecisionAssemblerPortBlock extends BaseEntityBlock {
    private final PrecisionAssemblerPortType portType;

    public PrecisionAssemblerPortBlock(PrecisionAssemblerPortType portType, Properties properties) {
        super(properties);
        this.portType = Objects.requireNonNull(portType, "portType");
    }

    public PrecisionAssemblerPortType portType() {
        return portType;
    }

    @Override
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos position,
            BlockState newState,
            boolean moved
    ) {
        if (level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())) {
            if (!moved && level.getBlockEntity(position) instanceof PrecisionAssemblerPortBlockEntity port) {
                port.dropStoredItemForRemoval();
            }
            PrecisionAssemblerRuntime.markDirty(serverLevel, position);
        }
        super.onRemove(state, level, position, newState, moved);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos position, Explosion explosion) {
        return !PrecisionAssemblerRemovalPolicy.blocksRemoval(level, position)
                && super.canDropFromExplosion(state, level, position, explosion);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos position, Explosion explosion) {
        if (!PrecisionAssemblerRemovalPolicy.blocksRemoval(level, position)) {
            super.onBlockExploded(state, level, position, explosion);
        }
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos position, Entity entity) {
        return !PrecisionAssemblerRemovalPolicy.blocksRemoval(level, position)
                && super.canEntityDestroy(state, level, position, entity);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new PrecisionAssemblerPortBlockEntity(position, state);
    }
}
